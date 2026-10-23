package br.com.faru.boitata.testing

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.Density
import br.com.faru.boitata.core.clock.SceneClock
import br.com.faru.boitata.core.clock.SceneConfig
import br.com.faru.boitata.core.clock.rememberSceneClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler

/**
 * `SceneConfig.maxFps`: the clock ticks at most that often, so on a fast screen a scene that moves only by its clock is drawn less often, and
 * the time it shows still runs at the real pace.
 */
class SceneClockPaceTest {
    /** Renders [seconds] of frames at [hz] and counts the frames in which the clock moved; returns the count and the clock's time gone by. */
    private fun run(hz: Int, maxFps: Int, seconds: Int = 2): Pair<Int, Float> {
        val scheduler = TestCoroutineScheduler()
        lateinit var clock: SceneClock
        val scene = ImageComposeScene(8, 8, Density(1f), coroutineContext = StandardTestDispatcher(scheduler)) {
            clock = rememberSceneClock(SceneConfig(adaptiveQuality = false, maxFps = maxFps))
            Spacer(Modifier.fillMaxSize().drawBehind { clock.seconds() })
        }
        val frameNanos = 1_000_000_000L / hz
        var t = 0L
        fun frame() { scheduler.advanceTimeBy(frameNanos / 1_000_000L); scheduler.runCurrent(); t += frameNanos; scene.render(t) }
        repeat(10) { frame() }
        val start = clock.seconds()
        var last = start
        var ticks = 0
        repeat(hz * seconds) {
            frame()
            val now = clock.seconds()
            if (now != last) { ticks++; last = now }
        }
        scene.close()
        return ticks to (last - start)
    }

    @Test
    fun withoutAPaceTheClockTicksEveryFrame() {
        val (ticks, _) = run(hz = 144, maxFps = 0)
        assertTrue(ticks >= 144 * 2 - 2, "ticked $ticks times in 288 frames")
    }

    @Test
    fun atAHundredTheClockTicksEveryOtherFrameOnA144HzScreen() {
        val (ticks, gone) = run(hz = 144, maxFps = 100)
        assertEquals(144f, ticks.toFloat(), 3f, "ticked $ticks times in 288 frames")
        assertEquals(2f, gone, 0.05f, "the scene's time still runs at the real pace")
    }

    @Test
    fun atAHundredTheClockTicksEveryFrameOnA60HzScreen() {
        val (ticks, _) = run(hz = 60, maxFps = 100)
        assertTrue(ticks >= 60 * 2 - 2, "ticked $ticks times in 120 frames")
    }

    private fun assertEquals(expected: Float, actual: Float, tolerance: Float, message: String) =
        assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "$message (expected $expected ± $tolerance, was $actual)")
}
