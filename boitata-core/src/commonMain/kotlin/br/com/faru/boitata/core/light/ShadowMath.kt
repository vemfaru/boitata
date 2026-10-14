package br.com.faru.boitata.core.light

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

/**
 * How one light throws one thing's shadow onto the ground, as numbers. The ground is seen at a slant, so a distance along the screen's y is [GROUND_SQUASH] of the same distance along x.
 *
 * Everything here is pure (no drawing, no colours) so the geometry can be tested: a shadow is the thing flipped onto the ground and sheared. For every pixel of height, the shadow
 * reaches [ShadowCast.leanX] pixels sideways and [ShadowCast.leanY] pixels down the screen, so a thing [h] pixels tall has a shadow whose tip lies ([leanX] * h, [leanY] * h) from its feet.
 */
public object ShadowMath {
    /** How much shorter a distance on the ground looks going up the screen than going across it. */
    public const val GROUND_SQUASH: Float = 0.5f

    /** The longest a shadow gets, as a multiple of its thing's height (a sun 16 degrees above the horizon). */
    public const val MAX_LENGTH: Float = 3.5f

    /**
     * Things have a footprint and the ground is seen from a little above, so even a light level with a thing gives its shadow some thickness toward the viewer instead of a line.
     * A constant shift keeps it continuous as a light moves from in front of a thing to behind it.
     */
    public const val FOOTPRINT: Float = 0.14f

    /** The lowest a sun or moon is drawn: 8 degrees, so even a rising sun gives a long but finite shadow. */
    public const val MIN_ELEVATION: Float = 0.14f

    /**
     * A light so far away its rays are parallel: the sun, the moon. The shadow falls toward [azimuth] (radians on the ground: 0 to the right, a quarter turn toward the viewer),
     * and is 1 / tan([elevation]) times as long as the thing is tall: long with the light low, short with it high.
     */
    public fun directional(azimuth: Float, elevation: Float, size: Float, into: ShadowCast) {
        val k = (1f / tan(elevation.coerceIn(MIN_ELEVATION, 1.5f))).coerceIn(0.05f, MAX_LENGTH)
        into.set(cos(azimuth) * k, sin(azimuth) * k * GROUND_SQUASH + FOOTPRINT, spread(size, k))
    }

    /**
     * A light at one place ([lightX], [lightY] on the ground, [lightHeight] above it) and a thing standing at ([feetX], [feetY]) that is [objectHeight] tall. The shadow falls straight away
     * from the light and is longer the nearer the light is to the thing and the lower it hangs; a light overhead gives a short shadow just under the thing. Measured at the thing's middle,
     * since a shadow from a near light grows faster toward its tip than a straight line says.
     */
    public fun point(lightX: Float, lightY: Float, lightHeight: Float, feetX: Float, feetY: Float, objectHeight: Float, size: Float, into: ShadowCast) {
        val gx = feetX - lightX
        val gy = (feetY - lightY) / GROUND_SQUASH           // back to distances on the ground
        val distance = hypot(gx, gy)
        val dx = if (distance < 1f) 0f else gx / distance
        val dy = if (distance < 1f) 1f else gy / distance
        // The light must be above the middle of the thing for its shadow to reach the ground at all; if it is not, the shadow is as long as it can be.
        val above = lightHeight - 0.5f * objectHeight
        val k = if (above <= 0f) MAX_LENGTH else (distance / above).coerceIn(0.05f, MAX_LENGTH)
        into.set(dx * k, dy * k * GROUND_SQUASH + FOOTPRINT, spread(size, k))
    }

    /** The direction (radians on the ground) a light at ([lightX], [lightY]) throws shadows over a thing at ([feetX], [feetY]). */
    public fun azimuthFrom(lightX: Float, lightY: Float, feetX: Float, feetY: Float): Float =
        atan2((feetY - lightY) / GROUND_SQUASH, feetX - lightX)

    /** The edge of a shadow gets softer the bigger the light is and the longer the shadow (far from the thing, it blurs). */
    private fun spread(size: Float, k: Float): Float = size * (0.05f + 0.07f * k)
}

/** One shadow's shape, reused so nothing is allocated while a scene draws. Written by [ShadowMath]. */
public class ShadowCast {
    /** Pixels sideways per pixel of height. */
    public var leanX: Float = 0f
        private set

    /** Pixels down the screen per pixel of height (negative: the shadow falls away, up the screen). */
    public var leanY: Float = 0f
        private set

    /** How soft the edge is, as a share of the thing's size. */
    public var spread: Float = 0f
        private set

    /** How dark, 0 to 1, set by whoever combines several lights. */
    public var alpha: Float = 0f

    public fun set(leanX: Float, leanY: Float, spread: Float) { this.leanX = leanX; this.leanY = leanY; this.spread = spread }

    /** How far the tip of the shadow of a thing [height] tall is from its feet, in pixels. */
    public fun length(height: Float): Float = hypot(leanX, leanY) * height
}
