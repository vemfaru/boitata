package br.com.faru.boitata.core.cues

import br.com.faru.boitata.core.sky.SkyWeather

/**
 * How loud each ambient bed should be, 0..1, for the weather: the numbers a sound engine mixes, worked out from the same [SkyWeather] that draws the
 * sky, not from what happens to be on screen.
 */
public class AmbienceLevels(
    /** Rain on the roof and the leaves. */
    public val rain: Float,
    /** Wind in the trees. */
    public val wind: Float,
    /** How much the air muffles everything (fog and heavy cloud take the high sounds): 0 clear, 1 thick. Use it to lower a filter's cutoff. */
    public val muffle: Float,
    /** The fire's crackle: not weather, so it is whatever the app says (see [AmbienceLevels.of]'s `fire`). */
    public val fire: Float = 0f,
) {
    public companion object {
        /** The levels for [weather], with a [fire] of 0..1 if one burns in the scene. */
        public fun of(weather: SkyWeather, fire: Float = 0f): AmbienceLevels = AmbienceLevels(
            rain = (weather.precipitation * (0.55f + 0.45f * weather.storm)).coerceIn(0f, 1f),
            wind = (0.12f + 0.45f * weather.storm + 0.2f * weather.precipitation + 0.1f * weather.cloud).coerceIn(0f, 1f),
            muffle = (0.7f * weather.fog + 0.2f * weather.cloud).coerceIn(0f, 1f),
            fire = fire.coerceIn(0f, 1f),
        )
    }
}
