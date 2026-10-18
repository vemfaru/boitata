package br.com.faru.boitata.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import br.com.faru.boitata.core.clock.RunOnSceneClock
import br.com.faru.boitata.core.fluid.LiquidCue
import br.com.faru.boitata.core.fluid.LiquidCueKind
import br.com.faru.boitata.core.fluid.LiquidEvents
import br.com.faru.boitata.core.fluid.LiquidVessel
import br.com.faru.boitata.synth.LiquidSounds
import br.com.faru.boitata.synth.PcmBuffer
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Which sound goes with which thing that happens (a [LiquidCueKind]): by default the one [LiquidSounds] makes, but any kind can be given a sound of your own ([set], a recording turned into a [PcmBuffer]), muted
 * ([setMuted]) or made louder or quieter ([setGain]). Sounds that depend on how big or what it is (a drip, a container set down) have a few variants, made once and kept.
 */
public class LiquidSoundMap {
    private val custom = HashMap<LiquidCueKind, PcmBuffer>()
    private val muted = HashSet<LiquidCueKind>()
    private val gains = HashMap<LiquidCueKind, Float>()
    private val made = HashMap<String, PcmBuffer>()

    /** Plays [buffer] for [kind] instead of the default; `null` goes back to the default. A loop kind ([LiquidCueKind.Stream], [LiquidCueKind.Boil], [LiquidCueKind.Soak]) wants a buffer that loops. */
    public fun set(kind: LiquidCueKind, buffer: PcmBuffer?) { if (buffer == null) custom.remove(kind) else custom[kind] = buffer }

    /** Whether [kind] has a sound of your own. */
    public fun isCustom(kind: LiquidCueKind): Boolean = kind in custom

    public fun setMuted(kind: LiquidCueKind, mute: Boolean) { if (mute) muted += kind else muted -= kind }
    public fun isMuted(kind: LiquidCueKind): Boolean = kind in muted

    /** How loud [kind] is, 0..2 (1 as made). */
    public fun setGain(kind: LiquidCueKind, gain: Float) { gains[kind] = gain.coerceIn(0f, 2f) }
    public fun gainOf(kind: LiquidCueKind): Float = gains[kind] ?: 1f

    /** The sound for [cue]: yours if you set one, else the default for it, else `null` if it is muted. */
    public fun bufferFor(cue: LiquidCue): PcmBuffer? = if (cue.kind in muted) null else custom[cue.kind] ?: default(cue.kind, cue.strength, cue.tone)

    /** The loop for a continuous [kind], or `null` if it is muted. */
    public fun loopFor(kind: LiquidCueKind): PcmBuffer? = if (kind in muted) null else custom[kind] ?: default(kind, 1f, 0.5f)

    private fun step(x: Float, steps: Int): Int = (x.coerceIn(0f, 1f) * steps).roundToInt()

    private fun default(kind: LiquidCueKind, strength: Float, tone: Float): PcmBuffer = when (kind) {
        LiquidCueKind.Stream -> made.getOrPut("stream") { LiquidSounds.stream() }
        LiquidCueKind.Splash -> made.getOrPut("splash") { LiquidSounds.splash() }
        LiquidCueKind.Gulp -> made.getOrPut("gulp") { LiquidSounds.gulp() }
        LiquidCueKind.Drip -> step(strength, 2).let { k -> made.getOrPut("drip$k") { LiquidSounds.drip(k / 2f, seed = 2L + k) } }
        LiquidCueKind.SetDown -> step(tone, 3).let { k -> made.getOrPut("setdown$k") { LiquidSounds.setDown(k / 3f, 0.7f, seed = 7L + k) } }
        LiquidCueKind.Knock -> step(tone, 2).let { k -> made.getOrPut("knock$k") { LiquidSounds.knock(k / 2f, seed = 8L + k) } }
        LiquidCueKind.Boil -> made.getOrPut("boil") { LiquidSounds.boil() }
        LiquidCueKind.Soak -> made.getOrPut("soak") { LiquidSounds.soak() }
    }
}

/**
 * Hears the cues of liquids ([LiquidEvents]: give it to a vessel's `events`) and plays them on [sink]: each one-off sound is scheduled on the device's own clock the moment it is heard, so a gulp lands with its frame; the
 * continuous ones (a stream, a boil, soaking) are loops whose loudness follows the level they are at and fades when they stop. [panOf] says where a vessel is on the scene, -1 left to 1 right, for the stereo position.
 * [map] says which sound goes with what. Call [tick] once a frame (the [LiquidAudio] composable does) and [release] when done.
 */
public class LiquidAudioPlayer(
    private val sink: AudioSink,
    public val map: LiquidSoundMap = LiquidSoundMap(),
    private val panOf: (LiquidVessel?) -> Float = { 0f },
    private val master: Float = 0.8f,
) : LiquidEvents {
    /** Off: nothing is played and the loops fade out. */
    public var enabled: Boolean = true

    private val loops = HashMap<LiquidCueKind, LoopVoice>()
    private val level = HashMap<LiquidCueKind, Float>()
    private val last = HashMap<LiquidCueKind, Double>()

    private fun continuous(kind: LiquidCueKind) = kind == LiquidCueKind.Stream || kind == LiquidCueKind.Boil || kind == LiquidCueKind.Soak

    private fun gap(kind: LiquidCueKind): Double = when (kind) {
        LiquidCueKind.Splash -> 0.09; LiquidCueKind.Drip -> 0.04; LiquidCueKind.Gulp -> 0.25; LiquidCueKind.Knock -> 0.3; LiquidCueKind.SetDown -> 0.1; else -> 0.0
    }

    override fun onCue(cue: LiquidCue) {
        if (!enabled) return
        if (continuous(cue.kind)) { level[cue.kind] = maxOf(level[cue.kind] ?: 0f, cue.strength); return }
        val now = sink.now()
        if (now - (last[cue.kind] ?: -1e9) < gap(cue.kind)) return
        val buffer = map.bufferFor(cue) ?: return
        last[cue.kind] = now
        sink.play(buffer, gain = ((0.35f + 0.65f * cue.strength) * map.gainOf(cue.kind) * master).coerceIn(0f, 1f), pan = panOf(cue.source).coerceIn(-1f, 1f), startAt = now)
    }

    /** Eases every loop toward the level the cues set since the last tick (up quickly, down more slowly), then starts the next tick afresh. */
    public fun tick(dt: Float) {
        for (kind in listOf(LiquidCueKind.Stream, LiquidCueKind.Boil, LiquidCueKind.Soak)) {
            val target = if (enabled && !map.isMuted(kind)) ((level[kind] ?: 0f) * map.gainOf(kind) * master).coerceIn(0f, 1f) else 0f
            var voice = loops[kind]
            if (voice == null && target > 0.001f) voice = map.loopFor(kind)?.let { sink.loop(it, 0f) }?.also { loops[kind] = it }
            voice?.let { it.gain += (target - it.gain) * (1f - exp(-(if (target > it.gain) 25f else 8f) * dt)) }
            level[kind] = 0f
        }
    }

    /** Stops every loop. */
    public fun release() { loops.values.forEach { it.stop() }; loops.clear() }
}

/** Runs [player] with the scene: it eases its loops each frame, follows [enabled] (the scene's sound switch), and stops when it leaves. */
@Composable
public fun LiquidAudio(player: LiquidAudioPlayer, enabled: Boolean = true) {
    SideEffect { player.enabled = enabled }
    RunOnSceneClock(player) { dt, _ -> player.tick(dt) }
    DisposableEffect(player) { onDispose { player.release() } }
}
