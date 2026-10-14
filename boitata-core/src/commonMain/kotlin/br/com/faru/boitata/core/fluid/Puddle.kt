package br.com.faru.boitata.core.fluid

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * A puddle of spilled liquid on a flat surface. It spreads until it is a thin film: how thin depends on the liquid (water stays about 3 mm thick, being held by surface tension; a thick oil stands 6 mm), so its
 * radius when it has settled is `sqrt(volume / (PI * thickness))`: a glassful makes a puddle 15 cm across. It gets there fast at first and slows (an exponential approach, slower the more viscous the liquid).
 * [volume] is in m^3; [burnt] (0..1) is how much of a burning puddle has been used up.
 */
public class Puddle(
    /** Where it lies, in the scene's units along the surface. */
    public var x: Float,
    public val fluid: Fluid,
    volume: Float = 0f,
) {
    public var volume: Float = volume
        private set

    /** How big it is now, in meters. */
    public var radius: Float = 0f
        private set

    /** How much of it has burnt away, 0..1. */
    public var burnt: Float = 0f

    /** How thick the settled film is, in meters. */
    public val thickness: Float get() = 0.003f + 0.003f * fluid.viscosity

    /** The radius it is spreading toward. */
    public val settledRadius: Float get() = sqrt((volume * (1f - burnt)).coerceAtLeast(0f) / (PI.toFloat() * thickness))

    /** More liquid lands in it ([volume] in m^3); a splash of it first wets a spot a few millimetres across. */
    public fun add(volume: Float) { this.volume += volume; if (radius < 0.004f) radius = 0.004f }

    /** Spreads it on by [dt] seconds. */
    public fun step(dt: Float) {
        val rate = 7f / (1f + 9f * fluid.viscosity)
        radius += (settledRadius - radius) * (1f - exp(-rate * dt))
        if (radius < 0f) radius = 0f
    }
}

/** Where a stream that runs over a lip lands. */
public object Spill {
    private const val G = 9.81f

    /** How long a stream that leaves a lip at height [lipHeight] above the surface falls before it lands: `sqrt(2 h / g)`. 0 if the lip is at the surface (it just runs out). */
    public fun fallTime(lipHeight: Float): Float = if (lipHeight <= 0f) 0f else sqrt(2f * lipHeight / G)

    /** How far out from the lip a stream that leaves it horizontally at [speed] m/s lands: `speed * fallTime`. */
    public fun reach(lipHeight: Float, speed: Float): Float = speed * fallTime(lipHeight)

    /** How thick the stream is, in meters (for a flow of [rate] m^2/s leaving at [speed]: `rate / speed`, the thickness that carries that much liquid at that speed). */
    public fun thickness(rate: Float, speed: Float): Float = if (speed <= 0.05f) 0f else rate / speed
}
