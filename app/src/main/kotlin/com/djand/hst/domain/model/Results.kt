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

/** How the engine classified a finished exercise result. */
enum class ProgressionEvent {
    /** Every set reached at least its minimum reps. */
    ACHIEVED,

    /** Not all sets reached minimum reps. */
    MISSED,

    /** Exercise was skipped; no progression impact. */
    SKIPPED,
}

/** Per-exercise outcome of evaluating a finished session. */
data class ProgressionOutcome(
    val exerciseId: String,
    val event: ProgressionEvent,
    val consecutiveMisses: Int = 0,
)

/** Result of evaluating one finished session: per-exercise outcomes. */
data class SessionEvaluation(
    val plan: CyclePlan,
    val outcomes: List<ProgressionOutcome>,
)
