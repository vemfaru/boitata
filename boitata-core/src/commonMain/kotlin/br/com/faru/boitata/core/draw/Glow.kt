package br.com.faru.boitata.core.draw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import br.com.faru.boitata.core.AdvancedBoitataApi

/**
 * A radial glow of radius 1 about (0, 0), from [inner] at the centre to transparent at the rim, with an optional [middle] colour at [middleAt].
 * Build it once per draw call (or cache it) and stamp it with [drawGlow] as many times as needed: a glow per ember, per fog bank or per cloud puff
 * then costs a transform instead of a new gradient shader each time.
 */
@AdvancedBoitataApi
public fun unitGlow(inner: Color, middle: Color? = null, middleAt: Float = 0.7f): Brush =
    if (middle == null) Brush.radialGradient(0f to inner, 1f to Color.Transparent, center = Offset.Zero, radius = 1f)
    else Brush.radialGradient(0f to inner, middleAt to middle, 1f to Color.Transparent, center = Offset.Zero, radius = 1f)

/**
 * Stamps a [unitGlow] at [center], [radius] across, at [alpha]: the same picture as a `Brush.radialGradient` of the same stops at that centre and
 * radius with its colours multiplied by [alpha], without making a shader.
 */
@AdvancedBoitataApi
public fun DrawScope.drawGlow(glow: Brush, center: Offset, radius: Float, alpha: Float = 1f, blendMode: BlendMode = BlendMode.SrcOver) {
    if (radius <= 0f || alpha <= 0f) return
    translate(center.x, center.y) {
        scale(radius, radius, pivot = Offset.Zero) {
            drawCircle(glow, radius = 1f, center = Offset.Zero, alpha = alpha.coerceAtMost(1f), blendMode = blendMode)
        }
    }
}
