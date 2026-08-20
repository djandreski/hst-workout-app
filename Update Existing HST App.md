# Update Existing HST App — Match the Lift Vault Excel Logic

You are modifying an **existing Android HST workout-tracking app** that you already built from a previous specification.

Do NOT rebuild the app from scratch.

The goal of this task is to **correct the workout/progression logic so it is based on the actual Lift Vault HST spreadsheet**, while keeping the existing simple StrongLifts-style UX and the customized exercise selection.

## 1. First: inspect the existing project

Before making changes:

- Inspect the current architecture.
- Find the existing HST progression/calculation code.
- Find the workout templates and exercise/set definitions.
- Find the database entities and stored cycle/progression data.
- Find the current workout UI.
- Determine whether the app already has user-entered starting weights and how they are persisted.

Do not unnecessarily change the UI, database architecture, or unrelated functionality.

The existing app is already functional. This task is primarily a **logic correction/refactor**.

---

# 2. Source of truth: the Lift Vault Excel spreadsheet

The original implementation incorrectly treated the program as a simplified:

15 reps → 10 reps → 5–8 reps → top set/back-off

program.

That is NOT the logic we want.

The supplied Lift Vault HST spreadsheet must now be treated as the **source of truth for the HST progression system**.

If the Excel file is available in the project/workspace, inspect the actual formulas and values directly.

Do not approximate the formulas.

Do not replace the spreadsheet's progression system with percentage-of-1RM calculations.

Do not invent a new progression formula.

The app should reproduce the spreadsheet's underlying calculation logic programmatically.

---

# 3. Actual HST cycle structure

The program consists of:

### Phase 1 — 15 RM

Workouts 1–6

Target:

15 reps

The load progresses gradually toward the user's 15RM.

### Phase 2 — 10 RM

Workouts 7–12

Target:

10 reps

The load progresses gradually toward the user's 10RM.

### Phase 3 — 5 RM

Workouts 13–18

Target:

5 reps

The load progresses gradually toward the user's 5RM.

### Phase 4 — post-5RM / heavy / negatives

Workouts 19–24

This phase continues beyond the 5RM progression and includes the heavier/negative-rep work represented in the original spreadsheet.

### After workout 24

The user takes approximately one week off / performs strategic deconditioning.

Then a new cycle begins with recalculated RM starting points.

---

# 4. Rep progression

Do NOT use the previous implementation's:

- 5–8 rep phase
- top-set/back-off-set structure
- generic 1RM percentage progression

unless those are explicitly required by the actual spreadsheet.

The target reps should be driven by the HST phase:

| Workouts | Target |
|---|---|
| 1–6 | 15 reps |
| 7–12 | 10 reps |
| 13–18 | 5 reps |
| 19–24 | Heavy / 5RM / negative phase according to spreadsheet |

The exact behavior of workouts 19–24 should be derived from the Excel formulas/structure.

---

# 5. Set structure

The previous implementation used arbitrary set counts.

Correct this.

The source HST spreadsheet uses:

- **2 sets for the first 5 exercises**
- **1 set for the remaining 6 exercises**

This should be preserved as the underlying HST volume model.

However, our customized program has fewer exercises than the original spreadsheet.

Therefore, map the same principle to the customized exercise list rather than blindly requiring 11 exercises.

For our customized workouts, use:

### Major exercises

2 sets

### Accessory/isolation exercises

1 set

The existing customized exercise selection should remain unless there is a technical reason to change it.

---

# 6. Customized workouts

Keep the customized exercise selection from the existing app.

### Workout A

1. Hack Squat — 2 sets
2. Incline Bench Press — 2 sets
3. Pull-ups — 2 sets
4. Chest-Supported Row — 2 sets
5. Dumbbell Lateral Raise — 2 sets
6. Seated Leg Curl — 1 set
7. Cable Triceps Pushdown — 1 set
8. EZ-Bar Curl — 1 set

### Workout B

1. Romanian Deadlift — 2 sets
2. Flat Dumbbell Press — 2 sets
3. Lat Pulldown — 2 sets
4. Seated Cable Row — 2 sets
5. Dumbbell Shoulder Press — 2 sets
6. Standing Calf Raise — 2 sets
7. Overhead Cable Triceps Extension — 1 set
8. Incline Dumbbell Curl — 1 set

### Workout C

1. Bulgarian Split Squat — 2 sets
2. Pull-ups — 3 sets
3. Incline Dumbbell Press — 2 sets
4. Chest-Supported T-Bar Row — 2 sets
5. Cable Lateral Raise — 3 sets
6. Leg Extension — 1 set
7. Face Pull — 2 sets
8. Hammer Curl — 1 set

Keep Workout C's 3 pull-up sets because pull-ups are a separate personal goal.

---

# 7. Pull-up progression is a special case

Do NOT blindly apply the HST 15/10/5 progression to bodyweight pull-ups.

Pull-ups have two purposes:

1. Back hypertrophy
2. Improving my maximum number of strict bodyweight pull-ups

Track them separately.

For bodyweight pull-ups:

- Record reps for every set.
- Do not require a weight increase when reps increase.
- Once the user consistently reaches approximately 3 × 8 strict bodyweight pull-ups, allow/suggest weighted pull-ups.
- Weighted pull-ups can then progress using the normal HST-style loading progression.

The app should display the appropriate variation automatically.

Example:

"Pull-ups — Bodyweight"

or

"Pull-ups — +5 kg"

depending on the user's progression.

---

# 8. Starting weights / RM inputs

The original spreadsheet works from **RM values**, not simply arbitrary 1RM percentages.

The setup flow should therefore support the relevant starting RM for each exercise.

For each exercise, the user should be able to provide the appropriate starting weight/reference for the HST cycle.

