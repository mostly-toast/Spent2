# Spent: Roadmap

**Rules:** one phase active at a time. Scope is frozen when a phase starts. Direction changes happen only between phases, are written down first, and get a git tag at each phase boundary (`phase-0-done`, ...).

**Active phase:** 2

## Phase 0: Skeleton
- Android Studio wizard project (Empty Activity, Compose), then split into `:core` (Kotlin/JVM) and `:app`.
- Version catalog, GitHub Actions CI (build, `:core:test`, lint, no-INTERNET manifest check), `allowBackup=false`.
- These six docs committed: SPEC, ARCHITECTURE, AGENTS, ROADMAP, PARKING_LOT, DECISIONS.
- Package layout from ARCHITECTURE.md §5 created (empty folders are fine).
- Done when: app runs on the phone via wireless adb; CI is green.

## Phase 1: Domain + storage
- `:core` models: Account, Category, Transaction (money as paise). Repository interfaces.
- `:app` data layer: Room entities, DAOs, mappers, repository implementations. Room is the single source of truth; repositories return Flows of `:core` models. Seed default categories. Add the indexes from ARCHITECTURE.md §12.
- Plain screens (one ViewModel each, a single `uiState`) to add and list accounts and transactions manually.
- Done when: manual add/edit/delete works end to end; `:core` tests pass.

## Phase 2: SMS pipeline in `:core` (no Android)
- Define the `SmsSource` and `Clock` ports. Sender normalizer and pre-filter, content-based transaction detector, parser, account resolution (digits, then mapped sender, then registry hint), layered dedup, transfer matcher, wired together as use cases.
- Fixture messages built from real, anonymized messages from Adi's own phone (his 3 banks first), fed through a fake `SmsSource` in tests. Seed sender list from the TRAI download, cross-checked, then extended from the phone's actual senders.
- Fixtures: run `tools/anonymize_sms.py` on Adi's phone (about 200 messages chosen for template variety), skim once, commit only the anonymized output.
- Measure parser health on the fixtures (see ARCHITECTURE.md section 15). Record the result in `DECISIONS.md`.
- Done when: fixture suite passes; re-importing the same messages creates no duplicates.

## Phase 3: Android integration
- SMS permissions flow; the real `SmsSource` adapter (ContentResolver) and live receiver as thin adapters over the `:core` pipeline; history import worker (batched, off the main thread, resumable); review queue, "ignore sender", daily reminder.
- Needs decided first: distribution (SPEC #2), reminder trigger (#4).
- Done when: real SMS on the phone appear as correct transactions.

## Phase 4: Budgets + auto category binning
- Needs decided first: auto-binning rules (SPEC #1), budget shape (#3).

## Phase 5: Backup
- JSON export/import through the system file picker.

## Phase 6: UI polish
The only phase where visual design happens: theme, colors, typography, motion, layouts.
