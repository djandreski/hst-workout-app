package com.djand.hst.domain.model

/**
 * What the user actually did for one exercise in a session: one entry per attempted
 * set. Sets not attempted count as 0 reps. A skipped exercise has no progression
 * impact at all (no miss recorded, schedule untouched).
 */
data class ExerciseResult(
    val exerciseId: String,
    val completedReps: List<Int>,
    val skipped: Boolean = false,
)

/** How the engine reacted to an [ExerciseResult]. */
enum class ProgressionEvent {
    /** Every set reached at least its minimum reps. */
    ACHIEVED,

    /** Isolation: every set reached the top of the rep range -> remaining sessions +1 increment. */
    BUMP_SCHEDULED,

    /** First miss -> the next occurrence repeats the same weight. */
    REPEAT_SCHEDULED,

    /** Second consecutive miss -> working weight -10%, remaining progression regenerated. */
    RESET,

    /** Exercise was skipped; no progression impact. */
    SKIPPED,
}

/** Per-exercise outcome of evaluating a finished session. */
data class ProgressionOutcome(
    val exerciseId: String,
    val event: ProgressionEvent,
    /** New value of the consecutive-miss counter to persist for this exercise. */
    val consecutiveMisses: Int,
)

/** Result of evaluating one finished session: the updated plan plus per-exercise outcomes. */
data class SessionEvaluation(
    val plan: CyclePlan,
    val outcomes: List<ProgressionOutcome>,
)
