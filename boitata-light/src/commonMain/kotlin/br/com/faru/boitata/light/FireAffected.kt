package br.com.faru.boitata.light

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import br.com.faru.boitata.core.FastNoise
import br.com.faru.boitata.core.burn.BodyGeometry
import br.com.faru.boitata.core.burn.BurnStatus
import br.com.faru.boitata.core.burn.BurnStyle
import br.com.faru.boitata.core.burn.FireBody
import br.com.faru.boitata.core.burn.Melting
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.fx.bump
import br.com.faru.boitata.core.fx.cycle
import br.com.faru.boitata.core.wind.Wind
import br.com.faru.boitata.core.wind.WindResponse
import kotlin.math.sin

/**
 * Your own pictures for the stages of a thing's life with fire. Each draws the whole thing with its feet at (0, 0) and up negative, in the colour it is given (the same convention as
 * `CastShadow`). A stage you leave null is drawn by the library from the material's style instead, using your intact picture as the mask.
 */
public class BurnStates(
    /** While it warms toward catching, melting or glowing. */
    public val warming: (DrawScope.(Color) -> Unit)? = null,
    /** While it is alight. */
    public val burning: (DrawScope.(Color) -> Unit)? = null,
    /** When it is done: charred, ash, a puddle. */
    public val spent: (DrawScope.(Color) -> Unit)? = null,
)

/**
 * Draws a thing the way fire treats what it is made of: the whole job, for any picture.
 *
 * [silhouette] draws the intact thing with its feet at (0, 0) and up negative, in the colour it is given. From that one drawing the library works out the rest, by the material's [BurnStyle]:
 * wood **chars** and glows with ember cracks; paper is **eaten away** by a burning edge and its ash flies off on the wind; a plant **shrivels** and crumbles to a stub; wax **sags**, drips and
 * settles into a puddle; cloth **smoulders** along a creeping edge and leaves grey ash; metal **glows** and cools back. Give [states] to replace any of those stages with your own art.
 *
 * [whole] is the intact thing's box as drawn now. [parts] are the other bodies of a thing made of several (a candle's wick): they get their own flames and wear, and sit on the sagging whole.
 * Flames and smoke are drawn for [body] and every part.
 */
public fun DrawScope.drawFireAffected(
    body: FireBody,
    whole: BodyGeometry,
    seconds: Float,
    wind: Wind,
    base: Color,
    quality: Quality = Quality.High,
    states: BurnStates? = null,
    parts: List<FireBody> = emptyList(),
    silhouette: DrawScope.(Color) -> Unit,
) {
    val colour = body.tint(base)
    val place = (whole.feetX / size.width).coerceIn(0f, 1f)
    val provided = when (body.status) {
        BurnStatus.Spent -> states?.spent
        BurnStatus.Burning -> states?.burning
        BurnStatus.Heating -> states?.warming
        BurnStatus.Cold -> null
    }
    val noise = FastNoise(body.id.hashCode().toLong())
    withTransform({ translate(whole.feetX, whole.feetY) }) {
        if (provided != null) provided(colour)
        else when (body.material.style) {
            BurnStyle.Char -> drawChar(body, whole, seconds, colour, quality, noise, silhouette)
            BurnStyle.Glow -> drawGlowing(body, whole, colour, silhouette)
            BurnStyle.Melt -> drawMelting(body, whole, seconds, base, colour, quality, noise, silhouette)
            BurnStyle.AshAway -> drawEaten(body, whole, seconds, wind, place, colour, 1f, 0f, quality, noise, silhouette)
            BurnStyle.Shrivel -> drawEaten(body, whole, seconds, wind, place, colour, 0.85f, 0.3f, quality, noise, silhouette)
            BurnStyle.Smoulder -> drawEaten(body, whole, seconds, wind, place, colour, 1f, 0f, quality, noise, silhouette, smoulder = true)
            BurnStyle.None -> silhouette(colour)
        }
    }
    // The other parts of a thing (a wick) wear down and show a little of their own colour on top of the whole.
    for (part in parts) {
        val g = part.geometry()
        if (part.material.style == BurnStyle.Char && part.status != BurnStatus.Cold) {
            val worn = 1f - 0.7f * part.consumed
            drawRect(part.tint(Color(0xFF3A2D24)), Offset(g.feetX - g.width / 2f, g.feetY - g.height * worn), androidx.compose.ui.geometry.Size(g.width, g.height * worn))
        }
        drawBurningBody(part, g, seconds, wind, quality)
    }
    drawBurningBody(body, body.geometry(), seconds, wind, quality)
}

