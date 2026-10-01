# Spent: Product Spec (rebuild)

Tags: **[C]** confirmed by Adi · **[P]** proposed, needs confirmation · **[?]** open decision (do not build until decided)

## 1. What it is
A privacy-first, local-only Android app that tracks income, expenses and transfers by reading bank/UPI SMS, plus manual entry. Nothing leaves the device.

## 2. Principles (non-negotiable)
1. **Local only.** No INTERNET permission, no analytics, no ads, no accounts, no cloud backup. [C]
2. **Logic before looks.** Features are built and tested in `:core` first. UI stays plain stock Material3 until the Polish phase.
3. **Money is INR `Long` paise.** Never Double/Float. [C]
4. **Scope is frozen per phase.** New ideas go to `PARKING_LOT.md`, not into code.
5. **Layered architecture.** UI, optional domain, data; Room is the single source of truth; the UI never touches the database directly. Details in `ARCHITECTURE.md`; reasons in `DECISIONS.md`.
6. **Efficient by design.** SMS import is batched, off the main thread, resumable and idempotent (re-importing creates no duplicates). Totals are computed in SQL.

## 3. Features

### F1 Accounts management [C]
User adds their accounts (name, bank, type, last 4 digits used to match SMS, opening balance). Required for SMS-to-account mapping and for transfer detection. Each account maintains a running current balance from its transactions. [C]

### F2 Transactions [C]
Types: Income, Expense, Transfer. Fields: amount (paise), type, account, category, date-time, counterparty/merchant, reference number, note, source (SMS or MANUAL), original SMS id.
- Manual add / edit / delete. [P]

### F3 Categories [C]
Each category has a type: Income, Expense or Transfer. Default set seeded on first run; user can add, edit, archive. [P for defaults/editing]

### F4 Budgets [C]
- [?] Period (monthly assumed), per-category vs overall, rollover, alerts.

### F5 SMS ingestion [C]
- **Sender registry:** bundled database mapping SMS sender IDs to banks. One bank has many sender IDs (different formats, account types, amount tiers). User can add/edit sender IDs.
- **Parser** extracts: amount, direction (debit/credit), account (last 4), merchant/counterparty, date-time, reference number, balance if present.
- **Two entry points** [P, my reading of the "cron job" note]: one-time import of existing SMS history, and a live receiver for incoming SMS.

### F6 Deduplication [C]
A strong dedup algorithm. One real-world transaction can produce several messages. See ARCHITECTURE.md.

### F7 Transfer detection [C]
Match same amount + close date/time, opposite directions, across the user's added accounts. Both accounts must exist in Accounts for a transfer to be recognised.

### F8 Unrecognised SMS handling [C]
Review queue for messages the parser cannot handle. Per-message action: **ignore this sender**. User can instead add the transaction manually.

### F9 Automatic category binning [C that it exists]
- [?] Rule design (merchant rules, learning from corrections, or both). Decided before its phase starts.

### F10 Reminder [C]
Scheduled notification nudging the user when the day's expenses are not yet added, received or configured.
- [?] Exact trigger condition and time.

### F11 Backup [C]
Local export/import file, no network. [P] JSON via the system file picker.

## 4. Non-goals
Networking of any kind, login, cloud sync, analytics, ads, multi-device, multi-currency.

## 5. Open decisions
| # | Decision | Needed before |
|---|----------|---------------|
| 1 | Auto-binning rule design | Phase 4 |
| 2 | Distribution: sideload vs Play Store (SMS permission policy is restrictive; verify before assuming publish) | Phase 3 |
| 3 | Budget shape (period, rollover, alerts) | Phase 4 |
| 5 | Reminder trigger condition and time | Phase 3 |
