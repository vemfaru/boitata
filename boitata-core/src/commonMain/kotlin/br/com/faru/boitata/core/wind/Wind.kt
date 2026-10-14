package br.com.faru.boitata.core.wind

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * What the wind is doing at one place and moment: [x] is the push along the ground, signed (positive blows to the right), with 1 a strong steady wind.
 */
public class WindSample(public val x: Float) {
    /** How hard it blows, whichever way. */
    public val strength: Float get() = abs(x)
}

/**
 * The wind of a scene: a source that blows a way, with gusts that come and go and a little turbulence, and that travels across the scene so a gust reaches one side before the other.
 *
 * It is a pure function of time and place ([at]), so the same wind gives the same picture every time and a test can pin it. What a thing does with the wind is [WindResponse]'s job.
 *
 * The parameters follow the ones game engines use for a wind zone (a main strength, a gust magnitude and how often gusts come, a turbulence) with a direction and a travel time added.
 */
public class Wind(
    /** The average strength, 0 (calm) to 1 (a storm). */
    public val speed: Float = 0f,
    /** Which way and how fully: -1 blows to the left, +1 to the right, 0 not at all. */
    public val direction: Float = 1f,
    /** How much the strength swings with gusts, 0 (steady) to 1 (violent): at 1 it swings between half and one and a half times [speed]. The average stays [speed]. */
    public val gustiness: Float = 0.3f,
    /** About how many seconds pass between one gust and the next. */
    public val gustPeriod: Float = 6f,
    /** Quick small variation on top of the gusts, 0 (smooth) to 1 (choppy). */
    public val turbulence: Float = 0.2f,
    /** How long a gust takes to cross the whole scene, in seconds; 0 makes the whole scene gust at once. */
    public val travelSeconds: Float = 1.6f,
) {
    /**
     * The wind at [place] (0 at the left of the scene to 1 at the right) at [seconds].
     * A gust arrives at the upwind side first and crosses to the other in [travelSeconds].
     */
    public fun at(seconds: Float, place: Float = 0f): WindSample {
        val s = speed.coerceIn(0f, 1f)
        val d = direction.coerceIn(-1f, 1f)
        if (s <= 0f || d == 0f) return CALM_SAMPLE
        val along = if (d >= 0f) place.coerceIn(0f, 1f) else 1f - place.coerceIn(0f, 1f)
        val tau = seconds - along * travelSeconds
        val a = 2f * PI.toFloat() * tau / gustPeriod.coerceAtLeast(0.5f)
        // Three slow waves of different lengths add up to a gust that is not a plain sine; 0.5 is its average.
        val gust = (0.5f + 0.28f * sin(a) + 0.2f * sin(2.3f * a + 1.1f) + 0.12f * sin(4.1f * a + 0.4f)).coerceIn(0f, 1f)
        val g = gustiness.coerceIn(0f, 1f)
        val chop = turbulence.coerceIn(0f, 1f) * 0.25f * sin(7.3f * tau + 0.5f) * sin(11.7f * tau + 1.9f)
        val magnitude = (s * (1f - 0.5f * g + g * gust + chop)).coerceAtLeast(0f)
        return WindSample(d * magnitude)
    }

    /** The same wind with some parameters changed. */
    public fun copy(
        speed: Float = this.speed, direction: Float = this.direction, gustiness: Float = this.gustiness,
        gustPeriod: Float = this.gustPeriod, turbulence: Float = this.turbulence, travelSeconds: Float = this.travelSeconds,
    ): Wind = Wind(speed, direction, gustiness, gustPeriod, turbulence, travelSeconds)

    public companion object {
        private val CALM_SAMPLE = WindSample(0f)

        public val Calm: Wind = Wind(speed = 0f)
        public val Breeze: Wind = Wind(speed = 0.22f, gustiness = 0.35f)
        public val Windy: Wind = Wind(speed = 0.5f, gustiness = 0.5f, gustPeriod = 5f, turbulence = 0.3f)
        public val Gale: Wind = Wind(speed = 0.85f, gustiness = 0.7f, gustPeriod = 3.5f, turbulence = 0.5f, travelSeconds = 1.1f)
    }
}
