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

    /** The share of a source's strength that a point [distance] cm away gets, for a source of reference distance [reach]. */
    public fun of(distance: Float, reach: Float): Float = minOf(NEAR, reach * reach / (distance * distance + GLOW * GLOW))
}
