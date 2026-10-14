package br.com.faru.boitata.light

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.fluid.LiquidVessel
import br.com.faru.boitata.core.fluid.Tilt
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max

/** How a container looks: a tumbler narrows toward its foot, a bottle has a neck above its body, a jug has a handle. The liquid and the physics are those of the plain box either way (the neck and handle are only drawn). */
public enum class VesselShape { Tumbler, Bottle, Jug, Mug }

/** What the container is made of, for drawing: [glass] tints the walls, [ink] outlines them (the woodcut's black line). */
public class VesselStyle(
    public val shape: VesselShape = VesselShape.Tumbler, public val glass: Color = Color(0xFFDCEBEA), public val ink: Color = Color(0xFF14110F),
    /** Drawn art in place of the plain outline, if the container has some. */
    public val art: VesselArt? = null,
)

/**
 * A container drawn by hand, in layers: the [back] (the far wall, behind the liquid), the [front] (the near wall and its carved lines, over it) and a [shine], all one picture size. [body] is the part of those pictures,
 * in their pixels, that holds the liquid: it is stretched onto the container's box (so the liquid fills it exactly) and whatever lies outside it (a bottle's neck, a handle) hangs out as drawn. An opaque container (a mug) has no back
 * or shine, and its [opening] (the rim seen from above) goes over the top of the body, the surface of what is in it showing inside. The pictures are solid with the lines cut out, and are painted in [tint].
 */
public class VesselArt(
    public val front: ImageBitmap, public val body: Rect, public val back: ImageBitmap? = null, public val shine: ImageBitmap? = null,
    public val opening: ImageBitmap? = null, public val tint: Color = Color(0xFFE9DFC6),
)

/**
 * Draws a [LiquidVessel] with its corner (the point it tips on) at ([pivotX], [pivotY]) on the floor, [pixelsPerMeter] big: the glass, the liquid at its level with the surface rippling as the simulation says, a
 * lit highlight along the walls and the opening seen from a little above. It is drawn tipped by the vessel's angle, the liquid staying level in the world as it does.
 * A hot liquid steams (wisps that rise from the surface and sway, thicker the hotter) and, near the boil, bubbles rise through it and burst; foam from a pour floats on top. A [VesselShape.Mug] is opaque
 * ceramic: only the surface of what is in it is seen, through the opening.
 *
 * What is **behind** the glass shows through the liquid **magnified**, the way a glass of water magnifies what is behind it (by about `1 + (index - 1) * 0.75` across), tinted by the liquid, which is what [behind]
 * is for: a layer the scene has recorded of everything behind the table. Where there is no liquid the glass is clear. [mirrored] draws the vessel flipped about the floor, faint, for a glossy table.
 */
