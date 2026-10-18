package br.com.faru.boitata.plants

import br.com.faru.boitata.core.fluid.Fluid
import br.com.faru.boitata.core.fluid.LiquidVessel
import kotlin.math.exp
import kotlin.math.min

/** What a liquid does to a plant it is poured on: [hydrates] (0..1, how much of it is water the roots can use) and [toxicity] (0..1, how much it harms them). */
public class PlantFluidEffect(public val hydrates: Float, public val toxicity: Float)

/** How a [Fluid] reads to a plant, worked out from its numbers so a mix of two is in between: cloud and thickness are not water, and `Fluid.toxicity` harms. */
public object PlantFluids {
    public fun of(fluid: Fluid): PlantFluidEffect {
        val toxicity = fluid.toxicity.coerceIn(0f, 1f)
        val water = (1f - 0.6f * fluid.turbidity - 0.7f * fluid.viscosity).coerceIn(0f, 1f)
        return PlantFluidEffect(water * (1f - 0.8f * toxicity), toxicity)
    }
}

/**
 * A pot of soil with plants in it: a container that holds soil, and soil that holds plants, each reacting to the one before it. [pot] is a [LiquidVessel] whose bottom [soilShare] is taken by the soil
 * (`LiquidVessel.solid`), so what is poured into it **stands on top** of the soil, up to the rim, and runs over the rim like any container if there is too much. Each [step]:
 *
 * - the standing water **soaks in** at the soil's pace (sand fast, clay slow, and slower the wetter the soil is), raising [patch]'s moisture by what the soil can hold, and what a liquid is (see [PlantFluids]) decides how much of it is
 *   water and how much poison it leaves in the soil;
 * - soil wetter than it can hold on to (field capacity, 0.8) **drains out of the hole at the bottom**: that water is [takeDrained], for the caller to run onto the table or into a saucer.
 *
 * The plants' own response (drooping, recovering, stress from poison and drowning) is [PlantModel], through [patch].
 */
public class Planter(public val pot: LiquidVessel, public val patch: PlantPatch, soilShare: Float = 0.72f) {
    init { pot.solid = pot.width * pot.height * soilShare.coerceIn(0f, 0.95f) }

    /** How much water the soil holds when soaked, as a cross-section area in m^2. */
    public val poreArea: Float get() = pot.solid * 0.45f * patch.soil.capacity

    /** All the water that has run out of the bottom so far, and what has run out since [takeDrained] was last called (areas, m^2). */
    public var drained: Float = 0f
        private set
    private var pending = 0f

    /** How much water ran out of the hole in the bottom since you last asked (an area in m^2): make a puddle of it, wet the foot of the pot. */
    public fun takeDrained(): Float { val d = pending; pending = 0f; return d }

    /** Empties the pot and sets the soil and the plants to a given state: [moisture] of the soil, [hydration] of every plant, no poison, no standing water. */
    public fun reset(moisture: Float = 0.1f, hydration: Float = 0.2f) {
        pot.reset(0f)
        patch.wet.moisture = moisture.coerceIn(0f, 1f); patch.wet.contamination = 0f; patch.wet.saturatedHours = 0f
        for (plant in patch.plants) { plant.hydration = hydration.coerceIn(0f, 1f); plant.stress = 0f }
        pending = 0f; drained = 0f
    }

    /** Lets what stands soak in and the soil drain for [dt] seconds. */
    public fun step(dt: Float) {
        if (dt <= 0f || poreArea <= 0f) return
        val wet = patch.wet
        val standing = pot.volume
        if (standing > 1e-9f) {
            val effect = PlantFluids.of(pot.fluid)
            val rate = (0.1f + 0.6f * patch.soil.drainage) * (1f - wet.moisture)
            val soak = min(standing, standing * (1f - exp(-rate * dt)) + 1e-9f)
            pot.drain(soak)
            wet.moisture = (wet.moisture + soak * effect.hydrates / poreArea).coerceIn(0f, 1f)
            wet.contamination = (wet.contamination + soak / poreArea * effect.toxicity * 1.5f).coerceIn(0f, 1f)
        }
        if (wet.moisture > FIELD_CAPACITY) {
            val out = (wet.moisture - FIELD_CAPACITY) * (1f - exp(-(0.5f + 2f * patch.soil.drainage) * dt))
            wet.moisture = (wet.moisture - out).coerceAtLeast(0f)
            val water = out * poreArea
            pending += water; drained += water
        }
    }

    private companion object { const val FIELD_CAPACITY = 0.8f }
}
