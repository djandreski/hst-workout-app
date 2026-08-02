package com.djand.hst.domain.model

/**
 * Everything the progression engine needs to know about one exercise.
 *
 * [weightKg] x [reps] is the user's current working weight as entered in the setup
 * wizard (NOT a 1RM). It is treated as the TRUE rep max for that rep count: the whole
 * cycle's block ladders are derived from it via Epley, and each block's ladder peaks
 * exactly at the corresponding derived rep max.
 *
 * For [Equipment.BODYWEIGHT] exercises (pull-ups), [weightKg] is the ADDED weight only;
 * 0 kg means bodyweight alone.
 *
 * [isCompound] selects the progression policy:
 * - compound: block ladders with top-set/back-off in block 4; miss -> repeat,
 *   double miss -> -10% and regenerate.
 * - isolation: same ladders in blocks 1-2, flat 10-15 rep work in blocks 3-4 with
 *   reactive reps-first progression.
 */
data class ExerciseInput(
    val exerciseId: String,
    val equipment: Equipment,
    val incrementKg: Double,
    val isCompound: Boolean,
    val weightKg: Double,
    val reps: Int,
) {
    init {
        require(incrementKg > 0.0) { "Increment must be positive, got $incrementKg" }
        require(weightKg >= 0.0) { "Weight must be >= 0, got $weightKg" }
        require(reps in 1..30) { "Reps must be in 1..30, got $reps" }
    }
}
