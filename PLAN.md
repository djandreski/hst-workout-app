# HST Workout Tracker — Implementation Plan

A personal Android app for one customized Hypertrophy-Specific Training (HST) program.
Philosophy: **StrongLifts 5x5 simplicity** — one tap, big text, zero clutter, fully offline.

## Locked-in decisions

| Decision | Choice |
|---|---|
| Toolchain | Install JDK 21 + Android cmdline SDK, compile and run tests before delivery |
| Setup input semantics | Entered weight × reps = **true rep max** (block ladder peaks at it) |
| RM estimation | **Epley both directions**: `1RM = W × (1 + r/30)`, `RM(r) = 1RM / (1 + r/30)` |
| Pull-ups | **Added weight only** (0 = bodyweight); suggest adding weight at 3×8 |

## Phase 0 — Toolchain setup

1. Install JDK 21 (user-local, no admin) via official zip or winget.
2. Install Android command-line tools (official Google zip) → `%LOCALAPPDATA%\Android\Sdk`,
   then `sdkmanager` for platform-tools, latest stable platform, build-tools; accept licenses.
3. Bootstrap the Gradle wrapper, then use `gradlew` exclusively.
4. `sdk.dir` in project-local `local.properties` only (never committed with absolute paths in git).

## Phase 1 — Project scaffold

- Single `app` module, Kotlin DSL, version catalog (`gradle/libs.versions.toml`), latest stable:
  Kotlin 2.x + Compose compiler plugin, AGP 8.x, Compose BOM + Material 3, Room (KSP), Hilt,
  DataStore Preferences, kotlinx-serialization.
- Package `com.djand.hst`, app name "HST", minSdk 26, metric-only, fully offline.

## Phase 2 — Domain layer (pure Kotlin, zero Android deps)

`domain/progression/ProgressionEngine` — the core, exhaustively documented and unit-tested:

- **RM math**: `1RM = W × (1 + reps/30)`; `RM(r) = 1RM / (1 + r/30)` for 15/10/5RM.
  Entered weight×reps treated as the true rep max for that rep count.
- **Block ladders** (classic spreadsheet zig-zag, 6 sessions per 2-week block):
  `[75%, 80%, 85%, 90%, 95%, 100%] × blockRM`, each rounded to the exercise's equipment
  increment, monotonic non-decreasing enforced. Scales safely from light isolations to
  heavy compounds (literal ±2.5 kg back-steps cannot).
- **Rep schemes**: Block 1 = 15; Block 2 = 10; Block 3 = 5–8 (target 8, accept ≥5, ladder on 5RM);
  Block 4 = top set of 5 at `5RM × [1.00, 1.00, 1.025, 1.025, 1.05, 1.05]` + back-off set(s) at
  80% of top set, 8–10 reps. Template set counts preserved (first set = top, rest = back-off).
- **Isolations**: stay at 10–15 reps in blocks 3–4. Reactive reps-first progression:
  weight increases only after all sets hit the top of the range; on miss, repeat;
  two consecutive misses → −10%.
- **Miss rules (compounds)**: miss → repeat same weight next occurrence;
  two consecutive misses → −10% (rounded to increment) and the remaining ladder is
  regenerated from the reduced base. Fully deterministic.
- `generateCycle()` → 24 fully prescribed sessions persisted up front.
- `generateDeload()` → weights × 0.85 (rounded), sets `ceil(sets / 2)`, no failure.
- `nextCycleInputs()` → achieved weights become the rep-max inputs of the next cycle.
- Pure extras: `WarmupCalculator` (40/60/80% ramp), `PlateCalculator`.

## Phase 3 — Data layer

- **Room**: `Exercise` (23 unique exercises seeded; equipment type, increment, compound flag),
  `WorkoutTemplate` (A/B/C), `TemplateExercise` (order, set counts),
  `Cycle`, `ExerciseProgression` (base weight/reps, derived RMs, consecutive-miss counter,
  pull-up-suggestion flag), `WorkoutSession` (planned → completed),
  `SetLog` (prescribed weight/reps, completed reps, status, notes — doubles as history),
  `BodyweightEntry`.
- **DataStore Preferences**: rest seconds (default 90), dark mode, per-equipment increments,
  bar weight, setup-complete flag.
- Repositories + `BackupRepository` (full-database JSON export/import via Storage Access Framework).

## Phase 4 — DI

Hilt modules: database + DAOs, repositories, DataStore. Engine injected as a stateless singleton.

## Phase 5 — UI (StrongLifts philosophy)

- **Setup wizard** (first launch): working weight × reps per exercise, grouped by workout
  A/B/C, large numeric fields → generates cycle 1.
- **Home**: Today's Workout + big Start button, cycle week + progress bar, last workout,
  optional bodyweight quick-log, deload banner, pull-up suggestion.
- **Workout screen**: one exercise card at a time (name, weight, target reps, previous-session
  comparison, PR chip, notes / skip / done, warm-up & plate calculator dialogs), large set
  checkboxes, rest-timer dialog after each set (countdown, skip, restart), auto-advance.
- **History**: sessions → expandable set details.
- **Stats**: weekly tonnage line, bodyweight line, per-exercise history — hand-rolled Canvas
  charts, no chart library.
- **Settings**: rest timer length, plate/equipment increments, dark mode, export database,
  import database, reset cycle.
- Single activity, minimal NavHost, no bottom navigation, no FAB.
- Small ViewModels, immutable `UiState`, unidirectional data flow via `StateFlow`.

## Phase 6 — Unit tests (~30 cases)

Epley math, increment rounding, all four block ladders, back-off math, isolation reps-first
progression, miss → repeat, double miss → −10% regeneration, deload generation, next-cycle
inputs, pull-up suggestion trigger, warm-up and plate calculators, A→B→C rotation.

## Phase 7 — Build & verify

`gradlew test` and `gradlew assembleDebug` must pass; iterate until green.

## Phase 8 — README

Architecture map, HST math with a worked example (80 kg × 10 → full 8-week ladder),
determinism guarantees, build/test/backup instructions.

## Assumed defaults (trivial to change)

- Package `com.djand.hst`, minSdk 26, rest timer 90 s, back-off = 80% of top set,
  bar weight 20 kg, equipment increments: barbell 2.5 kg, machine 2.5 kg, dumbbell 2 kg,
  cable 2.5 kg (all configurable in Settings).

## Flagged design decision

Intra-block zig-zag uses the spreadsheet's percentage ladder rounded to equipment increments
(not literal −2.5 kg steps) — the canonical spreadsheet math, and the only approach that works
for both very light and very heavy lifts. The +2.5 kg / next-pair / next-plate rules govern
rounding, post-cycle progression, resets, and isolation bumps.
