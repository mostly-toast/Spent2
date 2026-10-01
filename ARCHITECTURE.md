# Spent: Architecture

Follows Google's official Android app architecture: layers, a single source of truth, and unidirectional data flow. Decision recorded in `DECISIONS.md` (D-001). Tags: **[P]** = proposal, can still change.

## 1. Principles
1. **Layered, one-way dependencies.** UI layer → (optional) domain layer → data layer. A lower layer never knows about a higher one.
2. **Single source of truth.** Room is the only owner of persisted data. The UI observes the database through Flow and never keeps its own copy.
3. **Unidirectional data flow.** State flows down: database → repository → (use case) → ViewModel → UI. Events flow up: UI → ViewModel → (use case) → repository → database.
4. **Repositories are the only door to data.** UI, ViewModels and use cases never touch a DAO, a `ContentResolver` or a file directly.
5. **Domain layer only where logic is complex.** That means the SMS pipeline, transfers, dedup, budgets and category binning. Simple screens go ViewModel → repository.
6. **Ports and adapters at the edges.** Interfaces (repositories, `SmsSource`, `Clock`) are defined in `:core`. Android implementations live in `:app`.

## 2. Layers
```
UI layer       (:app)   Composables  <-- uiState (StateFlow) --  ViewModel
                                      --- events (functions) -->
Domain layer   (:core)  Use cases: SMS pipeline, dedup, transfer match,
                        binning, budgets            [only where needed]
Data layer     (:core)  Repository INTERFACES + models
               (:app)   Repository IMPLEMENTATIONS over Room DAOs, SmsSource
                        adapter, DataStore, backup file I/O
```

## 3. Dependency contract
| Layer / module | May depend on | Must NOT |
|---|---|---|
| Composables | ViewModel `uiState`, `:core` models | ViewModels' internals, DAOs, repositories, use cases |
| ViewModel | use cases, repository interfaces | DAOs, Room entities, `Context`, UI classes |
| Use cases (`:core`) | `:core` models, repository/port interfaces | anything `android.*`, Room, Compose |
| Repository impl (`:app`) | DAOs, mappers, ports | UI layer classes |
| `:core` | Kotlin stdlib, coroutines, `java.time` | Android, Room, any UI or storage library |

## 4. Tech stack [P]
| Area | Choice | Why |
|---|---|---|
| Language | Kotlin only | Compose, Room, Coroutines are Kotlin-first |
| UI | Jetpack Compose + Material3, Navigation Compose | Stock components only until the Polish phase |
| State | ViewModel + `StateFlow` | One `uiState` per screen |
| DI | Manual `AppContainer` (no Hilt) | Small app, fewer build moving parts |
| Database | Room with KSP | Single source of truth |
| Background | WorkManager (history import, daily reminder) + manifest `SMS_RECEIVED` receiver | No cron on Android |
| Settings | DataStore (Preferences) | Reminder time etc. |
| Backup | kotlinx.serialization (JSON) + Storage Access Framework | No network needed |
| Time | `java.time` (minSdk 26), behind a `Clock` port | Testable time |
| Async | Coroutines + Flow | |
| Tests | JUnit4 + kotlin.test for `:core`; instrumented tests only for Room | Fast agent feedback |
| Build | Gradle version catalog (`gradle/libs.versions.toml`) | One place for versions; use the wizard's current stable versions |
| CI | GitHub Actions: `assembleDebug`, `:core:test`, lint, manifest check | Gate for AI-written code |

