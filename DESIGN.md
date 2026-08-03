# HST — Design System

This document defines the visual language and component vocabulary for the HST app.
It is **descriptive, not yet implemented** — the goal is to lock in decisions before
touching `ui/theme` or any screen. See `PRD.md` for product intent and `PLAN.md` /
`README.md` for the current architecture.

---

## 1. Reference audit

`screens/` contains marketing screenshots from two different Android lifting-log apps.
Despite the filenames being generic, they are visually and structurally distinct apps:

| | **Reference A — "StrongLifts 5×5"** | **Reference B — generic 5×5 logger** |
|---|---|---|
| Screens | `unnamed.webp`, `(1)`–`(9)` (duplicated at two crops) | `(12)`–`(18)` |
| Structure | Single flat screen per concern, plain `TopAppBar`, no bottom nav, no FAB | Colored per-workout `TopAppBar`, 5-icon bottom nav, red FAB, dense icon toolbar |
| Set control | Big circular "pip" per set, filled = done | Numbered circle + separate reps/weight steppers (`− 10 +`) per set |
| Editing | Rare, tap-to-toggle only | Everything has inline `−`/`+` steppers (reps *and* weight, every row) |
| Rest timer | Bottom bar, near-black, big countdown, progress line | Not shown as a distinct pattern (timer is in the app bar) |
| Color | One accent (brick red) + neutrals | Stock Material red (`#F44336`) + colored app bars per screen |
| Iconography | Almost none (text-first) | Heavy — clip-art exercise icons, toolbar icons, bottom nav icons |
| Charts | Single-metric line chart, dropdown to switch exercise, gradient fill | Multi-series chart with colored toggle chips for every exercise at once |

**Decision: Reference A (StrongLifts) is the primary design language for HST.**

This isn't a stylistic preference — the PRD *literally specifies StrongLifts* as the
target ("Very similar philosophy to StrongLifts", "The UX should resemble StrongLifts",
"No floating action buttons. No bottom navigation with five pages."). Reference B
violates almost every one of those constraints (FAB, 5-icon bottom nav, per-row
steppers, dense toolbars, clip-art icons) and is closer to "generic fitness tracker" —
exactly what the PRD says this app must *not* be.

Reference B is kept only as a **secondary source for a handful of isolated ideas**
that don't conflict with the minimalist structure (see §10.6). Everything structural
(navigation shape, app bar behavior, editing model) comes from Reference A.

---

## 2. Design philosophy

Carried over from the PRD, made concrete as design rules:

1. **One tap whenever possible.** Default interaction for a set is a single tap.
   Corrections (wrong rep count, notes, skip) are secondary actions, never the
   primary tap target.
2. **Big text, big targets.** Numbers the user reads mid-set (weight, reps, rest
   countdown) are the largest text on screen. Touch targets for anything performed
   *during* a set (the done-tap, the rest-skip) are ≥ 56dp.
3. **One accent color.** Red is used for exactly one meaning family: *primary
   action / completion / current focus*. Everything else is neutral gray/black/white.
   No secondary or tertiary brand colors.
4. **Flat, not decorated.** No gradients, no drop shadows beyond Material's default
   1dp card elevation, no illustration/clip-art. Cards are separated by whitespace
   and a single elevation step, not by borders or color blocking.
5. **Structure never changes per screen.** One `TopAppBar` shape across the app.
   No screen recolors the app bar. No screen introduces its own navigation chrome
   (no bottom nav, no FAB — confirmed existing `HstNavHost` flat-route approach is correct).
6. **Almost no chrome.** Icons are used only for: back navigation, settings gear,
   and the few functional glyphs already in use (skip/undo affordances stay as
   text buttons, per PRD "almost zero menus").
7. **Motion is a hint, not a feature.** Only: auto-scroll to the current exercise,
   simple fades/expand-collapse on card toggle, and the rest-timer countdown.
   Nothing else animates.

---

## 3. Color system

### 3.1 Brand color

Sampled directly from Reference A's UI (button fill, filled set-pips, tab
underline, "Finish" label, rest-bar progress — all the same hue):

```
Sampled: rgb(201–210, 0–47, 42–46)  →  effectively Material "Red 700" (#D32F2F)
```

