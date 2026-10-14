package br.com.faru.boitata.light

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import br.com.faru.boitata.core.FastNoise
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.fx.bump
import br.com.faru.boitata.core.fx.cycle
import br.com.faru.boitata.core.light.Liquid
import kotlin.math.sin

private fun Liquid.shape(): Path = Path().apply { addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry)) }

/**
 * The water itself: the pond filled with a gradient from the colour of the sky it mirrors ([skyNear], at the far shore, to [skyFar], toward the viewer) into its own depth colour, with carved ripple lines drifting across it.
 * Draw it on the ground before anything that stands in or beside it.
 */
public fun DrawScope.drawWaterSurface(liquid: Liquid, skyNear: Color, skyFar: Color, seconds: Float, quality: Quality = Quality.High) {
    val oval = liquid.shape()
    val top = liquid.cy - liquid.ry; val bottom = liquid.cy + liquid.ry
    drawPath(oval, Brush.verticalGradient(0f to lerp(liquid.color, skyNear, 0.55f), 0.6f to lerp(liquid.color, skyFar, 0.25f), 1f to liquid.color, startY = top, endY = bottom))
    clipPath(oval) {
        val noise = FastNoise(5L)
        val n = when (quality) { Quality.High -> 46; Quality.Medium -> 26; Quality.Low -> 10 }
        for (i in 0 until n) {
            val u = noise.at(i, 0); val v = noise.at(i, 1)
            // Each ripple line drifts across and fades in and out in its own time.
            val life = cycle(seconds, 5f + 5f * noise.at(i, 2), noise.at(i, 3))
            val x = liquid.cx - liquid.rx + 2f * liquid.rx * ((u + life * 0.08f) % 1f)
            val y = top + (bottom - top) * v
            val len = liquid.rx * (0.05f + 0.09f * noise.at(i, 4))
            drawLine(Color(0xFFCFE6E4).copy(alpha = 0.22f * bump(life)), Offset(x, y), Offset(x + len, y), 1.6f)
        }
    }
    drawPath(oval, Color(0xFF0A1416).copy(alpha = 0.55f), style = Stroke(width = 2.5f))
}

/**
 * The reflection of a standing thing in the water at its feet: its picture mirrored about the waterline ([waterLine] pixels above its feet, 0 for a thing on the shore), clipped to the pond, fading with
 * how far down it goes, and shaken sideways by ripples (strips of it, each shifted by a slow wave, more as [Liquid]'s optics `distortion` says). It is as strong as the liquid's reflectance (Fresnel, see `LiquidMath`).
 * [silhouette] draws the thing with its feet at (0, 0), up negative, in the colour given. Draw it after [drawWaterSurface] and under the things.
 */
public fun DrawScope.drawReflection(liquid: Liquid, feetX: Float, feetY: Float, height: Float, waterLine: Float, seconds: Float, quality: Quality, colour: Color, silhouette: DrawScope.(Color) -> Unit) {
    val y0 = feetY - waterLine
    val length = height - waterLine
    if (length <= 1f || y0 > liquid.cy + liquid.ry) return
    val strips = when (quality) { Quality.High -> 14; Quality.Medium -> 7; Quality.Low -> 1 }
    val band = length / strips
    val wobble = liquid.optics.distortion * height * 0.03f
    clipPath(liquid.shape()) {
        for (i in 0 until strips) {
            val alpha = liquid.reflectance * (1f - 0.8f * i / strips)
            val shift = if (strips == 1) 0f else wobble * sin(i * 1.1f + seconds * 1.7f + feetX * 0.05f)
            clipRect(feetX - height, y0 + i * band, feetX + height, y0 + (i + 1) * band + 1f) {
                withTransform({ translate(feetX + shift, y0 - waterLine); scale(1f, -1f, Offset.Zero) }) { silhouette(colour.copy(alpha = alpha)) }
            }
        }
    }
}

