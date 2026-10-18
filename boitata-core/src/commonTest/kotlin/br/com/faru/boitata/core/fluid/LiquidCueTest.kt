package br.com.faru.boitata.core.fluid

import br.com.faru.boitata.core.pour.PourJob
import br.com.faru.boitata.core.sip.Drinker
import br.com.faru.boitata.core.sip.SipJob
import br.com.faru.boitata.core.sip.SipPose
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiquidCueTest {
    private fun listen(vararg vessels: LiquidVessel): MutableList<LiquidCue> {
        val heard = mutableListOf<LiquidCue>()
        for (v in vessels) v.events = LiquidEvents { heard += it }
        return heard
    }

    @Test
    fun aStreamIsHeardWhileItSpillsAndNothingBefore() {
        val glass = LiquidVessel(0.07f, 0.1f, Fluid.Water, 0.95f)
        val heard = listen(glass)
        repeat(30) { glass.step(1f / 60f) }
        assertTrue(heard.none { it.kind == LiquidCueKind.Stream }, "a standing glass is quiet")
        glass.body.angle = 0.7f
        repeat(30) { glass.step(1f / 60f) }
        assertTrue(heard.any { it.kind == LiquidCueKind.Stream && it.source === glass && it.strength > 0f })
    }

    @Test
    fun aVesselKnockedOverThudsOnceWhenItLiesDown() {
        val glass = LiquidVessel(0.07f, 0.1f, Fluid.Water, 0.3f)
        val heard = listen(glass)
        glass.knock(9f, 1f)
        repeat(60 * 6) { glass.step(1f / 60f) }
        assertEquals(1, heard.count { it.kind == LiquidCueKind.Knock }, "${heard.map { it.kind }}")
    }

    @Test
    fun aHotLiquidBoilsAndAColdOneDoesNot() {
        val hot = LiquidVessel(0.07f, 0.1f, Fluid.Water, 0.5f); hot.heatTo(100f)
        val cold = LiquidVessel(0.07f, 0.1f, Fluid.Water, 0.5f)
        val heardHot = listen(hot); val heardCold = listen(cold)
        hot.step(1f / 60f); cold.step(1f / 60f)
        assertTrue(heardHot.any { it.kind == LiquidCueKind.Boil })
        assertTrue(heardCold.none { it.kind == LiquidCueKind.Boil })
    }

    @Test
    fun everyGulpIsHeard() {
        val glass = LiquidVessel(0.08f, 0.1f, Fluid.Coffee, 0.8f)
        val heard = listen(glass)
        val job = SipJob(glass, Drinker.You, SipPose(400f, 600f, 1f, 0f), 1000f, 800f, 700f)
        repeat(60 * 3) { glass.step(1f / 60f); job.step(1f / 60f) }
        assertEquals(job.gulps, heard.count { it.kind == LiquidCueKind.Gulp })
        assertTrue(job.gulps >= 3)
    }

    @Test
    fun aStreamLandingInAnotherContainerSplashesInIt() {
        val jar = LiquidVessel(0.075f, 0.15f, Fluid.Water, 0.7f); val mug = LiquidVessel(0.11f, 0.09f, Fluid.Water, 0.1f)
        val heard = listen(jar, mug)
        val job = PourJob(jar, mug, 0.2f, 0.5f)
        var t = 0f
        while (!job.done && t < 60f) { jar.step(1f / 60f); mug.step(1f / 60f); job.step(1f / 60f); t += 1f / 60f }
        assertTrue(heard.any { it.kind == LiquidCueKind.Splash && it.source === mug }, "the splash is the mug's")
        assertTrue(heard.any { it.kind == LiquidCueKind.Stream && it.source === jar }, "the stream is the jar's")
    }

    @Test
    fun withoutAListenerNothingHappens() {
        val glass = LiquidVessel(0.07f, 0.1f, Fluid.Water, 0.95f)
        glass.body.angle = 0.7f
        repeat(30) { glass.step(1f / 60f) }
        assertTrue(glass.spilled > 0f)
    }

    @Test
    fun aMixKeepsToxicityBetweenItsParts() {
        val mix = Fluid.Water.mix(Fluid.Kerosene, 1f, 1f)
        assertTrue(mix.toxicity in 0.4f..0.6f)
    }
}
