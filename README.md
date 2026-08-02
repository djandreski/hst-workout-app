# HST Workout Tracker

A personal Android app for one customized Hypertrophy-Specific Training (HST) program.
Philosophy: **StrongLifts 5x5 simplicity** — one tap, big text, zero clutter, fully offline.

- Package `com.djand.hst`, app name **HST**, minSdk 26, metric-only, no network access.
- Single `app` module, Kotlin + Jetpack Compose (Material 3), Room, Hilt, DataStore.

---

## Architecture map

Single-module, layered, unidirectional data flow. The domain layer is pure Kotlin with
zero Android dependencies; everything progression-related lives there and is unit-tested
on the JVM.

```
com.djand.hst
├── HstApplication.kt              @HiltAndroidApp
├── MainActivity.kt                single activity, hosts HstApp
│
├── domain/                        pure Kotlin, no Android imports
│   ├── model/                     Equipment, ExerciseInput, Prescription, Program, Results
│   └── progression/
│       ├── RmMath.kt              Epley both directions + increment rounding
│       ├── ProgressionEngine.kt   cycle/deload generation, session evaluation, next-cycle inputs
│       ├── WarmupCalculator.kt    40/60/80% ramp
│       └── PlateCalculator.kt     greedy bar loading
│
├── data/
│   ├── local/
│   │   ├── HstDatabase.kt         Room, version 1
│   │   ├── entity/                Exercise, WorkoutTemplate, TemplateExercise, Cycle,
│   │   │                          ExerciseProgression, WorkoutSession, SetLog, BodyweightEntry
│   │   ├── dao/                   one DAO per entity
│   │   ├── relation/              join rows (SessionWithSetLogs, ExerciseHistoryRow, …)
│   │   └── seed/                  ProgramSeed: the fixed 23-exercise A/B/C program
│   ├── repository/                Cycle, Session, Exercise, Template, History, Bodyweight,
│   │                              Backup (JSON export/import)
│   ├── settings/                  AppSettings + SettingsRepository (DataStore Preferences)
│   └── backup/                    BackupData — versioned JSON schema (kotlinx-serialization)
│
├── di/                            Hilt: DatabaseModule (Room + DAOs), DomainModule (engine);
│                                  repositories are constructor-injected @Singletons
│
└── ui/                            Compose, Material 3, small ViewModels, immutable UiState
    ├── HstApp.kt / RootViewModel  shows SetupScreen until setup-complete flag, else NavHost
    ├── navigation/HstNavHost.kt   flat routes: home, workout/{sessionId}, history, stats, settings
    ├── setup/                     first-launch wizard: weight × reps per exercise → cycle 1
    ├── home/                      today's workout + big Start, week/progress, deload banner
    ├── workout/                   one exercise card at a time, set checkboxes, rest timer,
    │                              warm-up & plate dialogs, auto-advance
    ├── history/                   (placeholder) sessions → expandable set details
    ├── stats/                     (placeholder) hand-rolled Canvas charts, no chart library
    ├── settings/                  (placeholder) rest timer, increments, dark mode, backup, reset
    └── theme/
```

> **Current UI state:** setup, home and workout are fully implemented; history, stats and
> settings are compiling placeholders. The backup and reset-cycle logic below is already
> implemented in the data layer, awaiting its Settings screen wiring.

**Data flow:** Room/DataStore → repositories → ViewModel `StateFlow<UiState>` → Composables.
User events flow back down; the engine is invoked by repositories/ViewModels and never
touches Android APIs.

**Seeded program:** 23 unique exercises across workouts A/B/C (pull-ups appear in A and C),
each with equipment type, default increment and a compound flag that selects its
progression policy. A/B/C rotate continuously, 3 sessions per week.

---

## HST math

All progression math is in `domain/progression` and is fully deterministic.

### Rep-max estimation — Epley in both directions

```
1RM   = W × (1 + reps / 30)
RM(r) = 1RM / (1 + r / 30)
```

The weight × reps entered at setup is treated as the **true rep max** for that rep count.
All prescriptions are rounded to the exercise's equipment increment (round-half-up, with an
epsilon guard against floating-point dust), and each block ladder is kept monotonically
non-decreasing after rounding.

### Cycle structure

- A cycle is **8 weeks = 24 sessions**; workouts A/B/C rotate (session 1 = A, 2 = B, 3 = C, 4 = A, …).
- Four 2-week **blocks** of 6 sessions each; every workout occurs exactly twice per block.
- Ladder rung = `(sessionNumber − 1) mod 6`, so workout A climbs rungs 75%/90%,
  B climbs 80%/95%, C climbs 85%/100%.

| Block | Weeks | Anchor | Ladder | Reps |
|---|---|---|---|---|
| 1 | 1–2 | 15RM | 75, 80, 85, 90, 95, 100% | 15 |
| 2 | 3–4 | 10RM | 75, 80, 85, 90, 95, 100% | 10 |
| 3 | 5–6 | 5RM | 75, 80, 85, 90, 95, 100% | 8 (min 5) |
| 4 | 7–8 | 5RM | 100, 100, 102.5, 102.5, 105, 105% | top set 5 + back-off(s) 8–10 |

- **Back-off sets** (block 4): 80% of the *rounded* top-set weight, target 10 / min 8.
  Template set counts are preserved (first set = top, rest = back-off).
- **Zig-zag**: each new block starts at a lower percentage of a higher rep max, so the
  weight deliberately drops at every block boundary.
- **Isolations** follow the block ladders in blocks 1–2, then stay flat at 10–15 reps in
  blocks 3–4 with reactive reps-first progression: +1 increment only after every set hits
  15 reps; miss (< 10) → repeat; two consecutive misses → −10%.
