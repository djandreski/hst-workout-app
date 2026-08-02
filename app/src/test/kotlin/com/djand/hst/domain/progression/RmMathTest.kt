package com.djand.hst.domain.progression

import org.junit.Assert.assertEquals
import org.junit.Test

class RmMathTest {

    @Test
    fun `one rep max uses Epley`() {
        assertEquals(106.667, RmMath.oneRepMax(80.0, 10), 0.001)
        assertEquals(116.667, RmMath.oneRepMax(100.0, 5), 0.001)
        assertEquals(62.0, RmMath.oneRepMax(60.0, 1), 1e-9)
    }

    @Test
    fun `rep max inverts Epley`() {
        val oneRm = RmMath.oneRepMax(80.0, 10)
        assertEquals(71.111, RmMath.repMax(oneRm, 15), 0.001)
        assertEquals(91.429, RmMath.repMax(oneRm, 5), 0.001)
    }

    @Test
    fun `rep max round-trips through the one rep max`() {
        assertEquals(80.0, RmMath.repMaxFrom(80.0, 10, 10), 1e-9)
        assertEquals(100.0, RmMath.repMaxFrom(100.0, 5, 5), 1e-9)
    }

    @Test
    fun `rounding snaps to the nearest barbell increment`() {
        assertEquals(70.0, RmMath.roundToIncrement(71.11, 2.5), 1e-9)
        assertEquals(75.0, RmMath.roundToIncrement(73.89, 2.5), 1e-9)
        assertEquals(60.0, RmMath.roundToIncrement(60.0, 2.5), 1e-9)
    }

    @Test
    fun `rounding ties go up`() {
        assertEquals(77.5, RmMath.roundToIncrement(76.25, 2.5), 1e-9)
    }

    @Test
    fun `rounding respects the dumbbell increment`() {
        assertEquals(8.0, RmMath.roundToIncrement(8.4, 2.0), 1e-9)
        assertEquals(10.0, RmMath.roundToIncrement(9.45, 2.0), 1e-9)
    }

    @Test
    fun `rounding never goes below zero`() {
        assertEquals(0.0, RmMath.roundToIncrement(0.0, 2.5), 1e-9)
        assertEquals(0.0, RmMath.roundToIncrement(-3.0, 2.5), 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `increment must be positive`() {
        RmMath.roundToIncrement(80.0, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reps must be positive`() {
        RmMath.oneRepMax(80.0, 0)
    }
}
