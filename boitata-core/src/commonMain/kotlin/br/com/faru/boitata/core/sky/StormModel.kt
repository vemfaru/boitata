package br.com.faru.boitata.core.sky

import br.com.faru.boitata.core.FastNoise
import kotlin.math.exp
import kotlin.math.max

/**
 * One lightning strike: when it hits, how far away it is, how bright, the flicker of its return strokes, where in the view its bolt is.
 * Thunder follows the flash by [thunderDelay]: the speed of sound is about 343 m per second, so a strike 3 km off is heard 9 s later.
 */
data class Strike(
    val index: Int,
    val at: Float,
    val distanceKm: Float,
    val brightness: Float,
    /** Seconds after [at] of each stroke (the first is 0) and how bright each is, 0..1. */
    val strokeAt: List<Float>,
    val strokeBrightness: List<Float>,
    /** Across the view, 0..1, and the seed of the bolt's shape. */
    val boltX: Float,
    val boltSeed: Long,
) {
    val thunderDelay: Float get() = distanceKm / SPEED_OF_SOUND_KM_PER_SECOND

    companion object {
        const val SPEED_OF_SOUND_KM_PER_SECOND = 0.343f
    }
}

/**
 * A storm's lightning as a seeded schedule: the same [seed] always strikes at the same moments, so a test pins it and the picture, the room's flash
 * and the thunder all read one timeline. [intensity] (0..1) is how busy the sky is: strikes come every few seconds in a violent storm, every
 * quarter of a minute in a weak one.
 */
class StormModel(private val seed: Long, private val intensity: Float = 1f) {
    private val noise = FastNoise(seed)
    private val times = ArrayList<Float>()

    private fun gap(i: Int): Float {
        val busy = 1f - 0.7f * intensity.coerceIn(0f, 1f)
        return (3.5f + 11f * noise.at(i, 0)) * (0.35f + busy)
    }

    /** The [index]th strike (0 first). */
    fun strike(index: Int): Strike {
        while (times.size <= index) times.add((times.lastOrNull() ?: (1.5f + 3f * noise.at(-1, 0))) + gap(times.size))
        val at = times[index]
        val strokes = 1 + (noise.at(index, 1) * 3f).toInt()
        val offsets = ArrayList<Float>().apply { add(0f); for (k in 1 until strokes) add(add(this, k, index)) }
        val brightness = (0.6f + 0.4f * noise.at(index, 2)) * (0.75f + 0.25f * intensity)
        return Strike(
            index = index, at = at,
            // Mostly some way off, the odd one close: squared so the near ones are rare.
            distanceKm = 0.25f + 6.5f * noise.at(index, 3) * noise.at(index, 3),
            brightness = brightness,
            strokeAt = offsets,
            strokeBrightness = offsets.mapIndexed { k, _ -> if (k == 0) 1f else 0.45f + 0.5f * noise.at(index * 7 + k, 4) },
            boltX = 0.15f + 0.7f * noise.at(index, 5),
            boltSeed = seed * 31 + index,
        )
    }

    private fun add(list: List<Float>, k: Int, index: Int): Float = list.last() + 0.06f + 0.22f * noise.at(index * 7 + k, 6)

    /** The strikes starting in (from, to]. */
    fun strikes(from: Float, to: Float): List<Strike> {
        val out = ArrayList<Strike>()
        var i = 0
        while (true) {
            val s = strike(i++)
            if (s.at > to) return out
            if (s.at > from) out.add(s)
        }
    }

    /** How bright the flash is at [t], 0..1: each stroke is a sharp flare that fades in a tenth of a second, and they add. */
    fun flash(t: Float): Float {
        var total = 0f
        var i = indexNear(t)
        while (i >= 0) {
            val s = strike(i)
            if (t - s.at > 1.2f) break
            if (t >= s.at) {
                for (k in s.strokeAt.indices) {
                    val dt = t - (s.at + s.strokeAt[k])
                    if (dt >= 0f) total += s.brightness * s.strokeBrightness[k] * exp(-dt / 0.07f)
                }
            }
            i--
        }
        return total.coerceIn(0f, 1f)
    }

    /** The strike whose bolt is showing at [t] (within the first quarter second of its first stroke or of a later one), or null. */
    fun bolt(t: Float): Pair<Strike, Float>? {
        var i = indexNear(t)
        while (i >= 0) {
            val s = strike(i)
            if (t - s.at > 0.8f) return null
            for (k in s.strokeAt.indices) {
                val dt = t - (s.at + s.strokeAt[k])
                if (dt in 0f..0.16f) return s to (1f - dt / 0.16f) * s.strokeBrightness[k]
            }
            i--
        }
        return null
    }

    private fun indexNear(t: Float): Int {
        var i = 0
        while (strike(i + 1).at <= t) i++
        return if (strike(0).at > t) -1 else i
    }
}
