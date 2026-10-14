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

    /** How much bigger a shadow on a wall is allowed to be than the thing (a light almost level with the thing, a wall far behind it). */
    public const val MAX_WALL_SCALE: Float = 3.2f

    /** The lowest a sun or moon is drawn: 8 degrees, so even a rising sun gives a long but finite shadow. */
    public const val MIN_ELEVATION: Float = 0.14f

    /**
     * A light so far away its rays are parallel: the sun, the moon. The shadow falls toward [azimuth] (radians on the ground: 0 to the right, a quarter turn toward the viewer),
     * and is 1 / tan([elevation]) times as long as the thing is tall: long with the light low, short with it high.
     */
    public fun directional(azimuth: Float, elevation: Float, size: Float, into: ShadowCast) {
        val k = (1f / tan(elevation.coerceIn(MIN_ELEVATION, 1.5f))).coerceIn(0.05f, MAX_LENGTH)
        into.set(cos(azimuth) * k, sin(azimuth) * k * GROUND_SQUASH + FOOTPRINT, spread(size, k))
        into.clearProjective()
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
        into.setProjective(lightX - feetX, lightY - feetY, lightHeight)
    }

    /**
     * The shadow of an upright thing standing at ([feetX], [feetY]) falling on a **wall**: a vertical surface that faces the viewer and stands on the ground along the screen line [wallFootY]
     * (a back wall, or the face of a crate). The light is a point at ([lightX], [lightY]) on the ground, [lightHeight] above it.
     *
     * Where the light is nearer the viewer than the thing, its rays run on past the thing to the wall and the shadow lands there: the thing's outline, enlarged by how much farther the wall is
     * from the light than the thing is ([WallCast.scale], 1 for a thing against the wall, 2 for a wall twice as far as the thing), standing on a floor of its own that is [WallCast.footLift] pixels
     * above the wall's foot (negative: below the foot, so only its upper part shows). Returns false when there is no shadow on this wall: the thing is behind it, or the light is not in front of the thing.
     */
    public fun onWallFromPoint(lightX: Float, lightY: Float, lightHeight: Float, feetX: Float, feetY: Float, wallFootY: Float, size: Float, into: WallCast, maxScale: Float = MAX_WALL_SCALE): Boolean {
        val ly = lightY / GROUND_SQUASH; val py = feetY / GROUND_SQUASH; val wy = wallFootY / GROUND_SQUASH
        // The thing has to be between the light and the wall: the light nearer the viewer than the thing and the wall behind it (a back wall), or the light behind the thing and the wall in front of it (a screen
        // lit from behind, as in a shadow theatre). Either way the shadow is the outline enlarged by how much farther the wall is from the light than the thing.
        val between = (py - wy > 0.5f && ly - py > 0.5f) || (wy - py > 0.5f && py - ly > 0.5f)
        if (!between) return false
        val t = (wy - ly) / (py - ly)                                   // how far along the ray from the light the wall is, in units of the way to the thing
        if (t <= 1f) return false
        val scale = t.coerceAtMost(maxScale)
        into.set(lightX + scale * (feetX - lightX), lightHeight * (1f - scale), scale, size * (0.05f + 0.05f * scale))
        return true
    }

    /**
     * As [onWallFromPoint] for a light so far away its rays are parallel ([azimuth] the way shadows fall along the ground, [elevation] how high it is): the shadow is the same size as the thing,
     * moved along the ray: sideways and down by how far the wall is from the thing. Only a light that shines toward the wall (shadows falling away from the viewer) puts shadows on it.
     */
    public fun onWallFromDirection(azimuth: Float, elevation: Float, feetX: Float, feetY: Float, wallFootY: Float, size: Float, into: WallCast): Boolean {
        val dirY = sin(azimuth)
        val py = feetY / GROUND_SQUASH; val wy = wallFootY / GROUND_SQUASH
        if (dirY > -0.05f && dirY < 0.05f) return false
        val along = (wy - py) / dirY
        if (along <= 0.5f) return false                                    // how far along the ground the ray runs before it reaches the wall
        val e = elevation.coerceIn(MIN_ELEVATION, 1.5f)
        into.set(feetX + along * cos(azimuth), -along * tan(e), 1f, size * 0.08f)
        return true
    }

    /**
     * The height on a standing thing, in pixels, above which its shadow no longer lands on the floor in front of the wall along [wallFootY] but on the wall: where the ray from the light past the thing
     * reaches the floor exactly at the wall's foot. The floor shadow of everything below it and the wall shadow of everything above it meet there, with no gap and no overlap. Infinite if no wall shadow.
     */
    public fun wallCrossing(lightY: Float, lightHeight: Float, feetY: Float, wallFootY: Float): Float {
        val ly = lightY / GROUND_SQUASH; val py = feetY / GROUND_SQUASH; val wy = wallFootY / GROUND_SQUASH
        if (py <= wy || ly <= py) return Float.POSITIVE_INFINITY
        val sw = (wy - py) / (py - ly)                                   // how far past the thing, in units of the way from the light to it
        return lightHeight * sw / (1f + sw)
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

    /**
     * Whether the shadow is the exact one of a light at a place (a perspective projection, see [floorPoint]) and not the shear of a light far away. A point light's shadow of a standing thing is not
     * a straight lean: the nearer the thing's tip is to the light's height, the farther along the floor it lands, so the shadow stretches toward its tip.
     */
    public var projective: Boolean = false
        private set

    /** Where the light is relative to the thing's feet, in screen pixels (valid when [projective]), and how high it is above the ground. */
    public var lightDX: Float = 0f
        private set
    public var lightDY: Float = 0f
        private set
    public var lightHeight: Float = 0f
        private set

    public fun set(leanX: Float, leanY: Float, spread: Float) { this.leanX = leanX; this.leanY = leanY; this.spread = spread }

    internal fun setProjective(lightDX: Float, lightDY: Float, lightHeight: Float) { projective = true; this.lightDX = lightDX; this.lightDY = lightDY; this.lightHeight = lightHeight }

    internal fun clearProjective() { projective = false }

    /** Copies another shadow's shape (not its alpha). */
    public fun copyShapeFrom(o: ShadowCast) {
        set(o.leanX, o.leanY, o.spread)
        if (o.projective) setProjective(o.lightDX, o.lightDY, o.lightHeight) else clearProjective()
    }

    /**
     * Where the part of a standing thing at sideways offset [u] from its centre and height [z] throws its shadow on the floor, relative to the thing's feet, written to [into] as (x, y) in screen pixels.
     * For a point light that is the light's ray through the point continued to the floor: it lands `lightHeight / (lightHeight - z)` times as far from the light's foot as the point's own foot does,
     * which stretches toward the tip and is why a tall thing beside a low lamp has a very long shadow. Valid for `z` below the light's height; for a far-away light it is the straight lean.
     */
    public fun floorPoint(u: Float, z: Float, into: FloatArray) {
        if (!projective) { into[0] = u + leanX * z; into[1] = leanY * z; return }
        val m = lightHeight / (lightHeight - z)
        into[0] = lightDX + (u - lightDX) * m
        into[1] = lightDY * (1f - m)
    }

    /** How far the tip of the shadow of a thing [height] tall is from its feet, in pixels. */
    public fun length(height: Float): Float = hypot(leanX, leanY) * height
}

/** A shadow on a wall, reused so nothing is allocated while a scene draws. Written by [ShadowMath.onWallFromPoint] and [ShadowMath.onWallFromDirection]. */
public class WallCast {
    /** Where the thing's centre line lands on the wall, in screen x. */
    public var x: Float = 0f
        private set

    /** How high above the wall's foot the shadow's own foot lands, in pixels (negative: below it, so only the upper part of the shadow shows). */
    public var footLift: Float = 0f
        private set

    /** How much the thing's outline is enlarged. */
    public var scale: Float = 1f
        private set

    /** How soft the edge is, as a share of the thing's size. */
    public var spread: Float = 0f
        private set

    /** How dark, 0 to 1, set by whoever combines several lights. */
    public var alpha: Float = 0f

    public fun set(x: Float, footLift: Float, scale: Float, spread: Float) { this.x = x; this.footLift = footLift; this.scale = scale; this.spread = spread }
}
