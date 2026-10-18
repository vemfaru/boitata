package br.com.faru.boitata.core.fluid

import androidx.compose.ui.graphics.Color
import br.com.faru.boitata.core.stain.VesselBase
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A liquid, as the drawing and the simulation need to know it. Every number can be set, and [copy] makes a variation of any preset (a thicker coffee, a heavier wine):
 *
 * - [viscosity], 0 (water) to 1 (honey): how thick it is. Thick liquids slosh less, spill slowly and spread slowly into a thicker puddle. Heat thins it (see `LiquidVessel.effectiveViscosity`).
 * - [density], in kg/m^3 (water 1000, kerosene 800, mercury 13534): how heavy it is. It is what weighs on a container: a dense liquid lowers the centre of mass of a glass, so it is harder to knock over, and it is what
 *   things float or sink in.
 * - [index], the index of refraction: how much it magnifies and bends what is seen through a glass of it (water 1.33, oil 1.47).
 * - [turbidity], 0 clear to 1 opaque (milk).
 * - [flammable]: whether a puddle of it burns.
 * - [toxicity], 0 harmless to 1 deadly: how much it harms a living thing it is poured on (a plant's roots). Water 0, tea and coffee a little, wine and oil a lot, kerosene and mercury kill. A mix is the volume-weighted average.
 * - [potable]: whether it is safe to drink (water, tea, coffee, wine, milk, honey). A mix is only potable if everything in it is.
 */
public class Fluid(
    public val name: String,
    public val color: Color,
    public val viscosity: Float = 0f,
    public val index: Float = 1.33f,
    public val turbidity: Float = 0f,
    public val flammable: Boolean = false,
    public val density: Float = 1000f,
    public val potable: Boolean = false,
    public val toxicity: Float = 0f,
) {
    /**
     * What this and [other] make together, [thisVolume] of one and [otherVolume] of the other (any one unit): the colour, thickness, refraction, cloudiness and weight are averaged by volume, and it burns if either does.
     * Milk into coffee is a paler, cloudier coffee.  The name joins both (`Coffee + Milk`, the larger first).
     */
    public fun mix(other: Fluid, thisVolume: Float, otherVolume: Float): Fluid {
        val total = thisVolume + otherVolume
        if (this === other || otherVolume <= 0f || total <= 0f) return this
        val t = otherVolume / total
        if (t >= 1f) return other
        fun l(a: Float, b: Float) = a + (b - a) * t
        val major = if (t < 0.5f) this else other; val minor = if (t < 0.5f) other else this
        val label = when { major.name.contains(minor.name) -> major.name; minor.name.contains(major.name) -> minor.name; else -> "${major.name} + ${minor.name}" }
        return Fluid(label, androidx.compose.ui.graphics.lerp(color, other.color, t), l(viscosity, other.viscosity), l(index, other.index), l(turbidity, other.turbidity), flammable || other.flammable, l(density, other.density), potable && other.potable, l(toxicity, other.toxicity))
    }

    /** A variation on this liquid: whatever you give is changed, the rest is kept. */
    public fun copy(
        name: String = this.name, color: Color = this.color, viscosity: Float = this.viscosity, index: Float = this.index, turbidity: Float = this.turbidity,
        flammable: Boolean = this.flammable, density: Float = this.density, potable: Boolean = this.potable, toxicity: Float = this.toxicity,
    ): Fluid = Fluid(name, color, viscosity, index, turbidity, flammable, density, potable, toxicity)

    public companion object {
        public val Water: Fluid = Fluid("Water", Color(0xFF8FD0D8), 0f, 1.33f, density = 1000f, potable = true)
        public val Tea: Fluid = Fluid("Tea", Color(0xFFB8641E), 0.02f, 1.34f, turbidity = 0.2f, density = 1001f, potable = true, toxicity = 0.03f)
        public val Coffee: Fluid = Fluid("Coffee", Color(0xFF3B2316), 0.03f, 1.34f, turbidity = 0.92f, density = 1005f, potable = true, toxicity = 0.12f)
        public val Wine: Fluid = Fluid("Wine", Color(0xFF7A1630), 0.04f, 1.35f, turbidity = 0.25f, density = 990f, potable = true, toxicity = 0.45f)
        public val Milk: Fluid = Fluid("Milk", Color(0xFFF1EDE2), 0.05f, 1.35f, turbidity = 0.95f, density = 1030f, potable = true, toxicity = 0.15f)
        public val Oil: Fluid = Fluid("Oil", Color(0xFFD9A62B), 0.5f, 1.47f, turbidity = 0.1f, flammable = true, density = 920f, toxicity = 0.85f)
        public val Kerosene: Fluid = Fluid("Kerosene", Color(0xFFE8D58A), 0.1f, 1.44f, turbidity = 0.05f, flammable = true, density = 800f, toxicity = 1f)
        public val Honey: Fluid = Fluid("Honey", Color(0xFFD28A1B), 0.95f, 1.49f, turbidity = 0.15f, density = 1420f, potable = true, toxicity = 0.3f)
        public val Mercury: Fluid = Fluid("Mercury", Color(0xFFB9C0C8), 0.04f, 1.6f, turbidity = 1f, density = 13534f, toxicity = 1f)

        /** The liquids that come with the library. */
        public val All: List<Fluid> = listOf(Water, Tea, Coffee, Wine, Milk, Oil, Kerosene, Honey, Mercury)
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
    fluid: Fluid = Fluid.Water,
    fill: Float = 0.6f,
    /** What the empty container weighs, in kg (a glass about 0.25 kg): against the liquid's weight it decides how hard it is to knock over. */
    public val containerMass: Float = 0.12f + 18f * width * height,
    /** Which way it can be tipped to pour (see [PourSide]): a jug with its spout on the left only ever leans left. */
    public val pourSide: PourSide = PourSide.Either,
) {
    /** What is in it: change it to change the liquid (a pour of something else, a thicker coffee); the volume stays. */
    public var fluid: Fluid = fluid
    public val body: TippingBody = TippingBody(width, height).also { it.allowedSide = pourSide.sign }
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

    /** What clings to the lip after it has poured or spilled, as an area in m^2: beads form there and run off one at a time (see [takeDribbleDrop]). It comes out of [volume], so nothing is made. */
    public var dribble: Float = 0f
        private set
    private var dribbleTimer = 0f
    private var pendingDrop = 0f
    private var wasSpilling = false

    /** How much clings to the lip when a spill stops, as a share of the container's cross-section. */
    private val dribbleShare = 0.006f

    /** The drop that has just left the lip, as an area in m^2 (0 if none has since you last asked): the caller lets it fall, or run down the side. Ask once a step. */
    public fun takeDribbleDrop(): Float { val d = pendingDrop; pendingDrop = 0f; return d }

    /** The foot it stands on (see [VesselBase]); change it for a mug's ring foot or a bottle's whole base. */
    public var base: VesselBase = VesselBase(width * 0.45f)

    /** How wet the outside of the foot is, 0..1: it runs down the side when it spills, it comes from standing in a puddle, and a cold drink sweats a little. It dries on its own and is left behind on what it is set down on. */
    public var bottomWet: Float = 0f
        private set

    /** What the foot is wet with (the last thing that wet it). */
    public var bottomFluid: Fluid? = null
        private set

    /** Wets the foot with [amount] (0..1) of [with]: standing in a puddle, a splash. */
    public fun wetBottom(with: Fluid, amount: Float) {
        if (amount <= 0f) return
        bottomFluid = with
        bottomWet = (bottomWet + amount).coerceAtMost(1f)
    }

    /** Takes [amount] off the wet of the foot (it was left on a surface). */
    public fun dryBottom(amount: Float) { bottomWet = (bottomWet - amount).coerceAtLeast(0f) }

    /** How hot the liquid is, in degrees Celsius. It cools toward [roomTemp] (Newton's law: the hotter above the room, the faster) and mixes by volume when more is poured in. */
    public var temperature: Float = 20f
        private set

    /** The air temperature the liquid cools toward. */
    public var roomTemp: Float = 21f

    /** How fast it cools: the time, in seconds, in which it loses most of its heat (a cup of coffee takes twenty minutes in the world; a demo wants less). */
    public var coolingSeconds: Float = 240f

    /** A layer of foam or crema on the surface, 0..1 of a thin layer: made by pouring, it fades over a minute or so. */
    public var foam: Float = 0f
        private set

    /** The bubbles rising through the liquid, as share across (x) and share of the way up (y); [bubbles] of them are alive. */
    public val bubbleX: FloatArray = FloatArray(MAX_BUBBLES)
    public val bubbleY: FloatArray = FloatArray(MAX_BUBBLES)
    private val bubbleSpeed = FloatArray(MAX_BUBBLES)
    public var bubbles: Int = 0
        private set
    private var bubbleCarry = 0f
    private var bubbleSeed = 1

    /** How much it steams, 0 (cold) to 1 (scalding): visible from about 45 degrees. */
    public val steam: Float get() = ((temperature - 45f) / 50f).coerceIn(0f, 1f)

    /** How hard it is boiling, 0 below about 80 degrees to 1 at the boil: bubbles rise from the bottom and burst at the surface. */
    public val boil: Float get() = ((temperature - 80f) / 20f).coerceIn(0f, 1f)

    /** Sets the temperature, for something that starts hot (a kettle) or is heated (a flame under it). */
    public fun heatTo(degrees: Float) { temperature = degrees.coerceIn(-20f, 100f) }

    /** Heats the liquid by [amount] degrees (a flame under it, a hot plate): it cannot pass the boil. */
    public fun heat(amount: Float) { temperature = (temperature + amount).coerceAtMost(100f) }

    /** How much the liquid weighs, in kg: its density times its volume times the depth of the container (the cross-section is a slice as deep as it is wide). */
    public val liquidMass: Float get() = fluid.density * volume * width

    /** How thick it is right now: the fluid's viscosity, thinned by heat (it halves for every 25 degrees above 20, and thickens a little when cold). */
    public val effectiveViscosity: Float get() = (fluid.viscosity * kotlin.math.exp(-(temperature - 20f) / 36f)).coerceIn(0f, 1f)

    /** Something that is not liquid fills the bottom of it, as a cross-section area in m^2 (the soil in a pot): the liquid has only what is left above it. */
    public var solid: Float = 0f

    /** The height of the liquid's surface above the floor of the container's base when upright, in meters: what is under it ([solid]) and the liquid itself, spread over the width. */
    public val surfaceHeight: Float get() = (solid + volume) / width

    /** How full it is, 0 to 1 of its upright capacity (what is left over a [solid], if there is one). */
    public val fill: Float get() = (volume / (width * height - solid).coerceAtLeast(1e-9f)).coerceIn(0f, 1f)

    /** Whether any liquid is left. */
    public val empty: Boolean get() = volume <= width * height * 0.003f

    /** Whether it is spilling right now. */
    public val spilling: Boolean get() = spillRate > 0f

    /** The world height, in meters above the floor at the container's corner, of the level surface at this tilt. */
    public val level: Float get() = Tilt.levelFor(width, height, abs(body.angle), volume)

    /** How high a hand holds the container above the surface it stands on, in meters (a jar lifted over a mug to pour into it). */
    public var lift: Float = 0f

    /** The height, in meters above that surface, at which what runs over the lip lands (the level of the liquid in the mug it pours into); 0 when it falls to the surface. */
    public var landHeight: Float = 0f

    /** How far what runs over the lip falls before it lands, in meters. */
    public val fallHeight: Float get() = (lipHeight() + lift - landHeight).coerceAtLeast(0f)

    /**
     * [amount] of [incoming] liquid (an area in m^2) is poured in from another container: it mixes with what is there by volume ([Fluid.mix]: the colour, thickness and the rest), and the temperature too. Anything that
     * does not fit runs over the lip as usual. [at] is where across it lands (0..1).
     */
    public fun receive(incoming: Fluid, amount: Float, temperature: Float = this.temperature, at: Float = 0.5f) {
        if (amount <= 0f) return
        fluid = if (empty) incoming else fluid.mix(incoming, volume, amount)
        pour(amount, at, temperature)
    }

    /** Adds [amount] of liquid (an area in m^2) from a pour: the level rises and a small ripple spreads from where the stream lands ([at], 0..1 across). Anything that does not fit runs over the lip as usual. */
    public fun pour(amount: Float, at: Float = 0.5f, temperature: Float = this.temperature, foamy: Boolean = false) {
        // What is poured in mixes with what is there: the temperature is the average weighted by volume.
        if (volume + amount > 1e-9f) this.temperature = (this.temperature * volume + temperature * amount) / (volume + amount)
        if (foamy) foam = (foam + 0.7f).coerceAtMost(1f)
        volume += amount
        surface.impulse(at, 0.15f * (amount / (width * height)).coerceAtMost(0.4f) + 0.01f, 0.1f)
    }

    /** Whether there is something here to sip: not empty, and safe to drink. */
    public val sippable: Boolean get() = !empty && fluid.potable

    /** Takes [amount] out (a sip, a drain). */
    public fun drain(amount: Float) { volume = (volume - amount).coerceAtLeast(0f) }

    /**
     * Takes a mouthful: [share] of the container's capacity (a twelfth by default), or what is left if there is less. The level drops and the surface ripples a little. Returns how much was taken
     * (an area in m^2), 0 when it is empty. For now it only lowers the level: no tilt toward the mouth and no limit for a hot drink.
     */
    public fun sip(share: Float = 0.08f): Float {
        if (empty) return 0f
        val taken = minOf(volume, share.coerceAtLeast(0f) * width * height)
        drain(taken)
        surface.impulse(0.5f, -0.02f, 0.12f)
        return taken
    }

    /** Something falls in at [at] (0..1 across), [strength] 0..1: a drop, a stone, a splash ring spreads. */
    public fun drop(at: Float, strength: Float = 0.5f) { surface.impulse(at, -0.8f * strength, 0.06f); surface.impulse(at, 0.5f * strength, 0.12f) }

    /** Pushes the container sideways by an acceleration of [ax] m/s^2 (dragging it): the liquid lags behind and sloshes. */
    public fun accelerate(ax: Float) { lateral += ax }

    /** Puts the container back upright, still, and filled to [fill] (0..1): a refill, a fresh start. */
    public fun reset(fill: Float, temperature: Float = 20f) {
        this.temperature = temperature; foam = 0f; bubbles = 0
        bottomWet = 0f; bottomFluid = null; dribble = 0f; pendingDrop = 0f; wasSpilling = false; lift = 0f; landHeight = 0f; body.release()
        body.angle = 0f; body.omega = 0f; surface.reset(); spilled = 0f; spillRate = 0f; jetSpeed = 0f; lateral = 0f; lastOmega = 0f
        volume = width * height * fill.coerceIn(0f, 1f)
    }

    /** Knocks the container (see [TippingBody.knock]). */
    public fun knock(speed: Float, direction: Float = 1f) = body.knock(speed, direction)

    /** Moves everything on by [dt] seconds. */
    public fun step(dt: Float) {
        // What it weighs, container and liquid, decides how it tips: a heavy liquid lowers the centre of mass.
        body.setLoad(containerMass, liquidMass, if (width > 0f) volume / width else 0f)
        body.step(dt)
        // The liquid is pulled toward a surface that lies at the slope the sideways acceleration (and the container's own turning, felt at the middle of the liquid) says.
        val alpha = if (dt > 0f) (body.omega - lastOmega) / dt else 0f
        lastOmega = body.omega
        val a = lateral + alpha * height * 0.5f * fill
        surface.fit(width, (height * fill).coerceAtLeast(0.004f))
        surface.damping *= 1f + 3f * effectiveViscosity
        surface.step(dt, tilt = (-a / 9.81f).coerceIn(-0.6f, 0.6f))
        lateral = 0f

        // Cooling: toward the room's temperature, faster the hotter it is. Foam fades. Bubbles rise.
        temperature += (roomTemp - temperature) * (1f - kotlin.math.exp(-dt / coolingSeconds * 3f))
        foam = (foam - dt / 70f).coerceAtLeast(0f)
        moveBubbles(dt)
        // The foot dries; a drink much colder than the room sweats a little onto it.
        bottomWet = (bottomWet - dt / 25f).coerceAtLeast(0f)
        if (!empty && temperature < roomTemp - 8f && bottomWet < 0.25f) wetBottom(Fluid.Water, dt / 20f)

        // Spilling: whatever is above what the tilted container can hold runs over the lip, at the rate of a weir.
        val angle = abs(body.angle)
        val capacity = (Tilt.capacity(width, height, angle) - solid).coerceAtLeast(0f)
        val excess = volume - capacity
        if (excess > 1e-9f) {
            val lip = Tilt.lipHeight(width, height, angle)
            val chord = Tilt.surfaceWidth(width, height, angle, lip).coerceAtLeast(width * 0.12f)
            val head = excess / chord
            val flow = 0.6f * (2f / 3f) * sqrt(2f * 9.81f) * head.pow(1.5f) / (1f + 4f * effectiveViscosity)
            // A weir's flow dies away slowly as the level nears the brim; a real glass stops spilling once it is level, so the last film runs off at once.
            val thin = 1f / (1f + 4f * effectiveViscosity)
            val out = if (excess < width * height * 0.002f * thin) excess else minOf(excess, maxOf(flow, excess * 6f * thin) * dt)
            volume -= out; spilled += out
            // What runs over the lip runs down the outside, to the foot.
            wetBottom(fluid, out / (width * height) * 8f)
            spillRate = if (dt > 0f) out / dt else 0f
            jetSpeed = sqrt(2f * 9.81f * head)
            // A little of what runs out stirs what is left.
            surface.impulse(0.9f, 0.01f, 0.1f)
            if (volume <= width * height * 0.003f) { spilled += volume; volume = 0f }
        } else { spillRate = 0f; jetSpeed = 0f }

        // When a spill stops, a little clings to the lip; it forms beads that let go one after another.
        if (wasSpilling && spillRate <= 0f && volume > 0f) { val c = minOf(volume, dribbleShare * width * height); volume -= c; dribble += c; dribbleTimer = 0.25f }
        wasSpilling = spillRate > 0f
        if (dribble > 0f) {
            dribbleTimer -= dt
            if (dribbleTimer <= 0f) {
                val d = minOf(dribble, 0.0007f * width * height)
                dribble -= d; pendingDrop += d
                dribbleTimer = 0.35f + 0.25f * (1f - minOf(1f, dribble / (dribbleShare * width * height)))
                if (dribble < 1e-9f) dribble = 0f
            }
        }
    }

    private fun moveBubbles(dt: Float) {
        // New bubbles start at the bottom: many at the boil, a few simmering from 70 degrees. A bubble rises faster as it goes, and bursts at the surface, stirring it a little.
        val rate = if (empty) 0f else boil * 22f + (if (temperature > 70f) 2f else 0f) * ((temperature - 70f) / 10f).coerceIn(0f, 1f)
        bubbleCarry += rate * dt
        while (bubbleCarry >= 1f) {
            bubbleCarry -= 1f
            if (bubbles < MAX_BUBBLES) { bubbleSeed = bubbleSeed * 1103515245 + 12345; bubbleX[bubbles] = 0.12f + 0.76f * (((bubbleSeed ushr 8) and 0xFFFF) / 65535f); bubbleY[bubbles] = 0f; bubbleSpeed[bubbles] = 0.5f + 0.5f * (((bubbleSeed ushr 4) and 0xFF) / 255f); bubbles++ }
        }
        var i = 0
        while (i < bubbles) {
            bubbleY[i] += bubbleSpeed[i] * (0.4f + bubbleY[i]) * dt
            if (bubbleY[i] >= 1f) {
                surface.impulse(bubbleX[i], 0.012f, 0.04f)
                bubbles--; bubbleX[i] = bubbleX[bubbles]; bubbleY[i] = bubbleY[bubbles]; bubbleSpeed[i] = bubbleSpeed[bubbles]
                continue
            }
            i++
        }
    }

    /** The world x, in meters from the corner it tips on, and height of the lip the liquid runs over. */
    public fun lipX(): Float = Tilt.lipX(width, height, abs(body.angle))
    public fun lipHeight(): Float = Tilt.lipHeight(width, height, abs(body.angle))

    private companion object { const val MAX_BUBBLES = 40 }
}

/**
 * Drinks from a container while a mouth is held to it: a gulp ([LiquidVessel.sip]) at once and then one every [interval] seconds, for as long as you call [step] and there is something safe to drink.
 * [step] returns what was taken this step (an area in m^2, 0 between gulps).
 */
public class Sipper(private val interval: Float = 0.55f) {
    private var wait = 0f

    public fun step(vessel: LiquidVessel, dt: Float, share: Float = 0.08f): Float {
        if (!vessel.sippable) { wait = 0f; return 0f }
        wait -= dt
        if (wait > 0f) return 0f
        wait = interval
        return vessel.sip(share)
    }

    /** Starts afresh: the next [step] gulps at once. */
    public fun reset() { wait = 0f }
}