/**
 * A thing standing in water, drawn as it looks: the part above the surface as it is, the part under it **refracted**: it looks nearer the surface than it is by the water's index (a leg 1 under looks 0.75 under), so it is
 * squashed toward the waterline, shaken by the ripples, and coloured and dimmed by the water it is seen through (the deeper, the more). [depth] is how far the surface is above the thing's feet, in pixels.
 * A ring ripples out from where it meets the water. [silhouette] draws the thing with its feet at (0, 0), up negative, in the colour given.
 */
public fun DrawScope.drawStandingInWater(liquid: Liquid, feetX: Float, feetY: Float, height: Float, depth: Float, seconds: Float, quality: Quality, colour: Color, silhouette: DrawScope.(Color) -> Unit) {
    val y0 = feetY - depth
    clipRect(feetX - height * 2f, feetY - height * 2f, feetX + height * 2f, y0) { withTransform({ translate(feetX, feetY) }) { silhouette(colour) } }
    // Under the surface: the picture squashed to 1/n about the waterline, in strips shaken sideways, tinted by the water.
    val squash = 1f / liquid.index
    val under = lerp(colour, liquid.color, 0.5f).copy(alpha = 0.78f)
    val strips = when (quality) { Quality.High -> 10; Quality.Medium -> 5; Quality.Low -> 1 }
    val apparentFeet = y0 + depth * squash
    val band = (apparentFeet - y0) / strips
    val wobble = liquid.optics.distortion * height * 0.025f
    for (i in 0 until strips) {
        val shift = if (strips == 1) 0f else wobble * sin(i * 1.3f + seconds * 2f + feetX * 0.04f)
        clipRect(feetX - height * 2f, y0 + i * band, feetX + height * 2f, y0 + (i + 1) * band + 1f) {
            withTransform({ translate(feetX + shift, apparentFeet); scale(1f, squash, Offset.Zero) }) { silhouette(under) }
        }
    }
    // The ripples where it meets the water.
    val ring = height * 0.16f
    for (k in 0..1) {
        val u = cycle(seconds, 1.8f, k * 0.5f + feetX * 0.001f)
        drawOval(Color(0xFFDDF0EE).copy(alpha = 0.45f * bump(u)), Offset(feetX - ring * (1f + 2f * u), y0 - ring * 0.25f * (1f + 2f * u)), androidx.compose.ui.geometry.Size(2f * ring * (1f + 2f * u), ring * 0.5f * (1f + 2f * u)), style = Stroke(1.5f))
    }
}

/**
 * The glitter of a light on the water: a column of short bright dashes under the light, from its foot toward where its mirror image is ([lightHeight] below the water), wider and brighter nearer, flickering and breaking up
 * with the ripples. Only the part over the pond shows. Draw it over the water and the reflections.
 */
public fun DrawScope.drawGlitter(liquid: Liquid, lightX: Float, lightGroundY: Float, lightHeight: Float, color: Color, strength: Float, seconds: Float, quality: Quality) {
    if (strength <= 0.01f) return
    val noise = FastNoise(9L)
    val n = when (quality) { Quality.High -> 22; Quality.Medium -> 12; Quality.Low -> 5 }
    clipPath(liquid.shape()) {
        for (i in 0 until n) {
            val v = (i + 0.5f) / n
            val y = lightGroundY + lightHeight * 1.3f * v
            val life = cycle(seconds, 0.9f + 1.2f * noise.at(i, 0), noise.at(i, 1))
            val width = lightHeight * (0.5f - 0.35f * v) * (0.5f + 0.7f * noise.at(i, 2))
            val x = lightX + (noise.at(i, 3) - 0.5f) * lightHeight * 0.35f * (0.4f + v) + sin(seconds * 2f + i) * 3f
            drawLine(color.copy(alpha = (strength * (1f - 0.75f * v) * bump(life)).coerceIn(0f, 1f)), Offset(x - width / 2f, y), Offset(x + width / 2f, y), 2.2f, blendMode = BlendMode.Plus)
        }
    }
}

/** Runs [block] with drawing cut to the surface of [liquid] (what is mirrored in a pond shows only over the water). */
public fun DrawScope.clipToLiquid(liquid: Liquid, block: DrawScope.() -> Unit) { clipPath(liquid.shape(), block = block) }