public fun DrawScope.drawVessel(
    vessel: LiquidVessel, style: VesselStyle, pivotX: Float, pivotY: Float, pixelsPerMeter: Float, seconds: Float, quality: Quality = Quality.High,
    behind: GraphicsLayer? = null, mirrored: Float = 0f, mirrorOnly: Boolean = false,
) {
    if (mirrored > 0.01f) {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, pivotY, size.width, size.height), Paint().apply { alpha = mirrored })
            withTransform({ translate(0f, 2f * pivotY); scale(1f, -1f, Offset.Zero) }) { drawVessel(vessel, style, pivotX, pivotY, pixelsPerMeter, seconds, quality, behind, 0f) }
            canvas.restore()
        }
        if (mirrorOnly) return
    }
    val body = vessel.body
    val s = if (body.angle >= 0f) 1f else -1f
    val a = abs(body.angle)
    val w = vessel.width; val h = vessel.height
    val ppm = pixelsPerMeter
    fun sx(x: Float) = pivotX + s * x * ppm
    fun sy(z: Float) = pivotY - z * ppm

    // The liquid, in the world: the rectangle below its level, with the surface edge replaced by the rippling curve.
    val level = vessel.level
    val liquid = Path()
    var hasLiquid = false
    if (!vessel.empty) {
        val corners = Tilt.corners(w, h, a)
        val xs = FloatArray(10); val zs = FloatArray(10); var n = 0
        for (i in 0 until 4) {
            val ax = corners[i * 2]; val az = corners[i * 2 + 1]; val j = (i + 1) % 4; val bx = corners[j * 2]; val bz = corners[j * 2 + 1]
            val aIn = az <= level + 1e-5f; val bIn = bz <= level + 1e-5f
            if (aIn) { xs[n] = ax; zs[n] = az; n++ }
            if (aIn != bIn) { val t = (level - az) / (bz - az); xs[n] = ax + t * (bx - ax); zs[n] = level; n++ }
        }
        if (n >= 3) {
            hasLiquid = true
            // Find the surface edge: two consecutive points at the level.
            var surfaceAt = -1
            for (i in 0 until n) { val j = (i + 1) % n; if (abs(zs[i] - level) < 1e-4f && abs(zs[j] - level) < 1e-4f && abs(xs[i] - xs[j]) > 1e-4f) { surfaceAt = i; break } }
            val amp = vessel.width * 1.0f
            for (i in 0 until n) {
                if (i == 0) liquid.moveTo(sx(xs[0]), sy(zs[0])) else liquid.lineTo(sx(xs[i]), sy(zs[i]))
                if (i == surfaceAt) {
                    val j = (i + 1) % n
                    val steps = if (quality == Quality.Low) 4 else 20
                    for (k in 1 until steps) {
                        val u = k / steps.toFloat()
                        val x = xs[i] + (xs[j] - xs[i]) * u
                        // The ripple is measured across the container from the far wall (u = 0) to the near one.
                        val across = (x - (-w * kotlin.math.cos(a))) / (w * kotlin.math.cos(a).coerceAtLeast(0.2f))
                        liquid.lineTo(sx(x), sy(level + amp * vessel.surface.heightAt(across.coerceIn(0f, 1f))))
                    }
                }
            }
            liquid.close()
        }
    }

    val ceramic = style.shape == VesselShape.Mug
    val art = style.art
    // The far wall of a drawn glass, behind the liquid.
    if (art?.back != null) withTransform({ translate(pivotX, pivotY); scale(s, 1f, Offset.Zero); rotate(a * 180f / PI.toFloat(), Offset.Zero) }) { drawArtLayer(art, art.back, w * ppm, h * ppm, art.tint.copy(alpha = 0.45f)) }
    // What is behind, through the liquid, magnified and tinted.
    if (hasLiquid && !ceramic) {
        val fluid = vessel.fluid
        val mag = 1f + (fluid.index - 1f) * 0.75f
        val centre = Offset(pivotX - s * w * ppm * 0.5f, pivotY - h * ppm * 0.5f)
        if (behind != null && quality != Quality.Low) clipPath(liquid) { withTransform({ scale(mag, 1f, centre) }) { drawLayer(behind) } }
        val alpha = 0.42f + 0.5f * fluid.turbidity
        drawPath(liquid, Brush.verticalGradient(0f to fluid.color.copy(alpha = alpha * 0.85f), 1f to fluid.color.copy(alpha = alpha), startY = sy(level), endY = pivotY))
        // The surface: a bright line along the ripples, and the opening seen from above as a flat ellipse at the level.
        val chordLeft = Tilt.corners(w, h, a)
        val bounds = liquid.getBounds()
        val topY = sy(level)
        drawOval(Color.White.copy(alpha = 0.28f), Offset(bounds.left, topY - bounds.width * 0.07f), Size(bounds.width, bounds.width * 0.14f))
        clipPath(liquid) { drawLine(Color.White.copy(alpha = 0.55f), Offset(bounds.left, topY), Offset(bounds.right, topY), 2f) }
        // Bubbles rising from the bottom, bursting at the surface.
        clipPath(liquid) {
            for (i in 0 until vessel.bubbles) {
                val bx = bounds.left + vessel.bubbleX[i] * bounds.width; val by = bounds.bottom - vessel.bubbleY[i] * (bounds.bottom - topY)
                val r = 1.6f + 3.4f * vessel.bubbleY[i]
                drawCircle(Color.White.copy(alpha = 0.18f), r, Offset(bx, by)); drawCircle(Color.White.copy(alpha = 0.6f), r, Offset(bx, by), style = Stroke(1.1f))
            }
        }
        // Foam from a pour, floating on the surface: pale, with small bubbles.
        if (vessel.foam > 0.02f) {
            val foamH = bounds.width * 0.08f * vessel.foam
            drawOval(lerp(fluid.color, Color.White, 0.6f).copy(alpha = 0.92f), Offset(bounds.left, topY - foamH * 0.6f), Size(bounds.width, foamH * 1.4f))
            for (i in 0 until 7) drawCircle(Color.White.copy(alpha = 0.5f * vessel.foam), foamH * 0.25f, Offset(bounds.left + bounds.width * ((i * 0.37f + 0.1f) % 0.9f + 0.05f), topY))
        }
    }

    // The glass, in the container's own frame: outline in ink, the rim as a flat ellipse, a highlight along the wall, the thick base.
    withTransform({ translate(pivotX, pivotY); scale(s, 1f, Offset.Zero); rotate(a * 180f / PI.toFloat(), Offset.Zero) }) {
        val wp = w * ppm; val hp = h * ppm
        if (art != null) {
            drawArtLayer(art, art.front, wp, hp, art.tint)
            art.shine?.let { drawArtLayer(art, it, wp, hp, Color.White.copy(alpha = 0.55f)) }
            if (ceramic && art.opening != null) drawArtOpening(art, art.opening, vessel, wp, hp)
            return@withTransform
        }
        val taper = when (style.shape) { VesselShape.Tumbler -> 0.07f; VesselShape.Bottle -> 0.02f; VesselShape.Jug -> 0.03f; VesselShape.Mug -> 0.05f }
        val topLeft = -wp; val bottomLeft = -wp * (1f - taper)
        val outline = Path().apply { moveTo(topLeft, -hp); lineTo(bottomLeft, 0f); lineTo(0f - wp * taper * 0f, 0f); lineTo(0f, -hp) }
        if (ceramic) drawPath(outline, style.glass) else drawPath(outline, style.glass.copy(alpha = 0.16f))
        // The thick base of the glass, darker.
        drawRect(style.glass.copy(alpha = 0.3f), Offset(bottomLeft, -hp * 0.07f), Size(wp * (1f - taper * 0.5f), hp * 0.07f))
        // The cosmetic parts: a bottle's neck above its body, a jug's handle at the side.
        when (style.shape) {
            VesselShape.Bottle -> {
                val neckW = wp * 0.38f
                val neck = Path().apply { moveTo(-wp * 0.5f - neckW / 2f, -hp); lineTo(-wp * 0.5f - neckW / 2f, -hp * 1.32f); lineTo(-wp * 0.5f + neckW / 2f, -hp * 1.32f); lineTo(-wp * 0.5f + neckW / 2f, -hp) }
                drawPath(neck, style.glass.copy(alpha = 0.16f)); drawPath(neck, style.ink, style = Stroke(2.5f))
            }
            VesselShape.Jug -> drawArc(style.ink, -80f, 160f, false, Offset(0f, -hp * 0.85f), Size(wp * 0.34f, hp * 0.6f), style = Stroke(3f))
            VesselShape.Tumbler -> Unit
            VesselShape.Mug -> drawArc(style.ink, -90f, 180f, false, Offset(0f, -hp * 0.78f), Size(wp * 0.38f, hp * 0.55f), style = Stroke(5f))
        }
        drawPath(outline, style.ink, style = Stroke(2.6f))
        if (ceramic) {
            // The opening seen from above: the rim, the dark inside, and the surface of what is in it a little way down.
            val rimTop = -hp - wp * 0.09f
            drawOval(style.glass, Offset(-wp, rimTop), Size(wp, wp * 0.18f))
            drawOval(Color(0xFF241B14), Offset(-wp * 0.93f, rimTop + wp * 0.015f), Size(wp * 0.86f, wp * 0.15f))
            if (!vessel.empty) {
                val depth = (1f - vessel.fill) * hp * 0.28f
                val surface = Path().apply { addOval(Rect(-wp * 0.93f, rimTop + wp * 0.015f, -wp * 0.07f, rimTop + wp * 0.165f)) }
                clipPath(surface) {
                    drawOval(vessel.fluid.color, Offset(-wp * 0.93f, rimTop + wp * 0.015f + depth), Size(wp * 0.86f, wp * 0.15f))
                    if (vessel.foam > 0.02f) drawOval(lerp(vessel.fluid.color, Color.White, 0.55f).copy(alpha = 0.9f * vessel.foam), Offset(-wp * 0.86f, rimTop + wp * 0.03f + depth), Size(wp * 0.72f, wp * 0.12f))
                    drawOval(Color.White.copy(alpha = 0.18f), Offset(-wp * 0.75f, rimTop + wp * 0.04f + depth), Size(wp * 0.4f, wp * 0.05f))
                }
            }
            drawOval(style.ink.copy(alpha = 0.8f), Offset(-wp, rimTop), Size(wp, wp * 0.18f), style = Stroke(2f))
        }
        if (!ceramic) drawOval(style.ink.copy(alpha = 0.8f), Offset(-wp, -hp - wp * 0.07f), Size(wp, wp * 0.14f), style = Stroke(2f))
        // A highlight of the window's light down the near wall.
        drawRect(Color.White.copy(alpha = if (ceramic) 0.5f else 0.35f), Offset(-wp * 0.88f, -hp * 0.88f), Size(wp * 0.05f, hp * 0.7f))
    }

    // Steam: wisps rising from the surface, swaying, thicker the hotter it is.
    if (!vessel.empty && vessel.steam > 0.04f && abs(body.angle) < 0.5f && quality != Quality.Low) {
        val corners = Tilt.corners(w, h, a)
        val rimX = sx((corners[4] + corners[6]) / 2f); val rimY = sy((corners[5] + corners[7]) / 2f)
        val noise = br.com.faru.boitata.core.FastNoise(11L)
        val wisps = 3 + (vessel.steam * 5f).toInt()
        for (i in 0 until wisps) {
            val life = br.com.faru.boitata.core.fx.cycle(seconds, 2.2f + 1.4f * noise.at(i, 0), noise.at(i, 1))
            val rise = life * h * ppm * (0.8f + 0.7f * noise.at(i, 2))
            val x0 = rimX + (noise.at(i, 3) - 0.5f) * w * ppm * 0.7f
            val path = Path()
            for (k in 0..6) {
                val u = k / 6f
                val x = x0 + kotlin.math.sin(seconds * 1.3f + i * 2f + u * 5f) * w * ppm * 0.16f * (0.3f + u)
                val y = rimY - rise * u
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val fade = br.com.faru.boitata.core.fx.bump(life) * vessel.steam
            drawPath(path, Color.White.copy(alpha = (0.45f * fade).coerceIn(0f, 0.55f)), style = Stroke(w * ppm * 0.07f * (1f - 0.6f * life) + 2f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
    }
}

/**
 * The stream running over a vessel's lip while it spills: a parabola from the lip (the speed it leaves with, falling under gravity) to where it lands on the surface, [pixelsPerMeter] big, as thick as the flow says
 * (`Spill.thickness`, a little exaggerated so it shows), with a bright thread down it and a splash where it lands. Draws nothing when nothing is spilling. The vessel's corner is at ([pivotX], [pivotY]) on the surface, as for [drawVessel].
 */
public fun DrawScope.drawSpillStream(vessel: LiquidVessel, pivotX: Float, pivotY: Float, pixelsPerMeter: Float, seconds: Float, quality: Quality = Quality.High) {
    if (!vessel.spilling) return
    val s = if (vessel.body.angle >= 0f) 1f else -1f
    val ppm = pixelsPerMeter
    val lipX = vessel.lipX(); val lipZ = vessel.lipHeight()
    val speed = vessel.jetSpeed
    val fall = br.com.faru.boitata.core.fluid.Spill.fallTime(lipZ)
    val thick = (br.com.faru.boitata.core.fluid.Spill.thickness(vessel.spillRate, speed) * ppm * 9f).coerceIn(2f, 22f)
    val color = vessel.fluid.color
    val steps = if (quality == Quality.Low) 6 else 18
    val path = Path()
    for (k in 0..steps) {
        val t = fall * k / steps
        val x = pivotX + s * (lipX + speed * t) * ppm
        val y = pivotY - (lipZ - 0.5f * 9.81f * t * t).coerceAtLeast(0f) * ppm
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color.copy(alpha = 0.8f), style = Stroke(thick, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    drawPath(path, Color.White.copy(alpha = 0.35f), style = Stroke(thick * 0.3f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    // The splash where it lands: two rings opening and a few drops thrown up.
    val landX = pivotX + s * (lipX + speed * fall) * ppm
    for (r in 0..1) {
        val u = br.com.faru.boitata.core.fx.cycle(seconds, 0.6f, r * 0.5f)
        drawOval(color.copy(alpha = 0.5f * br.com.faru.boitata.core.fx.bump(u)), Offset(landX - thick * (1f + 3f * u), pivotY - thick * 0.3f * (1f + 3f * u)), Size(2f * thick * (1f + 3f * u), thick * 0.6f * (1f + 3f * u)), style = Stroke(1.6f))
    }
    val noise = br.com.faru.boitata.core.FastNoise(3L)
    for (i in 0 until if (quality == Quality.Low) 2 else 6) {
        val u = br.com.faru.boitata.core.fx.cycle(seconds, 0.5f + 0.3f * noise.at(i, 0), noise.at(i, 1))
        val dx = (noise.at(i, 2) - 0.5f) * thick * 5f * u
        drawCircle(color.copy(alpha = 0.8f * (1f - u)), 1.8f, Offset(landX + dx, pivotY - thick * 3f * u * (1f - u) * 4f))
    }
}

/**
 * A puddle on a surface: a flat ellipse of the liquid centred on ([centreX], [surfaceY]), [radius] pixels wide, glossy, with a bright rim. [burnt] (0..1) dims it as it is used up and leaves a dark stain behind.
 */
public fun DrawScope.drawPuddle(color: Color, centreX: Float, surfaceY: Float, radius: Float, burnt: Float = 0f) {
    if (radius < 1.5f) return
    val ry = radius * 0.16f
    val top = Offset(centreX - radius, surfaceY - ry); val size = Size(2f * radius, 2f * ry)
    if (burnt > 0.02f) drawOval(Color(0xFF0F0C0A).copy(alpha = 0.45f * burnt), top, size)
    drawOval(color.copy(alpha = 0.5f * (1f - 0.8f * burnt)), top, size)
    drawOval(Color.White.copy(alpha = 0.22f * (1f - burnt)), Offset(centreX - radius * 0.7f, surfaceY - ry * 0.7f), Size(radius * 1.4f, ry * 0.7f))
    drawOval(Color.White.copy(alpha = 0.35f * (1f - burnt)), top, size, style = Stroke(1.4f))
}

/**
 * The light a filled glass throws on the table beside it, from a lamp on the side [fromLeft] says: a **coloured shadow** (the glass lets the light through, tinted by the liquid, so its shadow is the liquid's colour and faint, not
 * black) stretching away from the lamp, and inside it a bright **caustic** where the curved glass and liquid focus the light, wobbling with the surface as the ripples move. Both are as strong as the glass is full.
 * Draw on the table before the vessel. [pivotX], [pivotY] are as for [drawVessel]; nothing is drawn for an empty or tipped glass.
 */
public fun DrawScope.drawCaustic(vessel: LiquidVessel, pivotX: Float, pivotY: Float, pixelsPerMeter: Float, seconds: Float, fromLeft: Boolean, quality: Quality = Quality.High) {
    if (vessel.empty || abs(vessel.body.angle) > 0.4f) return
    val ppm = pixelsPerMeter
    val dir = if (fromLeft) 1f else -1f                         // the way the shadow goes
    val wp = vessel.width * ppm; val hp = vessel.height * ppm
    val fill = vessel.fill
    val tint = vessel.fluid.color
    val baseX = pivotX - wp / 2f
    // The coloured shadow: a long flat ellipse running away from the lamp, faint and tinted.
    val length = hp * (0.7f + 0.5f * fill)
    val shadow = Size(length, wp * 0.32f)
    drawOval(tint.copy(alpha = 0.2f * fill + 0.05f), Offset(if (dir > 0f) baseX else baseX - length + wp * 0.2f, pivotY - shadow.height * 0.4f), shadow)
    // The caustic: the light focused beyond the glass, brightest in a small patch, with filaments that shimmer with the surface.
    val focus = Offset(baseX + dir * (wp * 0.45f + length * 0.62f), pivotY + wp * 0.02f)
    val wob = vessel.surface.energy().coerceAtMost(4f)
    val bright = Color(0xFFFFF6DC)
    val radius = wp * 0.34f
    drawOval(Brush.radialGradient(0f to bright.copy(alpha = (0.55f * fill + 0.1f).coerceAtMost(0.7f)), 0.55f to tint.copy(alpha = 0.3f * fill), 1f to Color.Transparent, center = focus, radius = radius * 1.4f), Offset(focus.x - radius * 1.4f, focus.y - radius * 0.5f), Size(radius * 2.8f, radius), blendMode = BlendMode.Plus)
    if (quality != Quality.Low) {
        val noise = br.com.faru.boitata.core.FastNoise(13L)
        for (i in 0 until 7) {
            val phase = seconds * (1.2f + noise.at(i, 0)) + i * 1.7f + wob
            val y = focus.y - radius * 0.2f + radius * 0.4f * noise.at(i, 1) + kotlin.math.sin(phase) * 1.5f
            val x0 = focus.x - radius + radius * 2f * noise.at(i, 2); val len = radius * (0.5f + 0.8f * noise.at(i, 3))
            drawLine(bright.copy(alpha = (0.35f + 0.3f * kotlin.math.sin(phase * 1.3f)).coerceIn(0.05f, 0.7f) * fill), Offset(x0, y), Offset(x0 + len * kotlin.math.cos(phase * 0.3f), y + kotlin.math.sin(phase * 0.7f) * radius * 0.12f), 1.6f, blendMode = BlendMode.Plus)
        }
    }
}


/** Draws one of [art]'s layers in the container's frame (its corner at the origin, the box spanning x from -[wp] to 0 and y from -[hp] to 0), the body of the picture stretched onto the box. */
private fun DrawScope.drawArtLayer(art: VesselArt, image: ImageBitmap, wp: Float, hp: Float, tint: Color) {
    val kx = wp / art.body.width; val ky = hp / art.body.height
    withTransform({ translate(-wp - art.body.left * kx, -hp - art.body.top * ky); scale(kx, ky, Offset.Zero) }) {
        drawImage(image, filterQuality = FilterQuality.Medium, colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn))
    }
}

/** The rim of an opaque container seen from above, as a drawn ellipse the width of the body, with the surface of what is in it showing through its hollow. */
private fun DrawScope.drawArtOpening(art: VesselArt, image: ImageBitmap, vessel: LiquidVessel, wp: Float, hp: Float) {
    val ow = wp * 1.0f; val oh = ow * image.height / image.width
    val left = -wp; val top = -hp - oh * 0.5f
    if (!vessel.empty) {
        val inner = Rect(left + ow * 0.04f, top + oh * 0.12f, left + ow * 0.96f, top + oh * 0.88f)
        val depth = (1f - vessel.fill) * oh * 0.5f
        clipPath(Path().apply { addOval(inner) }) {
            drawRect(Color(0xFF241B14), inner.topLeft, inner.size)
            drawOval(vessel.fluid.color, Offset(inner.left, inner.top + depth), Size(inner.width, inner.height))
            if (vessel.foam > 0.02f) drawOval(lerp(vessel.fluid.color, Color.White, 0.55f).copy(alpha = 0.9f * vessel.foam), Offset(inner.left + inner.width * 0.1f, inner.top + depth + inner.height * 0.1f), Size(inner.width * 0.8f, inner.height * 0.7f))
            drawOval(Color.White.copy(alpha = 0.16f), Offset(inner.left + inner.width * 0.2f, inner.top + depth + inner.height * 0.2f), Size(inner.width * 0.4f, inner.height * 0.2f))
        }
    }
    withTransform({ translate(left, top); scale(ow / image.width, oh / image.height, Offset.Zero) }) {
        drawImage(image, filterQuality = FilterQuality.Medium, colorFilter = ColorFilter.tint(art.tint, BlendMode.SrcIn))
    }
}
