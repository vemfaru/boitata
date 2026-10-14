package br.com.faru.boitata.playground

import androidx.compose.runtime.CompositionLocalProvider
import br.com.faru.boitata.core.clock.FixedSceneClock
import br.com.faru.boitata.core.clock.WithSceneClock
import br.com.faru.boitata.core.wind.Wind
import br.com.faru.boitata.core.wind.WithSceneWind
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.runBlocking

/**
 * Draws the weather scene for a few seconds at 60 fps and prints how long a frame takes (CPU raster: relative, not what a GPU shows), then how often a
 * frame came out pixel-identical to the one before while layers and clouds should be moving. A smooth parallax changes every frame; one snapped to
 * whole pixels sits still for tens of frames and then jumps. `./gradlew :samples:playground-desktop:benchFrames`
 */
fun main() {
    stutter()
    val sprites = runBlocking { loadSprites() }
    for ((name, kind) in listOf("rain" to WeatherKind.Rain, "storm" to WeatherKind.Storm, "clear" to WeatherKind.Clear)) {
        var t by mutableFloatStateOf(0f)
        val scene = ImageComposeScene(width = 1280, height = 720, density = Density(1f)) {
            CompositionLocalProvider(LocalSprites provides sprites) { WithSceneClock(FixedSceneClock({ t })) { WithSceneWind(Wind.Windy) { WeatherScene({ t }, hour = 15f, kind = kind) } } }
        }
        repeat(30) { scene.render(it * 16_666_667L) }
        val frames = 180
        val times = LongArray(frames)
        for (i in 0 until frames) {
            t = (i + 30) / 60f
            val start = System.nanoTime()
            scene.render((i + 30) * 16_666_667L)
            times[i] = System.nanoTime() - start
        }
        scene.close()
        val ms = times.map { it / 1e6 }.sorted()
        println("weather-%-6s mean %.1f ms  p50 %.1f  p95 %.1f  max %.1f".format(name, ms.average(), ms[frames / 2], ms[(frames * 0.95).toInt()], ms.last()))
    }
}

private fun stutter() {
    val sprites = runBlocking { loadSprites() }
    for ((name, kind) in listOf("clear (layers)" to WeatherKind.Clear, "cloudy (layers and clouds)" to WeatherKind.Cloudy)) {
        var t by mutableFloatStateOf(0f)
        val scene = ImageComposeScene(width = 1280, height = 720, density = Density(1f)) {
            CompositionLocalProvider(LocalSprites provides sprites) { WithSceneClock(FixedSceneClock({ t })) { WithSceneWind(Wind.Windy) { WeatherScene({ t }, hour = 15f, kind = kind) } } }
        }
        repeat(10) { scene.render(it * 16_666_667L) }
        var previous: Int? = null
        var same = 0; var longestStill = 0; var run = 0
        val frames = 300
        for (i in 0 until frames) {
            t = 2f + i / 60f
            val hash = scene.render((i + 10) * 16_666_667L).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes.contentHashCode()
            if (hash == previous) { same++; run++; if (run > longestStill) longestStill = run } else run = 0
            previous = hash
        }
        scene.close()
        println("stutter %-28s %3d of %d frames identical to the one before, longest still run %d frames".format(name, same, frames, longestStill))
    }
}