- **Miss rules (compounds)**: miss → next occurrence repeats the same weight (keeping its
  own rep targets), ladder resumes after; second consecutive miss → −10% (rounded) and the
  remaining ladder is regenerated from the reduced base via Epley.
- **Deload** (week 9, sessions 25–27): weights × 0.85 (rounded), sets `ceil(sets / 2)`,
  no failure; deload sessions never drive progression.
- **Next cycle**: for each exercise, the weight of its last achieved session (top set for
  block-4 compounds) and the minimum reps across working sets become the next rep-max input.
- **Pull-ups** are tracked as *added weight only* (0 kg = bodyweight); the app suggests
  adding weight at 3 sets of 8.

### Worked example: 80 kg × 10 → full 8-week ladder

Hack Squat (machine, 2.5 kg increment), setup input **80 kg × 10** — i.e. a true 10RM:

```
1RM = 80 × (1 + 10/30)      = 106.67 kg
15RM = 106.67 / (1 + 15/30) =  71.11 kg
10RM = 80 kg (the input itself)
 5RM = 106.67 / (1 + 5/30)  =  91.43 kg
```

Rounded ladders (all six rungs; the rungs Hack Squat actually hits in **bold** — it belongs
to workout A, so rung indices 0 and 3 of each block):

| Block | 75% | 80% | 85% | 90% | 95% | 100% |
|---|---|---|---|---|---|---|
| 1 — 15RM 71.11 | **52.5** | 57.5 | 60.0 | **65.0** | 67.5 | 70.0 |
| 2 — 10RM 80 | **60.0** | 65.0 | 67.5 | **72.5** | 75.0 | 80.0 |
| 3 — 5RM 91.43 | **67.5** | 72.5 | 77.5 | **82.5** | 87.5 | 92.5 |
| 4 — top-set ladder 100/100/102.5/102.5/105/105% | **92.5** | 92.5 | 92.5 | **92.5** | 95.0 | 95.0 |

Session-by-session prescription across the 8 weeks (2 template sets):

| Week | Session | Block | Rung | Weight | Prescription |
|---|---|---|---|---|---|
| 1 | 1 | 1 | 75% | 52.5 | 2 × 15 |
| 2 | 4 | 1 | 90% | 65.0 | 2 × 15 |
| 3 | 7 | 2 | 75% | 60.0 | 2 × 10 |
| 4 | 10 | 2 | 90% | 72.5 | 2 × 10 |
| 5 | 13 | 3 | 75% | 67.5 | 2 × 8 (min 5) |
| 6 | 16 | 3 | 90% | 82.5 | 2 × 8 (min 5) |
| 7 | 19 | 4 | 100% | 92.5 | 5 (top) + 75.0 × 10 back-off (min 8) |
| 8 | 22 | 4 | 102.5% | 92.5 | 5 (top) + 75.0 × 10 back-off (min 8) |

Note the zig-zag: week 3 starts *lighter* than week 2 ended (65.0 → 60.0), climbing toward
a heavier rep max. Block 4's 102.5% rung still rounds to 92.5 kg at this increment, and
105% would reach 95.0 kg — post-5RM overreaching. Back-off = 80% of 92.5 = 74.0 → **75.0**.

---

## Determinism guarantees

- **Pure engine.** `ProgressionEngine`, `RmMath`, `WarmupCalculator` and `PlateCalculator`
  contain no Android imports, no clocks, no randomness. Same inputs → same plan, always.
- **Prescribed up front.** `generateCycle()` produces all 24 sessions before the cycle
  starts; nothing is decided mid-workout.
- **Total rounding rule.** Every weight passes through `roundToIncrement` (round-half-up +
  epsilon guard) with the exercise's increment, and block ladders are forced monotonically
  non-decreasing — no platform or locale dependence.
- **Future-only rewrites.** Session evaluation may rewrite *future* sessions (repeat /
  reset / bump); the evaluated session itself is never modified.
- **Deterministic recovery.** The second-miss −10% reset re-derives the remaining ladder
  via Epley from the reduced base; isolations flatten deterministically.
- **Deload is inert.** Deload sessions are excluded from evaluation and progression.
- **Pinned by tests.** 60 JVM unit tests lock in the Epley math, rounding, all four block
  ladders, back-off math, isolation progression, miss/reset regeneration, deload, next-cycle
  inputs, pull-up trigger, warm-up/plate calculators and the A→B→C rotation.

---

## Build, test, install

Prerequisites: **JDK 21** and the **Android SDK** (command-line tools with platform,
platform-tools and build-tools). Point Gradle at the SDK via `sdk.dir` in the project-local
`local.properties` (git-ignored). The Gradle wrapper (9.6.1) handles the rest.

```powershell
# Run all unit tests (pure JVM — no emulator needed)
.\gradlew test

# Build the debug APK -> app\build\outputs\apk\debug\app-debug.apk
.\gradlew assembleDebug

# Install on a connected device / running emulator
.\gradlew installDebug
```

## Backup & restore

Fully offline, so backups are manual. `BackupRepository` implements full-database JSON
export/import via the Storage Access Framework (the caller passes a document `Uri` from
`CreateDocument` / `OpenDocument`):

- **Export** writes the **entire** database — exercises, workout templates, template
  exercises, cycles, exercise progressions, workout sessions, set logs, bodyweight
  entries — plus settings as one versioned JSON file.
- **Import** checks the backup version, then replaces the whole database and restores
  settings inside a single transaction (rows re-inserted in foreign-key order with their
  original ids). Import is destructive — export first.
- **Reset cycle** (`CycleRepository.resetAllProgress()`) wipes every cycle with its
  sessions, set logs and progression state, and marks setup incomplete so the wizard runs
  again. The exercise/template catalogue is untouched.

These operations currently live in the data layer; the Settings screen that will expose
them is one of the remaining UI placeholders.
