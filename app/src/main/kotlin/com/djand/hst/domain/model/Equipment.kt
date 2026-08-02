package com.djand.hst.domain.model

/**
 * How the load for an exercise is provided. Determines the default smallest weight
 * increment used for rounding; the effective increment is configurable in Settings and
 * is passed to the engine explicitly via [ExerciseInput.incrementKg].
 *
 * Defaults (metric only):
 * - [BARBELL]: 2.5 kg (smallest pair of plates is 1.25 kg per side)
 * - [MACHINE]: 2.5 kg
 * - [DUMBBELL]: 2.0 kg (next available pair)
 * - [CABLE]: 2.5 kg (next plate)
 * - [BODYWEIGHT]: 2.5 kg — exercises like pull-ups where [ExerciseInput.weightKg] is the
 *   ADDED weight only (0 kg = bodyweight alone); extra load hangs from a belt.
 */
enum class Equipment(val defaultIncrementKg: Double) {
    BARBELL(2.5),
    MACHINE(2.5),
    DUMBBELL(2.0),
    CABLE(2.5),
    BODYWEIGHT(2.5),
}
