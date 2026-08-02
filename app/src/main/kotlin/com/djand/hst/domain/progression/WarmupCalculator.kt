package com.djand.hst.domain.progression

/** One warm-up set suggestion. */
data class WarmupSet(val weightKg: Double, val reps: Int)

/**
 * Simple 40/60/80% warm-up ramp towards the work weight. Each step is rounded to the
 * exercise's increment; steps that round to 0, reach the work weight, or duplicate
 * the previous step (common for light isolation weights) are dropped.
 */
object WarmupCalculator {

    /** Ramp fractions paired with suggested reps: 40% x 8, 60% x 5, 80% x 3. */
    private val RAMP: List<Pair<Double, Int>> = listOf(0.40 to 8, 0.60 to 5, 0.80 to 3)

    fun calculate(workWeightKg: Double, incrementKg: Double): List<WarmupSet> {
        require(workWeightKg > 0.0) { "Work weight must be positive, got $workWeightKg" }
        require(incrementKg > 0.0) { "Increment must be positive, got $incrementKg" }
        return RAMP
            .map { (fraction, reps) -> RmMath.roundToIncrement(workWeightKg * fraction, incrementKg) to reps }
            .filter { (weight, _) -> weight > 0.0 && weight < workWeightKg }
            .distinctBy { (weight, _) -> weight }
            .map { (weight, reps) -> WarmupSet(weight, reps) }
    }
}
