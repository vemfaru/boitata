package br.com.faru.boitata.audio

import br.com.faru.boitata.synth.PcmBuffer

/**
 * A sink that plays nothing and writes down what it was asked to do, on a clock you move yourself. For tests of anything that makes sound: assert *when* each sound was
 * scheduled without a speaker, a device or real time.
 */
public class RecordingAudioSink(public var clock: Double = 0.0) : AudioSink {
    public class Played(
        public val buffer: PcmBuffer,
        public val gain: Float,
        public val pan: Float,
        override val startAt: Double,
    ) : ScheduledSound {
        public var cancelled: Boolean = false
            private set
        override fun cancel() { cancelled = true }
        /** Whether it would be heard: not cancelled. */
        public val audible: Boolean get() = !cancelled
    }

    public class Loop(public val buffer: PcmBuffer, initialGain: Float) : LoopVoice {
        /** Every gain it has been set to, in order, starting with the one it began at. */
        public val gains: MutableList<Float> = mutableListOf(initialGain)
        override var gain: Float = initialGain
            set(value) { field = value; gains += value }
        public var stopped: Boolean = false
            private set
        override fun stop() { stopped = true }
    }

    public val played: MutableList<Played> = mutableListOf()
    public val loops: MutableList<Loop> = mutableListOf()
    public var suspended: Boolean = false
        private set
    public var unlocked: Boolean = false
        private set
    public var released: Boolean = false
        private set

    override fun now(): Double = clock

    override fun play(buffer: PcmBuffer, gain: Float, pan: Float, startAt: Double): ScheduledSound =
        Played(buffer, gain, pan, startAt).also { played += it }

    override fun loop(buffer: PcmBuffer, gain: Float): LoopVoice = Loop(buffer, gain).also { loops += it }

    override fun suspend() { suspended = true }
    override fun resume() { suspended = false }
    override fun unlock() { unlocked = true }
    override fun release() { released = true }
}
