package com.djand.hst.domain.progression

import org.junit.Assert.assertEquals
import org.junit.Test

class WarmupCalculatorTest {

    @Test
    fun `ramp is 40 60 80 percent of the work weight rounded to the increment`() {
        val sets = WarmupCalculator.calculate(92.5, 2.5)
        assertEquals(
            listOf(
                WarmupSet(37.5, 8),
                WarmupSet(55.0, 5),
                WarmupSet(75.0, 3),
            ),
            sets,
        )
    }

    @Test
    fun `light weights drop duplicate steps`() {
        // 8 kg with a 2 kg increment: 40% and 60% both round to 4 kg.
        val sets = WarmupCalculator.calculate(8.0, 2.0)
        assertEquals(listOf(WarmupSet(4.0, 8), WarmupSet(6.0, 3)), sets)
    }

    @Test
    fun `steps reaching the work weight are dropped`() {
        // 5 kg with a 2.5 kg increment: 80% rounds to the work weight itself.
        val sets = WarmupCalculator.calculate(5.0, 2.5)
        assertEquals(listOf(WarmupSet(2.5, 8)), sets)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `work weight must be positive`() {
        WarmupCalculator.calculate(0.0, 2.5)
    }
}
