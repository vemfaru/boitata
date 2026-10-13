package br.com.faru.boitata.audio

import br.com.faru.boitata.synth.PcmBuffer
import kotlin.time.TimeSource

/** The platform's sink: desktop mixes into a `SourceDataLine`, the web uses Web Audio, Android and iOS their own engines. Make one for the app and keep it. */
public expect fun createAudioSink(): AudioSink

/** A sink that makes no sound and keeps time by the wall clock: for a device with no audio, tests that need a clock, and platforms still to come. */
public class SilentAudioSink : AudioSink {
    private val start = TimeSource.Monotonic.markNow()
    private var stoppedAt: Double? = null
    private var offset = 0.0

    override fun now(): Double = stoppedAt ?: (start.elapsedNow().inWholeMicroseconds / 1_000_000.0 + offset)

    private class Sound(override val startAt: Double) : ScheduledSound { override fun cancel() {} }

    override fun play(buffer: PcmBuffer, gain: Float, pan: Float, startAt: Double): ScheduledSound = Sound(startAt)

    override fun loop(buffer: PcmBuffer, gain: Float): LoopVoice = object : LoopVoice {
        override var gain: Float = gain
        override fun stop() {}
    }

    override fun suspend() { if (stoppedAt == null) stoppedAt = now() }
    override fun resume() { stoppedAt?.let { offset = it - start.elapsedNow().inWholeMicroseconds / 1_000_000.0 }; stoppedAt = null }
    override fun unlock() {}
    override fun release() {}
}
