package br.com.faru.boitata.light

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.light.ShadowMath
import kotlin.math.cos
import kotlin.math.sin

/**
 * Every shadow a thing throws in a [LightRig], each from its own light in its own direction, as dark as that light's share of all the light there. [silhouette] draws the thing standing with its feet
 * at (0, 0) and up negative, in the colour it is given (as for [drawCastShadow]). [height] is how tall it is drawn, in pixels, which sets how long the shadows are.
 *
 * Quality caps how many lights cast (High 4, Medium 2, Low 1: the strongest first) and how many passes soften each edge.
 */
public fun DrawScope.drawCastShadows(
    rig: LightRig, feetX: Float, feetY: Float, height: Float, seconds: Float, maxAlpha: Float = 0.6f,
    quality: Quality = Quality.High, into: ShadowCasts = DefaultCasts, silhouette: DrawScope.(Color) -> Unit,
) {
    val n = rig.shadows(feetX, feetY, height, seconds, maxAlpha, when (quality) { Quality.High -> 4; Quality.Medium -> 2; Quality.Low -> 1 }, into)
    for (i in 0 until n) {
        val cast = into.casts[i]
        if (cast.alpha <= 0.01f) continue
        // The thing flipped onto the ground and sheared along the light: x gets lean * height, y gets lean * height down the screen. Softened with a few passes, each a little longer and fainter.
        for (pass in 0..(if (quality == Quality.Low) 0 else 2)) {
            val soften = 1f + pass * (0.04f + cast.spread)
            val m = Matrix().apply {
                this[0, 0] = 1f
                this[1, 0] = -cast.leanX * soften
                this[1, 1] = -(cast.leanY * soften)
                this[3, 0] = feetX
                this[3, 1] = feetY
            }
            withTransform({ transform(m) }) { silhouette(Color.Black.copy(alpha = cast.alpha * (if (pass == 0) 0.8f else 0.4f))) }
        }
    }
}

private val DefaultCasts = ShadowCasts()

/**
 * The glow of the lights on the ground and in the air: a pool of light on the ground under each point light (flattened, because the ground is seen at a slant), a cone for each spot, and
 * a bright bulb where each source is. Draw it over the scene (it adds light, [BlendMode.Plus]). Directional lights (sun and moon) have no pool: they are the sky's.
 */
public fun DrawScope.drawLightPools(rig: LightRig, seconds: Float, bulbs: Boolean = true) {
    drawLights(rig, seconds, pools = true, bulbs = bulbs)
}

/** Just the bright bulbs where the lights are, to draw over the things standing in the scene after the pools were drawn under them. */
public fun DrawScope.drawLightBulbs(rig: LightRig, seconds: Float) {
    drawLights(rig, seconds, pools = false, bulbs = true)
}

private fun DrawScope.drawLights(rig: LightRig, seconds: Float, pools: Boolean, bulbs: Boolean) {
    for (l in rig.lights) {
        if (!l.enabled || l.strength <= 0.01f) continue
        val flick = l.flickerAt(seconds)
        when (l) {
            is PointLight -> {
                if (pools) pool(l.x, l.y, l.reach, l.color, l.strength * flick * 0.42f)
                if (bulbs) bulb(l.x, l.y - l.height, l.color, l.strength * flick, l.height * 0.5f)
            }
            is SpotLight -> {
                if (pools) {
                    // The beam is a few wedges, each narrower and adding a little, so its edge is soft like a real penumbra and not a polygon.
                    val path = Path()
                    for (layer in 0 until BEAM_LAYERS) {
                        val half = l.cone * (1f - l.penumbra * layer / (BEAM_LAYERS - 1))
                        path.rewind()
                        path.moveTo(l.x, l.y)
                        for (i in 0..8) {
                            val a = l.aim - half + 2f * half * i / 8
                            path.lineTo(l.x + cos(a) * l.reach, l.y + sin(a) * l.reach * ShadowMath.GROUND_SQUASH)
                        }
                        path.close()
                        clipPath(path) { pool(l.x, l.y, l.reach, l.color, l.strength * flick * 0.5f / BEAM_LAYERS * 1.6f) }
                    }
                }
                if (bulbs) bulb(l.x, l.y - l.height, l.color, l.strength * flick, l.height * 0.5f)
            }
            is DirectionalLight -> Unit
        }
    }
}

private const val BEAM_LAYERS = 5

private fun DrawScope.pool(x: Float, y: Float, reach: Float, color: Color, alpha: Float) {
    withTransform({ scale(1f, ShadowMath.GROUND_SQUASH, Offset(x, y)) }) {
        drawCircle(Brush.radialGradient(0f to color.copy(alpha = alpha.coerceIn(0f, 1f)), 0.55f to color.copy(alpha = alpha * 0.35f), 1f to Color.Transparent, center = Offset(x, y), radius = reach), radius = reach, center = Offset(x, y), blendMode = BlendMode.Plus)
    }
}

private fun DrawScope.bulb(x: Float, y: Float, color: Color, strength: Float, radius: Float) {
    val r = radius.coerceIn(6f, 34f)
    drawCircle(Brush.radialGradient(0f to Color(0xFFFFF4D6).copy(alpha = strength.coerceIn(0f, 1f)), 0.25f to color.copy(alpha = 0.5f * strength), 1f to Color.Transparent, center = Offset(x, y), radius = r), radius = r, center = Offset(x, y), blendMode = BlendMode.Plus)
}
