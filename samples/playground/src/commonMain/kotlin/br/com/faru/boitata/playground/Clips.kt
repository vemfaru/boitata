package br.com.faru.boitata.playground

import br.com.faru.boitata.core.surface.PaneWeather
import br.com.faru.boitata.core.surface.SurfaceClimate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import br.com.faru.boitata.core.sky.MoonPhase
import br.com.faru.boitata.core.wind.Wind
import kotlin.math.cos
import kotlin.math.sin

/**
 * The short loops the README's GIFs are made from. Each is a scene with its controls moved by the clock instead of by hand, so a clip is
 * the same on every machine: the desktop `renderClips` task draws it frame by frame at a fixed rate.
 */
/** [stateful] clips (a fire that spreads) depend on what came before, so a preview has to play them from the start. */
class Clip(val name: String, val length: Float, val wind: Wind = Wind.Calm, val stateful: Boolean = false, val content: @Composable (seconds: () -> Float) -> Unit)

val Clips: List<Clip> = listOf(
    Clip("boitata", 12f, Wind.Breeze, stateful = true) { t -> ForestScene(t, rain = 0f, fog = 0.4f, timeScale = 3f) },
    Clip("boitata-rain", 8f, Wind.Windy, stateful = true) { t -> ForestScene(t, rain = 0.8f, fog = 0.5f) },
    Clip("boitata-wind", 8f, Wind.Gale, stateful = true) { t -> ForestScene(t, rain = 0f, fog = 0.2f) },
    Clip("sky-day", 8f, Wind.Windy) { t -> WeatherScene(t, hour = 5f + 15f * (t() / 8f).coerceIn(0f, 1f), kind = WeatherKind.Clear) },
    Clip("weather-rain", 6f, Wind.Windy) { t -> WeatherScene(t, hour = 15f, kind = WeatherKind.Rain) },
    Clip("weather-storm", 8f, Wind.Gale) { t -> WeatherScene(t, hour = 21.5f, kind = WeatherKind.Storm) },
    Clip("weather-snow", 6f, Wind.Breeze) { t -> WeatherScene(t, hour = 11f, kind = WeatherKind.Snow) },
    Clip("window-light", 8f, Wind.Breeze) { t -> WindowScene(t, hour = 7f + 11f * (t() / 8f).coerceIn(0f, 1f), cloud = 0f) },
    Clip("shadows", 8f, Wind.Breeze) { t -> ShadowScene({ Offset(0.5f + 0.38f * cos(t() * 0.8f), 0.45f + 0.25f * sin(t() * 0.8f)) }, 0.8f, seconds = t) },
    Clip("window-dirty", 8f, Wind.Breeze) { t -> WindowScene(t, hour = 7f + 11f * (t() / 8f).coerceIn(0f, 1f), cloud = 0f, glass = WindowGlass.Dirty.optics) },
    Clip("window-frost", 14f, Wind.Calm, stateful = true) { t ->
        val pane = remember { PaneWeather() }; val climate = remember { SurfaceClimate().also { WindowWeather.Frost.applyTo(it) } }
        WindowScene(t, hour = 8f, cloud = 0.3f, windowOpen = false, pane = pane, climate = climate, weatherSpeed = 4f)
    },
    Clip("window-rain", 14f, Wind.Windy, stateful = true) { t ->
        val pane = remember { PaneWeather() }; val climate = remember { SurfaceClimate().also { WindowWeather.Fog.applyTo(it); it.rain = 0.9f; it.wind = 0.4f } }
        WindowScene(t, hour = 15f, cloud = 0.8f, windowOpen = false, pane = pane, climate = climate, weatherSpeed = 3f)
    },
    Clip("moon-phases", 8f) { t -> WeatherScene(t, hour = 23f, kind = WeatherKind.Clear, moon = MoonPhase.All[t().toInt().coerceIn(0, 7)]) },
    Clip("burn", 10f, Wind(speed = 0.55f, direction = 1f, gustiness = 0.4f), stateful = true) { t -> BurnScene(t, timeScale = 3f) },
    Clip("lights", 10f, Wind.Breeze, stateful = true) { t -> LightsScene(t, hour = 15f + 9f * (t() / 10f).coerceIn(0f, 1f)) },
    Clip("light-stage", 10f, Wind.Breeze, stateful = true) { t -> LightStageScene(t, script = ::scriptedLamps) },
    Clip("light-walls", 10f, Wind.Breeze, stateful = true) { t -> LightStageScene(t, walls = true, script = ::scriptedLamps) },
    Clip("shadow-theatre", 12f, Wind.Calm, stateful = true) { t -> ShadowTheatreScene(t, script = ::scriptedTheatre) },
    Clip("pond", 10f, Wind.Breeze, stateful = true) { t -> PondScene(t, hour = 21f) },
    Clip("liquids", 16f, Wind.Calm, stateful = true) { t -> LiquidsScene(t, remember { LiquidBench() }, script = ::scriptedBench) },
    Clip("depth", 8f, Wind.Windy, stateful = true) { t -> DepthScene(t, script = ::scriptedDrag) },
    Clip("fire-campfire", 5f, Wind.Windy) { t -> FireScene(t, 0.9f, FireKind.Campfire) },
    Clip("fire-boitata", 5f, Wind.Breeze) { t -> FireScene(t, 0.9f, FireKind.Boitata) },
)
