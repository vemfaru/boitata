package br.com.faru.boitata.light

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import br.com.faru.boitata.core.stain.StainField

/** How flat a circle on a table lies, as seen from the front: the same as a puddle's. */
private const val FLATTEN = 0.16f

/**
 * The marks wet containers left on a surface (see [StainField]): each is the foot of the container that made it, a flat ellipse with a darker, harder edge where the wet dried last (the coffee ring), and for a
 * container with a rim foot only a ring. Draw on the surface before the puddles and the containers. ([originX], [surfaceY]) is where the surface's x = 0 and depth = 0 lie on screen; [pixelsPerMeter] scales both,
 * and a mark further back is drawn higher up.
 */
public fun DrawScope.drawStains(field: StainField, originX: Float, surfaceY: Float, pixelsPerMeter: Float) {
    for (i in 0 until field.count) {
        val r = field.radius[i] * pixelsPerMeter
        if (r < 1.5f) continue
        val s = field.strength[i]
        val cx = originX + field.x[i] * pixelsPerMeter
        val cy = surfaceY - field.depth[i] * pixelsPerMeter * FLATTEN
        val color = field.colorOf(i)
        val ry = r * FLATTEN
        val ring = field.ring[i]
        if (ring <= 0f) {
            drawOval(color.copy(alpha = 0.75f * s), Offset(cx - r, cy - ry), Size(2f * r, 2f * ry))
        } else {
            // A rim: stroke along the middle of the band, with a wider fainter pass for the soft side.
            val band = (r * ring).coerceAtLeast(1.5f)
            val mid = r - band / 2f
            val midRy = mid * FLATTEN
            drawOval(color.copy(alpha = 0.25f * s), Offset(cx - mid, cy - midRy), Size(2f * mid, 2f * midRy), style = Stroke(band * 1.8f))
            drawOval(color.copy(alpha = 0.75f * s), Offset(cx - mid, cy - midRy), Size(2f * mid, 2f * midRy), style = Stroke(band))
        }
        // The dried edge.
        drawOval(color.copy(alpha = 0.95f * s), Offset(cx - r, cy - ry), Size(2f * r, 2f * ry), style = Stroke(1.8f))
    }
}
