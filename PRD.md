# AI Coding Prompt: Build a Personal HST Workout Tracker (Android)

You are a senior Android engineer and UX designer. Your task is to build a complete Android application for **my personal use only**. The app should be production-quality but intentionally simple. Think **StrongLifts 5x5**, not MyFitnessPal.

## Goal

Create an Android app that manages my customized Hypertrophy-Specific Training (HST) program.

The app should:

* Calculate all workout weights automatically
* Tell me exactly what to do each workout
* Track completed workouts
* Progress weights automatically
* Require as little interaction as possible

I don't want a generic fitness tracker.

I don't need:

* Social features
* Exercise library
* Custom workout creation
* Nutrition tracking
* Analytics
* Wearables
* Cloud sync
* Accounts

Everything is optimized around **one program only**.

---

# Tech Stack

Use:

* Kotlin
* Jetpack Compose
* Material 3
* Room Database
* MVVM
* Repository Pattern
* StateFlow
* Hilt DI

No unnecessary architecture.

Everything should work offline.

---

# Program Structure

Three workouts rotate continuously.

## Workout A

1. Hack Squat – 2 sets
2. Incline Bench Press – 2 sets
3. Pull-ups – 2 sets
4. Chest Supported Row – 2 sets
5. Dumbbell Lateral Raise – 2 sets
6. Seated Leg Curl – 1 set
7. Cable Triceps Pushdown – 1 set
8. EZ Bar Curl – 1 set

---

## Workout B

1. Romanian Deadlift – 2 sets
2. Flat Dumbbell Press – 2 sets
3. Lat Pulldown – 2 sets
4. Seated Cable Row – 2 sets
5. Dumbbell Shoulder Press – 2 sets
6. Standing Calf Raise – 2 sets
7. Overhead Cable Triceps Extension – 1 set
8. Incline Dumbbell Curl – 1 set

---

## Workout C

1. Bulgarian Split Squat – 2 sets
2. Pull-ups (Weighted when possible) – 3 sets
3. Incline Dumbbell Press – 2 sets
4. Chest Supported T-Bar Row – 2 sets
5. Cable Lateral Raise – 3 sets
6. Leg Extension – 1 set
7. Face Pull – 2 sets
8. Hammer Curl – 1 set

---

# HST Cycle

The application manages repeating 8-week cycles.

Weeks 1-2

15 reps

Weeks 3-4

10 reps

Weeks 5-6

5-8 reps

Weeks 7-8

Top Set:
5 reps

Back-off Set:
8-10 reps

Isolation exercises remain at 10-15 reps.

After Week 8:

Display:

"Time for Deload"

Deload Week:

* Reduce weights by 15%
* Reduce sets by 50%
* No failure

After deload:

Start a brand-new cycle.

---

# Initial Setup

On first launch:

Ask for:

Current working weight for every exercise.

NOT 1RM.

Use current working weight.

Examples:

Hack Squat:
80 kg x 10

Incline Bench:
60 kg x 10

etc.

The app calculates everything from those numbers.

---

# Weight Progression

Progression is based on HST principles, but adapted.

Compound exercises:

Increase only if every prescribed set and rep target is achieved.

Default increments:

Barbell:
+2.5 kg

Machine:
+2.5 kg

Dumbbells:
Next available pair

Cable:
Next plate

Pull-ups:

When bodyweight pull-ups reach:

3 sets of 8 reps

Automatically suggest:

"Start adding weight."

If target reps are missed:

Repeat the same weight next workout.

Miss twice consecutively:

Reduce working weight by 10%.

Resume progression.

Isolation exercises:

Progress more conservatively.

Prefer:

Increase reps first.

Increase weight only after the top of the prescribed rep range is achieved with good technique.

---

# Weight Calculation

Implement the HST spreadsheet logic.

Given the user's current working weight, automatically estimate the training max (or equivalent reference), then generate the prescribed weights for each workout in the cycle so the load increases gradually across the rep blocks.

Requirements:

* Reproduce the spreadsheet calculations as closely as possible rather than inventing a different progression.
* Round all weights to the nearest increment supported by the exercise (e.g. 2.5 kg for barbells).
* Persist the calculated schedule so progression is deterministic.
* When a reset occurs after two failed attempts, regenerate the remaining progression from the reduced working weight.

Encapsulate all progression logic in a dedicated service/class with unit tests.

---

# Workout Screen

Very similar philosophy to StrongLifts.

Large title:

Workout B

Below:

Romanian Deadlift

2 × 10

70 kg

Set 1

[✓]

Set 2

[ ]

After checking both:

Automatically move to next exercise.

No navigation.

No menus.

No clutter.

---

# During Workout

Each exercise card shows:

Exercise Name

Current Weight

Target Reps

Completed Sets

Notes (optional)

Rest Timer button

Skip button

Done button

When done:

Automatically scroll.

---

# Rest Timer

After each set:

Popup:

Rest

90 sec

Countdown

Skip

Restart

---

# Home Screen

Very simple.

Today's Workout

Workout A

Start Workout

Below:

Current Cycle

Week 3

Workout 2

Progress Bar

Then:

History

Last Workout

Bodyweight (optional)

Nothing else.

---

# History

Each completed workout stores:

Date

Workout

Exercise

Weight

Reps

Completed

Missed

Notes

---

# Statistics

Very lightweight.

Only:

Estimated training volume over time

Bodyweight graph (optional)

Exercise history

No dashboards.

No AI.

No charts beyond the basics.

---

# Settings

Very few settings.

Metric only.

Rest timer length.

Plate increment.

Dark mode.

Export database.

Import database.

Reset cycle.

---

# UX Principles

The UX should resemble StrongLifts:

One tap whenever possible.

Large buttons.

Large text.

Minimal colors.

Almost zero menus.

No floating action buttons.

No bottom navigation with five pages.

No unnecessary animations.

The user should be able to start today's workout within five seconds of opening the app.

---

# Nice-to-have Features

* Automatic backup/export to JSON
* Exercise notes
* PR indicator
* Deload reminder
* Previous workout comparison
* Warm-up calculator
* Plate calculator

---

# Database

Suggested entities:

Exercise

Workout

WorkoutTemplate

WorkoutSession

ExerciseSet

Cycle

Progression

Settings

Bodyweight

History

---

# Code Quality

Write clean, modular, maintainable code.

Separate:

UI

Business logic

Progression engine

Database

Repositories

Avoid massive ViewModels.

Use immutable state.

Write unit tests for the progression engine.

Document the HST calculations thoroughly.

---

# Deliverables

1. Complete Android Studio project
2. Clean architecture
3. Room database
4. Fully functional progression engine
5. Jetpack Compose UI
6. Unit tests
7. README explaining the architecture and progression calculations

The priority is not building the most feature-rich fitness app. The priority is building the fastest, simplest, most reliable app for following this single customized HST program with almost no manual bookkeeping.
