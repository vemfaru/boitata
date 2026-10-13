package br.com.faru.boitata.core

/** A seeded stream of numbers 0..1 by index, so a field of stars or clouds is the same every frame. */
public class FastNoise(private val seed: Long) {
    /** The [channel]th number for [index]. */
    public fun at(index: Int, channel: Int): Float {
        var h = seed * 6364136223846793005L + index * 1442695040888963407L + channel * 2654435761L + 1013904223L
        h = h xor (h ushr 33); h *= -49064778989728563L; h = h xor (h ushr 33)
        return ((h ushr 40).toInt() and 0xFFFFFF) / 16777216f
    }
}
