package br.com.faru.boitata.plants

import br.com.faru.boitata.core.sky.SkyState

/**
 * What the sky and the air do to a plant at one moment, boiled down to the few numbers the plant model reads. A plain value: the same sky and wind give the same environment.
 */
public class PlantEnvironment(
    /** How bright the day is, 0..1 (already dimmed by cloud). */
    public val daylight: Float,
    /** The sun's light on the plant, 0..1: the sun's strength times how high it stands, so a low sun gives less than a high one even when clear. */
    public val light: Float,
    /** Which side the light comes from: -1 left, 0 overhead or none, 1 right. */
    public val lightSide: Float,
    /** Rain on the plant and the ground, 0..1. */
    public val rain: Float,
    /** How hot it is, 0 cold night to 1 a clear hot noon. */
    public val heat: Float,
    /** How hard the wind blows, 0..1 (its strength, not its direction). */
    public val wind: Float,
) {
    public companion object {
        /** The environment under [sky] with the wind at [windStrength] (0..1). */
        public fun of(sky: SkyState, windStrength: Float = 0f): PlantEnvironment {
            val elevation = sky.sunElevation.coerceAtLeast(0f)
            val sunny = sky.lightStrength * (if (sky.lightFrom.up && sky.sunElevation > 0f) 1f else 0f)
            val light = (sunny * (0.35f + 0.65f * elevation)).coerceIn(0f, 1f)
            val side = if (light > 0f) ((sky.lightFrom.x - 0.5f) * 2f).coerceIn(-1f, 1f) else 0f
            val heat = (0.2f + 0.7f * elevation * sky.weather.clearness - 0.15f * sky.weather.precipitation).coerceIn(0f, 1f)
            return PlantEnvironment(sky.daylight, light, side, sky.weather.precipitation, heat, windStrength.coerceIn(0f, 1f))
        }
    }
}
