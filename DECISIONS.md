# Spent: Decision Log

One entry per major decision: what we chose, what we rejected, why. To reverse a decision, add a new entry that says why; never silently change course. Agents must not reverse or work around anything here.

Format: `D-NNN · date · status`. Status: Accepted, Superseded by D-NNN.

## D-001 · 2026-10-02 · Accepted: Google's layered architecture
**Chosen:** UI layer, optional domain layer, data layer; Room as single source of truth; unidirectional data flow; repositories as the only data access; ports and adapters at the edges (repository interfaces, `SmsSource`, `Clock`).
**Rejected:** full Clean Architecture (more layers and interfaces than a small local app needs), and a screen-by-screen structure with no layering (leads to logic in composables).
**Why:** it's the officially recommended approach, it keeps the UI from touching the database directly, and it makes the SMS logic testable without a phone.

## D-002 · 2026-10-02 · Accepted: two modules, `:core` and `:app`
**Chosen:** `:core` is pure Kotlin/JVM; `:app` is Android. Gradle enforces that `:core` never imports Android.
**Rejected:** one module (no enforced boundary); many feature modules (too much overhead now).
**Why:** it stops an AI agent from mixing Android code into business logic. Split `:data` later only if needed.

## D-003 · 2026-10-02 · Accepted: logic first, plain UI, no terminal UI
**Chosen:** build and test logic in `:core` and keep screens deliberately plain stock Material3 until the Polish phase.
**Rejected:** a separate TUI first (can't run Android-only parts, and the frontend would be built twice).
**Why:** protects the architecture from UI drift without throwaway work.

## D-004 · 2026-10-02 · Accepted: local-only
**Chosen:** no INTERNET permission, no analytics or accounts, `allowBackup=false`. Backup is a local export file.
**Why:** privacy is the product. CI enforces it.

## D-005 · 2026-10-02 · Accepted: money as `Long` paise
**Rejected:** Double/Float (rounding errors).

## D-006 · 2026-10-02 · Accepted: manual DI, no Hilt
**Why:** small app, fewer build-time moving parts. Revisit if wiring becomes painful.

## D-007 · 2026-10-02 · Accepted: WorkManager + SMS receiver instead of "cron"
**Chosen:** WorkManager for history import and the daily reminder; a manifest receiver for live SMS.
**Why:** Android has no cron.

## D-008 · 2026-10-02 · Accepted: Kotlin only
**Rejected:** mixing Java in. **Why:** nothing gained, and the agent would mix both.

## D-009 · 2026-10-02 · Accepted: INR accounts with running balances
**Chosen:** amounts use INR `Long` paise. An account is created with an opening balance and maintains a running current balance as its transactions change.
**Rejected:** multi-currency support and opening-balance-only accounts with no maintained current balance.
**Why:** the app is for Indian bank and UPI SMS, and users need the current balance per account.

## D-010 · 2026-10-02 · Accepted: Phase 1 manual transaction invariants
**Chosen:** account IDs are UUID strings; last-four account digits are required and exactly four digits; account deletion is blocked while referenced by transactions. Income and expense require a category. Transfers have no category, require two different accounts, and atomically debit the source and credit the destination. Editing or deleting a transaction atomically reverses its former balance effect before applying a replacement effect.
**Rejected:** nullable/unchecked account match digits, categorised transfers, and deleting referenced accounts.
**Why:** these rules preserve reliable future SMS matching and account-balance integrity.

## D-011 · 2026-10-02 · Accepted: sender names are hints, account digits are the key
**Chosen:** match an SMS to an account by the last digits in the message; fall back to a user-mapped sender, then to the registry hint; otherwise ask the user once and remember.
**Rejected:** a complete sender-name database as the matching key. The TRAI list is a 2020 snapshot, 2 of Adi's 3 banks matched it, and banks use several headers.
**Why:** no list will ever be complete; digits work for banks nobody listed.

## D-012 · 2026-10-02 · Accepted: the sender suffix is a pre-filter only
**Chosen:** drop -P and -G, keep -T and -S, treat a missing suffix as unknown. Content decides.
**Why:** -T includes OTPs and -S includes non-transaction notices, and older messages have no suffix.

## D-013 · 2026-10-02 · Accepted: never silently drop a possible real transaction
**Chosen:** layered dedup (reference number, then balance-after, then a weak time-window rule across different channels only); unclear cases keep both rows and flag them.
**Why:** a visible duplicate is cheap to fix; a lost record is not.

## D-014 · 2026-10-02 · Accepted: no code copied from FinTrack or other unlicensed repos
**Chosen:** write our own parser from our own anonymized fixtures. Other projects may inform ideas and message formats only; any code reuse requires a permissive license (MIT, Apache-2.0, BSD) or written permission.
**Why:** FinTrack's README says Proprietary and has no LICENSE file. Public on GitHub is not permission to copy.

## D-015 · 2026-10-03 · Accepted: account digits are a 3-4 digit suffix match
**Chosen:** banks show 3 digits (`XX943`) or 4 (`X7095`). The user stores 4 digits; a message matches when its visible digits equal the end of the account number (minimum 3). Ties go to the bank hint, then the review queue.
**Rejected:** exact 4-digit equality, and padding or guessing the hidden digit.
**Why:** found on real messages: ICICI shows only 3 digits. Padding invents data.

## D-016 · 2026-10-03 · Accepted: fixtures are authoritative
**Chosen:** the parser is fixed to match the fixtures, never the other way round. A test or fixture changes only when the fixture is proven wrong, by Adi.
**Why:** an agent edited expected values to make failing tests pass.

## D-017 · 2026-10-04 · Accepted: rules now, model later (optional)
**Chosen:** the parser sits behind a `TransactionExtractor` interface. Exact rules handle amount, balance, date, reference and account digits; template-aware extractors handle counterparty. A small on-device model is not part of any phase.
**Go/no-go for a model:** after about 200 fixtures exist, measure how often the rules fail on unseen templates (parser health metric, ARCHITECTURE section 15). If it is high, evaluate a bundled tagging (NER-style) model, never a downloaded or hosted one, with its output checked against the rules.
**Why:** a model would have to be bundled (no INTERNET), costs APK size, needs labeled data we do not have yet, and generative models can invent digits. Decide on measured data, not guesses.

## D-018 · 2026-10-04 · Accepted: real SMS never leave Adi's machine
**Chosen:** fixtures come from `tools/anonymize_sms.py`, run locally. Raw exports and the mapping stay in `~/sms_private`, outside git. No real SMS is pasted into any chat or AI tool, including free models that may train on prompts.
**Why:** privacy is the product.

## D-019 · 2026-10-04 · Accepted: SMS rows may be uncategorised until binning
**Chosen:** D-010's "income and expense require a category" holds for `MANUAL` rows. A row whose source is `SMS` may have no category, because automatic binning is Phase 4 work. SMS rows are listed uncategorised until then.
**Rejected:** seeding an "Uncategorised" category per type (it invents a category the user never chose and still needs cleanup), and refusing to persist uncategorised SMS rows (they would be silently lost).
**Why:** binning must not block ingestion; a visible uncategorised row is honest, a missing row is not.

## D-020 · 2026-10-04 · Accepted: transactions carry their own dedup and transfer keys (Room schema v2)
**Chosen:** `Transaction` gains `balanceAfterPaise`, `senderHeader`, `smsHash`, `needsReview` and `transferId`, added to the Room entity with a v1 to v2 migration. `originalSmsId` keeps the real Android SMS id. Dedup lookups become targeted repository queries, not a full-table read.
**Rejected:** hiding balance-after and channel in the note text or re-reading the whole table per message.
**Why:** ARCHITECTURE sections 10 and 12 cannot be implemented without them, and the pipeline must not load the whole table to decide a duplicate.

## D-021 · 2026-10-04 · Accepted: a detected transfer keeps both legs
**Chosen:** when a transfer is detected, both leg rows stay, each stamped with `transferId`, and one `TRANSFER` summary row is inserted alongside them in a single transaction. The summary row carries no balance effect; the legs carry it. List reads hide legs, so one transfer shows once. Totals must exclude rows with a `transferId` (a `WHERE transferId IS NULL` clause on every aggregate).
**Rejected:** deleting the leg rows and keeping only the transfer (re-import then cannot match the deleted leg's SMS hash and produces a stray duplicate), and one transfer row with no legs (dedup would then depend on guessing whether a message was already absorbed).
**Why:** idempotent re-import is a Phase 2 done condition, and no real transaction may ever be dropped (D-013).

## D-022 · 2026-10-04 · Accepted: the bundled registry is the TRAI list cross-checked against the phone
**Chosen:** `core/src/main/resources/senders.json` holds every sender Adi collected: 1535 headers from TRAI's June 2020 compiled list plus the headers seen on his phone, each with `kind`, `status` (`seen`, `seen-unverified`, `listed`) and `source`. Lookup is by the normalised header, case-insensitively.
**Rejected:** a hand-written list of a few banks, and matching on the sender name as a key.
**Why:** the registry only supplies a bank hint (D-011); keeping the whole list with its provenance makes the cross-check auditable and costs about 200 KB.

## D-023 · 2026-10-04 · Measurement: parser health on the available fixtures
Measured by `FixtureHealthTest` on 60 messages (6 in `synthetic_fixtures.txt`, 54 in the `NNN.txt` block of the same file). Result: 47 read as transactions, 42 fully parsed, 5 parsed with no account digits, 3 queued for review because their amount cannot be read, 10 dropped as not transactions. Every remaining drop is a genuine non-transaction: activation notices, account linking, mandate creation and revocation, a transaction-limit notice, and a message whose debit wording is negated.

The 5 without account digits are one UPI LITE top-up and four Google AutoPay debits. Neither carries an account number, so both resolve only through a user sender mapping (D-011). The 3 queued messages are holds whose amount arrives truncated (`INR 49621.723`); the amount is never invented, so they wait for the user.

This measurement is provisional: it covers only the agent-written `synthetic_fixtures.txt`, because the anonymized corpus from `tools/anonymize_sms.py` does not exist in the repo yet. Re-measure and replace this entry when Adi commits that corpus.

## D-024 · 2026-10-04 · Accepted: a wallet top-up, a lien and a hold are debits
**Chosen:** wording that moves money out of an account is a debit even without debit wording: a wallet top-up ("UPI LITE Top-up ... has been successful") and a blocked amount ("ASBA lien of ... has been applied", "A hold of ... has been applied") are debits. A message with a readable direction and an identifiable account but an unreadable amount goes to the review queue rather than being dropped.
**Rejected:** dropping them as non-transactions, and reading a truncated amount such as `49621.723` as `496.21`.
**Why:** Adi confirmed both move money out of the account, the hold is released when the IPO is not allotted, and a silent drop loses a real transaction (D-013).

**Not yet handled:** the release side of a hold (an allotment failure returning the money) has no sample in the corpus. The corpus was searched for it: the only reversal wording present is `revoked`, in four mandate messages ("the mandate of INR 86,561.15 ... is revoked through RRN ...", "your AutoPay mandate is successfully revoked towards Google for Rs 5,218.59"). A mandate is a permission, not a movement, and the amount named is its limit, so `revoked` must not create a credit there. Until a real release sample exists, any message that mentions a `lien`, `hold` or `margin` is queued for review when it cannot be classified, instead of being dropped or guessed at. One anonymised sample from Adi's phone is needed before adding the credit wording; fixture text must not be invented.