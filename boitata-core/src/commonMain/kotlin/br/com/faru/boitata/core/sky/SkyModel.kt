package br.com.faru.boitata.core.sky

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The sky as pure arithmetic: for an hour on the clock and the weather, where the sun and the moon are, how bright the day is, how many stars
 * show, what colours the sky is, and what light comes in at a window. No drawing, no clock of its own: the same inputs give the same sky,
 * which is what lets the Free desk scrub the day with a slider and tests pin every number.
 */

/** What the weather does to the sky; each 0..1. */
data class SkyWeather(
    /** How much of the sky is cloud: 0 clear, 1 overcast. */
    val cloud: Float = 0f,
    /** Rain, snow or storm in the air: darkens and greys the sky further. */
    val precipitation: Float = 0f,
    /** Mist hiding the far view. */
    val fog: Float = 0f,
    val storm: Float = 0f,
) {
    /** How much of the sun's and the moon's light gets through. */
    val clearness: Float get() = (1f - 0.55f * cloud - 0.25f * precipitation - 0.3f * storm - 0.35f * fog).coerceIn(0.12f, 1f)

    companion object {
        val Clear = SkyWeather()
    }
}

/** A point in the window's view, 0..1 across and 0 (the top) to 1 (the bottom), and whether it is above the horizon. */
data class SkyPoint(val x: Float, val y: Float, val up: Boolean)

/** One moment of the sky. */
data class SkyState(
    val hour: Float,
    /** The sun's height above the horizon: 1 at noon, 0 on the horizon, below 0 at night. */
    val sunElevation: Float,
    val sun: SkyPoint,
    val moon: SkyPoint,
    /** The moon's lit part, 0 new to 1 full, and which side is lit (+1 right, -1 left). */
    val moonPhase: Float,
    /** How bright the day is: 0 at night, 1 in full day, ramping through dawn and dusk. */
    val daylight: Float,
    /** How much the sky is lit by twilight colour: strongest with the sun on the horizon. */
    val twilight: Float,
    /** How many stars show: 1 on a clear dark night, none by day, fewer under cloud. */
    val stars: Float,
    val zenith: Color,
    val horizon: Color,
    /** The colour of the sun's (or the moon's) light and its strength, 0..1, after the weather. */
    val lightColor: Color,
    val lightStrength: Float,
    /** Where the light comes from, as a point on the window (the sun or the moon, whichever shines). */
    val lightFrom: SkyPoint,
    /** The tint of the whole view, as a veil: the far view is bluer and lower in contrast by night. */
    val viewVeil: Color,
    /** Whether the lights in the windows across the street are on, 0..1. */
    val windowsLit: Float,
    val weather: SkyWeather,
)

object SkyModel {
    /** Sunrise and sunset on the clock, and how long the dawn and dusk take. */
    const val SUNRISE = 6.25f
    const val SUNSET = 18.25f
    private const val TWILIGHT_HOURS = 1.4f

    private val NightZenith = Color(0xFF05070D); private val NightHorizon = Color(0xFF16202E)
    private val DayZenith = Color(0xFF8798AC); private val DayHorizon = Color(0xFFC6CFD8)
    private val DawnHorizon = Color(0xFFB98B73); private val DuskHorizon = Color(0xFF9C6D58)
    private val TwilightZenith = Color(0xFF2B3548)
    private val Overcast = Color(0xFF8C939B); private val OvercastNight = Color(0xFF1B2028); private val StormGrey = Color(0xFF4A515B)
    private val SunWhite = Color(0xFFD9E2EC); private val SunLow = Color(0xFFE8B98A); private val Moon = Color(0xFFB4C4DA)

