package br.com.faru.boitata.core.thing

/**
 * What a layer of a thing or a surface of the room is made of, as light sees it. Plain numbers, so the light can be worked out anywhere:
 *
 * - [reflectance]: how much of the light that falls on it it gives back (0 a black velvet, about 0.8 fresh whitewash), in [color] (0xRRGGBB). It decides how bright the thing
 *   shows and how much it lights what is near it (a bounce).
 * - [gloss] and [sharpness]: how mirror-like it is. [gloss] is how much of the light comes back as a highlight (0 matte), [sharpness] how tight that highlight is (a Blinn–Phong
 *   exponent: about 8 for a waxy sheen, 60 for enamel, 200 for glass or polished metal).
 * - [transmission]: how much light passes through it (0 opaque, 0.9 clear glass), tinted [transmissionColor]. A shadow of it is that much lighter, and light comes through.
 * - [emission]: set when it gives light itself (a flame, a lit dial).
 */
public data class Material(
    val name: String,
    val reflectance: Float,
    val color: Int = 0xFFFFFF,
    val gloss: Float = 0f,
    val sharpness: Float = 8f,
    val transmission: Float = 0f,
    val transmissionColor: Int = 0xFFFFFF,
    val emission: Emission? = null,
) {
    init {
        require(reflectance in 0f..1f && gloss in 0f..1f && transmission in 0f..1f) { "$name: reflectance, gloss and transmission are shares (0 to 1)" }
    }

    /** How much of a light this material stops: what its shadow takes away. */
    public val opacity: Float get() = 1f - transmission
}

/** Light a material gives off itself: its [color], how [strength]ly it lights what is near it (0 to 1), and how much of a [flame] it is (0 steady, 1 an open flame that sways). */
public data class Emission(val color: Int, val strength: Float, val flame: Float = 0f)

/**
 * Materials as they really are (reflectance from measured albedos, rounded; gloss and sharpness by eye against the era's objects). Copy one with `copy(...)` to tune it for a
 * particular thing: a darker stain on a desk, a dirtier glass.
 */
public object Materials {
    public val Plaster: Material = Material("plaster", reflectance = 0.6f, color = 0xD8D0C0)
    public val PalePaint: Material = Material("pale paint", reflectance = 0.7f, color = 0xE4DCC8, gloss = 0.05f, sharpness = 12f)
    public val DarkWood: Material = Material("dark wood", reflectance = 0.15f, color = 0x5A4636, gloss = 0.08f, sharpness = 20f)
    public val LightWood: Material = Material("light wood", reflectance = 0.35f, color = 0xA88A68, gloss = 0.06f, sharpness = 16f)
    public val Floorboards: Material = Material("floorboards", reflectance = 0.2f, color = 0x6E5A48, gloss = 0.1f, sharpness = 24f)
    public val Enamel: Material = Material("enamel", reflectance = 0.4f, color = 0x9A9A96, gloss = 0.35f, sharpness = 60f)
    public val BlackEnamel: Material = Material("black enamel", reflectance = 0.05f, color = 0x202020, gloss = 0.5f, sharpness = 80f)
    public val Glass: Material = Material("glass", reflectance = 0.04f, color = 0xF0F4F4, gloss = 0.9f, sharpness = 200f, transmission = 0.85f, transmissionColor = 0xF4F0E6)
    public val Iron: Material = Material("iron", reflectance = 0.2f, color = 0x5C5C5C, gloss = 0.2f, sharpness = 30f)
    public val Brass: Material = Material("brass", reflectance = 0.45f, color = 0xB8963C, gloss = 0.6f, sharpness = 90f)
    public val Tin: Material = Material("tin", reflectance = 0.5f, color = 0xA0A4A6, gloss = 0.45f, sharpness = 50f)
    public val Bakelite: Material = Material("bakelite", reflectance = 0.12f, color = 0x4A3426, gloss = 0.4f, sharpness = 70f)
    public val Paper: Material = Material("paper", reflectance = 0.75f, color = 0xEDE6D6, transmission = 0.2f)
    public val Cloth: Material = Material("cloth", reflectance = 0.4f, color = 0x8C8070)
    public val Wax: Material = Material("wax", reflectance = 0.7f, color = 0xF0E8D8, gloss = 0.1f, sharpness = 8f, transmission = 0.3f, transmissionColor = 0xFFE0B0)
    public val Terracotta: Material = Material("terracotta", reflectance = 0.3f, color = 0xA0583C)
    public val Ceramic: Material = Material("ceramic", reflectance = 0.6f, color = 0xE8E4DC, gloss = 0.4f, sharpness = 70f)
    public val Soil: Material = Material("soil", reflectance = 0.08f, color = 0x3A2C22)
    public val Leaf: Material = Material("leaf", reflectance = 0.2f, color = 0x4A6A3A, gloss = 0.1f, sharpness = 12f, transmission = 0.15f, transmissionColor = 0xA0D070)
    public val CandleFlame: Material = Material("candle flame", reflectance = 0f, color = 0xFFC060, emission = Emission(0xF2A24A, 0.8f, flame = 1f))
    public val LampFlame: Material = Material("lamp flame", reflectance = 0f, color = 0xFFC870, emission = Emission(0xF2B65C, 0.75f, flame = 0.3f))
    public val Bulb: Material = Material("bulb", reflectance = 0f, color = 0xFFF0C8, emission = Emission(0xFFE2A8, 0.9f))

    /** Every ready-made material, by name. */
    public val all: List<Material> = listOf(
        Plaster, PalePaint, DarkWood, LightWood, Floorboards, Enamel, BlackEnamel, Glass, Iron, Brass, Tin, Bakelite, Paper, Cloth, Wax, Terracotta, Ceramic, Soil, Leaf,
        CandleFlame, LampFlame, Bulb,
    )
}
