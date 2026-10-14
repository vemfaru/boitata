package br.com.faru.boitata.playground

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import br.com.faru.boitata.core.sky.SkyModel
import br.com.faru.boitata.core.sky.SkyWeather
import br.com.faru.boitata.core.sky.StormModel
import br.com.faru.boitata.weather.Fog
import br.com.faru.boitata.weather.Rain
import br.com.faru.boitata.weather.SkyWindow
import br.com.faru.boitata.weather.Snow
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

/**
 * The sky over the forest, for an [hour] and a [kind] of weather: the sky model, the four carved layers far to near moving at their own
 * speeds, the carved clouds, rain, snow, mist and lightning.
 */
@Composable
fun WeatherScene(
    seconds: () -> Float, hour: Float, kind: WeatherKind, modifier: Modifier = Modifier.fillMaxSize(),
    /** The storm whose lightning is drawn; share it with `BoitataAudio` so the thunder follows the same flashes. */
    stormModel: StormModel = remember { StormModel(seed = 17L, intensity = 1f) },
) {
    val art = LocalSprites.current ?: return
    // The art has its horizon at 60% of its height; the view is told to put the horizon there.
    val layers = remember(art) { listOf(ViewLayer(art.far, 0.2f), ViewLayer(art.mid, 0.5f), ViewLayer(art.ground, 0.7f), ViewLayer(art.near, 0.95f)) }
    val view = remember(stormModel) { StormView(flash = { stormModel.flash(seconds()) }, bolt = { stormModel.bolt(seconds()) }) }
    val weather = kind.skyWeather()
    val skyArt = remember(art) { art.skyArt() }
    // The sky only changes when the hour or the weather does: work it out once, not on every frame (and once per layer).
    val skyState = remember(hour, weather) { SkyModel.at(hour, weather) }
    Box(modifier) {
        SkyWindow(
            sky = { skyState }, layers = layers, parallax = { Offset(kotlin.math.sin(seconds() * 0.4f) * 0.25f, 0f) },
            seconds = seconds, clouds = art.clouds, storm = if (kind == WeatherKind.Storm) view else null, horizon = ART_HORIZON, art = skyArt,
        )
        when (kind) {
            WeatherKind.Rain -> Rain(intensity = 0.7f, seconds = seconds)
            WeatherKind.Storm -> Rain(intensity = 1f, wind = 0.6f, seconds = seconds)
            WeatherKind.Snow -> Snow(intensity = 0.8f, seconds = seconds)
            WeatherKind.Fog -> Fog(density = 0.9f, seconds = seconds)
            else -> {}
        }
    }
}
