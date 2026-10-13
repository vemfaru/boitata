package br.com.faru.boitata.synth

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SynthTest {
    private fun crossingRate(p: PcmBuffer) = (1 until p.samples.size).count { (p.samples[it - 1] < 0) != (p.samples[it] < 0) }.toDouble() / p.samples.size

    @Test
    fun aFarStrikeRumblesLongerThanANearOne() {
        assertTrue(Thunder.seconds(5f) > Thunder.seconds(0.5f) + 2f)
        assertTrue(Thunder.render(5f).seconds > Thunder.render(0.5f).seconds)
        assertTrue(Thunder.seconds(30f) <= 7f)
    }

    @Test
    fun aNearStrikeStartsWithACrackAFarOneBuildsToARumble() {
        val near = Thunder.render(0.4f); val far = Thunder.render(6f)
        val tenth = near.sampleRate / 10
        fun peak(p: PcmBuffer, to: Int) = (0 until to).maxOf { abs(p.samples[it]) }
        assertTrue(peak(near, tenth) > 0.35f, "a loud crack in the first tenth of a second")
        assertTrue(peak(far, tenth) < far.peak * 0.6f, "far off it swells, it does not crack")
    }

    @Test
    fun theFarThunderIsLowerAndSofter() {
        val near = Thunder.render(0.6f); val far = Thunder.render(7f)
        assertTrue(crossingRate(far) < crossingRate(near) * 0.8, "fewer zero crossings: a lower sound")
        assertTrue(far.peak < near.peak, "and quieter")
    }

    @Test
    fun theSameSeedMakesTheSamePealAndADifferentSeedADifferentOne() {
        assertContentEquals(Thunder.render(2f, seed = 4).samples, Thunder.render(2f, seed = 4).samples)
        assertTrue(!Thunder.render(2f, seed = 4).samples.contentEquals(Thunder.render(2f, seed = 5).samples))
    }

    @Test
    fun thunderStaysInRangeAndFadesOutWithoutAClick() {
        val t = Thunder.render(2f)
        assertTrue(t.peak <= 1f && t.peak > 0.15f)
        assertTrue(abs(t.samples.last()) < 0.01f, "it fades out")
    }

    @Test
    fun shortsKeepTheShape() {
        val t = Thunder.render(1f)
        val back = PcmBuffer.fromShorts(t.toShorts())
        assertEquals(t.samples.size, back.samples.size)
        assertTrue(abs(back.rms - t.rms) < 0.001f)
    }

    @Test
    fun everyLoopIsDeterministicAndWrapsWithoutAJump() {
        for ((name, make) in listOf<Pair<String, () -> PcmBuffer>>("rain" to { Ambience.rain() }, "wind" to { Ambience.wind() }, "fire" to { Ambience.fire() })) {
            val a = make(); val b = make()
            assertContentEquals(a.samples, b.samples, "$name is the same every time")
            val seam = abs(a.samples.last() - a.samples.first())
            val typical = (1 until 20_000).map { abs(a.samples[it] - a.samples[it - 1]) }.average().toFloat()
            assertTrue(seam < typical * 12f + 0.05f, "$name wraps without a jump (seam $seam, typical step $typical)")
            assertTrue(a.peak in 0.2f..1f, "$name is at a sane level")
        }
    }

    @Test
    fun moreRainDensityIsBusierAndWindIsLowerThanRain() {
        assertTrue(Ambience.rain(density = 1f).rms >= Ambience.rain(density = 0.1f).rms)
        assertTrue(crossingRate(Ambience.wind()) < crossingRate(Ambience.rain()) * 0.5, "wind is the lowest of the beds")
    }
}
