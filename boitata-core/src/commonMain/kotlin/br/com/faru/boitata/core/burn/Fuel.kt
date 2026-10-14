package br.com.faru.boitata.core.burn

/**
 * What a thing is made of, as far as fire is concerned.
 *
 * Heat builds up in a thing that stands near a flame, slowly, and it catches when the heat reaches [ignitionHeat]; then it burns for [burnSeconds], with flames as big as [intensity] says,
 * and what is left is ash. A [living] thing (a plant, a tree) is spared by the Boitatá's guardian fire; nothing is spared by ordinary fire.
 */
public class Fuel(
    public val label: String,
    /**
     * How much heat it takes to catch. About one point of heat is what a thing standing at half a flame's reach gets in four seconds, so 0.6 is quick, 1.5 slow and 3 stubborn.
     * Stone and metal use [Fuel.None] and never catch.
     */
    public val ignitionHeat: Float,
    /** How long it burns once it has caught, in seconds. Infinity never burns out (a campfire kept fed). */
    public val burnSeconds: Float,
    /** The size of its flames, 0..1: dry grass flickers small, pine resin roars. */
    public val intensity: Float,
    /** Whether it is a living plant: the Boitatá's fire never lights it. */
    public val living: Boolean = false,
) {
    /** Whether it can burn at all. */
    public val flammable: Boolean get() = ignitionHeat > 0f && ignitionHeat < Float.POSITIVE_INFINITY

    public companion object {
        /** Stone, metal, a person with a lantern: heats up to nothing. */
        public val None: Fuel = Fuel("Not flammable", ignitionHeat = Float.POSITIVE_INFINITY, burnSeconds = 0f, intensity = 0f)

        /** Dry grass and straw: catches at a glance and is gone in seconds. */
        public val DryGrass: Fuel = Fuel("Dry grass", ignitionHeat = 0.6f, burnSeconds = 5f, intensity = 0.5f)

        /** Planks, crates and stools. */
        public val Wood: Fuel = Fuel("Wood", ignitionHeat = 1.5f, burnSeconds = 24f, intensity = 0.85f)

        /** A leafy plant in a pot. */
        public val Plant: Fuel = Fuel("Plant", ignitionHeat = 1.0f, burnSeconds = 9f, intensity = 0.6f, living = true)

        /** Palm fronds: dry and airy, they take fire readily. */
        public val Palm: Fuel = Fuel("Palm", ignitionHeat = 1.1f, burnSeconds = 14f, intensity = 0.75f, living = true)

        /** Pine resin: a long time to catch, then a roar. */
        public val Pine: Fuel = Fuel("Pine", ignitionHeat = 2.0f, burnSeconds = 30f, intensity = 1f, living = true)

        /** A campfire kept fed: always burning. */
        public val Campfire: Fuel = Fuel("Campfire", ignitionHeat = 0.01f, burnSeconds = Float.POSITIVE_INFINITY, intensity = 1f)
    }
}

/** Whose fire it is. */
public enum class FireKind {
    /** The fire of people: it burns whatever is flammable and spreads. */
    Ordinary,

    /** The Boitatá's fire: it never lights a living thing, and puts out ordinary fires it touches. */
    Guardian,
}
