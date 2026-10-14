package br.com.faru.boitata.playground

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

/**
 * The short loops the README's GIFs are made from. Each is a scene with its controls moved by the clock instead of by hand, so a clip is
 * the same on every machine: the desktop `renderClips` task draws it frame by frame at a fixed rate.
 */
class Clip(val name: String, val length: Float, val content: @Composable (seconds: () -> Float) -> Unit)

val Clips: List<Clip> = listOf(
    Clip("boitata", 8f) { t -> ForestScene(t, rain = 0f, fog = 0.4f) },
    Clip("boitata-rain", 8f) { t -> ForestScene(t, rain = 0.8f, fog = 0.5f) },
    Clip("sky-day", 8f) { t -> WeatherScene(t, hour = 5f + 15f * (t() / 8f).coerceIn(0f, 1f), kind = WeatherKind.Clear) },
    Clip("weather-rain", 6f) { t -> WeatherScene(t, hour = 15f, kind = WeatherKind.Rain) },
    Clip("weather-storm", 8f) { t -> WeatherScene(t, hour = 21.5f, kind = WeatherKind.Storm) },
    Clip("weather-snow", 6f) { t -> WeatherScene(t, hour = 11f, kind = WeatherKind.Snow) },
    Clip("window-light", 8f) { t -> WindowScene(t, hour = 7f + 11f * (t() / 8f).coerceIn(0f, 1f), cloud = 0f) },
    Clip("shadows", 8f) { t -> ShadowScene({ Offset(0.5f + 0.38f * cos(t() * 0.8f), 0.45f + 0.25f * sin(t() * 0.8f)) }, 0.8f) },
    Clip("depth", 8f) { t -> DepthScene(t, script = ::scriptedDrag) },
    Clip("fire-campfire", 5f) { t -> FireScene(t, 0.9f, FireKind.Campfire) },
    Clip("fire-boitata", 5f) { t -> FireScene(t, 0.9f, FireKind.Boitata) },
)
