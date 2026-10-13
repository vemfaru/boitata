package br.com.faru.boitata.audio

import br.com.faru.boitata.core.cues.AmbienceLevels
import br.com.faru.boitata.core.cues.StormCues
import br.com.faru.boitata.core.cues.ThunderCue
import br.com.faru.boitata.core.sky.StormModel
import br.com.faru.boitata.synth.Ambience
import br.com.faru.boitata.synth.PcmBuffer
import br.com.faru.boitata.synth.Thunder
import kotlin.math.abs

/** What a scene's sound may do. */
public class SceneAudioConfig(
    /** How far ahead of the scene's time sounds are handed to the device, in seconds. Long enough to ride out a late frame, short enough that a weather change can still cancel them. */
    public val lookaheadSeconds: Float = 0.6f,
    /** How far ahead thunder is made (synthesising a long peal takes a few milliseconds), in seconds. */
    public val renderAheadSeconds: Float = 8f,
    /** Keep thunder going when the system asks for less motion (the flash is off, the sound is not). */
    public val keepThunderUnderReducedMotion: Boolean = true,
    /** The most thunder voices at once; the quietest are dropped first. */
    public val maxThunderVoices: Int = 6,
    /** How fast a bed's loudness may change, per second (0..1 scale): weather changes fade in, they do not jump. */
    public val bedRampPerSecond: Float = 0.5f,
    /** Overall level of thunder and of the beds. */
    public val thunderGain: Float = 0.9f,
    public val bedGain: Float = 0.7f,
    /** Adds to every start time, in seconds, if a device is known to play late (a Bluetooth speaker). Negative plays early. */
    public val latencyOffsetSeconds: Float = 0f,
)

/** The three beds as numbers, made once and handed to the scheduler. */
public class AmbienceBeds(public val rain: PcmBuffer, public val wind: PcmBuffer, public val fire: PcmBuffer) {
    public companion object {
        /** Makes the default beds. A few tens of milliseconds of work: do it off the main thread. */
        public fun render(): AmbienceBeds = AmbienceBeds(Ambience.rain(), Ambience.wind(), Ambience.fire())
    }
}

/**
 * Turns a scene's time into sound scheduled on the device's clock. No Compose in here, so it is all testable with a [RecordingAudioSink].
 *
 * Call [advance] with the scene's time as often as you like (every frame is fine). It
 * - works out the offset between the scene's clock and the sink's clock, once per call, smoothing out frame jitter;
 * - hands every thunder cue due in the next [SceneAudioConfig.lookaheadSeconds] to the sink with its exact start time, once;
 * - cancels what has not started yet if the storm ends, the weather changes, or time jumps (a seek), and never plays a cue that was jumped over late;
 * - eases the rain, wind and fire beds to the levels the weather asks for.
 */
