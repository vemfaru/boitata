package br.com.faru.boitata.synth

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * A rifle shot, made in code: a sharp crack (a burst of bright noise, gone in a few hundredths of a second), the boom under it (a low tone that falls in pitch), and the echo off the hills, a few
 * soft repeats that lose their highs as they go. The farther away it is heard, the quieter and duller it is and the later and softer the echo comes. The same [seed] makes the same shot.
 */
public object Gunshot {
    public fun seconds(distanceKm: Float): Float = 1.2f + 0.8f * distanceKm.coerceIn(0f, 3f)

    public fun render(distanceKm: Float = 0.05f, seed: Long = 7L, sampleRate: Int = PcmBuffer.SAMPLE_RATE): PcmBuffer {
        val random = Random(seed)
        val d = distanceKm.coerceIn(0f, 3f)
        val n = (seconds(d) * sampleRate).toInt()
        val out = FloatArray(n)
        // The air takes the highs with distance: the crack's own low-pass opens less the farther it goes.
        val bright = (0.9 - 0.25 * d).coerceIn(0.2, 0.9)
        var hp = 0.0
        var lp = 0.0
        val dry = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / sampleRate
            val white = random.nextDouble() * 2 - 1
            hp += (white - hp) * 0.6
            lp += (white - lp) * bright * 0.5
            val crack = (white - hp * 0.3) * exp(-t / 0.012) * 0.95 + lp * exp(-t / 0.045) * 0.5
            // The boom: a tone that falls from 110 to 45 Hz as it dies.
            val boom = sin(2 * PI * (45.0 + 65.0 * exp(-t / 0.08)) * t) * exp(-t / 0.16) * 0.8
            dry[i] = (crack + boom).toFloat()
        }
        for (i in 0 until n) out[i] = dry[i]
        // The echo off the hills: three repeats, each later, softer and duller.
        val taps = floatArrayOf(0.23f, 0.51f, 0.9f)
        for ((k, tap) in taps.withIndex()) {
            val delay = ((tap + 0.12f * d) * sampleRate).toInt()
            val gain = 0.42f / (k + 1) / (1f + d)
            var smooth = 0f
            for (i in delay until n) {
                smooth += (dry[i - delay] - smooth) * (0.35f / (k + 1) + 0.08f)
                out[i] += smooth * gain
            }
        }
        val gain = 1f / (1f + 0.5f * d)
        for (i in 0 until n) {
            val fade = ((n - i).toFloat() / (0.3f * sampleRate)).coerceIn(0f, 1f)
            out[i] = softClip(out[i] * gain * fade).coerceIn(-1f, 1f)
        }
        return PcmBuffer(out, sampleRate)
    }
}
