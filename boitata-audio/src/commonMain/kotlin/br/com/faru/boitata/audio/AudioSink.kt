package br.com.faru.boitata.audio

import br.com.faru.boitata.synth.PcmBuffer

/**
 * Where sound goes out: the one thing a platform has to provide. A sink plays [PcmBuffer]s, mixes them, and keeps its own clock.
 *
 * **Time is the sink's own clock** ([now], in seconds, always moving forward while the device plays). Sounds are *scheduled* on it: `play(buffer, startAt = now() + 0.4)`
 * starts exactly then, whatever the frame rate is doing. That is what keeps thunder in step with a flash: the start time is worked out once from the scene's time, and handed
 * to the device, instead of a timer that fires when the app gets round to it.
 *
 * Implementations mix on their own thread or in the platform's engine; calls may come from the main thread and must return quickly.
 */
public interface AudioSink {
    /** The sink's clock, in seconds. Monotonic; its zero is arbitrary. */
    public fun now(): Double

    /**
     * Plays [buffer] once, starting at [startAt] on the sink's clock (now or in the past starts immediately). [gain] is 0..1 and [pan] is -1 (left) to 1 (right).
     * Returns a handle to cancel it, which works until it has started.
     */
    public fun play(buffer: PcmBuffer, gain: Float = 1f, pan: Float = 0f, startAt: Double = now()): ScheduledSound

    /** Starts [buffer] looping forever at [gain] (usually 0 and raised later): a bed of rain, wind, a fire. */
    public fun loop(buffer: PcmBuffer, gain: Float = 0f): LoopVoice

    /** Stops the device while the app is not in the foreground; the clock may stand still. */
    public fun suspend()

    /** Starts it again. */
    public fun resume()

    /** Browsers keep audio locked until the user touches the page: call this from a click, key press or tap. A no-op elsewhere. */
    public fun unlock()

    /** Frees the device. The sink cannot be used after this. */
    public fun release()
}

/** A sound waiting for its start time (or playing). */
public interface ScheduledSound {
    /** When it starts, on the sink's clock. */
    public val startAt: Double

    /** Stops it if it has not started, or cuts it off if it is playing. */
    public fun cancel()
}

/** A bed that plays forever; its loudness is changed with [gain]. */
public interface LoopVoice {
    /** 0..1. The sink may smooth changes so there are no clicks. */
    public var gain: Float

    public fun stop()
}
