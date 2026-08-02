package com.djand.hst.domain.model

/** Role of a set inside a block-4 prescription (or [NORMAL] everywhere else). */
enum class SetKind { NORMAL, TOP, BACK_OFF }

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
 * All prescribed sets for one exercise inside one session. Self-contained on purpose
 * ([incrementKg], [isCompound]) so a persisted plan can be re-evaluated and adjusted
 * (repeat / -10% reset / isolation bump) without consulting the exercise catalogue.
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
 * The main cycle consists of sessions 1..24: week 1..8, block 1..4 (6 sessions per
 * 2-week block). The deload week consists of sessions 25..27 (week 9, block 0,
 * [isDeload] = true) and does not drive progression.
 */
data class SessionPrescription(
    val sessionNumber: Int,
    val week: Int,
    val block: Int,
    val workout: WorkoutLetter,
    val isDeload: Boolean,
    val exercises: List<ExercisePrescription>,
)

/** A complete, deterministic prescription of one 8-week cycle (24 sessions). */
data class CyclePlan(
    val cycleNumber: Int,
    val sessions: List<SessionPrescription>,
)
