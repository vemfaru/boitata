package br.com.faru.boitata.audio

import br.com.faru.boitata.synth.PcmBuffer
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MixerTest {
    private val rate = 44_100
    private fun ones(n: Int, v: Float = 0.5f) = PcmBuffer(FloatArray(n) { v }, rate)

    /** Renders [blocks] blocks of [size] frames and returns the left channel as one array. */
    private fun SoftwareMixer.left(blocks: Int, size: Int): FloatArray {
        val out = FloatArray(size * 2)
        val all = FloatArray(blocks * size)
        for (b in 0 until blocks) { render(out, size); for (i in 0 until size) all[b * size + i] = out[i * 2] }
        return all
    }

    @Test
    fun aSoundBeginsOnExactlyItsFrameWhicheverBlockThatFallsIn() {
        for (size in listOf(64, 100, 512, 1000)) {
            val m = SoftwareMixer(rate)
            m.schedule(ones(300), 1f, 0f, startAt = 0.0, startFrame = 1234)
            val left = m.left(blocks = 4000 / size + 1, size = size)
            val first = left.indexOfFirst { it != 0f }
            assertEquals(1234, first, "block size $size: sample-accurate")
            assertEquals(300, left.count { it != 0f }, "and it lasts exactly its length")
        }
    }

    @Test
    fun aSoundScheduledInThePastStartsAtOnce() {
        val m = SoftwareMixer(rate)
        m.left(2, 512)
        m.schedule(ones(100), 1f, 0f, startAt = 0.0, startFrame = 10)
        val left = m.left(1, 512)
        assertEquals(0, left.indexOfFirst { it != 0f })
    }

    @Test
    fun panPlacesTheSoundWithoutChangingItsLoudness() {
        val out = FloatArray(512 * 2)
        val hardLeft = SoftwareMixer(rate).apply { schedule(ones(400), 1f, -1f, 0.0, 0) }
        hardLeft.render(out, 512)
        assertTrue(out[0] > 0.4f && abs(out[1]) < 1e-4f, "left only")
        val hardRight = SoftwareMixer(rate).apply { schedule(ones(400), 1f, 1f, 0.0, 0) }
        hardRight.render(out, 512)
        assertTrue(out[1] > 0.4f && abs(out[0]) < 1e-4f, "right only")
        val centre = SoftwareMixer(rate).apply { schedule(ones(400), 1f, 0f, 0.0, 0) }
        centre.render(out, 512)
        val power = out[0] * out[0] + out[1] * out[1]
        assertEquals(0.25f, power, 0.01f, "equal power: the same loudness as hard left (0.5 squared)")
    }

    @Test
    fun cancellingBeforeTheStartLeavesSilence() {
        val m = SoftwareMixer(rate)
        val s = m.schedule(ones(300), 1f, 0f, 0.0, 600)
        s.cancel()
        assertTrue(m.left(4, 512).all { it == 0f })
    }

    @Test
    fun cancellingWhilePlayingFadesOutInsteadOfClicking() {
        val m = SoftwareMixer(rate)
        val s = m.schedule(ones(5000, 0.8f), 1f, 0f, 0.0, 0)
        val before = m.left(1, 256)
        assertTrue(before.all { it > 0.5f })
        s.cancel()
        val after = m.left(2, 256)
        assertTrue(after.first() > 0.4f && after.first() < 0.8f, "starts fading from where it was")
        assertEquals(0f, after.last(), 1e-6f, "silent once the fade is over")
        assertTrue(after.toList().zipWithNext().all { (a, b) -> a - b < 0.01f }, "a smooth ramp down")
    }

    @Test
    fun aLoopWrapsAroundWithoutAGap() {
        val m = SoftwareMixer(rate)
        val data = PcmBuffer(FloatArray(100) { 0.1f + it / 400f }, rate)       // 0.1 .. 0.35: below the limiter, so the output is the data itself
        m.loop(data, 1f)
        val left = m.left(2, 512)
        for (i in left.indices) assertEquals(data.samples[i % 100], left[i], 1e-5f, "frame $i")
    }

    @Test
    fun aLoopsLevelRampsAcrossABlockInsteadOfJumping() {
        val m = SoftwareMixer(rate)
        val bed = m.loop(PcmBuffer(FloatArray(100) { 0.4f }, rate), 0f)
        assertTrue(m.left(1, 512).all { it == 0f })
        bed.gain = 1f
        val left = m.left(1, 512)
        assertTrue(left.toList().zipWithNext().all { (a, b) -> b >= a - 1e-6f }, "rising the whole block")
        assertEquals(0.4f, left.last(), 1e-3f, "arrives at the new level")
        assertTrue(left.first() < 0.01f, "and starts from silence")
        bed.stop()
        assertTrue(m.left(1, 512).all { it == 0f }, "stopped is silent")
    }

    @Test
    fun manyLoudSoundsAreRoundedOffNotClipped() {
        val m = SoftwareMixer(rate)
        repeat(8) { m.schedule(ones(300, 0.9f), 1f, 0f, 0.0, 0) }
        val out = FloatArray(512 * 2)
        m.render(out, 512)
        assertTrue(out.all { abs(it) <= 1f })
        assertTrue(out.take(300).all { it > 0.9f }, "loud, but still below the ceiling")
        assertTrue(out[2] < 1f)
    }

    @Test
    fun aSoundAtAnotherSampleRateIsResampledToTheMixersRate() {
        val m = SoftwareMixer(rate)
        m.schedule(PcmBuffer(FloatArray(22_050) { 0.5f }, 22_050), 1f, 0f, 0.0, 0)
        val left = m.left(100, 512)
        assertEquals(44_100, left.count { it != 0f }, "half the sample rate lasts the same time: twice the frames")
    }

    @Test
    fun itIsDeterministic() {
        fun run() = SoftwareMixer(rate).apply { schedule(ones(1000, 0.3f), 0.7f, 0.3f, 0.0, 77) }.left(4, 512)
        assertTrue(run().contentEquals(run()))
    }
}
