package br.com.faru.boitata.core.fx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FxTest {
    @Test
    fun aCycleRepeatsEveryPeriodAndStaysInsideZeroToOne() {
        assertEquals(cycle(0.5f, 2f), cycle(2.5f, 2f), 1e-5f)
        for (i in 0..200) assertTrue(cycle(i * 0.37f, 1.3f, 0.2f) in 0f..1f)
    }

    @Test
    fun aCyclesPhaseShiftsWhereItStarts() {
        assertEquals(0.25f, cycle(0f, 4f, 0.25f), 1e-5f)
    }

    @Test
    fun theBumpIsZeroAtTheEndsAndOneInTheMiddle() {
        assertEquals(0f, bump(0f), 1e-5f)
        assertEquals(1f, bump(0.5f), 1e-5f)
        assertEquals(0f, bump(1f), 1e-5f)
    }

    @Test
    fun withNoReachABandStandsStraightBelowTheWindow() {
        val q = LightShaft.band(10f, 50f, 100f, 60f, lean = 1f, reach = 0f, v0 = 0f, v1 = 1f)
        assertEquals(listOf(10f, 100f, 50f, 100f, 50f, 160f, 10f, 160f), q.toList())
    }

    @Test
    fun theFarEndOfThePatchLeansMoreThanTheNearEnd() {
        val q = LightShaft.band(0f, 10f, 0f, 100f, lean = 1f, reach = 50f, v0 = 0f, v1 = 1f)
        assertTrue(q[6] > q[0], "far-left is shifted farther than wall-left")
    }

    @Test
    fun theLightLeansTheWayItTravels() {
        val right = LightShaft.band(0f, 10f, 0f, 100f, lean = 1f, reach = 50f, v0 = 0f, v1 = 1f)
        val left = LightShaft.band(0f, 10f, 0f, 100f, lean = -1f, reach = 50f, v0 = 0f, v1 = 1f)
        assertTrue(right[6] > 0f && left[6] < 0f)
    }

    @Test
    fun aLowSunReachesFartherThanAHighOne() {
        assertTrue(LightShaft.reachFor(0.1f, 100f) > LightShaft.reachFor(0.9f, 100f))
    }
}
