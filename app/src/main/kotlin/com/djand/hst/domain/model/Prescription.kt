package com.djand.hst.domain.model

/** Role of a set: [NORMAL] everywhere except negatives in phase 4. */
enum class SetKind { NORMAL, NEGATIVE }

/**
 * One prescribed set. Hitting [minReps] on every set counts as achieving the
 * prescription; [targetReps] is what the UI displays as the goal.
 */
data class SetPrescription(
    val weightKg: Double,
    val targetReps: Int,
    val minReps: Int,
    val kind: SetKind,
)

/**
 * All prescribed sets for one exercise inside one session. Self-contained so the
 * persisted plan can be reconstructed and re-evaluated without the exercise catalogue.
 */
data class ExercisePrescription(
    val exerciseId: String,
    val incrementKg: Double,
    val isCompound: Boolean,
    val sets: List<SetPrescription>,
)

/**
 * One fully prescribed workout session.
 *
 * The main cycle consists of sessions 1..24: phase 1 (15RM, w1-6), phase 2 (10RM, w7-12),
 * phase 3 (5RM, w13-18), phase 4 (post-5RM/negatives, w19-24). Each phase spans exactly 6
 * sessions. After workout 24 the user takes a week of strategic deconditioning before
 * starting the next cycle with new RM values.
 */
data class SessionPrescription(
    val sessionNumber: Int,
    val phase: Int,
    val workout: WorkoutLetter,
    val exercises: List<ExercisePrescription>,
)

/** A complete, deterministic prescription of one cycle (24 sessions). */
data class CyclePlan(
    val cycleNumber: Int,
    val sessions: List<SessionPrescription>,
)
