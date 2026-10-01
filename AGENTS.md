# AGENTS.md: rules for AI agents working on Spent

Read `SPEC.md`, `ARCHITECTURE.md` and `DECISIONS.md` before any task. `ROADMAP.md` says which phase is active. Work only inside the active phase.

## Hard rules
- Kotlin only. No Java files.
- Money is `Long` paise. Never Double or Float.
- Never add the INTERNET permission or any networking library.
- Never add or upgrade a dependency without asking first. Propose it and wait.
- Do not expand scope. If a task seems to need something outside the active phase, stop and ask. New ideas go in `PARKING_LOT.md`; do not implement them.
- Anything marked **[?]** in `SPEC.md` is undecided. Do not build it.
- Do not reverse or work around a decision in `DECISIONS.md`. If you think one is wrong, say so and stop.
- Versions live only in `gradle/libs.versions.toml`.

## Architecture rules (see ARCHITECTURE.md)
- Layers: UI → (domain) → data. Dependencies point one way only.
- `:core` is pure Kotlin/JVM: no `android.*`, no Room annotations, no Compose.
- **Room is the single source of truth.** The UI observes database Flows; it never keeps its own copy of persisted data.
- **Repositories are the only access to data.** Composables, ViewModels and use cases never touch DAOs, `ContentResolver` or files.
- Repositories return `:core` models, never Room entities. Mapping lives in `data/` only.
- One ViewModel per screen, exposing a single `uiState: StateFlow`. User actions are ViewModel functions. No business logic or I/O in composables.
- Complex logic (SMS pipeline, dedup, transfers, binning, budgets) goes in `:core` use cases: one `operator fun invoke`, pure and deterministic. Get time from `Clock` and SMS from `SmsSource`.
- Writes touching more than one row run in a single Room transaction.
- Totals and budget sums are SQL aggregates. Never load a whole table to sum it in memory.
- Never parse or import SMS on the main thread.
- UI is stock Material3 only: no custom theme, colors, fonts or animations until the Polish phase.

## Workflow
- For any task touching more than two files: give a short plan and list your assumptions first, then wait for approval.
- Keep diffs small. No drive-by refactors, renames or formatting changes.
- Every piece of logic in `:core` ships with unit tests.
- SMS fixtures must be anonymized: invented names, numbers and reference IDs only.
- Do not run `git commit` or `git push` unless asked.

## Verify before saying "done"
Run, and fix failures:
```
./gradlew :core:test
./gradlew assembleDebug
./gradlew lintDebug
```
Report the result of each. If you could not run one, say so.

## When stuck
State what you tried. Do not guess library APIs. Look up the current docs if a docs tool is available. Ask rather than invent.
