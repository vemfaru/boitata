package br.com.faru.boitata.synth

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Beds of sound that play on a loop and get louder or softer with the weather: rain, wind, a fire. Each is made in code, with a seed, and ends where
 * it begins (no click when it wraps). Make one loop per bed, play it forever, and set its gain from `AmbienceLevels`.
 */
public object Ambience {
    private const val CROSSFADE_SECONDS = 1.0f

    /**
     * Rain: a bright hiss (noise with the lows taken out) and a scatter of drops, each a tiny tick. [density] 0..1 sets how many drops there are;
     * the hiss is the same whatever the density, scaled by the bed's gain.
     */
    public fun rain(seconds: Float = 8f, density: Float = 0.7f, seed: Long = 31L, sampleRate: Int = PcmBuffer.SAMPLE_RATE): PcmBuffer {
        val random = Random(seed)
        val fade = (CROSSFADE_SECONDS * sampleRate).toInt()
        val n = (seconds * sampleRate).toInt() + fade
        val out = FloatArray(n)
        var lowpass = 0.0
        var previous = 0.0
        for (i in 0 until n) {
            val white = random.nextDouble() * 2 - 1
            lowpass += (white - lowpass) * 0.12
            val bright = white - lowpass                     // the lows out: a hiss
            out[i] = (bright * 0.22).toFloat()
            previous = white
        }
        // Drops: a short tick whose pitch and strength vary, a few dozen a second at full density.
        val drops = (seconds * (25 + 220 * density.coerceIn(0f, 1f))).toInt()
        repeat(drops) {
            val at = random.nextInt(n - 800)
            val pitch = 1800f + 3200f * random.nextFloat()
            val gain = 0.15f + 0.45f * random.nextFloat()
            for (k in 0 until 700) {
                val t = k.toFloat() / sampleRate
                out[at + k] += (sin(2 * PI * pitch * t) * exp(-t / 0.0035) * gain).toFloat()
            }
        }
        return PcmBuffer(normalise(seamless(out, fade), 0.7f), sampleRate)
    }

    /**
     * Wind: low noise whose loudness swells and falls in gusts. The gusts are slow waves that repeat a whole number of times in the loop, so they
     * wrap too.
     */
    public fun wind(seconds: Float = 12f, seed: Long = 47L, sampleRate: Int = PcmBuffer.SAMPLE_RATE): PcmBuffer {
        val random = Random(seed)
        val fade = (CROSSFADE_SECONDS * sampleRate).toInt()
        val total = (seconds * sampleRate).toInt()
        val n = total + fade
        val out = FloatArray(n)
        val gusts = 3 + random.nextInt(3)
        val phases = FloatArray(gusts) { random.nextFloat() * 2f * PI.toFloat() }
        var low = 0.0
        var lower = 0.0
        for (i in 0 until n) {
            val white = random.nextDouble() * 2 - 1
            low += (white - low) * 0.05
            lower += (low - lower) * 0.12
            var gust = 0f
            for (k in 0 until gusts) gust += sin(2f * PI.toFloat() * (k + 1) * i / total + phases[k]) / (k + 1)
            val level = 0.45f + 0.35f * gust
            out[i] = ((low * 0.6 + lower * 1.4) * level.coerceAtLeast(0.05f)).toFloat()
        }
        return PcmBuffer(normalise(seamless(out, fade), 0.6f), sampleRate)
    }

    /** A fire: a low roar and the pops and crackles of burning wood, each a quick snap at its own pitch. */
    public fun fire(seconds: Float = 8f, seed: Long = 59L, sampleRate: Int = PcmBuffer.SAMPLE_RATE): PcmBuffer {
        val random = Random(seed)
        val fade = (CROSSFADE_SECONDS * sampleRate).toInt()
        val n = (seconds * sampleRate).toInt() + fade
        val out = FloatArray(n)
        var brown = 0.0
        for (i in 0 until n) {
            val white = random.nextDouble() * 2 - 1
            brown = (brown + white * 0.02) * 0.998
            out[i] = (brown * 3.0).toFloat()
        }
        val pops = (seconds * 14).toInt()
        repeat(pops) {
            val at = random.nextInt(n - 1500)
            val pitch = 600f + 2600f * random.nextFloat()
            val gain = 0.2f + 0.7f * random.nextFloat() * random.nextFloat()
            val length = 200 + random.nextInt(900)
            for (k in 0 until length) {
                val t = k.toFloat() / sampleRate
                val snap = (random.nextDouble() * 2 - 1) * exp(-t / 0.002) + sin(2 * PI * pitch * t) * exp(-t / 0.006)
                out[at + k] += (snap * gain * 0.5).toFloat()
            }
        }
        return PcmBuffer(normalise(seamless(out, fade), 0.65f), sampleRate)
    }

    /** Scales so the loudest sample is [peak]. */
    private fun normalise(samples: FloatArray, peak: Float): FloatArray {
        val max = samples.maxOfOrNull { kotlin.math.abs(it) }?.takeIf { it > 1e-6f } ?: return samples
        val k = peak / max
        for (i in samples.indices) samples[i] = softClip(samples[i] * k, 1.1f) * 0.95f
        return samples
    }
}
