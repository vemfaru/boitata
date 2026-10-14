package br.com.faru.boitata.testing

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import br.com.faru.boitata.core.clock.FixedSceneClock
import br.com.faru.boitata.core.clock.WithSceneClock
import br.com.faru.boitata.core.draw.sway
import br.com.faru.boitata.core.wind.LocalSceneWind
import br.com.faru.boitata.core.wind.SceneWind
import br.com.faru.boitata.core.wind.Wind
import br.com.faru.boitata.core.wind.WithSceneWind
import br.com.faru.boitata.light.Fire
import br.com.faru.boitata.weather.Rain
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** The wind really moves things: rain slants the way it blows, a flame leans, a swaying thing keeps its foot, and what drifts by travelled distance never jumps when the wind changes. */
class WindTest {
    private val right = Wind(speed = 0.7f, direction = 1f, gustiness = 0f, turbulence = 0f)
    private val left = Wind(speed = 0.7f, direction = -1f, gustiness = 0f, turbulence = 0f)

    /** The mean x of the top third of the opaque pixels minus the mean x of the bottom third: positive when the picture leans to the right going up. */
    private fun lean(still: Still, threshold: Int = 40): Double {
        val top = still.meanX(0, still.height / 3, threshold) ?: return 0.0
        val bottom = still.meanX(still.height * 2 / 3, still.height, threshold) ?: return 0.0
        return top - bottom
    }

    /** Which way the streaks run: the sideways shift (in pixels) that makes a lit pixel most often meet another a few rows lower. Positive: down and to the right. */
    private fun slant(still: Still, rows: Int = 12): Int {
        var best = 0; var bestScore = -1
        for (dx in -8..8) {
            var score = 0
            for (y in 0 until still.height - rows) for (x in 8 until still.width - 8) if (still.alpha(x, y) > 60 && still.alpha(x + dx, y + rows) > 60) score++
            if (score > bestScore) { bestScore = score; best = dx }
        }
        return best
    }

    @Test
    fun aSwayKeepsItsFootAndMovesItsTopTheWayYouSay() {
        fun bar(bend: Float) = renderStill(width = 200, height = 200) {
            Spacer(Modifier.fillMaxSize().drawBehind { sway(100f, 190f, bend) { drawRect(Color.White, Offset(95f, 40f), Size(10f, 150f)) } })
        }
        val straight = lean(bar(0f)); val rightward = lean(bar(1f)); val leftward = lean(bar(-1f))
        assertTrue(abs(straight) < 0.5, "straight stays upright: $straight")
        assertTrue(rightward > 5.0, "bent right, the top is to the right of the foot: $rightward")
        assertTrue(leftward < -5.0, "bent left, to the left: $leftward")
        val foot = bar(1f).meanX(190 - 20, 190)!!
        assertTrue(abs(foot - 100.0) < 3.0, "the foot has not moved: $foot")
    }

    @Test
    fun aFlameLeansDownwindAndStandsUpInACalm() {
        fun flame(wind: Wind) = renderStill(width = 300, height = 300) { WithSceneWind(wind) { Fire(Modifier.fillMaxSize()) } }
        val calm = lean(flame(Wind.Calm), 150); val toRight = lean(flame(right), 150); val toLeft = lean(flame(left), 150)
        assertTrue(toRight > calm + 4.0, "blowing right the flame leans right: calm $calm, right $toRight")
        assertTrue(toLeft < calm - 4.0, "blowing left it leans left: calm $calm, left $toLeft")
    }

    @Test
    fun rainSlantsTheWayTheWindBlows() {
        fun rain(wind: Wind) = renderStill(width = 400, height = 300) { WithSceneWind(wind) { Rain(Modifier.fillMaxSize(), intensity = 1f) } }
        // A drop blown right runs down and to the right.
        val calm = slant(rain(Wind.Calm)); val toRight = slant(rain(right)); val toLeft = slant(rain(left))
        assertTrue(abs(calm) <= 1 && toRight >= 2 && toLeft <= -2, "calm $calm, right $toRight, left $toLeft")
    }

    @Test
    fun theWindPicksUpWhenYouRaiseItAndTheFlameFollows() {
        fun flame(speed: Float) = renderStill(width = 300, height = 300) { WithSceneWind(right.copy(speed = speed)) { Fire(Modifier.fillMaxSize()) } }
        val light = lean(flame(0.15f), 150); val strong = lean(flame(0.9f), 150)
        assertTrue(strong > light + 2.0, "a stronger wind bends it more: $light then $strong")
    }

    @Test
    fun theDistanceTheAirHasTravelledGrowsWithTheWindAndNeverJumpsWhenItChanges() {
        var wind by mutableStateOf(Wind(speed = 0.3f, direction = 1f, gustiness = 0f, turbulence = 0f))
        var t by mutableFloatStateOf(0f)
        var captured: SceneWind? = null
        val scene = ImageComposeScene(width = 100, height = 100, density = Density(1f)) {
            WithSceneClock(FixedSceneClock({ t })) { WithSceneWind(wind) { captured = LocalSceneWind.current } }
        }
        fun step(seconds: Float) {
            var left = seconds
            while (left > 1e-4f) {
                val dt = minOf(0.05f, left); t += dt; left -= dt
                Snapshot.sendApplyNotifications(); scene.render((t * 1e9).toLong()); scene.render((t * 1e9).toLong() + 1)
            }
        }
        step(0.1f)
        val start = captured!!.travelled
        step(10f)
        val slow = captured!!.travelled - start
        assertTrue(abs(slow - 3.0f) < 0.5f, "0.3 for 10 seconds is about 3 units: $slow")
        val before = captured!!.travelled
        wind = wind.copy(speed = 0.9f)
        step(0.05f)
        assertTrue(captured!!.travelled - before < 0.2f, "switching to a gale does not make the distance jump: ${captured!!.travelled - before}")
        val mid = captured!!.travelled
        step(10f)
        assertTrue(captured!!.travelled - mid > 8f, "but from then on it grows three times as fast")
        wind = wind.copy(direction = -1f)
        val turning = captured!!.travelled
        step(20f)
        assertTrue(captured!!.travelled < turning, "blowing the other way, the air travels back")
        scene.close()
    }
}
