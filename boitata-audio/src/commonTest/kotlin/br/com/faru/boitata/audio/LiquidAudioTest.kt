package br.com.faru.boitata.audio

import br.com.faru.boitata.core.fluid.LiquidCue
import br.com.faru.boitata.core.fluid.LiquidCueKind
import br.com.faru.boitata.core.fluid.LiquidVessel
import br.com.faru.boitata.synth.PcmBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LiquidAudioTest {
    private fun player(sink: RecordingAudioSink, pan: (LiquidVessel?) -> Float = { 0f }) = LiquidAudioPlayer(sink, panOf = pan)

    @Test
    fun aGulpIsScheduledAtOnceOnTheSinksClock() {
        val sink = RecordingAudioSink(12.5); val p = player(sink)
        p.onCue(LiquidCue(LiquidCueKind.Gulp))
        assertEquals(1, sink.played.size)
        assertEquals(12.5, sink.played[0].startAt)
        assertSame(p.map.bufferFor(LiquidCue(LiquidCueKind.Gulp)), sink.played[0].buffer)
    }

    @Test
    fun aMutedKindPlaysNothingAndOthersStillDo() {
        val sink = RecordingAudioSink(); val p = player(sink)
        p.map.setMuted(LiquidCueKind.Gulp, true)
        p.onCue(LiquidCue(LiquidCueKind.Gulp)); p.onCue(LiquidCue(LiquidCueKind.Knock))
        assertEquals(1, sink.played.size)
        assertSame(p.map.bufferFor(LiquidCue(LiquidCueKind.Knock)), sink.played[0].buffer)
    }

    @Test
    fun aSoundOfYourOwnReplacesTheDefaultAndNullBringsItBack() {
        val sink = RecordingAudioSink(); val p = player(sink)
        val mine = PcmBuffer(FloatArray(100) { 0.1f })
        val default = p.map.bufferFor(LiquidCue(LiquidCueKind.Gulp))
        p.map.set(LiquidCueKind.Gulp, mine)
        p.onCue(LiquidCue(LiquidCueKind.Gulp))
        assertSame(mine, sink.played.last().buffer)
        assertTrue(p.map.isCustom(LiquidCueKind.Gulp))
        p.map.set(LiquidCueKind.Gulp, null)
        assertSame(default, p.map.bufferFor(LiquidCue(LiquidCueKind.Gulp)))
    }

    @Test
    fun theStereoPositionFollowsWhereTheVesselIs() {
        val left = LiquidVessel(0.07f, 0.1f); val right = LiquidVessel(0.07f, 0.1f)
        val sink = RecordingAudioSink(); val p = player(sink) { if (it === left) -0.8f else 0.8f }
        p.onCue(LiquidCue(LiquidCueKind.Gulp, 1f, left)); sink.clock += 1.0; p.onCue(LiquidCue(LiquidCueKind.Gulp, 1f, right))
        assertEquals(-0.8f, sink.played[0].pan); assertEquals(0.8f, sink.played[1].pan)
    }

    @Test
    fun aHarderThudIsLouderAndTheToneChoosesTheSound() {
        val sink = RecordingAudioSink(); val p = player(sink)
        p.onCue(LiquidCue(LiquidCueKind.SetDown, 0.2f, null, 0f)); sink.clock += 1.0
        p.onCue(LiquidCue(LiquidCueKind.SetDown, 1f, null, 1f))
        assertTrue(sink.played[1].gain > sink.played[0].gain)
        assertNotEquals(sink.played[0].buffer, sink.played[1].buffer, "clay and glass sound different")
    }

    @Test
    fun theSameKindIsNotPlayedOnTopOfItselfTooSoon() {
        val sink = RecordingAudioSink(); val p = player(sink)
        repeat(10) { p.onCue(LiquidCue(LiquidCueKind.Splash)); sink.clock += 0.01 }
        assertTrue(sink.played.size <= 2, "${sink.played.size}")
        sink.clock += 1.0; p.onCue(LiquidCue(LiquidCueKind.Splash))
        assertEquals(true, sink.played.size >= 2)
    }

    @Test
    fun aStreamIsALoopThatFollowsTheFlowAndFadesWhenItStops() {
        val sink = RecordingAudioSink(); val p = player(sink)
        repeat(30) { p.onCue(LiquidCue(LiquidCueKind.Stream, 0.8f)); p.tick(1f / 60f) }
        val loop = sink.loops.single()
        assertTrue(loop.gain > 0.4f, "up while it runs: ${loop.gain}")
        repeat(120) { p.tick(1f / 60f) }
        assertTrue(loop.gain < 0.05f, "down when it stops: ${loop.gain}")
        assertEquals(1, sink.loops.size, "one voice, reused")
    }

    @Test
    fun whenOffNothingPlaysAndTheLoopsFade() {
        val sink = RecordingAudioSink(); val p = player(sink)
        repeat(30) { p.onCue(LiquidCue(LiquidCueKind.Boil, 1f)); p.tick(1f / 60f) }
        val loop = sink.loops.single()
        p.enabled = false
        p.onCue(LiquidCue(LiquidCueKind.Gulp)); p.onCue(LiquidCue(LiquidCueKind.Boil, 1f))
        repeat(120) { p.tick(1f / 60f) }
        assertEquals(0, sink.played.size)
        assertTrue(loop.gain < 0.05f)
        p.release()
        assertTrue(loop.stopped)
    }

    @Test
    fun aVesselWithAListenerTellsItWhatItDoesAndOneWithoutIsSilent() {
        val heard = mutableListOf<LiquidCueKind>()
        val glass = LiquidVessel(0.07f, 0.1f, br.com.faru.boitata.core.fluid.Fluid.Water, 0.95f)
        glass.events = br.com.faru.boitata.core.fluid.LiquidEvents { heard += it.kind }
        glass.body.angle = 0.7f
        repeat(30) { glass.step(1f / 60f) }
        assertTrue(LiquidCueKind.Stream in heard, "it hears the stream: $heard")
        val quiet = LiquidVessel(0.07f, 0.1f, br.com.faru.boitata.core.fluid.Fluid.Water, 0.95f)
        quiet.body.angle = 0.7f
        repeat(30) { quiet.step(1f / 60f) }
    }
}
