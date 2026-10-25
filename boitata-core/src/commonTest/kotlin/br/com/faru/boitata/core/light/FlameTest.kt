package br.com.faru.boitata.core.light

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlameTest {
    private val o = FloatArray(3)

    @Test
    fun aBulbDoesNotMove() {
        Flame.at(12.3f, 0f, o)
        assertEquals(0f, o[0]); assertEquals(0f, o[1]); assertEquals(1f, o[2])
    }

    @Test
    fun aCandleWaversByMillimetresAndNeverJumps() {
        var maxSway = 0f; var maxJump = 0f; var prev = Float.NaN
        var t = 0f
        while (t < 30f) {
            Flame.at(t, 1f, o)
            maxSway = maxOf(maxSway, abs(o[0]))
            assertTrue(o[2] in 0.85f..1.15f, "brightness ${o[2]}")
            if (!prev.isNaN()) maxJump = maxOf(maxJump, abs(o[0] - prev))
            prev = o[0]; t += 1f / Flame.RATE
        }
        assertTrue(maxSway in 0.2f..(Flame.SWAY_CM + Flame.GUST_CM), "sway $maxSway cm")
        assertTrue(maxJump < 0.3f, "a step of the flame moves at most 3 mm: $maxJump")
    }

    @Test
    fun aFlameBehindGlassIsSteadier() {
        var open = 0f; var glass = 0f; var t = 0f
        while (t < 20f) { Flame.at(t, 1f, o); open += abs(o[0]); Flame.at(t, 0.3f, o); glass += abs(o[0]); t += 0.05f }
        assertEquals(0.3f, glass / open, 1e-3f)
    }
}
