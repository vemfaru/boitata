package br.com.faru.boitata.core.light

import kotlin.math.sqrt

/**
 * How much light a surface gets from a point light, as numbers (pure, so it can be tested and sampled into a gradient).
 *
 * A surface point at distance `r` from the light, whose normal makes angle theta with the direction to the light, gets `strength * falloff(r) * cos(theta)`: brighter the nearer it is, and
 * less the more the surface turns away (Lambert's cosine law). With the light a perpendicular distance `d` from a flat surface and `rho` along the surface from the foot of that perpendicular,
 * `r = sqrt(d*d + rho*rho)` and `cos(theta) = d / r`, so the light on a wall or a floor is a function of `rho` alone: a round pool whose size and shape depend on how far the light is from the surface.
 */
public object LightMath {
    /** 1 at the light, falling smoothly to 0 at [reach] (the same curve the lights use to light things). */
    public fun falloff(distance: Float, reach: Float): Float {
        val x = (1f - distance / reach).coerceIn(0f, 1f)
        val s = x * x * (3f - 2f * x)
        return s * s
    }

    /** The light on a surface [perpendicular] away from the light, [along] the surface from the point nearest the light: strength 1 at the light's own distance and straight on. */
    public fun onSurface(perpendicular: Float, along: Float, reach: Float): Float {
        val r = sqrt(perpendicular * perpendicular + along * along)
        if (r <= 0.0001f) return 1f
        return falloff(r, reach) * (perpendicular / r)
    }

    /** How far along a surface [perpendicular] from the light its light reaches: where the distance to the light is the whole [reach]. 0 if the surface is farther than that. */
    public fun surfaceRadius(perpendicular: Float, reach: Float): Float = if (perpendicular >= reach) 0f else sqrt(reach * reach - perpendicular * perpendicular)
}
