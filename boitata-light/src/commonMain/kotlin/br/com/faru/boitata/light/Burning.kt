package br.com.faru.boitata.light

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import br.com.faru.boitata.core.AdvancedBoitataApi
import br.com.faru.boitata.core.FastNoise
import br.com.faru.boitata.core.burn.BodyGeometry
import br.com.faru.boitata.core.burn.BurnStatus
import br.com.faru.boitata.core.burn.FireBody
import br.com.faru.boitata.core.burn.FireKind
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.fx.bump
import br.com.faru.boitata.core.fx.cycle
import br.com.faru.boitata.core.wind.Wind
import br.com.faru.boitata.core.wind.WindResponse
import kotlin.math.roundToInt

/**
 * What fire has done to a thing's colour: [base] when whole, warming toward an ember glow as it nears catching and while it burns, then charred to near black as it is consumed.
 * Draw the thing's picture with this colour (tint it) and then call [drawBurningBody] for the flames and smoke.
 */
public fun FireBody.tint(base: Color): Color {
    val glow = (nearIgnition * 0.3f + burn * 0.4f) * (1f - consumed)
    val warmed = lerp(base, Color(0xFFC2410C), glow.coerceIn(0f, 0.6f))
    return lerp(warmed, CHAR, (consumed * 0.94f).coerceIn(0f, 0.94f))
}

private val CHAR = Color(0xFF0A0807)

/**
 * The flames and smoke of one thing on fire: flames along it, as many and as big as its fuel and how alight it is, leaning and throwing embers with the wind; a glow; and smoke that rises
 * while it heats, burns and smoulders after, blown downwind. Draw it over the thing's picture. [geometry] is where the thing is drawn now.
 */
@AdvancedBoitataApi
public fun DrawScope.drawBurningBody(body: FireBody, geometry: BodyGeometry, seconds: Float, wind: Wind, quality: Quality = Quality.High) {
    val h = geometry.height; val w = geometry.width
    val place = (geometry.feetX / size.width).coerceIn(0f, 1f)
    val noise = FastNoise(body.id.hashCode().toLong())
    val flame = body.flame
    if (flame > 0.02f) {
        val palette = if (body.kind == FireKind.Guardian) FlamePalette.Boitata else FlamePalette.Campfire
        val count = (w / (h * 0.5f)).roundToInt().coerceIn(1, if (quality == Quality.Low) 2 else 4)
        val lean = WindResponse.Flame.bend(wind, seconds, place, phase = noise.at(0, 0)) * 0.45f
        val drift = WindResponse.Ember.drift(wind, seconds, place).coerceIn(-3f, 3f)
        for (k in 0 until count) {
            val x = geometry.feetX - w / 2f + w * (k + 0.5f) / count
            val base = geometry.feetY - h * (0.3f + 0.35f * noise.at(k, 1))
            drawFlame(x, base, w / count * 1.5f, h * 0.7f * flame.coerceAtMost(1f), seconds + k * 0.61f, flame.coerceAtMost(1f), palette, body.id.hashCode().toLong() + k, quality, lean, drift)
        }
        if (quality.softGlow) {
            val c = Offset(geometry.feetX, geometry.feetY - h * 0.5f)
            drawCircle(Brush.radialGradient(0f to palette.mid.copy(alpha = 0.3f * flame), 1f to Color.Transparent, center = c, radius = h * 1.1f), radius = h * 1.1f, center = c, blendMode = BlendMode.Plus)
        }
    }
    // Smoke: a faint wisp while it warms toward catching, thick while it burns, and a long slow trail of it from the ash.
    val density = maxOf(if (body.nearIgnition > 0.4f && body.status != BurnStatus.Burning) (body.nearIgnition - 0.4f) * 0.8f else 0f, body.burn * 0.6f, body.smoulder * 0.5f)
    if (density > 0.03f) drawSmoke(geometry, seconds, density, WindResponse.Dust.drift(wind, seconds, place).coerceIn(-3f, 3f), quality)
}

/** Smoke rising from the top of a thing, [density] 0..1, blown by [drift] (signed): soft grey puffs that swell and fade as they climb. */
@AdvancedBoitataApi
public fun DrawScope.drawSmoke(geometry: BodyGeometry, seconds: Float, density: Float, drift: Float, quality: Quality = Quality.High) {
    val noise = FastNoise(geometry.feetX.toBits().toLong())
    val h = geometry.height
    val puffs = quality.scaled(8)
    for (i in 0 until puffs) {
        val life = cycle(seconds, 3.2f + 1.6f * noise.at(i, 0), noise.at(i, 1))
        val rise = life * h * 1.6f
        val x = geometry.feetX + (noise.at(i, 2) - 0.5f) * geometry.width * 0.6f + drift * life * life * h * 2.4f
        val y = geometry.feetY - h * 0.7f - rise
        val r = h * (0.08f + 0.22f * life)
        drawCircle(Color(0xFF3A4048).copy(alpha = bump(life) * 0.4f * density.coerceAtMost(1f)), radius = r, center = Offset(x, y))
    }
}
