package br.com.faru.boitata.core.sip

import br.com.faru.boitata.core.fluid.Fluid
import br.com.faru.boitata.core.fluid.LiquidVessel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SipTest {
    private val ppm = 1000f
    private val from = SipPose(400f, 600f, 1f, 0f)

    private fun job(v: LiquidVessel = LiquidVessel(0.08f, 0.1f, Fluid.Coffee, 0.8f)) = SipJob(v, Drinker.You, from, ppm, 800f, 700f)

    private fun run(j: SipJob, seconds: Float) { repeat((seconds * 60).toInt()) { j.vessel.step(1f / 60f); j.step(1f / 60f) } }

    @Test
    fun itStartsOnTheTableAndComesUpToTheMouthBiggerAndTipped() {
        val j = job()
        assertEquals(SipJob.Phase.Raise, j.phase)
        assertEquals(1f, j.pose.scale); assertEquals(400f, j.pose.x)
        var lastScale = 1f
        repeat(60) { j.vessel.step(1f / 60f); j.step(1f / 60f); assertTrue(j.pose.scale >= lastScale - 1e-4f, "bigger as it comes up"); lastScale = j.pose.scale }
        assertEquals(SipJob.Phase.Drink, j.phase)
        assertTrue(j.pose.scale > 3f, "scale ${j.pose.scale}")
        assertTrue(j.pose.tilt > 0.6f, "leaning in toward the eye: ${j.pose.tilt}")
        assertEquals(400f, j.pose.x, 1f, "in the middle of the view")
        assertEquals(0f, j.vessel.body.angle, "it does not turn sideways")
    }

    @Test
    fun whileHeldItGulpsAtOnceAndThenEveryHalfSecondAndTheLevelFalls() {
        val j = job()
        val before = j.vessel.volume
        run(j, 0.8f)
        assertEquals(SipJob.Phase.Drink, j.phase)
        assertEquals(1, j.gulps, "the first gulp is at once")
        run(j, 2f)
        assertTrue(j.gulps in 4..6, "about one every half second: ${j.gulps}")
        assertEquals(before - j.taken, j.vessel.volume, 1e-6f, "what it took is what it lost")
    }

    @Test
    fun itTipsMoreAsThereIsLessToDrink() {
        val j = job()
        run(j, 0.9f)
        val early = j.pose.tilt
        run(j, 3f)
        assertTrue(j.pose.tilt >= early, "${j.pose.tilt} vs $early")
        assertTrue(j.vessel.volume > 0f && j.vessel.spilled == 0f, "nothing is spilled on the way")
    }

    @Test
    fun lettingGoPutsItBackExactlyWhereItWasAndUpright() {
        val j = job()
        run(j, 1.5f)
        j.release()
        run(j, 1.2f)
        assertTrue(j.done)
        assertEquals(400f, j.pose.x); assertEquals(600f, j.pose.y); assertEquals(1f, j.pose.scale); assertEquals(0f, j.pose.tilt)
        assertEquals(0f, j.vessel.body.angle); assertTrue(!j.vessel.body.held)
    }

    @Test
    fun letGoBeforeItIsUpItGoesBackWithoutGulping() {
        val j = job()
        run(j, 0.3f)
        j.release()
        run(j, 1.5f)
        assertTrue(j.done); assertEquals(0, j.gulps)
    }

    @Test
    fun itGoesBackOnItsOwnWhenTheGlassIsEmpty() {
        val j = job(LiquidVessel(0.08f, 0.1f, Fluid.Coffee, 0.2f))
        run(j, 12f)
        assertTrue(j.done, "phase ${j.phase}")
        assertTrue(j.vessel.empty)
    }

    @Test
    fun aGlassOfKeroseneIsNotDrunkButItStillComesAndGoes() {
        val j = job(LiquidVessel(0.08f, 0.1f, Fluid.Kerosene, 0.8f))
        run(j, 5f)
        assertTrue(j.done); assertEquals(0, j.gulps)
    }

    @Test
    fun theViewpointSetsHowBigAndWhereTheGlassIsHeld() {
        val v = LiquidVessel(0.08f, 0.1f, Fluid.Water, 0.5f)
        val pose = PovViewpoint(rimShare = 0.5f).drinkPose(v, ppm, 800f, 700f, 0.9f)
        assertEquals(0.5f * 800f / 80f, pose.scale, 1e-4f)
        assertEquals(400f, pose.x); assertEquals(0.9f, pose.tilt)
        assertTrue(pose.y > 700f * 0.74f, "the base is below the rim")
        assertEquals("You", Drinker.You.name)
        assertTrue(Drinker.You.viewpoint.vignette > 0f && Drinker.You.viewpoint.dim > 0f)
    }
}
