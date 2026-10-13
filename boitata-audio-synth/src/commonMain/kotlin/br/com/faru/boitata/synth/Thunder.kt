package br.com.faru.boitata.synth

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Thunder, made in code. Close, it is a sharp crack (a burst of noise) before a long rolling rumble; far off it is only the rumble, lower, softer and
 * longer, because the air takes the high sounds first. The rumble is brown noise (a leaky sum of white noise) under the envelope of several rolls.
 *
 * The same [seed] makes the same peal. Give each strike its own seed (the strike's index, say) so a storm does not repeat one sound.
 */
public object Thunder {
    /** How long the peal lasts for a strike [distanceKm] away: a couple of seconds close, up to seven far off. */
    public fun seconds(distanceKm: Float): Float = (2.4f + 0.75f * distanceKm).coerceAtMost(7f)

    public fun render(distanceKm: Float, seed: Long = distanceKm.toBits().toLong(), sampleRate: Int = PcmBuffer.SAMPLE_RATE): PcmBuffer {
        val random = Random(seed)
        val d = distanceKm.coerceIn(0.1f, 12f)
        val n = (seconds(d) * sampleRate).toInt()
        val out = FloatArray(n)
        // The rolls: bumps of rumble at random moments, each swelling and dying away.
        val rolls = 3 + random.nextInt(3)
        val bumpAt = FloatArray(rolls) { if (it == 0) 0f else random.nextFloat() * 0.7f * seconds(d) }
        val bumpGain = FloatArray(rolls) { if (it == 0) 1f else 0.35f + 0.5f * random.nextFloat() }
        val bumpLength = FloatArray(rolls) { 0.8f + 1.6f * random.nextFloat() + 0.2f * d }
        // The air takes the highs: the farther, the lower the rumble's own cutoff (a one-pole low-pass on the noise).
        val alpha = (0.2 - 0.014 * d).coerceIn(0.035, 0.2)
        var brown = 0.0
        var low = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / sampleRate
            val white = random.nextDouble() * 2 - 1
            brown = (brown + white * 0.02) * 0.9985
            low += (brown - low) * alpha
            var envelope = 0f
            for (k in 0 until rolls) {
                val x = t - bumpAt[k]
                if (x >= 0f) envelope += bumpGain[k] * (1f - exp(-x / 0.12f)) * exp(-x / bumpLength[k])
            }
            out[i] = (low * 9.0 * envelope).toFloat()
        }
        // The rumble is brought to a fixed level first (lower when a crack goes over it), then the crack is added on top.
        val crack = (1f - d / 3.2f).coerceIn(0f, 1f)
        val rumblePeak = out.maxOfOrNull { kotlin.math.abs(it) }?.takeIf { it > 1e-6f } ?: 1f
        val rumbleScale = (0.9f - 0.4f * crack) / rumblePeak
        for (i in 0 until n) out[i] *= rumbleScale
        if (crack > 0f) {
            var hp = 0.0
            val m = (0.16 * sampleRate).toInt().coerceAtMost(n)
            for (i in 0 until m) {
                val t = i.toFloat() / sampleRate
                val white = random.nextDouble() * 2 - 1
                hp += (white - hp) * 0.7
                out[i] += ((white - hp * 0.2) * exp(-t / 0.018) * 0.9 * crack + sin(2 * PI * 70 * t) * exp(-t / 0.05) * 0.5 * crack).toFloat()
            }
        }
        // Softer with distance, rounded off rather than clipped, and faded out at the end.
        val gain = 1f / (1f + 0.12f * d)
        for (i in 0 until n) {
            val fade = ((n - i).toFloat() / (0.4f * sampleRate)).coerceIn(0f, 1f)
            out[i] = softClip(out[i] * gain * fade)
        }
        return PcmBuffer(out, sampleRate)
    }
}
