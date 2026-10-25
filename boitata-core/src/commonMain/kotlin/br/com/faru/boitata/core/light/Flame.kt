package br.com.faru.boitata.core.light

import br.com.faru.boitata.core.FastNoise
import kotlin.math.floor

/**
 * How the light of a flame moves: where its light comes from sways and its brightness breathes, so everything it lights and every shadow it throws moves with it.
 * A shadow's tip moves by the sway times how far past the thing the shadow lands (`t − 1` along the ray), so a short shadow trembles and a long one on the far wall swings.
 *
 * An open candle's flame is about 1.2 cm wide and 3.5 cm tall; the light comes from about a third up it. In still air it wavers a few millimetres side to side and
 * grows and shrinks by a tenth; a breath of air leans it further and slower. A lantern's glass chimney keeps the draught off, so its flame is steadier ([amount] below 1).
 * An electric bulb does not sway at all (amount 0): a dying one only sputters, which is brightness, not place.
 *
 * Smooth noise, never steps: the sample changes continuously, and [step] quantises time so a scene redraws its shadows [RATE] times a second, not every frame.
 * Pure and deterministic: the same [seed] and time give the same flame.
 */
public object Flame {
    /** How many times a second a flame's shadows are worked out again: fast enough to look alive, slow enough to keep the scene cheap. */
    public const val RATE: Float = 15f

    /** The side-to-side waver of an open flame in still air, in centimetres either way. */
    public const val SWAY_CM: Float = 0.35f

    /** How far the flame's light rises and falls as it grows and shrinks, in centimetres either way. */
    public const val LIFT_CM: Float = 0.25f

    /** How much further a breath of air leans it, in centimetres (slower than the waver). */
    public const val GUST_CM: Float = 0.6f

    /** How much its brightness breathes, as a share either way. */
    public const val BREATH: Float = 0.12f

    private const val WAVER_HZ = 3f
    private const val GUST_HZ = 0.55f
    private const val BREATH_HZ = 7f

    private val noise = FastNoise(31L)

    /** The step of time a flame's shadows are drawn at ([RATE] a second). */
    public fun step(seconds: Float): Long = floor(seconds * RATE).toLong()

    /**
     * The flame of strength [amount] (0 none, 1 an open candle) at [seconds], into [out]: its light moved by (`out[0]` across, `out[1]` up) centimetres and its brightness times
     * `out[2]`. [seed] keeps two flames in a room apart.
     */
    public fun at(seconds: Float, amount: Float, out: FloatArray, seed: Int = 0) {
        if (amount <= 0f) { out[0] = 0f; out[1] = 0f; out[2] = 1f; return }
        val waver = smooth(seconds * WAVER_HZ, seed * 7 + 1)
        val gust = smooth(seconds * GUST_HZ, seed * 7 + 2)
        val rise = smooth(seconds * WAVER_HZ * 1.3f, seed * 7 + 3)
        val breath = smooth(seconds * BREATH_HZ, seed * 7 + 4)
        out[0] = amount * (SWAY_CM * waver + GUST_CM * gust)
        out[1] = amount * LIFT_CM * rise
        out[2] = 1f + amount * BREATH * breath
    }

    /** Value noise along one line, eased between whole steps: -1 .. 1, continuous. */
    private fun smooth(x: Float, row: Int): Float {
        val i = floor(x).toInt()
        val f = x - i
        val e = f * f * (3f - 2f * f)
        val a = noise.at(i, row); val b = noise.at(i + 1, row)
        return (a + (b - a) * e) * 2f - 1f
    }
}
