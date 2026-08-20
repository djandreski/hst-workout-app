package com.djand.hst.domain.model

/**
 * Everything the progression engine needs to know about one exercise.
 *
 * [weightKg] x [reps] is the user's approximate 15RM as entered in the setup wizard.
 * The progression engine derives 10RM and 5RM from it via Epley, and each of the four
 * HST phases ramps linearly (by [incrementKg] per workout) toward the corresponding RM.
 *
 * For [Equipment.BODYWEIGHT] exercises (pull-ups), [weightKg] is the ADDED weight only;
 * 0 kg means bodyweight alone.
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
