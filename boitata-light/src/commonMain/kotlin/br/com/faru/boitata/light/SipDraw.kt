package br.com.faru.boitata.light

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.fluid.LiquidVessel
import br.com.faru.boitata.core.sip.SipPose
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Draws [vessel] where a sip holds it ([pose]): the middle of its base at ([SipPose.x], [SipPose.y]), [SipPose.scale] times as big as on the table and leaning in toward the viewer by [SipPose.tilt]. Seen from a
 * little above, a vessel that leans toward you is foreshortened (its height shrinks by the cosine of the lean) and its opening opens up into an ellipse (the sine) in which the rim, the dark inside and the surface of the
 * liquid are seen. It has no scene behind it, so what is behind is not seen through the glass.
 */
public fun DrawScope.drawVesselAt(vessel: LiquidVessel, style: VesselStyle, pose: SipPose, pixelsPerMeter: Float, seconds: Float, quality: Quality = Quality.High) {
    val c = cos(pose.tilt); val sn = sin(pose.tilt)
    withTransform({ translate(pose.x, pose.y); scale(pose.scale, pose.scale * c, Offset.Zero) }) {
        drawVessel(vessel, style, vessel.width * pixelsPerMeter / 2f, 0f, pixelsPerMeter, seconds, quality)
    }
    // The opening, over the top of the body.
    val rx = vessel.width * pixelsPerMeter * pose.scale / 2f
    val ry = rx * 0.95f * sn
    if (ry < 2f) return
    val rimY = pose.y - vessel.height * pixelsPerMeter * pose.scale * c
    val rim = Offset(pose.x, rimY)
    drawOval(style.glass.copy(alpha = 0.95f), Offset(rim.x - rx, rim.y - ry), Size(2f * rx, 2f * ry))
    val inner = Path().apply { addOval(Rect(rim.x - rx * 0.9f, rim.y - ry * 0.9f, rim.x + rx * 0.9f, rim.y + ry * 0.9f)) }
    drawPath(inner, Color(0xFF241B14))
    if (!vessel.empty) {
        // The surface lies lower the less there is, toward the near wall as the glass leans in.
        val depth = (1f - vessel.fill) * vessel.height * pixelsPerMeter * pose.scale * c * 0.9f
        clipPath(inner) {
            val sy = rim.y - ry * 0.9f + depth
            drawOval(vessel.fluid.color, Offset(rim.x - rx * 0.9f, sy), Size(rx * 1.8f, ry * 1.8f))
            if (vessel.foam > 0.02f) drawOval(lerp(vessel.fluid.color, Color.White, 0.55f).copy(alpha = 0.9f * vessel.foam), Offset(rim.x - rx * 0.75f, sy + ry * 0.2f), Size(rx * 1.5f, ry * 1.3f))
            drawOval(Color.White.copy(alpha = 0.2f), Offset(rim.x - rx * 0.55f, sy + ry * 0.25f), Size(rx * 0.9f, ry * 0.5f))
        }
    }
    drawOval(style.ink.copy(alpha = 0.85f), Offset(rim.x - rx, rim.y - ry), Size(2f * rx, 2f * ry), style = Stroke(max(2f, pose.scale * 0.8f)))
}

/**
 * The darkening that makes a view first person: the whole scene dimmed by [dim] and its edges darkened by [vignette], both faded in by [progress] (0 nothing, 1 all). Draw over the scene, under what is held up.
 */
public fun DrawScope.drawSipOverlay(progress: Float, dim: Float, vignette: Float) {
    val p = progress.coerceIn(0f, 1f)
    if (p <= 0.001f) return
    drawRect(Color.Black.copy(alpha = (dim * p).coerceIn(0f, 1f)))
    val reach = max(size.width, size.height) * 0.75f
    drawRect(
        Brush.radialGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = (vignette * p).coerceIn(0f, 1f)), center = Offset(size.width / 2f, size.height * 0.55f), radius = reach),
    )
}
