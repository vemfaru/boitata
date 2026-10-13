package br.com.faru.boitata.core.clock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QualityGovernorTest {
    private val fast = 1f / 60f
    private val slow = 1f / 30f

    private fun QualityGovernor.run(frames: Int, seconds: Float) = repeat(frames) { onFrame(seconds) }

    @Test
    fun aSteadySixtyFramesASecondStaysAtHigh() {
        val g = QualityGovernor()
        g.run(2000, fast)
        assertEquals(Quality.High, g.quality)
    }

    @Test
    fun slowFramesStepDownOneLevelAtATime() {
        val g = QualityGovernor()
        g.run(60, slow)
        assertEquals(Quality.Medium, g.quality, "about a second of slow frames costs one level")
        g.run(60, slow)
        assertEquals(Quality.Low, g.quality)
        g.run(1000, slow)
        assertEquals(Quality.Low, g.quality, "Low is the floor")
    }

    @Test
    fun aFewSlowFramesDoNotStepDown() {
        val g = QualityGovernor()
        g.run(20, slow)
        g.run(100, fast)
        assertEquals(Quality.High, g.quality)
    }

    @Test
    fun hitchesAreIgnored() {
        val g = QualityGovernor()
        repeat(100) { g.onFrame(1f) }
        assertEquals(Quality.High, g.quality, "a one-second frame is a pause, not a heavy scene")
    }

    @Test
    fun itStepsBackUpOnlyAfterTheHoldAndSeveralCalmSeconds() {
        val g = QualityGovernor()
        g.run(60, slow)
        assertEquals(Quality.Medium, g.quality)
        g.run(300, fast)
        assertEquals(Quality.Medium, g.quality, "five calm seconds is inside the eight-second hold")
        g.run(900, fast)
        assertEquals(Quality.High, g.quality)
    }

    @Test
    fun aSceneThatIsOnlyTooHeavyAtHighDoesNotFlickerBetweenTheTwo() {
        // At High every frame is slow; at Medium and below they are fine. A governor that ignored its history would climb every few seconds.
        val g = QualityGovernor()
        var climbs = 0
        var previous = g.quality
        repeat(20 * 60 * 60) {                       // twenty minutes at 60 fps
            g.onFrame(if (g.quality == Quality.High) slow else fast)
            if (g.quality.ordinal > previous.ordinal) climbs++
            previous = g.quality
        }
        assertTrue(climbs in 1..25, "it tries High again now and then, waiting longer each time: $climbs climbs in 20 minutes")
        assertTrue(g.quality != Quality.Low, "and it does not stay at the floor either")
    }

    @Test
    fun particlesScaleWithQualityButNeverToNothing() {
        assertEquals(420, Quality.High.scaled(420))
        assertTrue(Quality.Low.scaled(420) in 100..200)
        assertEquals(1, Quality.Low.scaled(1))
        assertEquals(0, Quality.Low.scaled(0))
    }
}
