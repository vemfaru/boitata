package br.com.faru.boitata.core.fx

import kotlin.math.floor

/**
 * Time as a repeating 0..1 phase: how far through its life something that lives [period] seconds, born at [phase] (0..1), is at [seconds].
 * Rain drops, embers and flakes are all `cycle`s, so any moment can be computed without keeping state, and a frame is the same however it is reached.
 */
public fun cycle(seconds: Float, period: Float, phase: Float = 0f): Float {
    val x = seconds / period + phase
    return x - floor(x)
}

/** A smooth 0..1..0 bump over a life of 0..1: fades a particle in, then out. */
public fun bump(life: Float): Float {
    val x = (life * 2f - 1f)
    return 1f - x * x
}
