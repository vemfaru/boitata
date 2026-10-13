package br.com.faru.boitata.core.clock

import androidx.compose.ui.graphics.FilterQuality

/**
 * How much a scene may spend. Every effect scales its work by this one setting, so a heavy scene gets lighter in one place and the whole look
 * degrades together. It is what [QualityGovernor] steps up and down when frames run long.
 */
public enum class Quality(
    /** A share of the full particle count to draw (rain drops, flakes, embers, dust): 1 is everything. */
    public val particles: Float,
    /** Whether to draw the soft radial glows (the costly gradients). Off, a fire is just flame and a few carved rings. */
    public val softGlow: Boolean,
    /** How pictures are sampled when scaled. */
    public val imageFilter: FilterQuality,
) {
    Low(particles = 0.35f, softGlow = false, imageFilter = FilterQuality.Low),
    Medium(particles = 0.65f, softGlow = true, imageFilter = FilterQuality.Medium),
    High(particles = 1f, softGlow = true, imageFilter = FilterQuality.Medium);

    /** [count] scaled to this quality, never below 1 when [count] is above 0. */
    public fun scaled(count: Int): Int = if (count <= 0) 0 else (count * particles).toInt().coerceAtLeast(1)

    internal fun lower(): Quality = if (this == High) Medium else Low
    internal fun higher(): Quality = if (this == Low) Medium else High
}

/**
 * Watches how long frames take and says when to spend less, and when there is room to spend more again.
 *
 * Pure arithmetic over frame times, no clock and no drawing, so it can be tested with made-up frames. It steps down after about three-quarters of a
 * second of slow frames, and steps back up only after several calm seconds, waiting longer each time it had to step down again (so a scene right on
 * the edge settles instead of flickering between two looks). Hitches (a frame over a quarter of a second: a pause, a tab in the background) are
 * ignored: they say nothing about how heavy the scene is.
 */
public class QualityGovernor(
    private val targetFrameSeconds: Float = 1f / 60f,
    start: Quality = Quality.High,
) {
    public var quality: Quality = start
        private set

    private var average = targetFrameSeconds
    private var slowFrames = 0
    private var calmFrames = 0
    private var elapsed = 0f
    private var holdUntil = 0f
    private var hold = INITIAL_HOLD
    private var lastStepUpAt = -1000f

    /** Tells the governor that a frame took [frameSeconds]; returns the quality to draw the next one at. */
    public fun onFrame(frameSeconds: Float): Quality {
        if (frameSeconds <= 0f || frameSeconds > HITCH) return quality
        elapsed += frameSeconds
        average += (frameSeconds - average) * SMOOTHING
        if (average > targetFrameSeconds * SLOW) { slowFrames++; calmFrames = 0 }
        else if (average < targetFrameSeconds * CALM) { calmFrames++; slowFrames = 0 }
        else { slowFrames = 0; calmFrames = 0 }

        if (slowFrames >= SLOW_FRAMES && quality != Quality.Low) {
            // Falling soon after climbing means that level is too much: wait longer before trying it again.
            if (elapsed - lastStepUpAt < RELAPSE) hold = (hold * 2f).coerceAtMost(MAX_HOLD)
            quality = quality.lower()
            holdUntil = elapsed + hold
            slowFrames = 0
            average = targetFrameSeconds
        } else if (calmFrames >= CALM_FRAMES && quality != Quality.High && elapsed >= holdUntil) {
            quality = quality.higher()
            lastStepUpAt = elapsed
            calmFrames = 0
        }
        return quality
    }

    private companion object {
        const val HITCH = 0.25f
        const val SMOOTHING = 0.1f
        const val SLOW = 1.3f
        const val CALM = 1.08f
        const val SLOW_FRAMES = 45
        const val CALM_FRAMES = 300
        const val INITIAL_HOLD = 8f
        const val MAX_HOLD = 64f
        const val RELAPSE = 12f
    }
}
