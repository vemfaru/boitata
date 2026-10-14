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
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.fluid.LiquidVessel
import br.com.faru.boitata.core.fluid.Tilt
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max

/** How a container looks: a tumbler narrows toward its foot, a bottle has a neck above its body, a jug has a handle. The liquid and the physics are those of the plain box either way (the neck and handle are only drawn). */
public enum class VesselShape { Tumbler, Bottle, Jug }

/** What the container is made of, for drawing: [glass] tints the walls, [ink] outlines them (the woodcut's black line). */
public class VesselStyle(public val shape: VesselShape = VesselShape.Tumbler, public val glass: Color = Color(0xFFDCEBEA), public val ink: Color = Color(0xFF14110F))

/**
 * Draws a [LiquidVessel] with its corner (the point it tips on) at ([pivotX], [pivotY]) on the floor, [pixelsPerMeter] big: the glass, the liquid at its level with the surface rippling as the simulation says, a
 * lit highlight along the walls and the opening seen from a little above. It is drawn tipped by the vessel's angle, the liquid staying level in the world as it does.
 *
 * What is **behind** the glass shows through the liquid **magnified**, the way a glass of water magnifies what is behind it (by about `1 + (index - 1) * 0.75` across), tinted by the liquid, which is what [behind]
 * is for: a layer the scene has recorded of everything behind the table. Where there is no liquid the glass is clear. [mirrored] draws the vessel flipped about the floor, faint, for a glossy table.
 */
public fun DrawScope.drawVessel(
    vessel: LiquidVessel, style: VesselStyle, pivotX: Float, pivotY: Float, pixelsPerMeter: Float, seconds: Float, quality: Quality = Quality.High,
    behind: GraphicsLayer? = null, mirrored: Float = 0f,
) {
    if (mirrored > 0.01f) {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, pivotY, size.width, size.height), Paint().apply { alpha = mirrored })
            withTransform({ translate(0f, 2f * pivotY); scale(1f, -1f, Offset.Zero) }) { drawVessel(vessel, style, pivotX, pivotY, pixelsPerMeter, seconds, quality, behind, 0f) }
            canvas.restore()
        }
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

    // What is behind, through the liquid, magnified and tinted.
    if (hasLiquid) {
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
    }

    // The glass, in the container's own frame: outline in ink, the rim as a flat ellipse, a highlight along the wall, the thick base.
    withTransform({ translate(pivotX, pivotY); scale(s, 1f, Offset.Zero); rotate(a * 180f / PI.toFloat(), Offset.Zero) }) {
        val wp = w * ppm; val hp = h * ppm
        val taper = when (style.shape) { VesselShape.Tumbler -> 0.07f; VesselShape.Bottle -> 0.02f; VesselShape.Jug -> 0.03f }
        val topLeft = -wp; val bottomLeft = -wp * (1f - taper)
        val outline = Path().apply { moveTo(topLeft, -hp); lineTo(bottomLeft, 0f); lineTo(0f - wp * taper * 0f, 0f); lineTo(0f, -hp) }
        drawPath(outline, style.glass.copy(alpha = 0.16f))
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
        }
        drawPath(outline, style.ink, style = Stroke(2.6f))
        drawOval(style.ink.copy(alpha = 0.8f), Offset(-wp, -hp - wp * 0.07f), Size(wp, wp * 0.14f), style = Stroke(2f))
        // A highlight of the window's light down the near wall.
        drawRect(Color.White.copy(alpha = 0.35f), Offset(-wp * 0.88f, -hp * 0.88f), Size(wp * 0.05f, hp * 0.7f))
    }
}
