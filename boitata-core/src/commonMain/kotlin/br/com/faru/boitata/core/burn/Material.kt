package br.com.faru.boitata.core.burn

/** What fire does to a thing, which is what it looks like while it happens and what is left. */
public enum class BurnStyle {
    /** Wood and pine: darkens, ember cracks glow, and a charred shape is left. */
    Char,

    /** Paper, straw, dry grass: a burning edge sweeps through and eats it; ash flakes lift off and the wind takes them; nothing is left. */
    AshAway,

    /** Leaves and plants: blacken, curl and shrink, then crumble to a small charred stub. */
    Shrivel,

    /** Wax and plastic: softens, sags, drips and settles into a puddle that cools and stays. */
    Melt,

    /** Cloth and rope: no big flames, a glowing edge creeps through with smoke, and a heap of grey ash is left. */
    Smoulder,

    /** Metal: takes heat quickly, glows red then orange, and cools back; it does not burn. */
    Glow,

    /** Stone, clay: heat does nothing to it. */
    None,
}

/**
 * What a thing is made of, as far as fire is concerned. Every reaction is optional, as in the games that model this (a material has an ignition point, a melting point and so on, each
 * of which it may simply not have): a material that cannot ignite never burns, one with no melting point never melts.
 *
 * Heat builds in a thing that stands near a flame, and the numbers below are in the same units: a thing at half a flame's reach takes about one point of heat in four seconds.
 */
public class Material(
    public val label: String,
    /** Heat at which it catches fire; infinity if it cannot. 0.6 is quick, 1.5 slow, 3 stubborn. */
    public val ignitionHeat: Float,
    /** How long it burns once alight, in seconds. Infinity never burns out (a campfire kept fed, or a wick fed by wax). */
    public val burnSeconds: Float,
    /** The size of its flames, 0..1. */
    public val intensity: Float,
    /** A living plant: the Boitatá's fire never lights it. */
    public val living: Boolean = false,
    /** How it looks while it burns and what is left. */
    public val style: BurnStyle = BurnStyle.Char,
    /** Heat at which it starts to melt (permanently); infinity if it never does. */
    public val meltHeat: Float = Float.POSITIVE_INFINITY,
    /** How fast it melts once past its melting heat: the share of it melted per second for each point of heat above the melting heat. Candle wax is slow, plastic fast. */
    public val meltRate: Float = 0.004f,
    /** Heat at which it starts to glow (while hot; it fades as it cools); infinity if it never does. */
    public val glowHeat: Float = Float.POSITIVE_INFINITY,
    /** How fast it takes up heat compared with ordinary things (metal takes it quickly). */
    public val heatGain: Float = 1f,
    /** How fast it loses heat when no flame is near (metal holds it a long time). */
    public val coolRate: Float = 0.12f,
    /** Rain does not wet it and cannot put it out: the Boitatá's own flame. */
    public val waterproof: Boolean = false,
) {
    /** Whether it can burn at all. */
    public val flammable: Boolean get() = ignitionHeat > 0f && ignitionHeat < Float.POSITIVE_INFINITY

    /** Whether it can melt. */
    public val meltable: Boolean get() = meltHeat < Float.POSITIVE_INFINITY

    public companion object {
        /** Stone, clay, a figure with a lantern: heat does nothing. */
        public val None: Material = Material("Not flammable", Float.POSITIVE_INFINITY, 0f, 0f, style = BurnStyle.None)

        /** Paper: catches at a glance, burns in seconds, and flies away as ash. */
        public val Paper: Material = Material("Paper", ignitionHeat = 0.5f, burnSeconds = 4f, intensity = 0.55f, style = BurnStyle.AshAway)

        /** Dry grass and straw. */
        public val DryGrass: Material = Material("Dry grass", ignitionHeat = 0.6f, burnSeconds = 5f, intensity = 0.5f, style = BurnStyle.AshAway)

        /** Cloth and rope: slow to light, smoulders. */
        public val Cloth: Material = Material("Cloth", ignitionHeat = 1.1f, burnSeconds = 20f, intensity = 0.28f, style = BurnStyle.Smoulder)

        /** Planks, crates and stools. */
        public val Wood: Material = Material("Wood", ignitionHeat = 1.5f, burnSeconds = 24f, intensity = 0.85f, style = BurnStyle.Char)

        /** A leafy plant in a pot. */
        public val Plant: Material = Material("Plant", ignitionHeat = 1.0f, burnSeconds = 9f, intensity = 0.6f, living = true, style = BurnStyle.Shrivel)

        /** Palm fronds: dry and airy. */
        public val Palm: Material = Material("Palm", ignitionHeat = 1.1f, burnSeconds = 14f, intensity = 0.75f, living = true, style = BurnStyle.Shrivel)

        /** Pine resin: a long time to catch, then a roar. */
        public val Pine: Material = Material("Pine", ignitionHeat = 2.0f, burnSeconds = 30f, intensity = 1f, living = true, style = BurnStyle.Char)

        /** Candle wax: it does not burn itself, but melts in the heat of the wick (and is the wick's fuel). */
        public val Wax: Material = Material("Wax", ignitionHeat = Float.POSITIVE_INFINITY, burnSeconds = 0f, intensity = 0f, style = BurnStyle.Melt, meltHeat = 0.8f)

        /** A wick: lights at a touch, and burns as long as the wax below it lasts (give it a `feeds` link). */
        public val Wick: Material = Material("Wick", ignitionHeat = 0.35f, burnSeconds = Float.POSITIVE_INFINITY, intensity = 0.3f, style = BurnStyle.Char)

        /** Plastic: melts well before it would burn, then drips and stays. */
        public val Plastic: Material = Material("Plastic", ignitionHeat = 3.0f, burnSeconds = 14f, intensity = 0.7f, style = BurnStyle.Melt, meltHeat = 1.0f, meltRate = 0.03f)

        /** Metal: heats fast, glows, holds the heat, never burns. */
        public val Metal: Material = Material("Metal", ignitionHeat = Float.POSITIVE_INFINITY, burnSeconds = 0f, intensity = 0f, style = BurnStyle.Glow, glowHeat = 0.5f, heatGain = 1.6f, coolRate = 0.05f)

        /** A person in their clothes: cloth and leather catch slowly, then burn well. Not a plant, so the Boitatá's fire lights them; stone and animals ([None]) never catch. */
        public val Person: Material = Material("Person", ignitionHeat = 1.2f, burnSeconds = 12f, intensity = 0.65f, style = BurnStyle.Char)

        /** The Boitatá's own flame: always burning, never burns down and rain cannot touch it. */
        public val Spirit: Material = Material("Spirit", ignitionHeat = 0.01f, burnSeconds = Float.POSITIVE_INFINITY, intensity = 1f, style = BurnStyle.None, waterproof = true)

        /** A campfire kept fed: always burning. */
        public val Campfire: Material = Material("Campfire", ignitionHeat = 0.01f, burnSeconds = Float.POSITIVE_INFINITY, intensity = 1f, style = BurnStyle.Char)
    }
}

/** Whose fire it is. */
public enum class FireKind {
    /** The fire of people: it burns whatever is flammable and spreads. */
    Ordinary,

    /** The Boitatá's fire: it never lights a living thing, and puts out ordinary fires it touches. */
    Guardian,
}