**Brand red = `#D32F2F`.** Close enough to a standard Material token that we use
the token directly rather than a bespoke hex — it simplifies dark-theme derivation
and keeps contrast tools (Material Theme Builder / Accessibility Scanner) usable.

### 3.2 A named conflict, resolved

Material 3's baseline `error` role is *also* a red hue (`#B3261E` at tone 40). If
`primary` is red **and** `error` is red, "this set is done" (primary-filled pip) and
"you missed the rep target" (currently rendered via `colorScheme.error` in
`WorkoutScreen.kt`) become visually adjacent and easy to confuse — they sit in the
same row today.

**Decision:** keep M3's `error` role reserved for real failures (snackbars: backup
import/export failed, database errors — rare, full-screen or dialog-level, never
adjacent to a red "done" pip). Introduce a **custom semantic color, `Attention`**
(amber), for anything that means "missed / needs a decision" while looking at an
in-progress workout: missed rep count, deload banner accent, pull-up-suggestion
banner accent. Amber reads as "notice," not "danger," which matches what these
banners actually mean (informational, not destructive).

### 3.3 Light theme tokens

| Role | Hex | Used for |
|---|---|---|
| `primary` | `#D32F2F` | Start/Finish buttons, filled set-pips, PR chip, tab underline, links (History/Log text buttons) |
| `onPrimary` | `#FFFFFF` | Text/icons on filled red |
| `primaryContainer` | `#FFDAD6` | Rare — a tinted background behind a red accent (e.g. PR chip alt state), not core to the palette |
| `onPrimaryContainer` | `#410002` | Text on `primaryContainer` |
| `background` | `#F9F9F7` | Screen background behind cards (matches sampled `#F9F9F9`) |
| `onBackground` | `#1B1B1B` | Primary text on background |
| `surface` | `#FFFFFF` | Cards, dialogs, sheets |
| `onSurface` | `#1B1B1B` | Primary text on cards |
| `surfaceVariant` | `#ECECEC` | Unfilled set-pips, pill chips (workout letter badge, dropdown pill), dividers |
| `onSurfaceVariant` | `#6E6E6E` | Secondary/meta text ("Last workout", labels, timestamps) — matches current usage |
| `outline` | `#DDDDDD` | Hairline separators where whitespace alone isn't enough |
| `error` | `#B3261E` (M3 baseline) | Real errors only: failed save/import/export |
| `onError` | `#FFFFFF` | Text on error fill |
| **`attention`** *(custom)* | `#F9A825` | Missed-rep count, deload banner accent, pull-up-suggestion accent |
| **`onAttention`** *(custom)* | `#3E2E00` | Text/icon on `attention` fill |

### 3.4 Dark theme tokens

| Role | Hex | Notes |
|---|---|---|
| `primary` | `#FF6E6E` | Lightened red for sufficient contrast on near-black; used for text/icon-on-background cases (e.g. weight labels) |
| `onPrimary` | `#4A0002` | Dark maroon text on the lightened-red fill (filled pips/buttons can also just use `#EF5350` fill + white text — verify both combos in Theme Builder before shipping) |
| `background` | `#121212` | Standard Material dark background |
| `onBackground` | `#ECECEC` | |
| `surface` | `#1C1C1C` | Matches the sampled rest-timer-bar black almost exactly — reuse it for all dark cards, not just the timer |
| `onSurface` | `#ECECEC` | |
| `surfaceVariant` | `#2B2B2B` | Unfilled pips/pills in dark mode |
| `onSurfaceVariant` | `#B0B0B0` | |
| `outline` | `#3A3A3A` | |
| `error` | `#F2B8B5` | M3 baseline dark error |
| `onError` | `#601410` | |
| **`attention`** | `#FFC947` | |
| **`onAttention`** | `#1B1400` | |

> These are starting values, not a finished tonal palette. Before implementation,
> run the seed `#D32F2F` through Material Theme Builder (or `androidx.compose
> .material3:material3-color` generation) to get a fully derived, contrast-checked
> light+dark scheme, then hand-verify the two custom `attention` tokens against
> both backgrounds with the Accessibility Scanner.

### 3.5 What we are *not* doing

- No dynamic color (`Material You`) — the app is a single-purpose personal tool;
  a wallpaper-derived palette would fight the "one deliberate brand color" rule.
- No per-screen accent colors (Reference B recolors its app bar red/maroon per
  workout letter — rejected, see §10.6).
