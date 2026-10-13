package br.com.faru.boitata.playground

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import br.com.faru.boitata.core.FastNoise
import br.com.faru.boitata.core.sky.SkyModel
import br.com.faru.boitata.core.sky.SkyWeather
import br.com.faru.boitata.core.sky.StormModel
import br.com.faru.boitata.weather.Fog
import br.com.faru.boitata.weather.Rain
import br.com.faru.boitata.weather.Snow
import br.com.faru.boitata.weather.SkyWindow
import br.com.faru.boitata.weather.StormView
import br.com.faru.boitata.weather.ViewLayer

enum class WeatherKind(val label: String) { Clear("Clear"), Cloudy("Cloudy"), Rain("Rain"), Snow("Snow"), Fog("Fog"), Storm("Storm") }

fun WeatherKind.skyWeather(): SkyWeather = when (this) {
    WeatherKind.Clear -> SkyWeather.Clear
    WeatherKind.Cloudy -> SkyWeather(cloud = 0.8f)
    WeatherKind.Rain -> SkyWeather(cloud = 0.85f, precipitation = 0.7f)
    WeatherKind.Snow -> SkyWeather(cloud = 0.8f, precipitation = 0.5f)
    WeatherKind.Fog -> SkyWeather(cloud = 0.5f, fog = 0.85f)
    WeatherKind.Storm -> SkyWeather(cloud = 1f, precipitation = 0.9f, storm = 1f)
}

/** The sky over a skyline, for an [hour] and a [kind] of weather: the sky model, the parallax layers, rain, snow, mist and lightning. */
@Composable
fun WeatherScene(seconds: () -> Float, hour: Float, kind: WeatherKind, modifier: Modifier = Modifier.fillMaxSize()) {
    val layers = remember { skyline() }
    val storm = remember { StormModel(seed = 17L, intensity = 1f) }
    val view = remember(storm) { StormView(flash = { storm.flash(seconds()) }, bolt = { storm.bolt(seconds()) }) }
    val weather = kind.skyWeather()
    Box(modifier) {
        SkyWindow(
            sky = { SkyModel.at(hour, weather) }, layers = layers, parallax = { Offset(kotlin.math.sin(seconds() * 0.4f) * 0.25f, 0f) },
            seconds = seconds, storm = if (kind == WeatherKind.Storm) view else null,
        )
        // The view lifts the art so the roofs clear the furniture of a room; with nothing in front, close the gap with the street.
        Spacer(Modifier.fillMaxSize().drawBehind { drawRect(Color(0xFF0D141B), Offset(0f, size.height * 0.8f), Size(size.width, size.height * 0.2f)) })
        when (kind) {
            WeatherKind.Rain -> Rain(seconds, intensity = 0.7f)
            WeatherKind.Storm -> Rain(seconds, intensity = 1f, wind = 0.6f)
            WeatherKind.Snow -> Snow(seconds, intensity = 0.8f)
            WeatherKind.Fog -> Fog(seconds, density = 0.9f)
            else -> {}
        }
    }
}

/** Three rows of roofs, far to near, painted in code so the sample needs no image files. */
private fun skyline(): List<ViewLayer> = listOf(
    roofs(seed = 1L, base = 0.62f, height = 0.14f, color = Color(0xFF2B3A4A), depth = 0.2f),
    roofs(seed = 2L, base = 0.74f, height = 0.2f, color = Color(0xFF1A2530), depth = 0.5f),
    roofs(seed = 3L, base = 0.9f, height = 0.28f, color = Color(0xFF0D141B), depth = 0.9f),
)

private fun roofs(seed: Long, base: Float, height: Float, color: Color, depth: Float): ViewLayer {
    val w = 960; val h = 540
    val bitmap = ImageBitmap(w, h)
    val canvas = Canvas(bitmap)
    val scope = CanvasDrawScope()
    val noise = FastNoise(seed)
    scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
        var x = -10f; var i = 0
        while (x < w) {
            val bw = 40f + 70f * noise.at(i, 0)
            val bh = h * height * (0.35f + 0.65f * noise.at(i, 1))
            drawRect(color, Offset(x, h * base - bh + h * (1f - base) * 0f), Size(bw, h - (h * base - bh)))
            x += bw + 2f; i++
        }
    }
    return ViewLayer(bitmap, depth)
}
