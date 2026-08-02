package com.djand.hst.domain.progression

import kotlin.math.floor
import kotlin.math.max

/**
 * Rep-max mathematics for the HST progression engine.
 *
 * The Epley formula is used in BOTH directions (locked-in design decision):
 *
 *     1RM   = W * (1 + reps / 30)
 *     RM(r) = 1RM / (1 + r / 30)
 *
 * The weight x reps entered at setup is treated as the TRUE rep max for that rep
 * count, so e.g. "80 kg x 10" yields 1RM = 80 * (1 + 10/30) = 106.67 kg and
 * 15RM = 106.67 / (1 + 15/30) = 71.11 kg, 10RM = 80 kg, 5RM = 91.43 kg.
 *
 * This object is pure and contains no Android dependencies.
 */
object RmMath {

    /** Divisor of the Epley formula. */
    const val EPLEY_DIVISOR = 30.0

    /** Epley estimate of the one-rep max from [weightKg] lifted for [reps] reps. */
    fun oneRepMax(weightKg: Double, reps: Int): Double {
        require(weightKg >= 0.0) { "Weight must be >= 0, got $weightKg" }
        require(reps >= 1) { "Reps must be >= 1, got $reps" }
        return weightKg * (1.0 + reps / EPLEY_DIVISOR)
    }

    /** Epley estimate of the [reps]-rep max from a known [oneRepMaxKg]. */
    fun repMax(oneRepMaxKg: Double, reps: Int): Double {
        require(oneRepMaxKg >= 0.0) { "1RM must be >= 0, got $oneRepMaxKg" }
        require(reps >= 1) { "Reps must be >= 1, got $reps" }
        return oneRepMaxKg / (1.0 + reps / EPLEY_DIVISOR)
    }

    /**
     * Converts a known rep max ([weightKg] x [reps]) into the estimated max for
     * [targetReps] reps, going through the 1RM.
     */
    fun repMaxFrom(weightKg: Double, reps: Int, targetReps: Int): Double =
        repMax(oneRepMax(weightKg, reps), targetReps)

    /**
     * Rounds [weightKg] to the nearest multiple of [incrementKg], ties rounding up
     * (e.g. 76.25 kg at a 2.5 kg increment -> 77.5 kg). Never returns a negative
     * weight, so bodyweight pull-ups (added weight 0) stay at 0.
     */
    fun roundToIncrement(weightKg: Double, incrementKg: Double): Double {
        require(incrementKg > 0.0) { "Increment must be positive, got $incrementKg" }
        return max(0.0, floor(weightKg / incrementKg + 0.5 + EPS) * incrementKg)
    }

    /** Guards against floating-point dust just below a rounding boundary. */
    private const val EPS = 1e-9
}
