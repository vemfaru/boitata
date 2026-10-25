package br.com.faru.boitata.core.light

/**
 * How a light falls off with distance: the inverse-square law. The light a point gets from a source `r` cm away is `(reach / r)²` of what it gets at the source's [reach] (its
 * reference distance: where it lights a surface at its full strength), so at twice the distance a quarter, at four times a sixteenth. [GLOW] keeps it finite right at the
 * flame (a flame is not a point), and [NEAR] caps it close by, where the eye sees it as plain bright anyway. A far source (the sun through a window, `reach` huge) is even
 * across a room.
 */
public object Falloff {
    /** The size of what glows, in cm: the light at the source itself is finite. */
    public const val GLOW: Float = 3f

    /** The most a point near a source gets, in units of the source's strength. */
    public const val NEAR: Float = 4f

    /** A source whose reference distance is at least this (cm) is far away (the sun, the moon, the sky through a window): across a room it does not fall off at all. */
    public const val DISTANT: Float = 10_000f

    /** The share of a source's strength that a point [distance] cm away gets, for a source of reference distance [reach]. */
    public fun of(distance: Float, reach: Float): Float =
        if (reach >= DISTANT) 1f else minOf(NEAR, reach * reach / (distance * distance + GLOW * GLOW))
}

/**
 * How bright light looks: the eye does not see light in proportion to how much there is (a little lifts the dark a lot, more adds less and less), `1 − e^(−GAIN · E)`. The room's
 * light field is drawn through it, and a shadow is as strong as its light looks: what it takes away is its light's share of all the light there ([ShadowMesh.share]) times how
 * bright all that light looks. Faint moonlight throws a faint shadow, a candle close by or the sun a strong one.
 */
public object Exposure {
    public const val GAIN: Float = 1.6f

    public fun response(light: Float): Float = 1f - kotlin.math.exp(-GAIN * light.coerceAtLeast(0f))
}