public class SceneAudioScheduler(
    private val sink: AudioSink,
    private val config: SceneAudioConfig = SceneAudioConfig(),
    private val renderThunder: (ThunderCue) -> PcmBuffer = { Thunder.render(it.strike.distanceKm, seed = it.strike.boltSeed xor it.strike.index.toLong()) },
) {
    private var storm: StormModel? = null
    private var cursor = 0f                       // thunder due up to here has been handed to the sink
    private var lastScene = Float.NaN
    private var anchor = Double.NaN               // sink time minus scene time
    private val pending = mutableListOf<Pending>()
    private val rendered = HashMap<Int, PcmBuffer>()
    private var levels = AmbienceLevels(0f, 0f, 0f, 0f)
    private var beds: List<Bed> = emptyList()

    private class Pending(val sound: ScheduledSound, val cue: ThunderCue, val endsAt: Double)
    private class Bed(val voice: LoopVoice, val pick: (AmbienceLevels) -> Float)

    /** How many sounds are scheduled and have not finished. */
    public val pendingCount: Int get() = pending.size

    /** Sets the storm whose thunder to play, or null for none. Anything scheduled and not started is cancelled. */
    public fun setStorm(model: StormModel?) {
        if (model === storm) return
        cancelPending()
        rendered.clear()
        storm = model
        cursor = if (lastScene.isNaN()) 0f else lastScene
    }

    /** Sets the beds' target levels. */
    public fun setLevels(newLevels: AmbienceLevels) { levels = newLevels }

    /** Hands over the rendered beds; they start at silence and ease up. */
    public fun provideBeds(newBeds: AmbienceBeds) {
        beds.forEach { it.voice.stop() }
        beds = listOf(
            Bed(sink.loop(newBeds.rain, 0f)) { it.rain },
            Bed(sink.loop(newBeds.wind, 0f)) { it.wind },
            Bed(sink.loop(newBeds.fire, 0f)) { it.fire },
        )
    }

    public val needsBeds: Boolean get() = beds.isEmpty()

    /** The thunder that will be due within the render-ahead window and is not made yet, soonest first (few at a time, so a frame is not spent making them all). */
    public fun cuesToRender(sceneNow: Float, limit: Int = 1): List<ThunderCue> {
        val model = storm ?: return emptyList()
        return StormCues.thunder(model, maxOf(cursor, sceneNow - 0.0f), sceneNow + config.renderAheadSeconds)
            .filter { it.strike.index !in rendered }
            .take(limit)
    }

    /** Stores a made peal for [cue]. */
    public fun provide(cue: ThunderCue, buffer: PcmBuffer) { rendered[cue.strike.index] = buffer }

    /** Makes [cue]'s peal now (on whichever thread calls); used by [cuesToRender] callers and as the fallback when a cue comes due unmade. */
    public fun render(cue: ThunderCue): PcmBuffer = renderThunder(cue)

    /** Moves the sound to the scene's time [sceneNow] (seconds). */
    public fun advance(sceneNow: Float) {
        val audioNow = sink.now()
        val first = lastScene.isNaN()
        val jumped = !first && (sceneNow < lastScene - JUMP_BACK || sceneNow > lastScene + JUMP_FORWARD)
        if (jumped) { cancelPending(); cursor = sceneNow }
        if (first) cursor = sceneNow
        // The offset between the two clocks: taken fresh at the start, eased after, so one late frame does not shift every sound.
        val wanted = audioNow - sceneNow
        anchor = if (anchor.isNaN() || jumped || abs(wanted - anchor) > ANCHOR_SNAP) wanted else anchor + (wanted - anchor) * ANCHOR_EASE
        val dt = if (first || jumped) 0f else (sceneNow - lastScene).coerceIn(0f, 0.25f)
        lastScene = sceneNow

        pending.removeAll { it.endsAt < audioNow }
        scheduleThunder(sceneNow)
        easeBeds(dt)
    }

    private fun scheduleThunder(sceneNow: Float) {
        val model = storm ?: return
        val horizon = sceneNow + config.lookaheadSeconds
        if (horizon <= cursor) return
        for (cue in StormCues.thunder(model, cursor, horizon)) {
            val buffer = rendered.remove(cue.strike.index) ?: renderThunder(cue)
            val startAt = anchor + cue.playAt + config.latencyOffsetSeconds
            val gain = (cue.loudness * config.thunderGain).coerceIn(0f, 1f)
            val pan = ((cue.strike.boltX - 0.5f) * 1.2f).coerceIn(-0.6f, 0.6f)
            pending += Pending(sink.play(buffer, gain, pan, startAt), cue, startAt + buffer.seconds)
        }
        cursor = horizon
        // Too many at once: cut the quietest that has not started.
        while (pending.size > config.maxThunderVoices) {
            val quietest = pending.minBy { it.cue.loudness }
            quietest.sound.cancel(); pending.remove(quietest)
        }
    }

    private fun easeBeds(dt: Float) {
        val step = config.bedRampPerSecond * dt
        for (bed in beds) {
            val target = (bed.pick(levels) * config.bedGain).coerceIn(0f, 1f)
            val g = bed.voice.gain
            val next = if (dt == 0f) g else if (target > g) minOf(target, g + step) else maxOf(target, g - step)
            if (next != g) bed.voice.gain = next
        }
    }

    /** Cancels everything handed to the sink that has not started yet (a sound already playing is left to finish). */
    private fun cancelPending() {
        val audioNow = sink.now()
        pending.filter { it.sound.startAt > audioNow }.forEach { it.sound.cancel() }
        pending.removeAll { it.sound.startAt > audioNow }
    }

    /** Stops the beds and frees what it holds. Does not release the sink: the caller owns it. */
    public fun release() {
        cancelPending()
        beds.forEach { it.voice.stop() }
        beds = emptyList()
        rendered.clear()
    }

    private companion object {
        const val JUMP_BACK = 0.05f
        const val JUMP_FORWARD = 2f
        const val ANCHOR_SNAP = 0.25
        const val ANCHOR_EASE = 0.1
    }
}
