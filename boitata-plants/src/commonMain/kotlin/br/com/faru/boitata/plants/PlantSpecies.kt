package br.com.faru.boitata.plants

import br.com.faru.boitata.core.wind.WindResponse

/**
 * What a kind of plant needs and how it is built. Everything is 0..1 so the numbers read the same across kinds. No art in here: a species is only its needs and its body, and the game
 * decides what it looks like.
 */
public class PlantSpecies(
    /** How much light it wants: 0 a plant of the forest floor, 1 full sun all day. Too little starves it, too much scorches it. */
    public val lightNeed: Float,
    /** How much water it wants to find in the soil: 0 a cactus, 1 a bog plant. Also how much of it drowns in a wet soil. */
    public val waterNeed: Float,
    /** How well it takes heat before it wilts: 0 wilts at the first hot noon, 1 a desert plant. */
    public val heatTolerance: Float,
    /** How stiff its body is when full of water; wind leans it by `1 / (0.3 + 2.2 * stiffness)`, see [WindResponse.anchored]. */
    public val stiffness: Float,
    /** How much its leaves and stem fall when it is short of water or at rest at night: 0 a pine, 1 a lettuce. */
    public val leafFlex: Float,
    /** How far it turns toward the light: 0 not at all, 1 follows the sun. */
    public val phototropism: Float,
    /** How fast it grows when everything is right, as a share of full height per day. */
    public val growthPerDay: Float = 0.1f,
    /** How tall it stands above the soil when fully grown and healthy, in metres. What a shelf above it has to leave free; see [fit]. */
    public val matureHeight: Float = 0.5f,
    /** How wide its crown spreads when fully grown, in metres; it may overhang its pot and the edge of what it stands on. */
    public val spread: Float = 0.4f,
    /** How far its stems hang below the pot's rim, in metres: 0 for an upright plant, more for ivy and pothos. */
    public val hang: Float = 0f,
) {
    init {
        require(lightNeed in 0f..1f && waterNeed in 0f..1f && heatTolerance in 0f..1f) { "needs are 0..1" }
        require(matureHeight > 0f && spread > 0f && hang >= 0f) { "a plant has a size" }
    }

    /** How it answers the wind now, given how full of water it is ([turgor] 0..1) and how wet its leaves are ([wetLeaves] 0..1). A thirsty plant is floppier; wet leaves are heavier. */
    public fun windResponse(turgor: Float, wetLeaves: Float = 0f): WindResponse =
        WindResponse.anchored((stiffness * (0.6f + 0.4f * turgor.coerceIn(0f, 1f)) * (1f - 0.2f * wetLeaves.coerceIn(0f, 1f))).coerceIn(0.02f, 1f))

    public companion object {
        public val Fern: PlantSpecies = PlantSpecies(0.25f, 0.8f, 0.2f, 0.12f, 0.8f, 0.5f, 0.08f, matureHeight = 0.5f, spread = 0.7f)
        public val Grass: PlantSpecies = PlantSpecies(0.7f, 0.45f, 0.5f, 0.12f, 0.5f, 0.2f, 0.2f, matureHeight = 0.3f, spread = 0.3f)
        public val Flower: PlantSpecies = PlantSpecies(0.75f, 0.55f, 0.35f, 0.2f, 0.7f, 1f, 0.12f, matureHeight = 0.4f, spread = 0.3f)
        public val Succulent: PlantSpecies = PlantSpecies(0.9f, 0.1f, 0.95f, 0.3f, 0.1f, 0.2f, 0.03f, matureHeight = 0.2f, spread = 0.2f)
        public val Palm: PlantSpecies = PlantSpecies(0.85f, 0.5f, 0.75f, 0.3f, 0.4f, 0.3f, 0.04f, matureHeight = 1.5f, spread = 1.2f)
        public val Pine: PlantSpecies = PlantSpecies(0.6f, 0.35f, 0.6f, 0.55f, 0.05f, 0.1f, 0.03f, matureHeight = 2f, spread = 1.2f)
    }
}

/**
 * The ground a plant stands in. [capacity] is how much water it can hold (sand little, clay much), [drainage] how fast the extra runs away (sand fast, clay slow), and [fertility] how much
 * growth it supports.
 */
public class Soil(public val capacity: Float, public val drainage: Float, public val fertility: Float) {
    init {
        require(capacity > 0f) { "a soil holds some water" }
    }

    public companion object {
        public val Sand: Soil = Soil(capacity = 0.55f, drainage = 0.9f, fertility = 0.35f)
        public val Loam: Soil = Soil(capacity = 1f, drainage = 0.5f, fertility = 0.9f)
        public val Clay: Soil = Soil(capacity = 1.4f, drainage = 0.15f, fertility = 0.7f)
    }
}