/** Draws [content] into its own layer with the origin at the feet, so what is drawn with [BlendMode.Clear] or [BlendMode.SrcAtop] affects only the thing and not what is behind it. */
private fun DrawScope.inLayer(whole: BodyGeometry, content: DrawScope.() -> Unit) {
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(-whole.width * 1.5f, -whole.height * 1.8f, whole.width * 1.5f, whole.height * 0.4f), Paint())
        content()
        canvas.restore()
    }
}

/** Wood and its kin: the picture, charred, with glowing ember cracks while it is hot. */
private fun DrawScope.drawChar(body: FireBody, whole: BodyGeometry, seconds: Float, colour: Color, quality: Quality, noise: FastNoise, silhouette: DrawScope.(Color) -> Unit) {
    val glowing = (body.burn * (1f - body.consumed * 0.5f)).coerceAtLeast(body.nearIgnition * 0.4f)
    if (glowing < 0.05f) { silhouette(colour); return }
    inLayer(whole) {
        silhouette(colour)
        // Ember specks only where the thing is (SrcAtop draws over what is already there): the cracks of burning wood.
        val n = quality.scaled(((whole.width * whole.height) / 260f).toInt().coerceIn(10, 90))
        for (i in 0 until n) {
            val x = (noise.at(i, 0) - 0.5f) * whole.width
            val y = -noise.at(i, 1) * whole.height
            val flicker = 0.5f + 0.5f * sin(seconds * (4f + 6f * noise.at(i, 2)) + i)
            drawRect(Color(0xFFFF8A2E).copy(alpha = (glowing * (0.35f + 0.65f * flicker)).coerceIn(0f, 1f)), Offset(x, y), androidx.compose.ui.geometry.Size(2f + 3f * noise.at(i, 3), 1.2f), blendMode = BlendMode.SrcAtop)
        }
    }
}

/** Metal: the picture in its hot colour, with a halo while it glows. */
private fun DrawScope.drawGlowing(body: FireBody, whole: BodyGeometry, colour: Color, silhouette: DrawScope.(Color) -> Unit) {
    val g = body.glow
    if (g > 0.05f) drawCircle(Brush.radialGradient(0f to Color(0xFFFF7A2E).copy(alpha = 0.45f * g), 1f to Color.Transparent, center = Offset(0f, -whole.height * 0.5f), radius = whole.height * 0.9f), radius = whole.height * 0.9f, center = Offset(0f, -whole.height * 0.5f), blendMode = BlendMode.Plus)
    silhouette(colour)
}

/** Wax and plastic: it sags and spreads as it melts, drips while it does, and ends as a puddle that stays. */
private fun DrawScope.drawMelting(body: FireBody, whole: BodyGeometry, seconds: Float, base: Color, colour: Color, quality: Quality, noise: FastNoise, silhouette: DrawScope.(Color) -> Unit) {
    val m = body.melt
    if (m > 0.02f) {
        // The puddle comes first, under the thing: it widens with the melting and stays after.
        val w = whole.width * (1f + Melting.SPREAD * m) * (0.85f + 0.5f * m)
        drawOval(lerp(base, Color(0xFF14110F), 0.12f).copy(alpha = 0.9f * m.coerceAtMost(1f)), Offset(-w / 2f, -whole.height * 0.05f * m - 1f), androidx.compose.ui.geometry.Size(w, whole.height * 0.07f * m + 2f))
        // Drips run down its sides while it is melting.
        if (m < 0.97f && body.heat > body.material.meltHeat) {
            val high = whole.height * (1f - Melting.SAG * m)
            for (i in 0 until quality.scaled(3)) {
                val life = cycle(seconds, 1.8f + 1.2f * noise.at(i, 4), noise.at(i, 5))
                val x = (if (i % 2 == 0) -1f else 1f) * whole.width * (0.28f + 0.12f * noise.at(i, 6)) * (1f + Melting.SPREAD * m)
                drawCircle(base.copy(alpha = bump(life) * 0.9f), 1.6f + whole.width * 0.025f, Offset(x, -high * (1f - life) * 0.8f))
            }
        }
    }
    withTransform({ scale(1f + Melting.SPREAD * m, 1f - Melting.SAG * m, Offset.Zero) }) { silhouette(colour) }
}

