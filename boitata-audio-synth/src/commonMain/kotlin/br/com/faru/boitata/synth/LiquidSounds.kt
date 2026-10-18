package br.com.faru.boitata.synth

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * The sounds of liquids and the glass that holds them, made in code: a gulp, a drip, a splash, a stream, a soak, a boil, a container set down and one knocked over. Like the rest of this module every one is a function
 * of its arguments and a [seed]: the same call gives the same sound on every platform, so a test can pin it. The loops ([stream], [soak], [boil]) run seamlessly.
 */
public object LiquidSounds {
    private const val RATE = PcmBuffer.SAMPLE_RATE

    private fun n(seconds: Float): Int = (seconds * RATE).toInt()

    private fun finish(out: FloatArray, peak: Float): PcmBuffer {
        val top = out.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
        val k = if (top > 1e-6f) peak / top else 1f
        for (i in out.indices) out[i] = softClip(out[i] * k).coerceIn(-1f, 1f)
        return PcmBuffer(out)
    }

    /** A mouthful swallowed: two low glugs, the second a little softer, each a falling tone with a wash of water over it. */
    public fun gulp(seed: Long = 1L): PcmBuffer {
        val random = Random(seed)
        val out = FloatArray(n(0.34f))
        var lp = 0f
        for ((k, start) in floatArrayOf(0f, 0.15f).withIndex()) {
            val gain = if (k == 0) 1f else 0.7f
            for (i in n(start) until out.size) {
                val t = (i - n(start)).toFloat() / RATE
                val f = 130f + 140f * exp(-t / 0.05f)
                val env = exp(-t / 0.07f)
                lp += ((random.nextFloat() * 2f - 1f) - lp) * 0.08f
                out[i] += (sin(2.0 * PI * f * t).toFloat() * 0.8f + lp * 0.9f) * env * gain
            }
        }
        return finish(out, 0.7f)
    }

    /** A drop landing: a bright "plink" that rises a little as the bubble it makes closes. [size] 0..1: a big drop is lower and rings longer. */
    public fun drip(size: Float = 0.3f, seed: Long = 2L): PcmBuffer {
        val random = Random(seed)
        val s = size.coerceIn(0f, 1f)
        val out = FloatArray(n(0.24f))
        val f0 = 1500f - 800f * s
        val decay = 0.025f + 0.04f * s
        for (i in out.indices) {
            val t = i.toFloat() / RATE
            val f = f0 * (1f + 2.5f * t)
            val click = if (t < 0.003f) (random.nextFloat() * 2f - 1f) * 0.4f else 0f
            out[i] = sin(2.0 * PI * f * t).toFloat() * exp(-t / decay) + click
        }
        return finish(out, 0.6f)
    }

    /** Liquid landing in liquid: a burst of water noise that opens at once and dies in a few tenths of a second, with a few bubbles in its tail. */
    public fun splash(seed: Long = 3L): PcmBuffer {
        val random = Random(seed)
        val out = FloatArray(n(0.42f))
        var lp = 0f; var hp = 0f
        for (i in out.indices) {
            val t = i.toFloat() / RATE
            val white = random.nextFloat() * 2f - 1f
            lp += (white - lp) * 0.35f
            hp += (lp - hp) * 0.04f
            val band = lp - hp
            val env = (t / 0.004f).coerceAtMost(1f) * exp(-t / 0.11f)
            out[i] = band * env
        }
        // Bubbles in the tail.
        repeat(4) { k ->
            val start = 0.06f + 0.07f * k + 0.02f * random.nextFloat()
            val f = 600f + 700f * random.nextFloat()
            for (i in n(start) until minOf(out.size, n(start + 0.05f))) {
                val t = (i - n(start)).toFloat() / RATE
                out[i] += sin(2.0 * PI * f * (1f + 3f * t) * t).toFloat() * exp(-t / 0.012f) * 0.25f
            }
        }
        return finish(out, 0.7f)
    }

    private fun loopOf(seconds: Float, fade: Float, make: (FloatArray) -> Unit): PcmBuffer {
        val fadeSamples = n(fade)
        val raw = FloatArray(n(seconds) + fadeSamples)
        make(raw)
        return PcmBuffer(seamless(raw, fadeSamples))
    }

