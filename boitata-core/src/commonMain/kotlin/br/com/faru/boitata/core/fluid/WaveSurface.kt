package br.com.faru.boitata.core.fluid

import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * The surface of a liquid in a container, as a row of columns that rise and fall: the one-dimensional wave equation with damping, the thing that makes a ripple travel, bounce off the walls and die away, and makes the
 * liquid slosh when the container is pushed. Plain arrays, a fixed step, no allocation after construction.
 *
 * Positions across the container are shares from 0 (the far wall) to 1 (the near wall); heights are in the container's width units (0.05 is a 5%-of-the-width bump). The volume is conserved: the mean height stays zero.
 */
public class WaveSurface(public val columns: Int = 32) {
    public val height: FloatArray = FloatArray(columns)
    private val velocity = FloatArray(columns)

    /** How fast a ripple travels, in container widths per second: `sqrt(g * depth) / width`. */
    public var waveSpeed: Float = 6f

    /** How fast the sloshing and the ripples die away, per second. */
    public var damping: Float = 1.2f

    /** The frequency of the whole liquid rocking from side to side, in radians per second: how stiffly it is pulled back toward level. */
    public var sloshOmega: Float = 14f

    /** Sets the speed and the slosh from the real size: a container [widthMeters] across holding liquid [depthMeters] deep (about 1.5 Hz for a bucket, 3 Hz for a glass). */
    public fun fit(widthMeters: Float, depthMeters: Float) {
        val g = 9.81f
        waveSpeed = sqrt(g * depthMeters.coerceAtLeast(0.005f)) / widthMeters
        sloshOmega = sqrt(PI.toFloat() * g / widthMeters * tanh(PI.toFloat() * depthMeters / widthMeters))
        damping = 0.9f + 0.015f / widthMeters / 10f
    }

    /** A sudden bump at [at] (0..1): a drop landing (positive pushes the surface up, negative down), [width] wide as a share of the container. The volume is conserved by the next step. */
    public fun impulse(at: Float, amount: Float, width: Float = 0.08f) {
        val x0 = at.coerceIn(0f, 1f) * (columns - 1)
        val w = (width * (columns - 1)).coerceAtLeast(1f)
        for (i in 0 until columns) { val d = (i - x0) / w; velocity[i] += amount * kotlin.math.exp(-d * d) }
    }

    /** Moves the surface on by [dt] seconds. [tilt] is the slope the liquid is pulled toward (the container accelerating sideways, or tipped: the surface wants to lie at this slope), as height change per container width. */
    public fun step(dt: Float, tilt: Float = 0f) {
        val dx = 1f / (columns - 1)
        val c2 = waveSpeed * waveSpeed
        // Substeps so a ripple never crosses more than half a column in one.
        val subs = (waveSpeed * dt / dx / 0.5f).toInt().coerceIn(1, 24)
        val h = dt / subs
        val k = sloshOmega * sloshOmega
        repeat(subs) {
            for (i in 0 until columns) {
                val l = height[if (i == 0) 0 else i - 1]; val r = height[if (i == columns - 1) i else i + 1]
                val lap = (l - 2f * height[i] + r) / (dx * dx)
                val target = tilt * (i * dx - 0.5f)
                velocity[i] += (c2 * lap * dx * dx * 0.2f + k * (target - height[i]) * 0.3f) * h
            }
            val keep = 1f - (damping * h).coerceAtMost(0.5f)
            var mean = 0f
            for (i in 0 until columns) { velocity[i] *= keep; height[i] += velocity[i] * h; mean += height[i] }
            mean = mean / columns - tilt * 0f
            for (i in 0 until columns) height[i] -= mean
        }
    }

    /** The surface height at [x] (0..1), interpolated. */
    public fun heightAt(x: Float): Float {
        val u = x.coerceIn(0f, 1f) * (columns - 1)
        val i = u.toInt().coerceAtMost(columns - 2); val f = u - i
        return height[i] + (height[i + 1] - height[i]) * f
    }

    /** How much the surface is moving: the sum of the speeds, about 0 when it is still. */
    public fun energy(): Float { var e = 0f; for (v in velocity) e += v * v; return e }

    public fun reset() { height.fill(0f); velocity.fill(0f) }
}