## 5. Modules and packages
```
:core  (pure Kotlin/JVM; NO android.*, NO Room annotations)
  model/        Account, Category, Transaction, Money helpers, ParsedTransaction
  repository/   interfaces only: AccountRepository, TransactionRepository, ...
  port/         SmsSource, Clock
  sms/          sender registry logic, parser
  usecase/      ImportSms, ProcessIncomingSms, MatchTransfers, BindCategory, ...
  rules/        dedup, transfer matching, budget math
:app   (Android)
  ui/<feature>/ Screen, ViewModel, UiState (one folder per feature)
  data/db/      Room entities, DAOs, database, mappers (entity <-> :core model)
  data/repo/    repository implementations
  data/sms/     SmsSource adapter (ContentResolver), SMS receiver
  work/         WorkManager workers
  notify/       notification channels and reminder
  di/           AppContainer
```
`:app` depends on `:core`, never the reverse; Gradle enforces it. If `data/` grows large, split a `:data` module later.

## 6. UI layer rules
- One ViewModel per screen exposing a single `uiState: StateFlow<XxxUiState>`.
- UiState is an immutable data class or sealed interface, always derived from repository Flows. Convert with `stateIn(viewModelScope, WhileSubscribed(5_000), initial)`.
- User actions are plain ViewModel functions (`onSaveClicked()`), never state mutated by composables.
- No business logic or I/O in composables.

## 7. Data layer rules
- Reads return `Flow`; writes are `suspend`.
- Any write touching more than one row or table runs in a single Room transaction.
- Repositories expose `:core` models, never Room entities. Mapping happens only in `data/`.
- Low-level exceptions are caught in the data layer and returned as typed results, not thrown to the UI.

## 8. Domain layer rules
- A use case is a class with a single `operator fun invoke`.
- Pure and deterministic: time comes from the `Clock` port, SMS from the `SmsSource` port.
- Everything here has unit tests with fixtures.

## 9. SMS pipeline
```
SmsSource.readAll(since) | live receiver  ->  RawSms
  -> ignored-sender filter
  -> sender registry (sender ID -> bank)       unknown sender -> review queue
  -> parser -> ParsedTransaction               unparseable    -> review queue
  -> account match (bank + last 4)             no account     -> review queue
  -> dedup
  -> transfer matcher
  -> category binning
  -> TransactionRepository (Room)
```
The whole pipeline is a use case in `:core`. The receiver and the history worker are thin adapters that convert Android input into `RawSms` and call it.

## 10. Deduplication [P]
1. Reference number present: duplicate if same account + same reference number.
2. Otherwise a duplicate candidate when same account, amount, direction and timestamps within N minutes (N is a named constant, tuned on real data).
3. Idempotent: re-importing history never creates new rows.
4. A hash of the raw SMS short-circuits exact repeats.

## 11. Transfer matching [P]
- Candidate pair: opposite directions, same amount, two different accounts both added by the user, timestamps within N minutes.
- A matched pair becomes one Transfer with two legs, excluded from income/expense totals.
- If the counterpart account isn't in Accounts, the message stays a normal income/expense.
- Several candidates: send to the review queue instead of guessing.

## 12. Efficiency rules [P]
- History import runs in batches, one Room transaction per batch, on `Dispatchers.IO`, never on the main thread.
- Import is resumable: remember the last processed SMS id/timestamp.
- Index the columns used by dedup and listing: `(accountId, referenceNo)`, `(accountId, timestamp)`.
- Totals and budget sums are SQL aggregates (`SUM`, `GROUP BY`), not loaded into memory.
- List screens query one month at a time, not the whole table.

## 13. Data rules
- Amounts: `Long` paise. Dates: `java.time`, stored as epoch millis plus zone id.
- Raw SMS text is stored locally for re-parsing; the user can delete it.

## 14. Permissions and privacy enforcement
- Allowed: `READ_SMS`, `RECEIVE_SMS`, `POST_NOTIFICATIONS`.
- Forbidden: `INTERNET` and any networking library. CI greps the merged manifest and fails if INTERNET appears.
- `android:allowBackup="false"` plus cloud backup excluded in data extraction rules.

## 15. Test fixtures
`core/src/test/resources/sms/`: anonymized sample messages (fake names, numbers, reference IDs), grouped by bank. A parser change is only done when all fixtures pass.
