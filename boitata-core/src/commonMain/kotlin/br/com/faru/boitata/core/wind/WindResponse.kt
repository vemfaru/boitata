package br.com.faru.boitata.core.wind

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * What one thing does with the wind. There are three kinds, because a falling snowflake and a rooted palm answer it differently:
 *
 * - **Free** things float or fall (rain, snow, leaves, embers, dust, fog, clouds). They always go with the wind: [weight] (0 a feather, 1 a stone) says how little, and [surface]
 *   (0 a needle, 1 a sheet of paper) how much it catches.
 * - **Anchored** things are fixed at their foot but flexible (trees, palms, grass, a flame, a banner). They lean and sway round their base: [stiffness] (0 limp, 1 a post) says how much they resist.
 * - **Rigid** things (a crate, a rock) do not move.
 *
 * [influence] is a plain multiplier on top, like the one a particle system has for wind zones: 1 as is, 0 ignores the wind, 2 doubles it. Use it to turn a single thing off.
 * A heavy or stiff thing is also slow: it feels a gust a moment late and smoothed out, a light one jerks with it. (All of it is arithmetic on time, with no state.)
 */
public class WindResponse private constructor(
    public val kind: Kind,
    public val influence: Float,
    public val weight: Float,
    public val surface: Float,
    public val stiffness: Float,
) {
    public enum class Kind { Free, Anchored, Rigid }

    /** How long this thing takes to answer a change in the wind, in seconds. */
    public val lagSeconds: Float
        get() = when (kind) {
            Kind.Free -> 0.9f * weight
            Kind.Anchored -> 0.55f * stiffness
            Kind.Rigid -> 0f
        }

    /**
     * How much a free thing is carried, as a multiple of the wind's own push, for things that drift over a long time (fog, clouds): the same as [drift] without the time. 0 for anchored and rigid things.
     */
    public val driftGain: Float
        get() = if (kind == Kind.Free) influence * surface / (0.1f + weight) else 0f

    /** The same response with another [influence]: `.withInfluence(0f)` makes a thing ignore the wind. */
    public fun withInfluence(value: Float): WindResponse = WindResponse(kind, value, weight, surface, stiffness)

    /** The wind as this thing feels it at [place] and [seconds]: the real wind, a little late and smoothed, in proportion to how heavy or stiff it is. */
    public fun felt(wind: Wind, seconds: Float, place: Float = 0f): Float {
        if (kind == Kind.Rigid || influence == 0f) return 0f
        val lag = lagSeconds
        if (lag < 0.01f) return wind.at(seconds, place).x
        // The mean of the wind over the last `lag` seconds: four samples stand for the smoothing.
        var sum = 0f
        for (i in 0..3) sum += wind.at(seconds - lag * i / 3f, place).x
        return sum / 4f
    }

    /**
     * For a free thing: how far it is blown sideways, in units where 1 is the wind itself on something of average catch. A snowflake or a leaf gets several times the wind's strength,
     * a raindrop most of it, a pebble almost none.
     */
    public fun drift(wind: Wind, seconds: Float, place: Float = 0f): Float {
        if (kind == Kind.Rigid || influence == 0f) return 0f
        return influence * (surface / (0.1f + weight)) * felt(wind, seconds, place)
    }

    /**
     * For an anchored thing: how far it leans, -1 (all the way to the left) to 1 (to the right), as a share of its greatest bend; a little flutter at its own pace is added once
     * the wind picks up, and [phase] (a number per thing) keeps neighbours from swaying in step.
     */
    public fun bend(wind: Wind, seconds: Float, place: Float = 0f, phase: Float = 0f): Float {
        if (kind != Kind.Anchored || influence == 0f) return 0f
        val push = felt(wind, seconds, place)
        // Limp grass is bent nearly all the way by a fresh breeze (0.5); a pine is bent a third of the way; a trunk barely.
        val lean = (influence * push / (0.3f + stiffness * 2.2f)).coerceIn(-1f, 1f)
        // Lighter things flutter faster; the flutter grows with the wind and is small beside the lean.
        val rate = 0.9f / (0.3f + stiffness)
        val flutter = 0.12f * abs(push).coerceAtMost(1f) * sin(2f * PI.toFloat() * rate * seconds + phase * 6.2832f) * influence
        return (lean + flutter).coerceIn(-1.2f, 1.2f)
    }

    public companion object {
        /** A thing that floats or falls, [weight] 0..1 (feather to stone) and [surface] 0..1 (needle to sheet). */
        public fun free(weight: Float, surface: Float, influence: Float = 1f): WindResponse = WindResponse(Kind.Free, influence, weight.coerceIn(0f, 1f), surface.coerceIn(0f, 1f), 0f)

        /** A thing fixed at its foot but flexible, [stiffness] 0..1 (limp to post). */
        public fun anchored(stiffness: Float, influence: Float = 1f): WindResponse = WindResponse(Kind.Anchored, influence, 1f, 1f, stiffness.coerceIn(0f, 1f))

        /** A thing the wind does not move. */
        public val Rigid: WindResponse = WindResponse(Kind.Rigid, 0f, 1f, 0f, 1f)

        // Things that float or fall.
        public val Raindrop: WindResponse = free(weight = 0.6f, surface = 0.6f)
        public val Snowflake: WindResponse = free(weight = 0.12f, surface = 0.8f)
        public val Leaf: WindResponse = free(weight = 0.1f, surface = 1f)
        public val Ember: WindResponse = free(weight = 0.12f, surface = 0.45f)
        public val Dust: WindResponse = free(weight = 0.05f, surface = 0.6f)
        public val Fog: WindResponse = free(weight = 0.35f, surface = 0.7f)
        public val LowCloud: WindResponse = free(weight = 0.55f, surface = 0.8f)
        public val HighCloud: WindResponse = free(weight = 0.3f, surface = 0.9f)

        // Things that are fixed at their foot.
        public val Grass: WindResponse = anchored(stiffness = 0.12f)
        public val Plant: WindResponse = anchored(stiffness = 0.2f)
        public val Palm: WindResponse = anchored(stiffness = 0.3f)
        public val Pine: WindResponse = anchored(stiffness = 0.55f)
        public val Trunk: WindResponse = anchored(stiffness = 0.95f)
        public val Flame: WindResponse = anchored(stiffness = 0.1f)
    }
}