    private fun smooth(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun wrap(h: Float): Float = ((h % 24f) + 24f) % 24f

    /** The sun's height, 1 at the middle of the day. Negative at night, reaching -1 at midnight. */
    fun sunElevation(hour: Float): Float {
        val h = wrap(hour)
        val day = SUNSET - SUNRISE
        val t = (h - SUNRISE) / day
        return if (t in 0f..1f) sin(PI.toFloat() * t) else -sin(PI.toFloat() * ((h - SUNSET + 24f) % 24f) / (24f - day)).coerceIn(0f, 1f)
    }

    /** Daylight 0..1: night before sunrise less the twilight, full from an hour after, down again to dusk. */
    fun daylight(hour: Float): Float {
        val h = wrap(hour)
        return smooth(SUNRISE - TWILIGHT_HOURS * 0.5f, SUNRISE + TWILIGHT_HOURS, h) * (1f - smooth(SUNSET - TWILIGHT_HOURS, SUNSET + TWILIGHT_HOURS * 0.5f, h))
    }

    private fun arc(hour: Float, rise: Float, set: Float): SkyPoint {
        val h = wrap(hour)
        val length = ((set - rise) + 24f) % 24f
        val t = ((h - rise) + 24f) % 24f / length
        val up = t <= 1f
        val u = t.coerceIn(0f, 1f)
        // Across the window from left (east) to right (west), rising to a height of 0.55 of the view at the middle.
        val x = 0.08f + 0.84f * u
        val y = HORIZON_Y - 0.28f * sin(PI.toFloat() * u)
        return SkyPoint(x, if (up) y else HORIZON_Y + 0.25f, up)
    }

    /** The view's horizon as a fraction of its height. The art is drawn with it at 60% ([ART_HORIZON_Y]); the view is shifted up so the roofs clear the desk. */
    const val HORIZON_Y = 0.34f
    const val ART_HORIZON_Y = 0.6f

    fun at(hour: Float, weather: SkyWeather = SkyWeather.Clear, moonPhase: Float = 0.85f): SkyState {
        val h = wrap(hour)
        val daylight = daylight(h)
        val elevation = sunElevation(h)
        val twilight = (1f - abs(elevation) / 0.28f).coerceIn(0f, 1f) * (1f - smooth(0.9f, 1f, abs(elevation)))
        val clear = weather.clearness
        val stars = ((1f - daylight * 1.6f).coerceIn(0f, 1f)) * (1f - 0.9f * weather.cloud) * (1f - 0.8f * weather.fog) * (1f - 0.9f * weather.precipitation)

        // The sky's colours: night to day through the twilight, then greyed and darkened by the weather.
        val sunrise = h < 12f
        val twilightHorizon = if (sunrise) DawnHorizon else DuskHorizon
        var zenith = lerp(NightZenith, DayZenith, daylight)
        var horizon = lerp(NightHorizon, DayHorizon, daylight)
        zenith = lerp(zenith, TwilightZenith, twilight * 0.7f * (1f - daylight * 0.5f))
        horizon = lerp(horizon, twilightHorizon, twilight * 0.85f)
        val grey = lerp(OvercastNight, Overcast, daylight)
        val mix = (weather.cloud * 0.8f + weather.fog * 0.6f + weather.precipitation * 0.3f).coerceIn(0f, 1f)
        zenith = lerp(zenith, lerp(grey, StormGrey, weather.storm * 0.8f), mix)
        horizon = lerp(horizon, lerp(grey, StormGrey, weather.storm * 0.8f), mix * 0.85f)

        val sun = arc(h, SUNRISE, SUNSET)
        val moon = arc(h, SUNSET - 0.5f, SUNRISE + 0.5f)
        // Whichever shines: the sun while it is up, the moon after dusk.
        val sunLight = (daylight * 0.85f + twilight * 0.15f)
        val moonLight = (1f - daylight) * 0.32f * (0.55f + 0.45f * moonPhase) * if (moon.up) 1f else 0.15f
        val fromSun = sunLight >= moonLight
        val lightColor = if (fromSun) lerp(SunLow, SunWhite, ((elevation - 0.05f) / 0.5f).coerceIn(0f, 1f)) else Moon
        val strength = ((if (fromSun) sunLight else moonLight) * clear).coerceIn(0f, 1f)
        val veil = lerp(Color(0x33182034), Color(0x00000000), daylight).let { if (weather.fog > 0f) lerp(it, Color(0x30B8BEC6), weather.fog) else it }
        val windows = (1f - daylight * 1.3f).coerceIn(0f, 1f) * (0.55f + 0.45f * (weather.cloud + weather.precipitation).coerceAtMost(1f)).coerceAtMost(1f)

        return SkyState(
            hour = h, sunElevation = elevation, sun = sun, moon = moon, moonPhase = moonPhase,
            daylight = daylight * (0.35f + 0.65f * clear), twilight = twilight, stars = stars,
            zenith = zenith, horizon = horizon, lightColor = lightColor, lightStrength = strength,
            lightFrom = if (fromSun) sun else moon, viewVeil = veil, windowsLit = windows, weather = weather,
        )
    }
}
