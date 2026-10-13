package br.com.faru.boitata.testing

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import br.com.faru.boitata.core.clock.LocalSceneClock
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.sky.SkyModel
import br.com.faru.boitata.core.sky.SkyWeather
import br.com.faru.boitata.light.Fire
import br.com.faru.boitata.light.FlamePalette
import br.com.faru.boitata.weather.Fog
import br.com.faru.boitata.weather.Rain
import br.com.faru.boitata.weather.Snow
import br.com.faru.boitata.weather.SkyWindow
import br.com.faru.boitata.weather.ViewLayer
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What Boitatá promises about its own effects, checked by drawing them: they change every frame while they should be moving (no stepping), and a lower
 * quality costs less. These are the two regressions that actually happened (a parallax snapped to whole pixels; a scene that cannot get lighter).
 */
class EffectsTest {
    @Test
    fun rainChangesEveryFrame() {
        val stats = measureScene { Rain(Modifier.fillMaxSize(), intensity = 0.8f) }
        assertTrue(stats.identicalFrames <= 2, "rain drew the same picture twice in a row ${stats.identicalFrames} times, at frames ${stats.identicalAt}")
    }

    @Test
    fun snowChangesEveryFrame() {
        val stats = measureScene { Snow(Modifier.fillMaxSize(), intensity = 0.8f) }
        assertTrue(stats.identicalFrames <= 2, "snow stood still ${stats.identicalFrames} frames")
    }

    @Test
    fun fogDriftsEveryFrame() {
        val stats = measureScene { Fog(Modifier.fillMaxSize(), density = 0.8f) }
        assertTrue(stats.identicalFrames <= 3, "fog stood still ${stats.identicalFrames} frames")
    }

    @Test
    fun aFireChangesEveryFrame() {
        val stats = measureScene { Fire(Modifier.fillMaxSize(), palette = FlamePalette.Campfire) }
        assertTrue(stats.identicalFrames <= 2, "the fire stood still ${stats.identicalFrames} frames")
    }

    @Test
    fun theSkysParallaxMovesEveryFrameNotInWholePixelJumps() {
        // The slowest real case: a drift of a few pixels a second. Snapped to whole pixels this repeated the same frame for over a second at a time.
        val layers = layers()
        val stats = measureScene(frames = 180) {
            val clock = LocalSceneClock.current!!
            SkyWindow(
                sky = { SkyModel.at(15f, SkyWeather.Clear) }, layers = layers,
                parallax = { Offset(sin(clock.seconds() * 0.4f) * 0.25f, 0f) },
            )
        }
        assertTrue(stats.longestStill <= 3, "the parallax sat still for ${stats.longestStill} frames in a row (${stats.identicalFrames} of ${stats.frames} frames unchanged)")
    }

    @Test
    fun cloudsDriftSmoothly() {
        val layers = layers(); val strip = strip()
        val stats = measureScene(frames = 180) {
            SkyWindow(sky = { SkyModel.at(15f, SkyWeather(cloud = 0.8f)) }, layers = layers, parallax = { Offset.Zero }, clouds = strip)
        }
        assertTrue(stats.longestStill <= 3, "the clouds sat still for ${stats.longestStill} frames in a row")
    }

    @Test
    fun lowerQualityCostsLessForTheHeavyEffects() {
        fun cost(q: Quality, content: @androidx.compose.runtime.Composable () -> Unit) = measureScene(width = 960, height = 540, frames = 60, quality = q, content = content).meanMillis
        val rainHigh = cost(Quality.High) { Rain(Modifier.fillMaxSize(), intensity = 1f) }
        val rainLow = cost(Quality.Low) { Rain(Modifier.fillMaxSize(), intensity = 1f) }
        println("cost  rain High %.2f Low %.2f".format(rainHigh, rainLow))
        assertTrue(rainLow < rainHigh * 0.8, "rain at Low ($rainLow ms) should cost clearly less than at High ($rainHigh ms)")
        val fogHigh = cost(Quality.High) { Fog(Modifier.fillMaxSize(), density = 1f) }
        val fogLow = cost(Quality.Low) { Fog(Modifier.fillMaxSize(), density = 1f) }
        println("cost  fog High %.2f Low %.2f".format(fogHigh, fogLow))
        assertTrue(fogLow < fogHigh * 0.8, "fog at Low ($fogLow ms) should cost clearly less than at High ($fogHigh ms)")
        val fireHigh = cost(Quality.High) { Fire(Modifier.fillMaxSize()) }
        val fireLow = cost(Quality.Low) { Fire(Modifier.fillMaxSize()) }
        println("cost  fire High %.2f Low %.2f".format(fireHigh, fireLow))
        assertTrue(fireLow < fireHigh, "a fire at Low ($fireLow ms) should cost less than at High ($fireHigh ms)")
    }

    private fun layers(): List<ViewLayer> = listOf(0.2f, 0.5f, 0.9f).map { ViewLayer(roofs(it), it) }

    private fun roofs(seed: Float): ImageBitmap = bitmap(960, 540) {
        var x = 0f
        var i = 0
        while (x < 960f) {
            val w = 30f + 40f * ((i * 7 + (seed * 10).toInt()) % 5) / 4f
            val h = 80f + 160f * ((i * 3 + (seed * 10).toInt()) % 7) / 6f
            drawRect(Color(0xFF14110F), Offset(x, 324f - h), Size(w, 540f - (324f - h)))
            x += w + 2f; i++
        }
    }

    private fun strip(): ImageBitmap = bitmap(1024, 128) {
        for (i in 0 until 5) drawCircle(Color(0xFFDDDDDD), 24f + 8f * i, Offset(80f + i * 190f, 64f))
    }

    private fun bitmap(w: Int, h: Int, draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit): ImageBitmap {
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat()), draw)
        return image
    }
}
