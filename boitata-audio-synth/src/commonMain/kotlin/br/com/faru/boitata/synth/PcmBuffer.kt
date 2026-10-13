package br.com.faru.boitata.synth

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A mono sound as numbers: [samples] from -1 to 1 at [sampleRate] samples a second.
 *
 * Everything in this module is a function of its arguments (a distance, a seed, a length), so the same call gives the same sound on every platform
 * and a test can pin it. A platform's audio engine only has to play the numbers.
 */
public class PcmBuffer(public val samples: FloatArray, public val sampleRate: Int = SAMPLE_RATE) {
    public val seconds: Float get() = samples.size.toFloat() / sampleRate

    /** The loudest sample, 0..1 (more than 1 would clip). */
    public val peak: Float get() = samples.maxOfOrNull { abs(it) } ?: 0f

    /** The average level (root mean square), a fair measure of how loud it sounds. */
    public val rms: Float get() = if (samples.isEmpty()) 0f else sqrt(samples.fold(0.0) { a, s -> a + s * s } / samples.size).toFloat()

    /** 16-bit signed samples, the common format for a platform's engine. */
    public fun toShorts(): ShortArray = ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * 32767f).roundToInt().toShort() }

    public companion object {
        public const val SAMPLE_RATE: Int = 44_100

        public fun fromShorts(shorts: ShortArray, sampleRate: Int = SAMPLE_RATE): PcmBuffer =
            PcmBuffer(FloatArray(shorts.size) { shorts[it] / 32768f }, sampleRate)
    }
}

/** Rounds a sound off instead of clipping it: smooth for small values, never past -1..1. */
internal fun softClip(x: Float, drive: Float = 1.3f): Float = kotlin.math.tanh(x * drive) / kotlin.math.tanh(drive)

/**
 * Makes [raw] (which must be [fade] samples longer than the loop) loop without a click: the last [fade] samples are crossfaded into the first, so the
 * end of the loop runs straight into its start.
 */
internal fun seamless(raw: FloatArray, fade: Int): FloatArray {
    val n = raw.size - fade
    require(n > fade && fade > 0) { "the loop must be longer than its crossfade" }
    val out = raw.copyOf(n)
    for (i in 0 until fade) {
        val w = i.toFloat() / fade * (kotlin.math.PI.toFloat() / 2f)
        out[i] = raw[i] * kotlin.math.sin(w) + raw[n + i] * kotlin.math.cos(w)
    }
    return out
}
