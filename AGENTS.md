# AGENTS.md

Personal Android app (not a library, no CI, no team conventions to preserve beyond this file).

## Build / test

```powershell
.\gradlew test                                            # JVM unit tests only, no emulator needed
.\gradlew test --tests "com.djand.hst.domain.progression.RmMathTest"   # single test class
.\gradlew assembleDebug                                    # -> app\build\outputs\apk\debug\app-debug.apk
.\gradlew installDebug                                     # requires connected device/emulator
```

- Requires JDK 21 and an Android SDK; `local.properties` (git-ignored) must set `sdk.dir`.
- Single module (`:app`), Kotlin DSL, version catalog in `gradle/libs.versions.toml`.
- Kotlin sources live under `src/main/kotlin` and `src/test/kotlin` (not `java`) — put new files there.
- No lint/formatter/CI is configured. `gradlew test` is the only enforced check before delivering changes.

## Architecture (see README.md for full map + HST math)

- `domain/` is **pure Kotlin, zero Android imports** — `ProgressionEngine`, `RmMath`,
  `WarmupCalculator`, `PlateCalculator`. Keep it that way; this is what the 60 JVM unit tests pin down.
  Any change to progression math must stay deterministic (no clocks/randomness) and update/add tests.
- `data/` = Room (`local/`), repositories, DataStore settings, JSON backup (`backup/`).
- `di/` = Hilt modules; repositories are constructor-injected singletons.
- `ui/` = Compose + Material 3, one ViewModel per screen, `StateFlow<UiState>`, unidirectional data flow.
- Data flow: Room/DataStore → repository → ViewModel → Composable; the engine is only ever called
  from repositories/ViewModels, never touches Android APIs directly.

## Current implementation status

- Fully implemented: **setup, home, workout**.
- **Placeholders (compile but are stubs):** history, stats, settings screens. The backend logic they
  need (history repository, backup/restore, reset-cycle) already exists in `data/` — don't re-implement it,
  just wire the UI.

## Project-specific facts easy to miss

- Fully offline app: no network access, no accounts, no cloud sync — don't add any networking deps.
- Package `com.djand.hst`, minSdk 26, metric units only.
- `screens/` at repo root holds reference *screenshot images* (`.webp`) of other apps used for design
  comparison in `DESIGN.md` — not app assets, don't touch/reference them from code.
- `DESIGN.md` is a **not-yet-implemented** design spec (colors, shapes, component specs) for a
  StrongLifts-style redesign of `ui/theme` and the placeholder screens. Current `ui/theme/Theme.kt`
  does not yet match it — check `DESIGN.md` before changing theme/tokens or building out
  history/stats/settings UI, since it's the intended target, not just background reading.
- `PRD.md` / `PLAN.md` are the original spec/plan that produced this codebase; `README.md` reflects
  the *current* state and is the more reliable source when they disagree.
- All progression weights round via `roundToIncrement` (round-half-up + epsilon guard) and block
  ladders are forced monotonically non-decreasing — replicate this rounding rule rather than adding
  a new one if you touch progression math.
