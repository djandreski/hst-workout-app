package com.djand.hst.data.local.relation

import com.djand.hst.domain.model.SetKind

/**
 * One performed set of an exercise, enriched with its session's coordinates —
 * the row shape used by the per-exercise history in Statistics.
 */
data class ExerciseHistoryRow(
    val sessionId: Long,
    val cycleId: Long,
    val sessionNumber: Int,
    val completedAtEpochMs: Long?,
    val exerciseId: String,
    val setIndex: Int,
    val kind: SetKind,
    val prescribedWeightKg: Double,
    val prescribedTargetReps: Int,
    val completedReps: Int?,
)
