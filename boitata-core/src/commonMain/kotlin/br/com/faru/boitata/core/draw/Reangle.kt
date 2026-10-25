package br.com.faru.boitata.core.draw

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A picture drawn from one angle shown from another, without drawing it again: a thing seen from above shows its top and its front, and how much of each goes with the angle
 * the eye looks down at it. Its top band is its depth times sin(angle), its front its height times cos(angle) (style.md's parallel projection); seen steeper the top grows and
 * the front shrinks, seen flatter (a shelf near eye height) the other way.
 *
 * [topBand] is how far down the picture its top band reaches, from the picture's top (world units); [base] where the thing touches its surface. Drawn from [drawnAt] (the angle
 * the art looks right at, radians), shown at [seenAt]: the top band is scaled by [topScale], the front by [frontScale], both kept on the base. Exact for a box or a cylinder;
 * painted detail (a highlight on a rim) squashes with its band. Pure numbers; [Modifier.reangle] draws it.
 */
public class Reangle(public val topBand: Float, public val base: Float, public val drawnAt: Float, public val seenAt: Float) {
    public val topScale: Float = (sin(seenAt) / sin(drawnAt)).coerceIn(MIN_SCALE, MAX_SCALE)
    public val frontScale: Float = (cos(seenAt) / cos(drawnAt)).coerceIn(MIN_SCALE, MAX_SCALE)

    /** Whether it changes anything worth drawing twice for (within half a percent it is the picture as drawn). */
    public val visible: Boolean get() = kotlin.math.abs(topScale - 1f) > 0.005f || kotlin.math.abs(frontScale - 1f) > 0.005f

    public companion object {
        /** A band is never squashed below this, nor stretched past [MAX_SCALE]: beyond them a re-angled picture looks pressed, not turned. */
        public const val MIN_SCALE: Float = 0.35f
        public const val MAX_SCALE: Float = 1.8f

        /** The angle the eye looks down at a point [below] cm under it and [ahead] cm in front of it, in radians. */
        public fun elevation(below: Float, ahead: Float): Float = atan2(below, ahead)

        public fun degrees(radians: Float): Float = (radians * 180.0 / PI).toFloat()
    }
}

/**
 * Draws what is under it re-angled by [r] ([Reangle]): its front band (from [Reangle.topBand] down to [Reangle.base]) scaled by the front's factor and kept on the base, its top
 * band scaled by the top's and laid on the front, and anything below the base (a trailing stem, a shadow) as it was. [originY] is the world y of this box's top, so the bands,
 * given in world units, can be found in it. What is drawn is the content twice, clipped: put it over a layer (an offscreen prop) so each copy is a replay.
 */
public fun Modifier.reangle(r: Reangle?, originY: Float): Modifier = if (r == null || !r.visible) this else drawWithContent {
    val d = density
    val split = (r.topBand - originY) * d
    val base = (r.base - originY) * d
    // Wide clips across: a glow or a pool drawn by the thing reaches past its box sideways and must not be cut.
    val l = -WIDE; val rr = WIDE
    // The front band, from the split down to the base, scaled about the base.
    val frontTop = base - (base - split) * r.frontScale
    withTransform({ translate(0f, base * (1f - r.frontScale)); scale(1f, r.frontScale, pivot = androidx.compose.ui.geometry.Offset.Zero) }) {
        clipRect(l, split, rr, base) { this@drawWithContent.drawContent() }
    }
    // The top band, everything above the split, scaled about the split and laid on the front's new top.
    withTransform({ translate(0f, frontTop - split * r.topScale); scale(1f, r.topScale, pivot = androidx.compose.ui.geometry.Offset.Zero) }) {
        clipRect(l, -WIDE, rr, split) { this@drawWithContent.drawContent() }
    }
    // Below the base, as drawn.
    clipRect(l, base, rr, WIDE) { this@drawWithContent.drawContent() }
}

/** Far enough past any box that nothing drawn is cut. */
private const val WIDE = 100_000f