/**
 * Paper, grass, plants and cloth: a burning edge climbs from the base and eats the thing away, ragged, glowing along the front, and the ash flies off. [leave] is how much of it can be eaten
 * (a plant leaves a charred stub), [curl] how much it shrinks as it goes, and [smoulder] makes the edge a slow glow instead of a flame.
 */
private fun DrawScope.drawEaten(
    body: FireBody, whole: BodyGeometry, seconds: Float, wind: Wind, place: Float, colour: Color, leave: Float, curl: Float,
    quality: Quality, noise: FastNoise, silhouette: DrawScope.(Color) -> Unit, smoulder: Boolean = false,
) {
    val eaten = (body.consumed * leave).coerceIn(0f, 1f)
    val shrink = 1f - curl * body.consumed
    if (eaten >= 0.999f) {
        // Nothing left but, for cloth, a heap of grey ash.
        if (smoulder) drawOval(Color(0xFF6F737A).copy(alpha = 0.55f), Offset(-whole.width * 0.35f, -whole.height * 0.05f), androidx.compose.ui.geometry.Size(whole.width * 0.7f, whole.height * 0.09f))
        return
    }
    val h = whole.height * shrink
    val front = -h * eaten                                            // the edge's height, going up as it eats
    inLayer(whole) {
        withTransform({ scale(1f + curl * 0.3f * body.consumed, shrink, Offset.Zero) }) { silhouette(colour) }
        if (eaten > 0.003f) {
            // The ragged edge: a profile along the width, everything below it cleared.
            val n = 14
            val edge = Path().apply {
                moveTo(-whole.width, 0.5f * h)
                for (i in 0..n) {
                    val x = -whole.width * 0.7f + whole.width * 1.4f * i / n
                    lineTo(x, front - h * 0.06f * noise.at(i, 7) * (if (i % 2 == 0) 1f else 0.4f))
                }
                lineTo(whole.width, 0.5f * h)
                close()
            }
            drawPath(edge, Color.Black, blendMode = BlendMode.Clear)
            // The glowing edge, on the part that is left.
            val glow = (body.burn * 0.9f + 0.1f).coerceIn(0f, 1f)
            val edgeLine = Path().apply {
                for (i in 0..n) {
                    val x = -whole.width * 0.7f + whole.width * 1.4f * i / n
                    val y = front - h * 0.06f * noise.at(i, 7) * (if (i % 2 == 0) 1f else 0.4f) - 1f
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            drawPath(edgeLine, Color(0xFFFF7A2E).copy(alpha = glow), style = Stroke(if (smoulder) 4f else 2.5f), blendMode = BlendMode.SrcAtop)
        }
    }
    if (body.burn > 0.1f || body.smoulder > 0.2f) drawAsh(body, whole, front, seconds, wind, place, quality, noise)
}

/** Flakes of ash lifting off the burning edge, rising a little on the hot air and then carried off by the wind: very light, so they go a long way. */
private fun DrawScope.drawAsh(body: FireBody, whole: BodyGeometry, frontY: Float, seconds: Float, wind: Wind, place: Float, quality: Quality, noise: FastNoise) {
    val amount = (body.burn + body.smoulder * 0.5f).coerceAtMost(1f)
    val drift = WindResponse.Ash.drift(wind, seconds, place).coerceIn(-12f, 12f)
    for (i in 0 until quality.scaled(18)) {
        val life = cycle(seconds, 2.4f + 2.2f * noise.at(i, 8), noise.at(i, 9))
        val x = (noise.at(i, 10) - 0.5f) * whole.width * 0.9f + drift * life * life * whole.height * 1.1f + sin(seconds * 2f + i) * 2f
        val y = frontY - life * whole.height * (0.5f + 0.4f * noise.at(i, 11)) + life * life * whole.height * 0.3f
        val size = 1.5f + 2.5f * noise.at(i, 12)
        drawRect(Color(0xFFA7ABB2).copy(alpha = bump(life) * 0.85f * amount), Offset(x, y), androidx.compose.ui.geometry.Size(size, size * 0.7f))
    }
}
