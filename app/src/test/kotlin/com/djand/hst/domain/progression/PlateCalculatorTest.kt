package com.djand.hst.domain.progression

import org.junit.Assert.assertEquals
import org.junit.Test

class PlateCalculatorTest {

    @Test
    fun `exact loads are found greedily largest plate first`() {
        val load = PlateCalculator.calculate(100.0, barKg = 20.0)
        assertEquals(listOf(25.0, 15.0), load.perSide) // 40 kg per side
        assertEquals(100.0, load.totalKg, 1e-9)
        assertEquals(0.0, load.remainderKg, 1e-9)
    }

    @Test
    fun `small plates are used for exact fractional loads`() {
        val load = PlateCalculator.calculate(102.5, barKg = 20.0)
        assertEquals(listOf(25.0, 15.0, 1.25), load.perSide)
        assertEquals(102.5, load.totalKg, 1e-9)
        assertEquals(0.0, load.remainderKg, 1e-9)
    }

    @Test
    fun `unreachable targets report the remainder`() {
        val load = PlateCalculator.calculate(103.0, barKg = 20.0)
        assertEquals(listOf(25.0, 15.0, 1.25), load.perSide)
        assertEquals(102.5, load.totalKg, 1e-9)
        assertEquals(0.5, load.remainderKg, 1e-9)
    }

    @Test
    fun `target at or below the bar is the empty bar`() {
        val load = PlateCalculator.calculate(15.0, barKg = 20.0)
        assertEquals(emptyList<Double>(), load.perSide)
        assertEquals(20.0, load.totalKg, 1e-9)
        assertEquals(0.0, load.remainderKg, 1e-9)
    }

    @Test
    fun `single plate per side`() {
        val load = PlateCalculator.calculate(60.0, barKg = 20.0)
        assertEquals(listOf(20.0), load.perSide)
        assertEquals(60.0, load.totalKg, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `plate list must not be empty`() {
        PlateCalculator.calculate(100.0, barKg = 20.0, availablePlatesKg = emptyList())
    }
}
