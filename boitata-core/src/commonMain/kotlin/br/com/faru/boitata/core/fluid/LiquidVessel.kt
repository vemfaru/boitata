package br.com.faru.boitata.core.fluid

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A liquid, as the drawing and the simulation need to know it: its colour, how thick it is, how it bends light, and whether it burns.
 * [viscosity] 0 (water) to 1 (honey): thick liquids slosh less, ripple more slowly and spread slowly when spilled.
 */
public class Fluid(
    public val name: String,
    public val color: Color,
    public val viscosity: Float = 0f,
    /** The index of refraction: how much it magnifies and bends what is seen through a glass of it (water 1.33, oil 1.47). */
    public val index: Float = 1.33f,
    /** How cloudy it is, 0 clear to 1 opaque (milk). */
    public val turbidity: Float = 0f,
    /** Whether a puddle of it burns (kerosene, oil, spirit). */
    public val flammable: Boolean = false,
) {
    public companion object {
        public val Water: Fluid = Fluid("Water", Color(0xFF8FD0D8), 0f, 1.33f)
        public val Tea: Fluid = Fluid("Tea", Color(0xFFB8641E), 0.02f, 1.34f, turbidity = 0.2f)
        public val Wine: Fluid = Fluid("Wine", Color(0xFF7A1630), 0.04f, 1.35f, turbidity = 0.25f)
        public val Milk: Fluid = Fluid("Milk", Color(0xFFF1EDE2), 0.05f, 1.35f, turbidity = 0.95f)
        public val Oil: Fluid = Fluid("Oil", Color(0xFFD9A62B), 0.5f, 1.47f, turbidity = 0.1f, flammable = true)
        public val Kerosene: Fluid = Fluid("Kerosene", Color(0xFFE8D58A), 0.1f, 1.44f, turbidity = 0.05f, flammable = true)
    }
}

/**
 * A container with a liquid in it: knock it and it tips (see [TippingBody]), the liquid sloshes and ripples ([WaveSurface]), and when it tips far enough the liquid runs over the lip and is lost (spilled), at the rate
 * water goes over a weir (Torricelli: the stream leaves with speed `sqrt(2 g h)`, h the depth of liquid over the lip, and the flow is `Cd * (2/3) * sqrt(2 g) * h^1.5`). Volume is conserved: what leaves the container
 * is counted in [spilled].
 *
 * Sizes are in meters, so the physics (the tipping, the sloshing frequency, the speed of a ripple) is that of the real thing: a glass is a few centimetres, a bucket a few decimetres. Volumes are cross-section areas in m^2.
 */
public class LiquidVessel(
    public val width: Float,
    public val height: Float,
    public val fluid: Fluid = Fluid.Water,
    fill: Float = 0.6f,
) {
    public val body: TippingBody = TippingBody(width, height)
    public val surface: WaveSurface = WaveSurface(32)

    /** The liquid in it, as a cross-section area in m^2. */
    public var volume: Float = width * height * fill.coerceIn(0f, 1f)
        private set

    /** Everything that has run over the lip so far, in the same units. */
    public var spilled: Float = 0f
        private set

    /** How fast it is running over the lip now (area per second); 0 when nothing is. */
    public var spillRate: Float = 0f
        private set

    /** The speed the stream leaves the lip, in m/s. */
    public var jetSpeed: Float = 0f
        private set

    private var lateral = 0f
    private var lastOmega = 0f

    /** How full it is, 0 to 1 of its upright capacity. */
    public val fill: Float get() = (volume / (width * height)).coerceIn(0f, 1f)

    /** Whether any liquid is left. */
    public val empty: Boolean get() = volume <= width * height * 0.003f

    /** Whether it is spilling right now. */
    public val spilling: Boolean get() = spillRate > 0f

    /** The world height, in meters above the floor at the container's corner, of the level surface at this tilt. */
    public val level: Float get() = Tilt.levelFor(width, height, abs(body.angle), volume)

    /** Adds [amount] of liquid (an area in m^2) from a pour: the level rises and a small ripple spreads from where the stream lands ([at], 0..1 across). Anything that does not fit runs over the lip as usual. */
    public fun pour(amount: Float, at: Float = 0.5f) {
        volume += amount
        surface.impulse(at, 0.15f * (amount / (width * height)).coerceAtMost(0.4f) + 0.01f, 0.1f)
    }

    /** Takes [amount] out (a sip, a drain). */
    public fun drain(amount: Float) { volume = (volume - amount).coerceAtLeast(0f) }

    /** Something falls in at [at] (0..1 across), [strength] 0..1: a drop, a stone, a splash ring spreads. */
    public fun drop(at: Float, strength: Float = 0.5f) { surface.impulse(at, -0.8f * strength, 0.06f); surface.impulse(at, 0.5f * strength, 0.12f) }

    /** Pushes the container sideways by an acceleration of [ax] m/s^2 (dragging it): the liquid lags behind and sloshes. */
    public fun accelerate(ax: Float) { lateral += ax }

    /** Puts the container back upright, still, and filled to [fill] (0..1): a refill, a fresh start. */
    public fun reset(fill: Float) {
        body.angle = 0f; body.omega = 0f; surface.reset(); spilled = 0f; spillRate = 0f; jetSpeed = 0f; lateral = 0f; lastOmega = 0f
        volume = width * height * fill.coerceIn(0f, 1f)
    }

    /** Knocks the container (see [TippingBody.knock]). */
    public fun knock(speed: Float, direction: Float = 1f) = body.knock(speed, direction)

    /** Moves everything on by [dt] seconds. */
    public fun step(dt: Float) {
        body.step(dt)
        // The liquid is pulled toward a surface that lies at the slope the sideways acceleration (and the container's own turning, felt at the middle of the liquid) says.
        val alpha = if (dt > 0f) (body.omega - lastOmega) / dt else 0f
        lastOmega = body.omega
        val a = lateral + alpha * height * 0.5f * fill
        surface.fit(width, (height * fill).coerceAtLeast(0.004f))
        surface.damping *= 1f + 3f * fluid.viscosity
        surface.step(dt, tilt = (-a / 9.81f).coerceIn(-0.6f, 0.6f))
        lateral = 0f

        // Spilling: whatever is above what the tilted container can hold runs over the lip, at the rate of a weir.
        val angle = abs(body.angle)
        val capacity = Tilt.capacity(width, height, angle)
        val excess = volume - capacity
        if (excess > 1e-9f) {
            val lip = Tilt.lipHeight(width, height, angle)
            val chord = Tilt.surfaceWidth(width, height, angle, lip).coerceAtLeast(width * 0.12f)
            val head = excess / chord
            val flow = 0.6f * (2f / 3f) * sqrt(2f * 9.81f) * head.pow(1.5f) / (1f + 4f * fluid.viscosity)
            val out = minOf(excess, flow * dt)
            volume -= out; spilled += out
            spillRate = if (dt > 0f) out / dt else 0f
            jetSpeed = sqrt(2f * 9.81f * head)
            // A little of what runs out stirs what is left.
            surface.impulse(0.9f, 0.01f, 0.1f)
            if (volume <= width * height * 0.003f) { spilled += volume; volume = 0f }
        } else { spillRate = 0f; jetSpeed = 0f }
    }

    /** The world x, in meters from the corner it tips on, and height of the lip the liquid runs over. */
    public fun lipX(): Float = Tilt.lipX(width, height, abs(body.angle))
    public fun lipHeight(): Float = Tilt.lipHeight(width, height, abs(body.angle))
}
