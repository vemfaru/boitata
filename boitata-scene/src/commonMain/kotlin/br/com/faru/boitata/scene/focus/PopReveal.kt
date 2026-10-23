package br.com.faru.boitata.scene.focus

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import br.com.faru.boitata.core.clock.LocalSceneClock
import kotlin.math.max

/**
 * How a set of items (a wheel's keys) pops in and out, one after another. Opening, item of rank r in group g starts at `r * staggerMillis + g * groupDelayMillis`, grows from
 * [from] to its size over [popMillis] with a small overshoot and fades in over [fadeMillis]; its label follows [labelDelayMillis] after it starts, rising [labelRise] units into place
 * over [labelMillis]. Closing goes the other way round: the last to open goes first, [closeStaggerMillis] apart, each over [closeMillis].
 *
 * Every function here is pure arithmetic on milliseconds, so the order and timing can be tested without a clock.
 */
@Immutable
public data class PopMotion(
    val staggerMillis: Int = 24,
    val groupDelayMillis: Int = 60,
    val popMillis: Int = 140,
    val fadeMillis: Int = 70,
    val from: Float = 0.6f,
    val labelDelayMillis: Int = 30,
    val labelMillis: Int = 90,
    val labelRise: Float = 6f,
    val closeStaggerMillis: Int = 15,
    val closeMillis: Int = 100,
) {
    /** When the item of [rank] in [group] starts to open. */
    public fun openStart(rank: Int, group: Int = 0): Int = rank * staggerMillis + group * groupDelayMillis

    /** How long opening takes for items up to [lastRank] in groups up to [lastGroup]. */
    public fun openTotal(lastRank: Int, lastGroup: Int = 0): Int = openStart(lastRank, lastGroup) + max(popMillis, labelDelayMillis + labelMillis)

    /** When the item of [rank] starts to close, with [lastRank] the highest rank: the last to open goes first. */
    public fun closeStart(rank: Int, lastRank: Int): Int = (lastRank - rank) * closeStaggerMillis

    /** How long closing takes for items up to [lastRank]. */
    public fun closeTotal(lastRank: Int): Int = closeStart(0, lastRank) + closeMillis

    /** Scale, [ms] after an item starts to open. */
    public fun openScale(ms: Float): Float = if (ms <= 0f) from else from + (1f - from) * Overshoot.transform((ms / popMillis).coerceIn(0f, 1f))

    /** Opacity, [ms] after an item starts to open. */
    public fun openAlpha(ms: Float): Float = (ms / fadeMillis).coerceIn(0f, 1f)

    /** The label's opacity, [ms] after its item starts to open. */
    public fun openLabel(ms: Float): Float = ((ms - labelDelayMillis) / labelMillis).coerceIn(0f, 1f)

    /** Scale and opacity, [ms] after an item starts to close. */
    public fun closeScale(ms: Float): Float = 1f - (1f - from) * (ms / closeMillis).coerceIn(0f, 1f)
    public fun closeAlpha(ms: Float): Float = 1f - (ms / closeMillis).coerceIn(0f, 1f)

    public companion object {
        /** Grows a touch past its size and settles back. */
        public val Overshoot: CubicBezierEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    }
}

/**
 * A set of items popping in and out ([PopMotion]), on one timeline: read [scale], [alpha], [labelAlpha] and [labelOffset] in the draw phase (`graphicsLayer`) so nothing
 * recomposes while it plays. [visible] turns false once a close has finished, for the caller to remove the items. Made by [rememberPopReveal].
 */
@Stable
public class PopReveal internal constructor(public val motion: PopMotion) {
    internal val time = Animatable(0f)
    internal var opening by mutableStateOf(true)
    internal var rank: (Int) -> Int = { it }
    internal var group: (Int) -> Int = { 0 }
    internal var lastRank by mutableStateOf(0)
    internal var lastGroup by mutableStateOf(0)

    /** False once a close has played out. */
    public val visible: Boolean get() = opening || time.value < motion.closeTotal(lastRank)

    private fun since(i: Int): Float = time.value - (if (opening) motion.openStart(rank(i), group(i)) else motion.closeStart(rank(i), lastRank))

    public fun scale(i: Int): Float = if (opening) motion.openScale(since(i)) else motion.closeScale(since(i))
    public fun alpha(i: Int): Float = if (opening) motion.openAlpha(since(i)) else motion.closeAlpha(since(i))
    public fun labelAlpha(i: Int): Float = if (opening) motion.openLabel(since(i)) else motion.closeAlpha(since(i))

    /** How far below its place the label still is, in [PopMotion.labelRise] units. */
    public fun labelOffset(i: Int): Float = (1f - labelAlpha(i)) * motion.labelRise
}

/**
 * Pops [count] items in while [open] is true and out when it turns false, ordered by [rank] (0 first: put the item nearest what it belongs to first) and [group] (a later group
 * follows the earlier one by [PopMotion.groupDelayMillis]). Under reduced motion (the scene clock's, unless [reduced] says otherwise) they appear and go at once.
 */
@Composable
public fun rememberPopReveal(
    count: Int, open: Boolean, rank: (Int) -> Int = { it }, group: (Int) -> Int = { 0 }, motion: PopMotion = PopMotion(), reduced: Boolean? = null,
): PopReveal {
    val reveal = remember(motion) { PopReveal(motion) }
    reveal.rank = rank; reveal.group = group
    reveal.lastRank = (0 until count).maxOfOrNull(rank) ?: 0
    reveal.lastGroup = (0 until count).maxOfOrNull(group) ?: 0
    val instant = reduced ?: (LocalSceneClock.current?.reducedMotion ?: false)
    LaunchedEffect(open, count) {
        reveal.opening = open
        val total = (if (open) motion.openTotal(reveal.lastRank, reveal.lastGroup) else motion.closeTotal(reveal.lastRank)).toFloat()
        reveal.time.snapTo(0f)
        if (instant) reveal.time.snapTo(total) else reveal.time.animateTo(total, tween(total.toInt(), easing = LinearEasing))
    }
    return reveal
}