    /** Liquid pouring or running over a lip, as a loop: a rushing of filtered noise whose brightness drifts, with a burble in it. */
    public fun stream(seed: Long = 4L): PcmBuffer {
        val random = Random(seed)
        val buffer = loopOf(2.0f, 0.2f) { raw ->
            var lp = 0f; var lp2 = 0f
            val phases = FloatArray(3) { random.nextFloat() * 6.28f }
            for (i in raw.indices) {
                val t = i.toFloat() / RATE
                val white = random.nextFloat() * 2f - 1f
                val k = 0.18f + 0.1f * sin(t * 2.3f + phases[0])
                lp += (white - lp) * k
                lp2 += (lp - lp2) * 0.5f
                val burble = sin(2.0 * PI * (520f + 260f * sin(t * 5.1f + phases[1]) + 90f * sin(t * 13f + phases[2])) * t).toFloat() * 0.07f * (0.5f + 0.5f * sin(t * 3.7f))
                raw[i] = lp2 * 0.9f + burble
            }
        }
        return finish(buffer.samples, 0.5f)
    }

    /** Water soaking into soil, as a loop: a soft, low trickle, much quieter and duller than a stream, with tiny crackles. */
    public fun soak(seed: Long = 5L): PcmBuffer {
        val random = Random(seed)
        val buffer = loopOf(2.0f, 0.2f) { raw ->
            var lp = 0f
            for (i in raw.indices) {
                val t = i.toFloat() / RATE
                lp += ((random.nextFloat() * 2f - 1f) - lp) * (0.05f + 0.02f * sin(t * 1.7f))
                val crackle = if (random.nextFloat() < 0.0006f) (random.nextFloat() - 0.5f) * 0.9f else 0f
                raw[i] = lp * 0.9f + crackle
            }
        }
        return finish(buffer.samples, 0.35f)
    }

    /** A liquid at the boil, as a loop: a low rumble and a scatter of quick bubble pops. */
    public fun boil(seed: Long = 6L): PcmBuffer {
        val random = Random(seed)
        val buffer = loopOf(2.0f, 0.2f) { raw ->
            var lp = 0f
            for (i in raw.indices) { lp += ((random.nextFloat() * 2f - 1f) - lp) * 0.02f; raw[i] = lp * 0.5f }
            repeat(40) {
                val start = random.nextInt(raw.size - n(0.04f))
                val f = 500f + 900f * random.nextFloat()
                val amp = 0.25f + 0.35f * random.nextFloat()
                for (j in 0 until n(0.04f)) {
                    val t = j.toFloat() / RATE
                    raw[start + j] += sin(2.0 * PI * f * (1f + 4f * t) * t).toFloat() * exp(-t / 0.012f) * amp
                }
            }
        }
        return finish(buffer.samples, 0.45f)
    }

    /**
     * A container set down: a short knock with a ring after it. [tone] 0..1 is what it is made of: 0 a clay pot (a dull, low thud that dies at once), 1 a glass (a higher, ringing tap with a second, inharmonic partial). [strength]
     * 0..1 is how hard.
     */
    public fun setDown(tone: Float = 0.7f, strength: Float = 0.6f, seed: Long = 7L): PcmBuffer {
        val random = Random(seed)
        val tn = tone.coerceIn(0f, 1f)
        val out = FloatArray(n(0.12f + 0.4f * tn))
        val f = 90f + 900f * tn
        val decay = 0.04f + 0.14f * tn
        for (i in out.indices) {
            val t = i.toFloat() / RATE
            val body = sin(2.0 * PI * f * t).toFloat() * exp(-t / decay)
            val ring = sin(2.0 * PI * f * 2.76 * t).toFloat() * exp(-t / (decay * 1.5f)) * 0.4f * tn
            val click = if (t < 0.003f) (random.nextFloat() * 2f - 1f) * 0.5f else 0f
            out[i] = body + ring + click
        }
        return finish(out, 0.35f + 0.5f * strength.coerceIn(0f, 1f))
    }

    /** A container knocked over: a heavy thud, then the rattle of it settling (a few quick taps, softer each time) and the slap of what was in it. */
    public fun knock(tone: Float = 0.7f, seed: Long = 8L): PcmBuffer {
        val random = Random(seed)
        val out = FloatArray(n(0.9f))
        val first = setDown(tone * 0.6f, 1f, seed).samples
        for (i in first.indices) if (i < out.size) out[i] += first[i] * 1.2f
        for ((k, at) in floatArrayOf(0.13f, 0.23f, 0.33f).withIndex()) {
            val tap = setDown(tone, 0.5f - 0.15f * k, seed + 1 + k).samples
            for (i in tap.indices) if (n(at) + i < out.size) out[n(at) + i] += tap[i] * (0.8f - 0.25f * k)
        }
        var lp = 0f
        for (i in out.indices) {
            val t = i.toFloat() / RATE
            lp += ((random.nextFloat() * 2f - 1f) - lp) * 0.2f
            out[i] += lp * exp(-t / 0.18f) * 0.5f * (t / 0.02f).coerceAtMost(1f)
        }
        return finish(out, 0.9f)
    }
}
