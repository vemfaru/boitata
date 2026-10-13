package br.com.faru.boitata.audio

import br.com.faru.boitata.core.cues.AmbienceLevels
import br.com.faru.boitata.core.cues.StormCues
import br.com.faru.boitata.core.sky.StormModel
import br.com.faru.boitata.synth.PcmBuffer
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SchedulerTest {
    private val model = StormModel(seed = 17L, intensity = 1f)
    private val tiny = PcmBuffer(FloatArray(441), 44_100)          // a 10 ms stand-in for a peal: the tests are about when, not what

    /** A scheduler on a recording sink whose clock runs [offset] seconds ahead of the scene's, stepped by [frame] seconds each call. */
    private class Rig(val sink: RecordingAudioSink, val scheduler: SceneAudioScheduler, val offset: Double) {
        var scene = 0f
        fun step(dt: Float) { scene += dt; sink.clock = scene + offset; scheduler.advance(scene) }
        fun run(seconds: Float, frame: Float = 1f / 60f) { repeat((seconds / frame).toInt()) { step(frame) } }
    }

    private fun rig(offset: Double = 100.0, config: SceneAudioConfig = SceneAudioConfig(), storm: StormModel? = model): Rig {
        val sink = RecordingAudioSink(offset)
        val scheduler = SceneAudioScheduler(sink, config) { tiny }
        scheduler.setStorm(storm)
        return Rig(sink, scheduler, offset).also { it.step(0f) }
    }

    @Test
    fun thunderIsScheduledAtTheFlashPlusTheTimeTheSoundTakes() {
        val r = rig()
        r.run(60f)
        val cues = StormCues.thunder(model, 0f, 60f)
        assertTrue(cues.size >= 2, "the storm strikes in a minute")
        assertEquals(cues.size, r.sink.played.size, "every cue handed over, and none twice")
        for ((cue, played) in cues.zip(r.sink.played)) {
            assertTrue(abs(played.startAt - (cue.playAt + r.offset)) < 0.02, "scheduled at ${played.startAt}, wanted ${cue.playAt + r.offset}")
        }
    }

    @Test
    fun theSoundIsHandedOverOnlyShortlyBeforeItIsDue() {
        val r = rig()
        val cue = StormCues.thunder(model, 0f, 60f).first()
        r.scene = cue.playAt - 3f
        r.step(0f)
        assertTrue(r.sink.played.none { it.startAt > r.sink.clock + 1.0 }, "nothing more than the lookahead ahead")
    }

    @Test
    fun frameJitterDoesNotMoveASoundFromItsTime() {
        val r = rig()
        val random = kotlin.random.Random(3)
        val cue = StormCues.thunder(model, 0f, 60f).first()
        while (r.scene < cue.playAt) r.step(1f / 60f + (random.nextFloat() - 0.5f) * 0.02f)
        val played = r.sink.played.first()
        assertTrue(abs(played.startAt - (cue.playAt + r.offset)) < 0.03, "within 30 ms of its time despite jittery frames: off by ${played.startAt - (cue.playAt + r.offset)}")
    }

    @Test
    fun endingTheStormCancelsWhatHasNotStarted() {
        val r = rig()
        val cue = StormCues.thunder(model, 0f, 60f).first()
        r.scene = cue.playAt - 0.4f
        r.step(0f)
        val waiting = r.sink.played.filter { it.startAt > r.sink.clock }
        assertTrue(waiting.isNotEmpty(), "a sound is waiting for its moment")
        val countBefore = r.sink.played.size
        r.scheduler.setStorm(null)
        assertTrue(waiting.all { it.cancelled }, "the weather changed: it must not play")
        r.run(20f)
        assertEquals(countBefore, r.sink.played.size, "and no new thunder is scheduled once the storm has ended")
    }

    @Test
    fun aSeekBackReplaysNothingTwiceAndCancelsWhatWasWaiting() {
        val r = rig()
        r.run(30f)
        val before = r.sink.played.size
        r.scene = 5f
        r.sink.clock = r.scene + r.offset
        r.scheduler.advance(r.scene)
        assertTrue(r.sink.played.drop(before).isEmpty(), "jumping does not itself play anything")
        r.run(10f)
        val replayed = StormCues.thunder(model, 5f, 15f).size
        assertEquals(before + replayed, r.sink.played.size, "the cues in the replayed stretch are scheduled again, once each")
    }

    @Test
    fun aJumpForwardSkipsTheThunderInBetweenInsteadOfPlayingItLate() {
        val r = rig()
        r.run(5f)
        val before = r.sink.played.size
        r.scene = 120f
        r.sink.clock = r.scene + r.offset
        r.scheduler.advance(r.scene)
        assertEquals(before, r.sink.played.size, "the 115 seconds skipped are not played in a burst")
    }

    @Test
    fun noStormMeansNoThunder() {
        val r = rig(storm = null)
        r.run(60f)
        assertTrue(r.sink.played.isEmpty())
    }

    @Test
    fun theLoudestVoicesSurviveWhenThereAreTooMany() {
        val r = rig(config = SceneAudioConfig(maxThunderVoices = 1, lookaheadSeconds = 40f))
        r.run(1f)
        assertTrue(r.scheduler.pendingCount <= 1)
        assertTrue(r.sink.played.count { !it.cancelled } <= 1)
    }

    @Test
    fun bedsEaseToTheWeatherInsteadOfJumping() {
        val r = rig(storm = null)
        r.scheduler.provideBeds(AmbienceBeds(tiny, tiny, tiny))
        r.scheduler.setLevels(AmbienceLevels(rain = 1f, wind = 0f, muffle = 0f, fire = 0f))
        r.run(0.5f)
        val rain = r.sink.loops[0]
        assertTrue(rain.gain in 0.05f..0.7f * 0.5f + 0.01f, "after half a second the rain has begun to rise, not arrived: ${rain.gain}")
        r.run(5f)
        assertEquals(0.7f, rain.gain, 1e-3f)
        assertTrue(rain.gains.zipWithNext().all { (a, b) -> abs(b - a) < 0.02f }, "every step is small: no click")
        assertEquals(0f, r.sink.loops[1].gain, "no wind was asked for")
    }

    @Test
    fun bedsFallBackToSilenceWhenTheWeatherClears() {
        val r = rig(storm = null)
        r.scheduler.provideBeds(AmbienceBeds(tiny, tiny, tiny))
        r.scheduler.setLevels(AmbienceLevels(1f, 1f, 0f, 1f)); r.run(5f)
        r.scheduler.setLevels(AmbienceLevels(0f, 0f, 0f, 0f)); r.run(5f)
        assertTrue(r.sink.loops.all { it.gain < 0.01f })
    }

    @Test
    fun thunderMadeAheadOfTimeIsUsedAndNotMadeAgain() {
        var made = 0
        val sink = RecordingAudioSink(50.0)
        val scheduler = SceneAudioScheduler(sink) { made++; tiny }
        scheduler.setStorm(model)
        scheduler.advance(0f)
        val due = scheduler.cuesToRender(0f)
        assertEquals(1, due.size, "one at a time, so a frame is not spent making them all")
        val cue = due.first()
        scheduler.provide(cue, scheduler.render(cue))
        assertEquals(1, made)
        assertFalse(scheduler.cuesToRender(0f).any { it.strike.index == cue.strike.index }, "made once, not asked for again")
        var t = 0f
        while (t < cue.playAt + 0.1f) { t += 0.05f; sink.clock = 50.0 + t; scheduler.advance(t) }
        assertEquals(1, made, "when it came due the made peal was used")
        assertEquals(1, sink.played.size)
    }

    @Test
    fun releaseCancelsAndSilencesEverything() {
        val r = rig()
        r.scheduler.provideBeds(AmbienceBeds(tiny, tiny, tiny))
        val cue = StormCues.thunder(model, 0f, 60f).first()
        r.scene = cue.playAt - 0.4f; r.step(0f)
        r.scheduler.release()
        assertTrue(r.sink.played.filter { it.startAt > r.sink.clock }.all { it.cancelled })
        assertTrue(r.sink.loops.all { it.stopped })
    }

    @Test
    fun aLatencyOffsetShiftsEveryStart() {
        val plain = rig(); val late = rig(config = SceneAudioConfig(latencyOffsetSeconds = -0.12f))
        plain.run(30f); late.run(30f)
        val a = plain.sink.played.first().startAt; val b = late.sink.played.first().startAt
        assertEquals(-0.12, b - a, 0.02)
    }
}