At minimum, the app needs a reliable way to establish:

- 15RM
- 10RM
- 5RM

for each exercise.

Do NOT assume that a user's 1RM is required.

If the existing app currently asks for a "current working weight", modify the underlying terminology and calculation where necessary so that it correctly represents the HST RM-based system.

Keep the setup UX simple.

---

# 9. Reproduce spreadsheet calculations

Create a dedicated progression engine, for example:

`HstProgressionEngine`

It should be responsible for:

- generating workouts 1–24
- calculating each exercise's load
- determining target reps
- determining sets
- handling increments
- handling the heavy/negative phase
- generating the next cycle

The UI should NOT contain progression calculations.

The progression engine should be deterministic.

Given the same RM inputs and increments, it must always produce the same schedule.

---

# 10. Weight increments and rounding

Preserve the spreadsheet's concept of an exercise-specific increment.

Examples:

- Barbell: configurable increment
- Dumbbell: configurable increment
- Machine: configurable increment
- Cable: configurable increment

Weights must be rounded to realistic gym increments.

Do not silently introduce arbitrary percentages.

If the spreadsheet's formula produces a value that cannot be loaded in the gym, apply the configured rounding rule.

Show the actual rounded training weight to the user.

---

# 11. Failure handling

The previous app implemented:

"miss twice → reduce weight 10%"

Do NOT automatically apply that rule if it conflicts with the HST spreadsheet.

Instead:

- Record whether the prescribed reps were completed.
- Keep workout history.
- Allow the user to repeat a load when appropriate.
- Do not modify the generated HST schedule simply because one set was missed unless the spreadsheet logic explicitly calls for it.

If you think a failure-adaptation mechanism is useful, implement it as a clearly separated optional behavior rather than changing the underlying HST progression algorithm.

---

# 12. Cycle completion

After workout 24:

Display a simple message:

"Cycle complete"

"Take approximately 1 week off, then start your next cycle."

The next cycle should allow recalculating the user's RM starting points.

Do not simply continue the old weights indefinitely.

---

# 13. Database migration

The app may already contain workout/progression data generated using the old algorithm.

Do NOT silently reinterpret old historical data.

Historical completed workouts should remain intact.

For an active cycle created using the old algorithm, provide a safe migration strategy.

Prefer:

- preserve completed workout history
- discard/recalculate only future uncompleted workouts
- mark the active progression as using the new HST engine

If this is difficult or unsafe, provide a "Start New HST Cycle" migration option rather than corrupting existing data.

---

# 14. UI changes

Keep the existing UI.

The app should still feel like:

**StrongLifts 5×5**

Simple.

Fast.

Minimal.

Do not add unnecessary screens or configuration.

However, update the workout display so it clearly shows:

### Example

**Week 3 · Workout 8**

**10 RM Phase**

Incline Bench Press

60 kg × 10

Set 1 ✓

Set 2 ✓

---

For the final phase:

**Workout 20**

**Post-5RM Phase**

Exercise

65 kg × 5

or the appropriate negative-rep instruction based on the spreadsheet.

---

# 15. Cycle progress

The home screen should show:

**HST Cycle 1**

**Workout 8 / 24**

**10 RM Phase**

[██████░░░░░░░░░░░░]

Next:

**Workout 9 — Workout C**

Keep this extremely simple.

---

# 16. Testing requirements

This is important.

Create unit tests for the progression engine.

Tests should verify:

### Phase boundaries

Workout 6 → Workout 7

15RM → 10RM

Workout 12 → Workout 13

10RM → 5RM

Workout 18 → Workout 19

5RM → post-5RM phase

Workout 24 → cycle complete

### Weight calculations

Use known values from the supplied spreadsheet and verify that the app produces the same values.

### Rounding

Verify that calculated weights are rounded correctly.

### Sets

Verify correct set counts.

### Reps

Verify correct rep targets for each phase.

### Cycle reset

Verify that a new cycle correctly accepts new RM values.

### Pull-ups

Verify that bodyweight pull-ups are tracked separately from normal weighted exercises.

---

# 17. Important implementation rule

Before modifying the progression engine:

**Inspect the supplied Excel workbook and reproduce its formulas exactly.**

If the workbook contains formulas, inspect the formulas rather than only looking at the displayed values.

If there is any ambiguity between the previous app implementation and the spreadsheet:

**the spreadsheet wins.**

If there is any ambiguity between the spreadsheet and the customized exercise selection:

**keep the customized exercise selection, but preserve the spreadsheet's progression methodology.**

---

# 18. Do not over-engineer

This is a personal app.

Do NOT add:

- user accounts
- social features
- workout sharing
- exercise marketplace
- nutrition tracking
- AI coaching
- cloud backend
- unnecessary settings
- complicated analytics

The ideal experience is:

Open app → see today's workout → start → record sets → finish.

---

# 19. Final acceptance criteria

The update is complete when:

1. Existing app still builds and runs.
2. Existing simple UX is preserved.
3. Existing workout history is not corrupted.
4. HST cycle is 24 workouts + strategic deconditioning.
5. Rep phases match the actual spreadsheet.
6. Set structure matches the HST volume model.
7. Weight progression reproduces the Excel calculations.
8. Customized exercises remain in place.
9. Pull-ups have their own progression logic.
10. Unit tests verify the progression engine against known spreadsheet values.
11. No 1RM-percentage progression remains unless specifically present in the source Excel.
12. No invented 5–8/top-set/back-off phase remains.
13. The app can generate a complete new 24-workout cycle from RM inputs.

Before finishing, show a concise summary of:

- what code was changed
- what spreadsheet formulas were implemented
- how the old progression differs from the new one
- how existing data was migrated
- which tests were added
- any assumptions that had to be made