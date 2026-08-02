package com.djand.hst.domain.progression

/**
 * How to load a bar for [PlateCalculator]: the plates for ONE side (largest first),
 * the resulting [totalKg] (bar + both sides) and [remainderKg] — how much of the
 * target could not be loaded with the available plates (0 when exact).
 */
data class PlateLoad(
    val perSide: List<Double>,
    val totalKg: Double,
    val remainderKg: Double,
)

/**
 * Greedy largest-plate-first bar loading. Standard kilogram plates make the greedy
 * choice optimal. A target at or below the bar weight yields the empty bar.
 */
object PlateCalculator {

    /** Standard kg plate denominations. */
    val STANDARD_PLATES_KG: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

    const val DEFAULT_BAR_KG = 20.0

    fun calculate(
        targetKg: Double,
        barKg: Double = DEFAULT_BAR_KG,
        availablePlatesKg: List<Double> = STANDARD_PLATES_KG,
    ): PlateLoad {
        require(targetKg >= 0.0) { "Target must be >= 0, got $targetKg" }
        require(barKg >= 0.0) { "Bar must be >= 0, got $barKg" }
        require(availablePlatesKg.isNotEmpty()) { "At least one plate denomination is required" }
        require(availablePlatesKg.all { it > 0.0 }) { "Plates must be positive" }

        if (targetKg <= barKg) {
            return PlateLoad(perSide = emptyList(), totalKg = barKg, remainderKg = 0.0)
        }

        var perSideRemaining = (targetKg - barKg) / 2.0
        val perSide = ArrayList<Double>()
        for (plate in availablePlatesKg.sortedDescending()) {
            while (perSideRemaining + EPS >= plate) {
                perSide += plate
                perSideRemaining -= plate
            }
        }
        val total = barKg + 2.0 * perSide.sum()
        return PlateLoad(
            perSide = perSide,
            totalKg = total,
            remainderKg = (targetKg - total).coerceAtLeast(0.0),
        )
    }

    /** Floating-point tolerance when comparing the remaining per-side weight. */
    private const val EPS = 1e-9
}
