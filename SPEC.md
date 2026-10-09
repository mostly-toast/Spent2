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
User adds their accounts (name, bank, type, last 4 digits). The last digits are the **primary key for matching an SMS to an account**. Banks mask accounts differently (`XX943`, `X7095`, `xxxx 7535`), so a message shows only 3 or 4 digits. The user enters 4 digits; a message matches an account when its visible digits equal the end of that account's number (suffix match, minimum 3 digits). If two accounts match, the bank hint breaks the tie; otherwise the message goes to the review queue. Never pad or invent digits. The user can also map a sender ID to an account, for messages that carry no digits. Required for transfer detection too.
- Each account is created with an opening balance and maintains a running current balance as its transactions change. [C]

### F2 Transactions [C]
Types: Income, Expense, Transfer. Fields: amount (paise), type, account, category, date-time, counterparty/merchant, reference number, note, source (SMS or MANUAL), original SMS id.
- Manual add / edit / delete. [C]

### F3 Categories [C]
Each category has a type: Income, Expense or Transfer. Default set seeded on first run; user can add, edit, archive. [P for editing]
The first-run defaults are exactly: expense: Food, Travel, Shopping, Bills & Utilities, Health, Education, Entertainment, Other Expense; income: Salary, Grants, Interest, Freelance, Other Income. Transfers have no category. [C]

### Phase 1 manual-entry decisions [C]
- INR is the only currency. Account bank and type are free text; account last four digits are required and exactly four digits.
- Account IDs are UUID strings. Deletion is blocked while any transaction references the account.
- Income and expense require a category, except rows whose source is SMS (see DECISIONS D-019); transfers have no category and require distinct source and destination accounts.
- Account creation persists opening and current balances. Transaction create, edit, and delete atomically apply or reverse the affected account balance changes.

### F4 Budgets [C]
- [?] Period (monthly assumed), per-category vs overall, rollover, alerts.

### F5 SMS ingestion [C]
- **Sender registry:** bundled list mapping SMS sender IDs to banks. Sender names are *hints*, not keys: the TRAI list is a 2020 snapshot and one bank registers several headers. User can add/edit sender IDs.
- **Detection by content, not by name:** a message is a transaction if it has an amount, debit/credit wording, and an account suffix or reference number. Only alphanumeric sender headers are read; messages from ordinary phone numbers never are. The sender suffix (-T/-S/-P/-G) is a cheap pre-filter only (see ARCHITECTURE.md).
- **Parser** extracts: amount, direction (debit/credit), account (last 4), merchant/counterparty, date-time, reference number, balance if present.
- **Two entry points** [P, my reading of the "cron job" note]: one-time import of existing SMS history, and a live receiver for incoming SMS.

### F6 Deduplication [C]
A strong, layered dedup algorithm. One real-world transaction can produce several messages. See ARCHITECTURE.md. **Rule: never silently drop a possible real transaction.** When unsure, keep both and flag for review.

### F7 Transfer detection [C]
Match same amount + close date/time, opposite directions, across the user's added accounts. Both accounts must exist in Accounts for a transfer to be recognised.

### F8 Unrecognised SMS handling [C]
Review queue for messages the parser cannot handle or cannot assign to an account. Per-message actions: **ignore this sender**, **map this sender to an account** (remembered for future messages), or add the transaction manually.

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
| 4 | Reminder trigger condition and time | Phase 3 |
