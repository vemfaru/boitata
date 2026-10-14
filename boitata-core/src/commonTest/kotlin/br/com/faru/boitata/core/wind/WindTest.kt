package br.com.faru.boitata.core.wind

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindTest {
    private fun samples(wind: Wind, place: Float = 0f, seconds: Int = 600, step: Float = 0.1f) = (0 until (seconds / step).toInt()).map { wind.at(it * step, place).x }

    @Test
    fun aCalmWindDoesNothingAndTheDirectionDecidesTheSign() {
        assertEquals(0f, Wind.Calm.at(12f).x)
        assertTrue(samples(Wind(speed = 0.5f, direction = 1f)).all { it >= 0f }, "blows right")
        assertTrue(samples(Wind(speed = 0.5f, direction = -1f)).all { it <= 0f }, "blows left")
        assertEquals(0f, Wind(speed = 0.8f, direction = 0f).at(3f).x)
    }

    @Test
    fun aSteadyWindIsConstant() {
        val steady = Wind(speed = 0.4f, gustiness = 0f, turbulence = 0f)
        assertTrue(samples(steady, seconds = 60).all { abs(it - 0.4f) < 1e-5f })
    }

    @Test
    fun gustsSwingAroundTheAverageWhichStaysTheSpeed() {
        val wind = Wind(speed = 0.5f, gustiness = 0.8f, turbulence = 0f, gustPeriod = 5f)
        val xs = samples(wind)
        assertEquals(0.5f, xs.average().toFloat(), 0.04f, "the average is the speed")
        assertTrue(xs.max() > 0.5f * 1.25f, "gusts rise above it: ${xs.max()}")
        assertTrue(xs.min() < 0.5f * 0.75f, "and lulls fall below it: ${xs.min()}")
        assertTrue(xs.max() <= 0.5f * 1.5f + 1e-4f && xs.min() >= 0.5f * 0.5f - 1e-4f, "within the stated swing")
    }

    @Test
    fun moreGustinessMeansMoreVariation() {
        fun spread(g: Float) = samples(Wind(speed = 0.5f, gustiness = g, turbulence = 0f)).let { it.max() - it.min() }
        assertTrue(spread(0.2f) < spread(0.6f) && spread(0.6f) < spread(1f))
    }

    @Test
    fun aGustCrossesTheSceneAndReachesTheFarSideLater() {
        val wind = Wind(speed = 0.6f, direction = 1f, gustiness = 1f, turbulence = 0f, gustPeriod = 8f, travelSeconds = 2f)
        fun peakTime(place: Float) = (0 until 800).maxBy { wind.at(it * 0.01f, place).x } * 0.01f
        // Blowing right: the left side (place 0) gusts first, the right side (1) two seconds later.
        val left = peakTime(0f); val right = peakTime(1f)
        val lag = ((right - left) + 8f) % 8f
        assertEquals(2f, lag, 0.15f, "a gust takes travelSeconds to cross")
        val back = Wind(speed = 0.6f, direction = -1f, gustiness = 1f, turbulence = 0f, gustPeriod = 8f, travelSeconds = 2f)
        fun peakBack(place: Float) = (0 until 800).maxBy { back.at(it * 0.01f, place).x * -1f } * 0.01f
        assertEquals(2f, ((peakBack(0f) - peakBack(1f)) + 8f) % 8f, 0.15f, "blowing left it crosses from the right")
    }

    @Test
    fun itIsTheSameEveryTimeAndAlwaysInRange() {
        val wind = Wind.Gale
        assertEquals(wind.at(7.3f, 0.4f).x, Wind.Gale.at(7.3f, 0.4f).x)
        assertTrue(samples(wind, seconds = 120).all { it in 0f..1.6f })
        assertTrue(Wind(speed = 5f, direction = 9f).at(2f).x <= 1.6f, "out-of-range values are clamped")
    }

    @Test
    fun theNamedWindsGrowInStrength() {
        val means = listOf(Wind.Calm, Wind.Breeze, Wind.Windy, Wind.Gale).map { w -> samples(w, seconds = 300).average() }
        assertTrue(means.zipWithNext().all { (a, b) -> b > a }, "$means")
    }
}
