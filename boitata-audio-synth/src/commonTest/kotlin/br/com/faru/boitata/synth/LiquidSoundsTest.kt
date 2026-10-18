package br.com.faru.boitata.synth

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class LiquidSoundsTest {
    private fun zeroCrossings(b: PcmBuffer): Int { var n = 0; for (i in 1 until b.samples.size) if ((b.samples[i - 1] < 0f) != (b.samples[i] < 0f)) n++; return n }

    private val everything: Map<String, PcmBuffer> by lazy {
        mapOf(
            "gulp" to LiquidSounds.gulp(), "drip" to LiquidSounds.drip(), "splash" to LiquidSounds.splash(), "stream" to LiquidSounds.stream(), "soak" to LiquidSounds.soak(),
            "boil" to LiquidSounds.boil(), "setDown" to LiquidSounds.setDown(), "knock" to LiquidSounds.knock(),
        )
    }

    @Test
    fun everySoundHasSomethingInItAndNeverClips() {
        for ((name, b) in everything) {
            assertTrue(b.rms > 0.01f, "$name is silent: ${b.rms}")
            assertTrue(b.peak <= 1f, "$name clips: ${b.peak}")
            assertTrue(b.seconds > 0.05f, "$name is too short")
        }
    }

    @Test
    fun theSameSeedMakesTheSameSoundAndAnotherMakesAnother() {
        assertContentEquals(LiquidSounds.splash(3L).samples, LiquidSounds.splash(3L).samples)
        assertTrue(!LiquidSounds.splash(3L).samples.contentEquals(LiquidSounds.splash(4L).samples))
    }

    @Test
    fun theLoopsRunSeamlesslyAndAreLongEnoughNotToRepeatTooSoon() {
        for (name in listOf("stream", "soak", "boil")) {
            val s = everything.getValue(name).samples
            assertTrue(s.size > PcmBuffer.SAMPLE_RATE, "$name is a loop of at least a second")
            assertTrue(abs(s.last() - s.first()) < 0.25f, "$name joins without a click: ${s.last()} to ${s.first()}")
        }
    }

    @Test
    fun aBigDropIsLowerThanASmallOne() {
        assertTrue(zeroCrossings(LiquidSounds.drip(1f)) < zeroCrossings(LiquidSounds.drip(0f)) , "a big drop rings lower")
    }

    @Test
    fun glassRingsLongerAndHigherThanClay() {
        val clay = LiquidSounds.setDown(0f); val glass = LiquidSounds.setDown(1f)
        assertTrue(glass.seconds > clay.seconds, "${glass.seconds} vs ${clay.seconds}")
        assertTrue(zeroCrossings(glass) / glass.seconds > zeroCrossings(clay) / clay.seconds, "glass is higher")
    }

    @Test
    fun aHarderKnockIsLouder() {
        assertTrue(LiquidSounds.setDown(0.7f, 1f).peak > LiquidSounds.setDown(0.7f, 0.1f).peak)
    }

    @Test
    fun aStreamIsBrighterAndLouderThanASoak() {
        assertTrue(everything.getValue("stream").rms > everything.getValue("soak").rms)
        assertTrue(zeroCrossings(everything.getValue("stream")) > zeroCrossings(everything.getValue("soak")))
    }
}
