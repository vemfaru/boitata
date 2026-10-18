package br.com.faru.boitata.core.stain

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import br.com.faru.boitata.core.fluid.Fluid
import br.com.faru.boitata.core.fluid.LiquidVessel
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/**
 * What a surface does with a wet container set down on it:
 *
 * - [porosity], 0..1: how much of the wet soaks in and marks it (bare wood takes a lot, glaze and metal almost none).
 * - [permanence], 0..1: how much of a mark made by something that colours (coffee, wine: see [Fluid.turbidity]) never dries away. Clear water leaves a ring that fades whatever the surface.
 * - [dryRate]: how fast the part that is only wet fades, per second (the share left falls by `exp(-dryRate t)`).
 * - [tone]: the colour a wet spot of it goes (wood goes darker): what a ring of clear water looks like.
 */
public class SurfaceMaterial(
    public val name: String,
    public val porosity: Float,
    public val permanence: Float,
    public val dryRate: Float,
    public val tone: Color,
) {
    public companion object {
        public val Wood: SurfaceMaterial = SurfaceMaterial("Wood", 0.8f, 0.6f, 1f / 90f, Color(0xFF2A1A0E))
        public val Varnished: SurfaceMaterial = SurfaceMaterial("Varnished wood", 0.18f, 0.25f, 1f / 25f, Color(0xFF3A2514))
        public val Stone: SurfaceMaterial = SurfaceMaterial("Stone", 0.3f, 0.35f, 1f / 40f, Color(0xFF4A4642))
        public val Ceramic: SurfaceMaterial = SurfaceMaterial("Ceramic", 0.04f, 0f, 1f / 6f, Color(0xFF6A645C))
        public val Metal: SurfaceMaterial = SurfaceMaterial("Metal", 0.02f, 0f, 1f / 5f, Color(0xFF50555A))

        public val All: List<SurfaceMaterial> = listOf(Wood, Varnished, Stone, Ceramic, Metal)
    }
}

/**
 * The foot of a container: a circle of [radius] meters. With [ring] at 0 the whole base touches the surface (a bottle, a jug); with a number up to 1 only a rim does, and that rim is [ring] of the radius wide
 * (the thin edge of a tumbler, the foot of a mug), so what is left is a ring and not a disc.
 */
public class VesselBase(public val radius: Float, public val ring: Float = 0f)

/**
 * Finds the moment a container is set down: it has been moving (being carried or slid) and now it has come to rest. [step] gives the speed in m/s along the surface and returns true once, at that moment.
 */
public class SetDown(private val moving: Float = 0.08f, private val still: Float = 0.03f, private val settleSeconds: Float = 0.15f) {
    private var was = false
    private var calm = 0f

    public fun step(speed: Float, dt: Float): Boolean {
        val s = abs(speed)
        if (s > moving) { was = true; calm = 0f; return false }
        if (s < still) calm += dt else calm = 0f
        if (was && calm >= settleSeconds) { was = false; return true }
        return false
    }
}

/**
 * The marks that wet containers leave on a surface: a fixed-size pool, nothing allocated while it runs. Positions are in meters along the surface ([x]) and back from the front ([depth]); [radius] is the foot of the
 * container that made it.
 *
 * A mark gets stronger each time a wet foot touches it ([deposit]) and weaker as it dries ([step]), to the [permanent] part that stays (coffee on wood does not come out). A foot set down where a mark already is
 * adds to that mark and does not pile up a new one.
 */
public class StainField(private val capacity: Int = 48) {
    public val x: FloatArray = FloatArray(capacity)
    public val depth: FloatArray = FloatArray(capacity)
    public val radius: FloatArray = FloatArray(capacity)
    public val ring: FloatArray = FloatArray(capacity)
    public val strength: FloatArray = FloatArray(capacity)
    public val permanent: FloatArray = FloatArray(capacity)
    private val color = ULongArray(capacity)
    private val material = arrayOfNulls<SurfaceMaterial>(capacity)
    public var count: Int = 0
        private set

    /** The colour of mark [i]. */
    public fun colorOf(i: Int): Color = Color(color[i])

    /** The surface mark [i] is on. */
    public fun materialOf(i: Int): SurfaceMaterial = material[i]!!

    /**
     * The foot of [vessel] touches [surface] at ([atX], [atDepth]) for [amount] of a full touch (1 is the whole wet of the foot, set down hard; a small number a little at a time while it stands). It takes that much of
     * the wet off the foot, and nothing if the foot is dry, tipped over or the surface does not soak.
     */
    public fun touch(vessel: LiquidVessel, atX: Float, atDepth: Float, surface: SurfaceMaterial, amount: Float = 1f) {
        val wet = vessel.bottomWet
        if (wet < 0.01f || abs(vessel.body.angle) > 0.3f) return
        val fluid = vessel.bottomFluid ?: vessel.fluid
        val used = min(wet, amount * wet)
        vessel.dryBottom(used)
        deposit(atX, atDepth, vessel.base, fluid, used, surface)
    }

    /** Marks [surface] under a foot with [wetness] (0..1) of [fluid] on it. */
    public fun deposit(atX: Float, atDepth: Float, base: VesselBase, fluid: Fluid, wetness: Float, surface: SurfaceMaterial) {
        // Clear water darkens the surface itself; a colouring liquid leaves its own colour. How much soaks in goes with how porous the surface is.
        val gain = wetness * surface.porosity * (0.35f + 0.65f * fluid.turbidity)
        if (gain < 0.002f) return
        val keep = gain * surface.permanence * fluid.turbidity
        val tint = lerp(surface.tone, fluid.color, fluid.turbidity)
        var i = nearest(atX, atDepth, base.radius, surface)
        if (i < 0) {
            i = if (count < capacity) count++ else weakest()
            x[i] = atX; depth[i] = atDepth; radius[i] = base.radius; ring[i] = base.ring
            strength[i] = 0f; permanent[i] = 0f; color[i] = tint.value; material[i] = surface
        } else {
            color[i] = lerp(Color(color[i]), tint, gain / (strength[i] + gain)).value
        }
        strength[i] = min(1f, strength[i] + gain)
        permanent[i] = min(strength[i], permanent[i] + keep)
    }

    private fun nearest(atX: Float, atDepth: Float, r: Float, surface: SurfaceMaterial): Int {
        for (i in 0 until count) if (material[i] === surface && abs(radius[i] - r) < r * 0.3f && abs(x[i] - atX) < r / 3f && abs(depth[i] - atDepth) < r / 3f) return i
        return -1
    }

    private fun weakest(): Int {
        var w = 0
        for (i in 1 until count) if (strength[i] < strength[w]) w = i
        return w
    }

    /** Takes every mark away (a clean cloth). */
    public fun clear() { for (i in 0 until count) material[i] = null; count = 0 }

    /** Lets the marks dry for [dt] seconds: the wet part of each fades, the permanent part stays. */
    public fun step(dt: Float) {
        var i = 0
        while (i < count) {
            val dry = 1f - exp(-material[i]!!.dryRate * dt)
            strength[i] -= (strength[i] - permanent[i]) * dry
            if (strength[i] < 0.01f) {
                count--
                x[i] = x[count]; depth[i] = depth[count]; radius[i] = radius[count]; ring[i] = ring[count]
                strength[i] = strength[count]; permanent[i] = permanent[count]; color[i] = color[count]; material[i] = material[count]; material[count] = null
                continue
            }
            i++
        }
    }
}