- No multi-color chart legends (Reference B's colored chips per exercise) — one
  chart, one color, matches PRD "no dashboards."

---

## 4. Typography

Both references use the platform default sans (Roboto-family) for all *in-app* UI —
the heavy geometric display font only appears in the marketing headline overlays
("Proven 3×/week strength program"), not in the product itself. **No custom font is
bundled or needed.** Keep `Typography()` at Material 3 defaults and drive emphasis
through `fontWeight`/size role selection, exactly as the codebase already does.

| Role | Used for | Weight guidance |
|---|---|---|
| `displayLarge` / `displayMedium` | Rest-timer countdown, "Workout A" hero title, cycle-finished headline | Bold |
| `displaySmall` | Exercise weight×reps hero line inside the expanded card | Bold |
| `headlineSmall` | Primary button label ("Start Workout", "Finish Workout"), expanded exercise name | SemiBold/Bold |
| `titleLarge` | Setup section headers, dialog titles | Medium |
| `titleMedium` | Collapsed exercise name, set row label, card row primary text | Medium |
| `labelLarge` | Small red summary label ("5×5 45lb"), section labels ("Program", "Last workout") | Medium, `primary` or `onSurfaceVariant` color depending on emphasis |
| `bodyLarge` / `bodyMedium` | Instructional copy, notes, dialog body text | Regular |
| `bodySmall` | Timestamps, helper text, previous-session comparison | Regular, `onSurfaceVariant` |

Rule of thumb inherited from Reference A: **numbers are bold, labels are regular.**
A weight×reps value is never rendered in the same weight as its caption.

---

## 5. Shape & elevation

| Element | Shape | Elevation |
|---|---|---|
| Cards | `RoundedCornerShape(16.dp)` | 1dp (Material tonal elevation only — no drop shadow) |
| Primary buttons | `RoundedCornerShape(28.dp)` — fully rounded pill at 56–72dp height | 0dp (flat fill, no shadow) |
| Pill chips (workout-letter badge, dropdown selector) | `RoundedCornerShape(50)` (stadium) | 0dp |
| Set-pips (see §10.2) | Circle | 0dp |
| Dialogs / bottom sheets | `RoundedCornerShape(20.dp)` top corners for sheets, `24.dp` all corners for `AlertDialog` (M3 default) | Standard dialog elevation |
| Rest-timer bar | `RoundedCornerShape(20.dp)`, docked to the bottom of the screen as a persistent bar (not a modal `AlertDialog`) | 3dp — it should read as "floating above content" |

This replaces the current `MaterialTheme.shapes.small` used ad-hoc for the PR chip;
define the full `Shapes()` object instead of relying on the single default.

---

## 6. Spacing & layout grid

- Base unit: **8dp**. All padding/margins are multiples of it (already the case —
  `16.dp` screen padding, `8.dp`/`12.dp`/`16.dp`/`24.dp` internal spacing).
- Screen content padding: `16.dp` horizontal, consistent with current `HomeScreen`
  / `WorkoutScreen`.
- Card internal padding: `16.dp` (compact rows) or `24.dp` (hero cards: Today's
  Workout, Cycle Finished).
- Vertical rhythm between cards in a list: `16.dp` (`Arrangement.spacedBy(16.dp)`,
  already the standard).
- Minimum touch target: `48.dp`; primary in-workout actions target `56–72.dp`
  (matches the existing `Button` heights in `HomeScreen`/`WorkoutScreen`).

---

## 7. Iconography

- Use **outline Material Symbols only** (already the case: `Icons.Filled.Settings`,
  `Icons.AutoMirrored.Filled.ArrowBack`). No custom icon set, no exercise clip-art
  (explicitly rejecting Reference B's illustrated muscle icons — they add visual
  noise the PRD doesn't ask for and every exercise would need bespoke art).
- Icon usage stays minimal and functional: back arrow, settings gear, rest-timer
  skip (`Close`), PR badge (text "PR", not an icon), warm-up/plate/notes as **text
  buttons**, not icon buttons — text is more scannable at a glance mid-workout and
  avoids needing a legend.

---

## 8. Motion

- Card expand/collapse (`ExerciseCard` collapsed ↔ expanded): default Compose
  `AnimatedVisibility`/size animation, ~200ms, standard easing. No custom curves.
- Auto-scroll to current exercise: keep `animateScrollToItem`, no change.
- Rest timer: countdown text update has no animation (it's a clock, not a
  transition); the progress bar animates linearly, matching current
  `LinearProgressIndicator` behavior.
- No screen-transition animation beyond the Navigation-Compose default crossfade.
- Nothing else. Per PRD: "No unnecessary animations."

---

## 9. Dark mode

- Already wired via `ThemeMode` (`SYSTEM`/`LIGHT`/`DARK`) in `SettingsRepository` and
  `HstTheme`. No new mechanism needed — just fill in the token table from §3.4.
- The rest-timer bar's near-black (`#1C1C1C`) becomes indistinguishable from the
  dark-theme `surface` color — intentional; in dark mode the timer bar should look
  like "just another surface," not a separately-branded overlay.

---

## 10. Component specifications

These are the concrete patterns the redesign phase should implement. Each maps to
an existing composable so the follow-up work is a targeted diff, not a rewrite.

### 10.1 App bar

One shape everywhere: plain `TopAppBar`, `surface`-colored (default M3, i.e. no
custom color per screen — this directly rejects Reference B's colored/maroon app
bar per workout letter). Title uses `titleLarge`; a subtitle (week/session) may sit
below it at `bodySmall`/`onSurfaceVariant`, as `WorkoutScreen` already does.

### 10.2 Set control ("pip"), replacing the current Button+stepper row

Reference A's circular pip is adopted as the primary set-completion control,
adapted for HST's richer data model (target vs. actual reps, top/back-off sets):

- **Not started:** circle, `surfaceVariant` fill, `onSurfaceVariant` number
  (target reps), no border.
- **Done, on target:** circle, `primary` fill, `onPrimary` bold number (actual reps).
- **Done, missed target:** circle, `surfaceVariant` fill (not primary — a miss is
  not "complete" in the same visual sense), `attention`-colored number and a thin
  `attention` ring, so a miss is scannable without reusing the red brand color.
- **Tap** on a not-started pip → marks done at target reps (the one-tap default).
- **Tap** on a done pip → reveals an inline `−`/`+` stepper *only for that pip*
  (to correct actual reps) plus an "Undo" affordality — i.e., the correction UI is
  progressive disclosure, not a permanently-visible stepper on every row (this is
  the one deliberate departure from Reference B's "steppers everywhere," which the
  PRD's "almost zero menus / one tap" principle argues against).
- Top/back-off sets keep their existing text label above the pip row ("Top set" /
  "Back-off"), not a color change on the pip itself.

### 10.3 Rest timer

Move from the current `AlertDialog` to a **persistent bottom bar**, matching
Reference A exactly (it appears identically across all of Reference A's screenshots,
so it's clearly core to that app's identity, not an incidental crop):

- Docked bar, `surface`/near-black fill (`#1C1C1C` in light mode too — this is the
  one place a "dark chrome on light theme" element is justified, since it mirrors
  a physical rest-period countdown, not page content).
- Left: big countdown (`displaySmall`+, monospaced-feel via tabular figures).
- Center/label: "Rest 90s" or similar, `bodyMedium`, muted white.
- Right: a close/skip glyph.
- Thin linear progress track under the bar, `primary`-colored fill.
- Restart affordance: keep as a small text action within the bar rather than a
  second dialog button (current `RestTimerDialog` has Skip/Restart as dialog
  buttons — collapse to "skip" as the primary bar action, "restart" as a small
  text link beside the countdown).
- Non-modal: unlike the current `AlertDialog`, the bar should not block scrolling
  the exercise list underneath it (a user should be able to check a previous
  exercise's log while resting).

### 10.4 Exercise card header

Adopt Reference A's compact summary line: exercise name on the left (`titleMedium`),
a right-aligned `labelLarge`/`primary`-colored summary — `"{sets}×{reps} {weight}"`
— with a small chevron indicating expand/collapse state. This replaces the
separate large `displaySmall` weight line currently duplicated between the
collapsed subtitle and the expanded header; keep the big number only once, in the
expanded state, sized `displaySmall` as today.

### 10.5 Home hero card ("Today's Workout")

Keep the current `TodayCard` structure (label → big "Workout X" → week/session
caption → full-width tall primary button) — it already matches Reference A's
"Next Workout … Start Workout" card almost exactly. **Do not** adopt Reference A's
black "Program / StrongLifts 5×5" header block above it; it's a second visual
"theme" competing with the card below it and adds a settings-like affordance
(pencil-edit) the PRD doesn't call for. One flat `TopAppBar` + hero card is enough.

### 10.6 What's borrowed from Reference B (the only three things)

1. **Plate-calculator visualization** — a horizontal stacked bar of colored plate
   blocks sized/labeled by plate weight (Reference A also has its own version of
   this — green/gray/dark blocks — so this is really a shared idea, reinforced by
   both apps independently). Replace the current text-only `PlatesDialog` list
   ("Per side: 20 + 10 + 2.5") with this bar visualization.
2. **Chart gridline + gradient-fill line style** for the Stats screen's hand-rolled
   Canvas charts — light gray gridlines, one colored line with a soft gradient
   fill beneath it, dotted markers per data point, sparse axis labels. Reference A
   shows the same treatment, so this is the converged spec, not a B-specific import.
3. Nothing else structural. Bottom nav, FAB, per-exercise clip-art, colored app
   bars, and always-visible steppers are explicitly rejected (§1, §10.2, §10.5).

### 10.7 Stats screen (currently a placeholder)

One metric at a time, selected via a simple dropdown/segmented control (Reference
A's "Squat ▾" pattern), not Reference B's multi-series colored-chip toggle —
matches PRD "no dashboards... no charts beyond the basics." Applies to both the
per-exercise weight history and the bodyweight/tonnage views.

### 10.8 History screen (currently a placeholder)

A simple reverse-chronological list of sessions (date, workout letter, duration),
expandable to per-set detail — no calendar-grid view. Reference A's List/Calendar/
Notes triple-tab is more than this app needs per the PRD's "very lightweight"
history spec; a flat list is enough and avoids adding a third navigation pattern.

### 10.9 Settings screen (currently a placeholder)

Plain list of grouped text rows (rest timer length, plate increment, dark mode,
export, import, reset cycle) — no icon-heavy toolbar, no nested settings screens.
Matches PRD "very few settings."

### 10.10 Banners (deload, pull-up suggestion)

Keep the current `BannerCard` shape but recolor its accent from `primary` to
`attention` (amber) per §3.2 — these are notices, not the same visual category as
a completed/primary action.

---

## 11. Token summary for `Theme.kt` (for the implementation phase)

Not applied yet — recorded here so the next change is a direct transcription:

```
Light: primary #D32F2F, onPrimary #FFFFFF, background #F9F9F7, onBackground #1B1B1B,
       surface #FFFFFF, onSurface #1B1B1B, surfaceVariant #ECECEC,
       onSurfaceVariant #6E6E6E, outline #DDDDDD, error #B3261E, onError #FFFFFF
Dark:  primary #FF6E6E, onPrimary #4A0002, background #121212, onBackground #ECECEC,
       surface #1C1C1C, onSurface #ECECEC, surfaceVariant #2B2B2B,
       onSurfaceVariant #B0B0B0, outline #3A3A3A, error #F2B8B5, onError #601410

Custom (not part of ColorScheme — extend via a small CompositionLocal or a
MaterialTheme wrapper data class):
Light: attention #F9A825, onAttention #3E2E00
Dark:  attention #FFC947, onAttention #1B1400

Shapes: card 16dp, button 28dp (pill), chip/pill stadium, dialog 24dp,
        rest-timer bar 20dp (top corners only if docked flush to screen bottom)
Typography: Material 3 defaults, no custom font family, weight-driven emphasis only.
```

---

## 12. Open questions for the redesign phase

- Confirm exact contrast pass on the dark-theme `primary` (`#FF6E6E`) against both
  `background` (#121212) and as a fill under white text — pick one canonical usage,
  not both, to avoid an inconsistent "sometimes text, sometimes fill" red.
- Decide whether the rest-timer bar should also show a "next up" exercise name
  (Reference A doesn't; it's a nice-to-have from the PRD's list, not required).
- Decide the exact tap target size for the per-pip stepper reveal (§10.2) — needs
  a real layout pass once implemented, since pips are small (≈56dp) and the
  stepper needs its own ≥48dp targets without overflowing the card width on
  narrow phones.
