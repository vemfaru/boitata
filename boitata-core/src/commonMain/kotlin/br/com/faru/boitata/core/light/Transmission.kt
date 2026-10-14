package br.com.faru.boitata.core.light

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * How much of a light reaches a point, given the things standing between: for each [Billboard] the ray crosses, the light that gets through is multiplied by `1 - opacity * coverage`, where coverage is how solid the thing
 * is at the spot the ray goes through (its [AlphaMask], or 1 for a box). A crate between the lamp and a person puts the person in its shadow (0); glass lets most through, tinted; paper lets half through; the gaps
 * in a lattice let the rest through. The things multiply, so light through two sheets of paper is a quarter.
 *
 * Pure arithmetic over a short list, no allocation: the result is written to a 4-element array `[level, red, green, blue]`: the share of the light that gets through, and the colour it comes out in.
 * Works in the same world as [ShadowMath]: the ground is seen at a slant, so screen y is [ShadowMath.GROUND_SQUASH] of a ground distance.
 */
public object Transmission {
    /** A light at ([lightX], [lightY]) on the ground, [lightHeight] up, and a point at ([px], [py]) on the ground, [pz] up. */
    public fun fromPoint(lightX: Float, lightY: Float, lightHeight: Float, px: Float, py: Float, pz: Float, things: List<Billboard>, self: Any?, into: FloatArray) {
        into[0] = 1f; into[1] = 1f; into[2] = 1f; into[3] = 1f
        val ly = lightY / ShadowMath.GROUND_SQUASH; val y = py / ShadowMath.GROUND_SQUASH
        val span = y - ly
        if (span > -0.001f && span < 0.001f) return
        for (b in things) {
            if (!b.blocksLight || (self != null && b.owner === self)) continue
            val by = b.footY / ShadowMath.GROUND_SQUASH
            // Where along the ray from the light to the point the thing's plane is: it must lie between them.
            val s = (by - ly) / span
            if (s <= 0.001f || s >= 0.999f) continue
            pass(b, lightX + s * (px - lightX), lightHeight + s * (pz - lightHeight), into)
            if (into[0] <= 0.001f) { into[0] = 0f; return }
        }
    }

    /** A light so far away its rays are parallel (shadows fall toward [azimuth] at [elevation]) and a point at ([px], [py]) on the ground, [pz] up. */
    public fun fromDirection(azimuth: Float, elevation: Float, px: Float, py: Float, pz: Float, things: List<Billboard>, self: Any?, into: FloatArray) {
        into[0] = 1f; into[1] = 1f; into[2] = 1f; into[3] = 1f
        val dirY = sin(azimuth); val dirX = cos(azimuth)
        if (dirY > -0.001f && dirY < 0.001f) return
        val y = py / ShadowMath.GROUND_SQUASH
        val rise = tan(elevation.coerceIn(ShadowMath.MIN_ELEVATION, 1.5f))
        for (b in things) {
            if (!b.blocksLight || (self != null && b.owner === self)) continue
            val by = b.footY / ShadowMath.GROUND_SQUASH
            // Travelling from the point toward the light, backwards along the way shadows fall, how far along the ground the thing's plane is.
            val d = (y - by) / dirY
            if (d <= 0.001f) continue
            pass(b, px - d * dirX, pz + d * rise, into)
            if (into[0] <= 0.001f) { into[0] = 0f; return }
        }
    }

    /** Applies what [b] does to a ray that crosses its plane at screen x [x] and height [z] above the ground. */
    private fun pass(b: Billboard, x: Float, z: Float, into: FloatArray) {
        if (x < b.left || x > b.right || z < 0f || z > b.height) return
        val m = b.mask
        val cover = if (m == null) 1f else m.coverage((x - b.left) / (b.right - b.left), z / b.height)
        if (cover <= 0.001f) return
        val o = b.optics
        val stopped = (o.opacity * cover).coerceIn(0f, 1f)
        into[0] *= 1f - stopped
        if (o.translucent) {
            // What passes is coloured by the thing, as much as the ray was inside it.
            into[1] *= 1f + (o.tint.red - 1f) * cover; into[2] *= 1f + (o.tint.green - 1f) * cover; into[3] *= 1f + (o.tint.blue - 1f) * cover
        }
    }
}
