package br.com.faru.boitata.core.depth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import br.com.faru.boitata.core.Perspective

/**
 * One thing standing on a [DepthStage]: where its feet are, how tall it is drawn at the reference depth, and how wide it is for its height.
 * Position is state, so a drawing that reads it in a draw block redraws when the thing is dragged and nothing recomposes.
 */
public class DepthItem(
    public val id: String,
    feetX: Float,
    feetY: Float,
    /** Height in pixels when the feet are at the perspective's reference depth. */
    public val baseHeight: Float,
    /** Width over height of what is drawn, so a click can be tested against its box. */
    public val aspect: Float,
) {
    public var feetX: Float by mutableFloatStateOf(feetX)
    public var feetY: Float by mutableFloatStateOf(feetY)

    /** 0 on the ground, 1 held in the hand: a thing being dragged is lifted a little (drawn larger, its shadow pushed out). Animated by the drag. */
    public var lift: Float by mutableFloatStateOf(0f)
}

/**
 * Things on a flat ground that can be moved about: nearer is bigger and covers what is behind, farther is smaller and sits nearer the horizon.
 *
 * The ground is a rectangle in the same pixels as the drawing; its bottom edge is the front (nearest) and its top edge the back, and the [perspective] turns a foot
 * position into a scale. Pure arithmetic over positions, no drawing: draw `ordered()` from first to last and every thing covers the ones behind it.
 */
public class DepthStage(
    public val perspective: Perspective,
    public val groundLeft: Float,
    public val groundTop: Float,
    public val groundRight: Float,
    public val groundBottom: Float,
) {
    private val all = ArrayList<DepthItem>()
    public val items: List<DepthItem> get() = all

    public fun add(item: DepthItem): DepthItem = item.also { all += it }

    /** How big [item] is drawn, as a multiple of its base size: the perspective's, with the lift on top. */
    public fun scale(item: DepthItem): Float = perspective.scale(item.feetY) * (1f + LIFT_GROWTH * item.lift)

    /** Its drawn height in pixels. */
    public fun height(item: DepthItem): Float = item.baseHeight * scale(item)

    /** Its box as left, top, right, bottom: standing on its feet, centred on them. */
    public fun bounds(item: DepthItem): FloatArray {
        val h = height(item)
        val w = h * item.aspect
        return floatArrayOf(item.feetX - w / 2f, item.feetY - h, item.feetX + w / 2f, item.feetY)
    }

    /** The items from the farthest to the nearest: draw them in this order and nearer things cover farther ones. */
    public fun ordered(): List<DepthItem> = all.sortedBy { it.feetY }

    /** The nearest item whose box holds ([x], [y]), or null: the one on top where things overlap. */
    public fun hit(x: Float, y: Float): DepthItem? = all.sortedByDescending { it.feetY }.firstOrNull {
        val b = bounds(it)
        x >= b[0] && x <= b[2] && y >= b[1] && y <= b[3]
    }

    /** Puts [item]'s feet at ([x], [y]), kept on the ground. */
    public fun moveTo(item: DepthItem, x: Float, y: Float) {
        item.feetX = x.coerceIn(groundLeft, groundRight)
        item.feetY = y.coerceIn(groundTop, groundBottom)
    }

    /** Moves [item] by ([dx], [dy]) pixels, kept on the ground. */
    public fun moveBy(item: DepthItem, dx: Float, dy: Float) { moveTo(item, item.feetX + dx, item.feetY + dy) }

    /** How far back a thing is: 0 at the front edge, 1 at the back edge. */
    public fun depthOf(item: DepthItem): Float = ((groundBottom - item.feetY) / (groundBottom - groundTop)).coerceIn(0f, 1f)

    public companion object {
        /** How much bigger a held thing is drawn. */
        public const val LIFT_GROWTH: Float = 0.08f
    }
}
