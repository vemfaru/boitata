package br.com.faru.boitata.core.fluid

import kotlin.math.cos
import kotlin.math.sin

/**
 * The geometry of a container with liquid in it as it tips over, in two dimensions (the cross-section of a glass, a bottle, a jug).
 *
 * The container is a rectangle [width] across and [height] tall, tipping about the bottom corner on the side it falls toward, by [angle] radians (0 upright, a quarter turn lying on its side). Everything is measured in the
 * world with the pivot corner at the origin, x along the floor toward the way it falls and z up. The liquid keeps a level surface whatever the container does, so what matters is how much liquid the tilted container can
 * hold without it running over the lowest part of its rim: [capacity]. Anything above that spills.
 */
public object Tilt {
    /** The four corners in the world, as x0, z0, x1, z1, ...: bottom far, bottom (the pivot), top near (the lip side), top far. */
    public fun corners(width: Float, height: Float, angle: Float, into: FloatArray = FloatArray(8)): FloatArray {
        val c = cos(angle); val s = sin(angle)
        // Container frame (x across from the far wall, y up) relative to the pivot at (width, 0), turned clockwise by [angle].
        fun put(i: Int, px: Float, py: Float) { into[i * 2] = px * c + py * s; into[i * 2 + 1] = -px * s + py * c }
        put(0, -width, 0f); put(1, 0f, 0f); put(2, 0f, height); put(3, -width, height)
        return into
    }

    /** The area of the container's cross-section below the world height [z] (the liquid of a level surface at [z]). */
    public fun areaBelow(width: Float, height: Float, angle: Float, z: Float): Float {
        val p = corners(width, height, angle)
        // Clip the quadrilateral to z' <= z (Sutherland-Hodgman against one line), then the shoelace formula.
        val out = FloatArray(20); var n = 0
        for (i in 0 until 4) {
            val ax = p[i * 2]; val az = p[i * 2 + 1]; val j = (i + 1) % 4; val bx = p[j * 2]; val bz = p[j * 2 + 1]
            val aIn = az <= z; val bIn = bz <= z
            if (aIn) { out[n * 2] = ax; out[n * 2 + 1] = az; n++ }
            if (aIn != bIn) { val t = (z - az) / (bz - az); out[n * 2] = ax + t * (bx - ax); out[n * 2 + 1] = z; n++ }
        }
        var area = 0f
        for (i in 0 until n) { val j = (i + 1) % n; area += out[i * 2] * out[j * 2 + 1] - out[j * 2] * out[i * 2 + 1] }
        return kotlin.math.abs(area) / 2f
    }

    /** The world height of the lowest point of the rim, where liquid first runs over: the near top corner while it is tipping toward it. */
    public fun lipHeight(width: Float, height: Float, angle: Float): Float {
        val p = corners(width, height, angle)
        return minOf(p[5], p[7])
    }

    /** The world x of that lowest point of the rim. */
    public fun lipX(width: Float, height: Float, angle: Float): Float {
        val p = corners(width, height, angle)
        return if (p[5] <= p[7]) p[4] else p[6]
    }

    /** The most liquid, as an area, the container holds at this tilt without spilling over the lip. */
    public fun capacity(width: Float, height: Float, angle: Float): Float = areaBelow(width, height, angle, lipHeight(width, height, angle))

    /** The world height of the level surface of [volume] of liquid in the container at this tilt (the surface is at the lip if it is full). */
    public fun levelFor(width: Float, height: Float, angle: Float, volume: Float): Float {
        val p = corners(width, height, angle)
        var lo = minOf(minOf(p[1], p[3]), minOf(p[5], p[7])); var hi = lipHeight(width, height, angle)
        if (volume >= areaBelow(width, height, angle, hi)) return hi
        repeat(24) { val mid = (lo + hi) / 2f; if (areaBelow(width, height, angle, mid) < volume) lo = mid else hi = mid }
        return (lo + hi) / 2f
    }

    /** How wide the surface is at world height [z], inside the container: the chord of the level line. */
    public fun surfaceWidth(width: Float, height: Float, angle: Float, z: Float): Float {
        val d = 0.001f * maxOf(width, height)
        return ((areaBelow(width, height, angle, z + d) - areaBelow(width, height, angle, z - d)) / (2f * d)).coerceAtLeast(0f)
    }
}
