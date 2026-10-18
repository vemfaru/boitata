package br.com.faru.boitata.plants

/**
 * A place a pot can stand, in metres: the top of a desk, a shelf board, a crate floor, the floor itself. [width] and [depth] are what the pot's base has to fit in, [clearanceAbove] is how high
 * the space is up to the next board, the ceiling or a sill (infinite when nothing is above), and [hangRoom] is how far below the surface's top the air is free, for stems that trail over an edge.
 */
public class Surface(
    public val width: Float,
    public val depth: Float,
    public val clearanceAbove: Float = Float.POSITIVE_INFINITY,
    public val hangRoom: Float = Float.POSITIVE_INFINITY,
) {
    init {
        require(width > 0f && depth > 0f && clearanceAbove > 0f && hangRoom >= 0f) { "a surface has a size" }
    }
}

/** The pot a plant stands in, in metres: [width] across its base (the pot is round, so its depth is the same) and [height] from its feet to the soil. */
public class PotSize(public val width: Float, public val height: Float) {
    init {
        require(width > 0f && height >= 0f) { "a pot has a size" }
    }
}

/** Whether a potted plant can stand somewhere, and if not, why. */
public enum class PlantFit { Fits, TooTall, TooWide, TooDeep, HangsTooFar }

/** How tall it stands above the soil at [growth] (0 a seedling to 1 full size): a seedling is a quarter of its height. */
public fun PlantSpecies.heightAt(growth: Float): Float = matureHeight * (0.25f + 0.75f * growth.coerceIn(0f, 1f))

/**
 * Whether this plant in [pot] fits on [surface]. The pot must fit the surface's width and depth; the pot and the plant together must clear what is above ([Surface.clearanceAbove]); stems that
 * hang must have room below ([Surface.hangRoom]). It is checked at [growth] 1 by default, the full size, so a plant that fits now will not bump the board above it as it grows. Leaves may
 * overhang the pot and the edge, so [PlantSpecies.spread] does not count here (the game uses it to keep two crowns from sharing one place).
 */
public fun PlantSpecies.fit(surface: Surface, pot: PotSize, growth: Float = 1f): PlantFit = when {
    pot.width > surface.width -> PlantFit.TooWide
    pot.width > surface.depth -> PlantFit.TooDeep
    pot.height + heightAt(growth) > surface.clearanceAbove -> PlantFit.TooTall
    hang > surface.hangRoom -> PlantFit.HangsTooFar
    else -> PlantFit.Fits
}
