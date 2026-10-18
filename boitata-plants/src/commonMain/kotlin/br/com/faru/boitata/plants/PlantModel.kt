package br.com.faru.boitata.plants

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * One plant: how full of water it is, how much light it gets, how stressed and how grown. Advance it with [PlantModel.step]; read the derived [droop], [leafTurn] and [turgor] to draw it.
 * Pure state: the same steps from the same start give the same plant, so a day can be scrubbed and tests can pin it.
 */
public class PlantState(
    hydration: Float = 1f,
    growth: Float = 0.3f,
) {
    /** How full of water, 0 wilted to 1 full. Follows the soil, and falls in hot sun. */
    public var hydration: Float = hydration.coerceIn(0f, 1f)
        internal set

    /** The light it gets now, smoothed, 0..1. */
    public var light: Float = 0f
        internal set

    /** How badly off it is, 0 thriving to 1 dying: drought, a drowned root, too little or too much light, or heat. */
    public var stress: Float = 0f
        internal set

    /** How grown it is, 0 a seedling to 1 full size. */
    public var growth: Float = growth.coerceIn(0f, 1f)
        internal set

    /** Which way its leaves face, -1 left to 1 right, following the light. */
    public var leafTurn: Float = 0f
        internal set

    /** How rigid it is from its water, 0..1. */
    public val turgor: Float get() = (hydration * (1f - 0.5f * stress)).coerceIn(0f, 1f)

    /** How far it has drooped, 0 upright to 1 flat: thirst and, at night, rest. [species] says how much it can droop at all. */
    public fun droop(species: PlantSpecies, daylight: Float = 1f): Float =
        ((1f - turgor) * species.leafFlex + 0.15f * species.leafFlex * (1f - daylight.coerceIn(0f, 1f))).coerceIn(0f, 1f)
}

/** The water in the ground under a plant (or a patch of them), as a share of what it can hold: 0 dust, 1 soaked. */
public class SoilMoisture(moisture: Float = 0.5f) {
    public var moisture: Float = moisture.coerceIn(0f, 1f)
        internal set

    /** How much of what has been poured on it harms roots, 0 clean to 1 poisoned (kerosene, oil, too much wine). It washes out slowly, faster in rain. */
    public var contamination: Float = 0f
        internal set

    /** How long it has stood soaked, in hours: roots that stay under water drown. */
    public var saturatedHours: Float = 0f
        internal set
}

/** The rules: how soil, light, heat and wind change a plant over time. All arithmetic, no clock of its own. */
public object PlantModel {
    private fun approach(from: Float, to: Float, ratePerHour: Float, dtHours: Float): Float = to + (from - to) * exp(-ratePerHour * dtHours)

    /** Moves the [soil] on by [dtHours] under [env]: rain fills it, drainage and the sun and wind empty it, and the plants drink [thirst] (the sum of what [step] returned). */
    public fun stepSoil(soil: SoilMoisture, kind: Soil, env: PlantEnvironment, dtHours: Float, thirst: Float = 0f) {
        var m = soil.moisture
        m += env.rain * 0.3f * dtHours / kind.capacity
        m -= kind.drainage * 0.03f * m * m * dtHours                        // the extra runs away faster the wetter it is
        m -= (0.001f + 0.006f * env.light + 0.003f * env.heat + 0.002f * env.wind) * dtHours / kind.capacity
        m -= thirst * 0.006f * dtHours / kind.capacity
        soil.moisture = m.coerceIn(0f, 1f)
        soil.contamination = (soil.contamination - (0.01f + 0.1f * env.rain) * dtHours).coerceIn(0f, 1f)
        soil.saturatedHours = if (soil.moisture > 0.9f) soil.saturatedHours + dtHours else (soil.saturatedHours - 2f * dtHours).coerceAtLeast(0f)
    }

    /** Moves one [plant] on by [dtHours]. Returns how much water it drank (for [stepSoil]'s `thirst`). */
    public fun step(plant: PlantState, species: PlantSpecies, soil: Soil, wet: SoilMoisture, env: PlantEnvironment, dtHours: Float): Float {
        val dt = dtHours.coerceAtLeast(0f)
        val day = env.daylight.coerceIn(0f, 1f)

        // Water: the plant fills toward what the soil gives (a plant that wants less is happy with less), and hot sun empties it.
        val supply = min(1f, wet.moisture / max(0.05f, species.waterNeed))
        val thirst = (0.05f + 0.25f * env.light + 0.1f * env.wind) * (1f - species.heatTolerance * 0.6f)
        plant.hydration = approach(plant.hydration, supply, 0.4f, dt).let { it - thirst * env.heat * 0.15f * dt * (1f - supply * 0.7f) }.coerceIn(0f, 1f)
        val drank = species.waterNeed * (0.3f + 0.7f * env.light) * (0.2f + 0.8f * day) * plant.growth.coerceAtLeast(0.2f)

        // Light: smoothed, so a passing cloud does not flick the plant.
        plant.light = approach(plant.light, env.light, 1.5f, dt)

        // Stress: the worst of the things that hurt it. Only by day does light count; at night it rests.
        val drought = (1f - plant.hydration) * (1f - plant.hydration) * 1.2f
        val drowned = max(((wet.moisture - 0.92f) / 0.08f).coerceIn(0f, 1f), (wet.saturatedHours / 12f).coerceIn(0f, 1f)) * (1f - species.waterNeed)
        val poisoned = (wet.contamination * 1.1f).coerceIn(0f, 1f)
        val dark = max(0f, species.lightNeed - 0.15f - plant.light) * day * 1.4f
        val glare = max(0f, plant.light - species.lightNeed - 0.3f) * day * 1.2f * (1f - species.heatTolerance)
        val scorch = max(0f, env.heat - 0.55f - species.heatTolerance * 0.4f) * 1.6f
        val target = max(max(max(drought, drowned), poisoned), max(max(dark, glare), scorch)).coerceIn(0f, 1f)
        val rate = if (target > plant.stress) 0.6f else 0.15f
        plant.stress = approach(plant.stress, target, rate, dt)

        // Growth: only a healthy plant, in light, in a soil with something in it.
        val health = (1f - plant.stress).let { it * it }
        plant.growth = (plant.growth + species.growthPerDay / 24f * dt * health * soil.fertility * plant.light * day).coerceIn(0f, 1f)

        // Leaves turn toward the light, slowly.
        val turn = env.lightSide * species.phototropism * day * (0.4f + 0.6f * plant.light)
        plant.leafTurn = approach(plant.leafTurn, turn.coerceIn(-1f, 1f), 0.8f, dt)
        return drank
    }
}
