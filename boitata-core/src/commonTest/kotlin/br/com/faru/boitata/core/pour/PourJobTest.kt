package br.com.faru.boitata.core.pour

import br.com.faru.boitata.core.fluid.Fluid
import br.com.faru.boitata.core.fluid.LiquidVessel
import br.com.faru.boitata.core.fluid.PourSide
import br.com.faru.boitata.core.fluid.Tilt
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PourJobTest {
    private fun jar(fill: Float = 0.85f) = LiquidVessel(0.075f, 0.15f, Fluid.Coffee, fill)
    private fun mug(fill: Float = 0.2f) = LiquidVessel(0.11f, 0.09f, Fluid.Milk, fill)

    /** Runs the job to its end (or a minute), like the bench: the vessels step, then the job. Returns the volume (m^3) that missed the target. */
    private fun run(job: PourJob, source: LiquidVessel, target: LiquidVessel, seconds: Float = 60f): Float {
        var missed = 0f
        var t = 0f
        while (!job.done && t < seconds) {
            source.step(1f / 60f); target.step(1f / 60f); job.step(1f / 60f)
            missed += job.missed
            t += 1f / 60f
        }
        return missed
    }

    @Test
    fun theJarPoursIntoTheMugAndEndsUprightAtHome() {
        val j = jar(); val m = mug()
        val job = PourJob(j, m, homeX = 0.3f, targetX = 0.6f)
        val before = m.volume
        run(job, j, m)
        assertTrue(job.done)
        assertTrue(m.volume > before + 0.001f, "the mug gained: ${m.volume} vs $before")
        assertEquals(0f, j.body.angle); assertEquals(0f, j.lift); assertEquals(0.3f, job.x)
        assertTrue(!j.body.held)
    }

    @Test
    fun whatTheJarLosesTheMugGainsOrIsMissed() {
        val j = jar(); val m = mug()
        val total = j.volume * j.width + m.volume * m.width
        val job = PourJob(j, m, 0.3f, 0.6f)
        val missed = run(job, j, m)
        val after = j.volume * j.width + m.volume * m.width + (j.spilled - j.spilled) + missed
        // The mug may have run over too (its own spill), which it counts in m.spilled.
        assertEquals(total, after + m.spilled * m.width, 1e-6f)
    }

    @Test
    fun theStreamIsHeldOverTheMugSoNothingIsMissedWhenItFits() {
        val j = jar(0.5f); val m = mug(0.1f)
        val job = PourJob(j, m, 0.3f, 0.6f)
        val missed = run(job, j, m)
        assertTrue(missed < j.width * j.height * 0.02f, "missed $missed")
        assertTrue(job.delivered > j.width * j.height * 0.3f * j.width * 0.5f)
    }

    @Test
    fun itPoursLeftToo() {
        val j = jar(0.5f); val m = mug(0.1f)
        val job = PourJob(j, m, homeX = 0.6f, targetX = 0.3f)
        val before = m.volume
        run(job, j, m)
        assertTrue(job.done && m.volume > before)
    }

    @Test
    fun itStopsWhenTheMugIsFullAndTheJarKeepsTheRest() {
        val j = jar(0.95f); val m = mug(0.8f)
        val job = PourJob(j, m, 0.3f, 0.6f)
        run(job, j, m)
        assertTrue(job.done)
        assertTrue(m.fill < 1.2f)
        assertTrue(!j.empty, "there is coffee left in the jar")
    }

    @Test
    fun anEmptyJarJustCarriesHomeWithNothingPoured() {
        val j = jar(0f); val m = mug(0.3f)
        val before = m.volume
        val job = PourJob(j, m, 0.3f, 0.6f)
        run(job, j, m)
        assertTrue(job.done); assertEquals(before, m.volume, 1e-6f)
    }

    @Test
    fun coffeeIntoMilkMakesAMixBetweenThem() {
        val j = jar(0.6f); val m = mug(0.3f)
        val job = PourJob(j, m, 0.3f, 0.6f)
        run(job, j, m)
        val mixed = m.fluid
        assertTrue(mixed.name.contains("Coffee") && mixed.name.contains("Milk"), mixed.name)
        assertTrue(mixed.turbidity in Fluid.Coffee.turbidity..Fluid.Milk.turbidity, "${mixed.turbidity}")
        assertTrue(mixed.density in Fluid.Coffee.density..Fluid.Milk.density, "${mixed.density}")
        assertTrue(mixed.color != Fluid.Coffee.color && mixed.color != Fluid.Milk.color)
    }

    @Test
    fun aHeldVesselDoesNotFallOverAndFallsBackWhenReleased() {
        val v = LiquidVessel(0.075f, 0.15f, Fluid.Water, 0.5f)
        v.body.hold(1.2f)
        repeat(120) { v.step(1f / 60f) }
        assertTrue(abs(v.body.angle - 1.2f) < 0.01f, "held at ${v.body.angle}")
        v.body.hold(0f)
        repeat(120) { v.step(1f / 60f) }
        assertTrue(abs(v.body.angle) < 0.01f)
        v.body.release()
        repeat(120) { v.step(1f / 60f) }
        assertEquals(0f, v.body.angle)
    }

    @Test
    fun fluidMixAveragesByVolume() {
        val half = Fluid.Water.mix(Fluid.Milk, 1f, 1f)
        assertEquals((Fluid.Water.density + Fluid.Milk.density) / 2f, half.density, 1e-3f)
        assertEquals(Fluid.Milk.turbidity / 2f, half.turbidity, 1e-4f)
        assertEquals("Water + Milk", Fluid.Water.mix(Fluid.Milk, 100f, 1f).name)
        assertEquals("Milk + Water", half.name)
        assertTrue(Fluid.Water.mix(Fluid.Kerosene, 1f, 1f).flammable)
    }

    @Test
    fun aVesselWithASpoutOnOneSideNeverTipsTheOtherWay() {
        val jug = LiquidVessel(0.1f, 0.14f, Fluid.Milk, 0.7f, pourSide = PourSide.Left)
        jug.knock(9f, direction = 1f)
        repeat(240) { jug.step(1f / 60f) }
        assertEquals(0f, jug.body.angle, "a knock from the blocked side does nothing")
        jug.body.hold(1.0f)
        repeat(60) { jug.step(1f / 60f) }
        assertEquals(0f, jug.body.angle, 1e-3f, "a hand cannot turn it the wrong way")
        jug.body.hold(-1.0f)
        repeat(120) { jug.step(1f / 60f) }
        assertTrue(jug.body.angle < -0.9f, "but the right way it goes: ${jug.body.angle}")
        jug.body.release()
        jug.knock(9f, direction = -1f)
        repeat(300) { jug.step(1f / 60f) }
        assertTrue(jug.body.angle <= 0f, "and a knock toward the spout tips it that way")
    }

    @Test
    fun aRightHandedVesselPoursFromTheLeftOfTheTargetEvenWhenTheTargetIsOnItsLeft() {
        val bottle = LiquidVessel(0.09f, 0.17f, Fluid.Wine, 0.6f, pourSide = PourSide.Right); val glass = LiquidVessel(0.074f, 0.10f, Fluid.Water, 0.2f)
        val job = PourJob(bottle, glass, homeX = 0.7f, targetX = 0.4f)
        val before = glass.volume
        var maxAngle = 0f; var minAngle = 0f; var t = 0f
        while (!job.done && t < 90f) {
            bottle.step(1f / 60f); glass.step(1f / 60f); job.step(1f / 60f); t += 1f / 60f
            maxAngle = maxOf(maxAngle, bottle.body.angle); minAngle = minOf(minAngle, bottle.body.angle)
        }
        assertTrue(job.done && glass.volume > before, "it got there and poured")
        assertEquals(0f, minAngle, "it never leaned left")
        assertTrue(maxAngle > 0.5f, "it leaned right: $maxAngle")
    }

    @Test
    fun aFullerVesselNeedsLessTiltToReachTheLipThanAnEmptierOne() {
        val full = Tilt.angleToReachLip(0.075f, 0.15f, 0.075f * 0.15f * 0.9f)
        val half = Tilt.angleToReachLip(0.075f, 0.15f, 0.075f * 0.15f * 0.5f)
        val low = Tilt.angleToReachLip(0.075f, 0.15f, 0.075f * 0.15f * 0.1f)
        assertTrue(full < half && half < low, "full $full, half $half, low $low")
        assertEquals(0f, Tilt.angleToReachLip(0.075f, 0.15f, 0.075f * 0.15f))
    }

    @Test
    fun aShotPoursAboutAShotAndTopUpFillsTheTarget() {
        fun pour(level: PourLevel): Pair<PourJob, LiquidVessel> {
            val j = LiquidVessel(0.1f, 0.2f, Fluid.Coffee, 1f); val m = mug(0.1f)
            val job = PourJob(j, m, 0.3f, 0.6f, level)
            run(job, j, m)
            return job to m
        }
        val (shot, small) = pour(PourLevel.Shot)
        val want = PourLevel.Shot.let { (it as PourLevel.Amount).share } * small.width * small.height * small.width
        assertTrue(shot.done && shot.delivered in want * 0.8f..want * 1.6f, "shot ${shot.delivered}, wanted about $want")
        val (_, filled) = pour(PourLevel.TopUp)
        assertTrue(filled.fill >= 0.94f, "topped up to ${filled.fill}")
    }

    @Test
    fun untilStoppedKeepsPouringPastFullUntilToldToStop() {
        val j = jar(0.95f); val m = mug(0.8f)
        val job = PourJob(j, m, 0.3f, 0.6f, PourLevel.UntilStopped)
        var t = 0f
        while (t < 6f) { j.step(1f / 60f); m.step(1f / 60f); job.step(1f / 60f); t += 1f / 60f }
        assertTrue(job.phase == PourJob.Phase.Pour, "still pouring with a full target")
        assertTrue(m.spilled > 0f, "the target ran over")
        job.stop()
        run(job, j, m)
        assertTrue(job.done && !j.empty, "stopped with some left")
    }
}
