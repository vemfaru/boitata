package br.com.faru.boitata.audio

import br.com.faru.boitata.synth.PcmBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The real desktop sink, with silence only: it must keep a clock that runs, stops when suspended, and accept sounds. (On a machine with no audio device it keeps time by the wall clock.) */
class JvmSinkTest {
    private val silence = PcmBuffer(FloatArray(4410), 44_100)

    @Test
    fun theClockRunsStopsWhenSuspendedAndRunsAgain() = clockRuns(createAudioSink())

    @Test
    fun withNoAudioDeviceTheWallClockKeepsTimeAndStopsWhenSuspendedToo() = clockRuns(JvmAudioSink(openDevice = false))

    private fun clockRuns(sink: AudioSink) {
        try {
            val t0 = sink.now()
            Thread.sleep(400)
            val t1 = sink.now()
            assertTrue(t1 - t0 in 0.2..0.8, "the clock follows real time while playing: ${t1 - t0}")
            sink.suspend()
            Thread.sleep(100)
            val s0 = sink.now()
            Thread.sleep(300)
            assertEquals(s0, sink.now(), 0.05, "a suspended device does not advance")
            sink.resume()
            Thread.sleep(300)
            assertTrue(sink.now() > s0 + 0.1, "and it runs again after resume")
        } finally {
            sink.release()
        }
    }

    @Test
    fun itAcceptsScheduledSoundsAndBedsAndCancels() {
        val sink = createAudioSink()
        try {
            val sound = sink.play(silence, gain = 0.5f, pan = -0.3f, startAt = sink.now() + 0.2)
            assertTrue(sound.startAt > sink.now())
            sound.cancel()
            val bed = sink.loop(silence, gain = 0f)
            bed.gain = 0.4f
            bed.stop()
        } finally {
            sink.release()
        }
    }
}
