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

    /** How thick the liquid was when it spilled (0..1); the fluid's, thinned by the heat it had. */
    public var viscosity: Float = fluid.viscosity

    /** How thick the settled film is, in meters. */
    public val thickness: Float get() = 0.003f + 0.003f * viscosity

    /** The radius it is spreading toward. */
    public val settledRadius: Float get() = sqrt((volume * (1f - burnt)).coerceAtLeast(0f) / (PI.toFloat() * thickness))

    /** More liquid lands in it ([volume] in m^3); a splash of it first wets a spot a few millimetres across. */
    public fun add(volume: Float) { this.volume += volume; if (radius < 0.004f) radius = 0.004f }

    /** Takes [volume] away (it ran off an edge, it soaked in). */
    public fun remove(volume: Float) { this.volume = (this.volume - volume).coerceAtLeast(0f) }

    /** Spreads it on by [dt] seconds. */
    public fun step(dt: Float) {
        val rate = 7f / (1f + 9f * viscosity)
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

/**
 * Drops falling off an edge: each leaves a point with a little [volume] and falls under gravity until it reaches the floor, where it hands its liquid to [Drips.step]'s `onLand`. A fixed-size pool, nothing allocated while it runs.
 * Positions are in the scene's pixels; [gravity] is in pixels per second squared.
 */
public class Drips(private val capacity: Int = 64) {
    public val x: FloatArray = FloatArray(capacity)
    public val y: FloatArray = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val volume = FloatArray(capacity)
    private val tags = arrayOfNulls<Any>(capacity)
    public var count: Int = 0
        private set

    /** A drop leaves the edge at ([x], [y]) carrying [volume] (m^3) of whatever [tag] says (the fluid, say), and it comes back to [step]'s `onLand`. */
    public fun emit(x: Float, y: Float, volume: Float, tag: Any? = null) {
        if (count >= capacity) return
        this.x[count] = x; this.y[count] = y; vy[count] = 0f; this.volume[count] = volume; tags[count] = tag; count++
    }

    /** Takes every drop away. */
    public fun clear() { count = 0 }

    /** Lets everything fall for [dt] seconds; a drop that reaches [floorY] is removed and reported with where it landed and how much it carried. */
    public fun step(dt: Float, floorY: Float, gravity: Float, onLand: (x: Float, volume: Float, tag: Any?) -> Unit) {
        var i = 0
        while (i < count) {
            vy[i] += gravity * dt
            y[i] += vy[i] * dt
            if (y[i] >= floorY) {
                onLand(x[i], volume[i], tags[i])
                count--; x[i] = x[count]; y[i] = y[count]; vy[i] = vy[count]; volume[i] = volume[count]; tags[i] = tags[count]; tags[count] = null
                continue
            }
            i++
        }
    }
}
