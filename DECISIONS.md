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
