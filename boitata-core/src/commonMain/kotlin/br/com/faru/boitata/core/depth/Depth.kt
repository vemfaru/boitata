package br.com.faru.boitata.core.depth

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Where a thing at a depth stands among a scene's layers: which of them are drawn over it. */
public enum class DepthSlot {
    /** Farther than the mid trees: drawn before them, so they hide it. */
    Behind,

    /** Between the mid trees and the near foliage: the mid trees are behind it, the foliage in front. */
    Between,

    /** Nearer than the near foliage: drawn last, over everything. */
    InFront,
}

/**
 * Depth as a single number: **0 is the nearest (in the viewer's face), 1 the horizon.** Everything that follows a thing through the layers reads it:
 * what size it is, how faded by the air, which layer slot it is drawn in, and where on the ground it stands.
 */
public object Depth {
    /** Beyond this (nearer to the horizon) a thing is behind the mid trees. */
    public const val BEHIND_FROM: Float = 0.62f

    /** Nearer than this a thing is in front of the near foliage. */
    public const val IN_FRONT_UNTIL: Float = 0.24f

    public fun slot(z: Float): DepthSlot = when {
        z >= BEHIND_FROM -> DepthSlot.Behind
        z <= IN_FRONT_UNTIL -> DepthSlot.InFront
        else -> DepthSlot.Between
    }

    /** How big a thing is at depth [z], against one at the middle (z = 0.5): about 2.4 times at the front, 0.42 at the horizon. Smooth, and always falling. */
    public fun scale(z: Float): Float = exp(SCALE_RATE * (0.5f - z.coerceIn(0f, 1f)))

    /** How much the air has taken from a thing at depth [z]: 1 up close, falling to about [FAR_VISIBILITY] at the horizon. */
    public fun visibility(z: Float): Float {
        val d = z.coerceIn(0f, 1f)
        return 1f - (1f - FAR_VISIBILITY) * d * d
    }

    /**
     * A path that swings from the front to the horizon and back, once every [period] seconds: z for the head at time [t]. A thing that follows it passes through every
     * slot, in and out. [phase] shifts where in the cycle it is.
     */
    public fun swing(t: Float, period: Float = 22f, phase: Float = 0f): Float = 0.5f - 0.5f * cos(2f * PI.toFloat() * (t / period + phase))

    /** The side-to-side drift that goes with [swing]: a slow S, -1..1. */
    public fun sway(t: Float, period: Float = 22f, phase: Float = 0f): Float =
        0.75f * sin(2f * PI.toFloat() * (t / period * 1.5f + phase)) + 0.25f * sin(2f * PI.toFloat() * (t / period * 4f + phase))

    private const val SCALE_RATE = 1.75f
    private const val FAR_VISIBILITY = 0.4f
}
