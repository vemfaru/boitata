package br.com.faru.boitata.audio

import br.com.faru.boitata.synth.PcmBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * A mixer that works on numbers alone: it turns scheduled one-shots and loops into blocks of stereo samples. A sink that has to feed a device itself (the desktop's
 * `SourceDataLine`, Android's `AudioTrack`) calls [render] from its audio thread; the mixing, the timing and the limiting are the same everywhere and are tested here without a speaker.
 *
 * **Time is the frame count.** A sound scheduled at frame `F` begins exactly at the `F`th frame of output, whichever block that falls in: sample-accurate, no matter when the
 * mixer thread happens to run. One scheduled for a frame already passed starts at once. Not thread-safe: the sink serialises calls.
 */
internal class SoftwareMixer(val sampleRate: Int = PcmBuffer.SAMPLE_RATE) {
    /** Frames rendered so far: where the next block starts on the timeline. */
    var position: Long = 0
        private set

    private val shots = ArrayList<Shot>()
    private val loops = ArrayList<Bed>()

    private inner class Shot(val data: FloatArray, val left: Float, val right: Float, override val startAt: Double, val startFrame: Long) : ScheduledSound {
        var cancelled = false
        var fadeOut = -1                        // frames of fade left once cancelled mid-sound
        override fun cancel() { cancelled = true }
    }

    private inner class Bed(val data: FloatArray, initial: Float) : LoopVoice {
        var shown = initial                     // the gain the last block ended at
        var stopped = false
        var phase = 0
        override var gain: Float = initial
        override fun stop() { stopped = true }
    }

    /** Schedules [buffer] to begin at frame [startFrame]. */
    fun schedule(buffer: PcmBuffer, gain: Float, pan: Float, startAt: Double, startFrame: Long): ScheduledSound {
        val angle = (pan.coerceIn(-1f, 1f) + 1f) * (PI.toFloat() / 4f)         // equal-power: no change in loudness across the stereo field
        val g = gain.coerceIn(0f, 1f)
        // A sound that is already late starts from its first sample now, never from the middle of itself.
        return Shot(resampled(buffer), g * cos(angle), g * sin(angle), startAt, maxOf(startFrame, position)).also { shots += it }
    }

    fun loop(buffer: PcmBuffer, gain: Float): LoopVoice = Bed(resampled(buffer), gain.coerceIn(0f, 1f)).also { loops += it }

    /** Writes [frames] frames of interleaved stereo (left, right, left, ...) into [out], replacing what was there, and moves the timeline on. */
    fun render(out: FloatArray, frames: Int) {
        require(out.size >= frames * 2)
        out.fill(0f, 0, frames * 2)
        val blockStart = position
        val blockEnd = blockStart + frames
        val keep = ArrayList<Shot>(shots.size)
        for (s in shots) {
            if (s.cancelled && s.startFrame >= blockEnd) continue            // cancelled before it started: gone
            val from = maxOf(s.startFrame, blockStart)
            val first = (from - blockStart).toInt()
            // Where in the sound the block begins (a sound scheduled in the past starts from its first sample, now).
            val offset = (maxOf(from, s.startFrame) - s.startFrame).toInt()
            val n = minOf(frames - first, s.data.size - offset)
            if (n > 0) {
                var fade = s.fadeOut
                if (s.cancelled && fade < 0) fade = FADE_FRAMES
                for (k in 0 until n) {
                    var v = s.data[offset + k]
                    if (fade >= 0) { v *= fade.toFloat() / FADE_FRAMES; fade-- ; if (fade < 0) { s.fadeOut = -2; break } }
                    out[(first + k) * 2] += v * s.left
                    out[(first + k) * 2 + 1] += v * s.right
                }
                if (s.cancelled && s.fadeOut != -2) s.fadeOut = fade
            }
            val finished = s.fadeOut == -2 || (!s.cancelled && offset + n >= s.data.size && s.startFrame + s.data.size <= blockEnd)
            if (!finished) keep += s
        }
        shots.clear(); shots.addAll(keep)

        val liveBeds = ArrayList<Bed>(loops.size)
        for (b in loops) {
            if (b.stopped) continue
            val g0 = b.shown; val g1 = b.gain
            if (g0 > 0.0005f || g1 > 0.0005f) {
                var p = b.phase
                for (f in 0 until frames) {
                    val g = g0 + (g1 - g0) * (f + 1) / frames           // a ramp across the block, so a change of level is never a click
                    val v = b.data[p] * g
                    out[f * 2] += v; out[f * 2 + 1] += v
                    p++; if (p >= b.data.size) p = 0
                }
                b.phase = p
            } else {
                b.phase = (b.phase + frames) % b.data.size              // silent, but time still goes on, so the beds stay in step
            }
            b.shown = g1
            liveBeds += b
        }
        loops.clear(); loops.addAll(liveBeds)

        // Many things at once must not clip: round the sum off.
        for (i in 0 until frames * 2) out[i] = limit(out[i])
        position = blockEnd
    }

    private fun resampled(buffer: PcmBuffer): FloatArray {
        if (buffer.sampleRate == sampleRate) return buffer.samples
        val ratio = buffer.sampleRate.toDouble() / sampleRate
        val n = (buffer.samples.size / ratio).toInt()
        return FloatArray(n) { i ->
            val x = i * ratio
            val a = x.toInt().coerceAtMost(buffer.samples.size - 1); val b = (a + 1).coerceAtMost(buffer.samples.size - 1)
            val t = (x - a).toFloat()
            buffer.samples[a] * (1f - t) + buffer.samples[b] * t
        }
    }

    companion object {
        const val FADE_FRAMES = 256

        /** Rounds a sum off instead of clipping it: transparent for small values, never past -1..1. */
        fun limit(x: Float): Float = if (abs(x) < 0.5f) x else tanh(x * 1.0f).coerceIn(-1f, 1f)
    }
}
