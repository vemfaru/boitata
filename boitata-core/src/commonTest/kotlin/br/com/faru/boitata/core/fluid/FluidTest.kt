package br.com.faru.boitata.core.fluid

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FluidTest {
    private val quarter = (PI / 2).toFloat()

    @Test
    fun anUprightContainerHoldsItsWholeVolumeAndOneLyingOnItsSideHoldsNothingOfIt() {
        assertEquals(1.5f * 3f, Tilt.capacity(1.5f, 3f, 0f), 1e-3f)
        assertEquals(0f, Tilt.capacity(1.5f, 3f, quarter), 1e-3f, "lying on its side the lip is at the floor")
    }

    @Test
    fun aSquareTippedToFortyFiveDegreesHoldsHalfItsVolume() {
        assertEquals(0.5f, Tilt.capacity(1f, 1f, (PI / 4).toFloat()), 1e-3f)
    }

    @Test
    fun theAreaBelowALevelIsThatLevelTimesTheWidthWhenUprightAndTheLevelFindsItsVolume() {
        assertEquals(1.2f * 2f, Tilt.areaBelow(2f, 3f, 0f, 1.2f), 1e-4f)
        val level = Tilt.levelFor(2f, 3f, 0.4f, 2.5f)
        assertEquals(2.5f, Tilt.areaBelow(2f, 3f, 0.4f, level), 0.01f)
        assertTrue(Tilt.levelFor(2f, 3f, 0.4f, 100f) <= Tilt.lipHeight(2f, 3f, 0.4f) + 1e-4f, "never above the lip")
    }

    @Test
    fun theMoreItTipsTheLessItCanHoldSoAFullOneSpills() {
        var last = Float.MAX_VALUE
        for (deg in listOf(0, 10, 20, 40, 60, 80)) {
            val cap = Tilt.capacity(1f, 2f, (deg * PI / 180).toFloat())
            assertTrue(cap <= last + 1e-4f, "capacity falls as it tips: $deg -> $cap")
            last = cap
        }
    }

    @Test
    fun aRippleTravelsBouncesAndDiesAwayWithTheVolumeKept() {
        val w = WaveSurface(40).also { it.waveSpeed = 4f; it.damping = 1.0f }
        w.impulse(0.5f, 1f)
        var peak = 0f
        repeat(60) { w.step(1f / 60f); peak = maxOf(peak, w.height.max()) }
        assertTrue(peak > 0.002f, "it rose: $peak")
        assertEquals(0f, w.height.sum(), 1e-3f, "the volume is the same")
        repeat(600) { w.step(1f / 60f) }
        assertTrue(w.height.maxOf { abs(it) } < 0.003f, "and it died away: ${w.height.maxOf { abs(it) }}")
    }

    @Test
    fun aTiltedContainersSurfaceSettlesToTheSlopeItIsPulledToward() {
        val w = WaveSurface(32)
        repeat(900) { w.step(1f / 60f, tilt = 0.3f) }
        // The surface is a line of slope 0.3 per container width: left lower, right higher.
        assertEquals(0.3f, w.heightAt(1f) - w.heightAt(0f), 0.08f)
        assertTrue(w.heightAt(1f) > w.heightAt(0f))
    }

    @Test
    fun aGentlePushLeavesAGlassStandingAndAHardOneKnocksItOver() {
        val gentle = TippingBody(0.07f, 0.1f)
        gentle.knock(0.6f)
        repeat(600) { gentle.step(1f / 60f) }
        assertEquals(0f, gentle.angle, 1e-3f, "it rocked and sat back down")
        val hard = TippingBody(0.07f, 0.1f)
        hard.knock(8f)
        repeat(600) { hard.step(1f / 60f) }
        assertTrue(hard.lying && abs(hard.angle) > quarter - 0.01f, "it fell on its side: ${hard.angle}")
    }

    @Test
    fun aKnockTheOtherWayTipsItTheOtherWay() {
        val b = TippingBody(0.07f, 0.1f); b.knock(8f, -1f)
        repeat(600) { b.step(1f / 60f) }
        assertTrue(b.angle < -quarter + 0.02f, "${b.angle}")
    }
}

class VesselTest {
    private fun run(v: LiquidVessel, seconds: Float) = repeat((seconds * 120).toInt()) { v.step(1f / 120f) }

    @Test
    fun aGlassKnockedOverSpillsItsLiquidAndTheVolumeIsKept() {
        val glass = LiquidVessel(0.07f, 0.1f, Fluid.Water, fill = 0.8f)
        val before = glass.volume
        glass.knock(9f)
        run(glass, 6f)
        assertTrue(glass.body.lying, "it fell over")
        assertTrue(glass.empty, "and emptied: ${glass.volume}")
        assertEquals(before, glass.volume + glass.spilled, before * 0.01f, "nothing lost, nothing made")
    }

    @Test
    fun aGlassThatIsRockedButNotTippedKeepsItsLiquid() {
        val glass = LiquidVessel(0.07f, 0.1f, Fluid.Water, fill = 0.5f)
        val before = glass.volume
        glass.knock(0.6f)
        run(glass, 3f)
        assertEquals(before, glass.volume, before * 0.001f)
        assertEquals(0f, glass.spilled)
    }

    @Test
    fun itSpillsOnlyOnceTippedFarEnoughForItsLevelToReachTheLip() {
        val half = LiquidVessel(0.07f, 0.1f, Fluid.Water, fill = 0.3f)
        half.body.angle = 0.4f
        run(half, 0.2f)
        assertEquals(0f, half.spilled, "a third full, only a little tipped: nothing runs out")
        val full = LiquidVessel(0.07f, 0.1f, Fluid.Water, fill = 0.95f)
        full.body.angle = 0.4f
        full.step(1f / 60f)
        assertTrue(full.spilling && full.jetSpeed > 0.1f, "nearly full and tipped: it runs over the lip at ${full.jetSpeed} m/s")
    }

    @Test
    fun thickLiquidsSpillSlowerThanWater() {
        fun spilledAfterASecond(f: Fluid): Float {
            val v = LiquidVessel(0.07f, 0.1f, f, fill = 0.9f); v.body.angle = 0.9f
            repeat(6) { v.step(1f / 120f) }
            return v.spilled
        }
        assertTrue(spilledAfterASecond(Fluid.Water) > spilledAfterASecond(Fluid.Oil) * 1.5f)
    }

    @Test
    fun pouringRaisesTheLevelAndADropMakesRipples() {
        val glass = LiquidVessel(0.07f, 0.1f, fill = 0.2f)
        val level = glass.level
        glass.pour(0.07f * 0.02f)
        assertTrue(glass.level > level)
        glass.drop(0.5f, 0.8f)
        glass.step(1f / 60f)
        assertTrue(glass.surface.energy() > 0f)
    }
}

class PuddleTest {
    @Test
    fun aGlassfulMakesAPuddleAboutFifteenCentimetresAcrossThatSpreadsFastAtFirst() {
        val p = Puddle(0f, Fluid.Water, volume = 0.07f * 0.07f * 0.06f)         // a glass, 7 cm across and 6 cm of water
        assertEquals(0.14f, p.settledRadius, 0.04f)
        p.add(0f)
        repeat(30) { p.step(1f / 60f) }
        val half = p.radius
        repeat(300) { p.step(1f / 60f) }
        assertTrue(half in 0.02f..p.radius, "it had begun: $half")
        assertEquals(p.settledRadius, p.radius, 0.005f, "and it settles")
    }

    @Test
    fun oilSpreadsSlowerAndStaysThickerThanWater() {
        fun after(f: Fluid, seconds: Float): Float { val p = Puddle(0f, f, 3e-4f); p.add(0f); repeat((seconds * 60).toInt()) { p.step(1f / 60f) }; return p.radius }
        assertTrue(after(Fluid.Water, 0.3f) > after(Fluid.Oil, 0.3f), "slower")
        assertTrue(Puddle(0f, Fluid.Oil, 3e-4f).settledRadius < Puddle(0f, Fluid.Water, 3e-4f).settledRadius, "thicker, so smaller")
    }

    @Test
    fun aBurningPuddleShrinksAsItIsUsedUp() {
        val p = Puddle(0f, Fluid.Kerosene, 3e-4f); p.add(0f); repeat(600) { p.step(1f / 60f) }
        val whole = p.radius
        p.burnt = 0.75f
        repeat(600) { p.step(1f / 60f) }
        assertTrue(p.radius < whole * 0.6f, "${p.radius} of $whole")
    }

    @Test
    fun aStreamLandsFartherTheHigherTheLipAndTheFasterItLeaves() {
        assertEquals(0f, Spill.fallTime(0f))
        assertTrue(Spill.reach(0.2f, 1f) > Spill.reach(0.05f, 1f))
        assertTrue(Spill.reach(0.1f, 2f) > Spill.reach(0.1f, 1f))
        assertEquals(0.5f * 9.81f * Spill.fallTime(0.1f) * Spill.fallTime(0.1f), 0.1f, 1e-4f, "it falls the height of the lip")
    }
}

class HotLiquidTest {
    @Test
    fun hotLiquidPouredIntoColdMixesByVolume() {
        val cup = LiquidVessel(0.08f, 0.1f, Fluid.Coffee, fill = 0.4f)          // 20 degrees
        val before = cup.volume
        cup.pour(before, temperature = 90f)                                     // as much again, at 90
        assertEquals(55f, cup.temperature, 0.5f)
    }

    @Test
    fun hotCoffeeCoolsTowardTheRoomAndNeverBelowIt() {
        val cup = LiquidVessel(0.08f, 0.1f, Fluid.Coffee, fill = 0.7f)
        cup.heatTo(90f); cup.coolingSeconds = 60f
        var last = 90f
        repeat(1200) { cup.step(1f / 60f); assertTrue(cup.temperature <= last + 1e-3f, "falls"); last = cup.temperature }
        assertTrue(cup.temperature in 30f..55f, "cooled most of the way in 20 s of a 60 s cooling time: ${cup.temperature}")
        repeat(60 * 600) { cup.step(1f / 60f) }
        assertTrue(cup.temperature >= 20.9f, "never below the room: ${cup.temperature}")
    }

    @Test
    fun itSteamsFromFortyFiveDegreesAndBoilsNearAHundred() {
        val cup = LiquidVessel(0.08f, 0.1f, fill = 0.7f)
        cup.heatTo(30f); assertEquals(0f, cup.steam); assertEquals(0f, cup.boil)
        cup.heatTo(70f); assertTrue(cup.steam in 0.3f..0.7f); assertEquals(0f, cup.boil)
        cup.heatTo(100f); assertEquals(1f, cup.steam); assertEquals(1f, cup.boil)
    }

    @Test
    fun aBoilingPotRaisesBubblesThatBurstAndAColdOneHasNone() {
        val boiling = LiquidVessel(0.2f, 0.2f, fill = 0.7f); boiling.heatTo(100f); boiling.coolingSeconds = 1e9f
        var seen = 0
        repeat(180) { boiling.step(1f / 60f); seen = maxOf(seen, boiling.bubbles) }
        assertTrue(seen > 4, "bubbles: $seen")
        val cold = LiquidVessel(0.2f, 0.2f, fill = 0.7f)
        repeat(180) { cold.step(1f / 60f) }
        assertEquals(0, cold.bubbles)
    }

    @Test
    fun pouringHotCoffeeMakesFoamThatFades() {
        val cup = LiquidVessel(0.08f, 0.1f, Fluid.Coffee, fill = 0.2f)
        cup.pour(0.08f * 0.1f * 0.3f, temperature = 92f, foamy = true)
        assertTrue(cup.foam > 0.6f)
        repeat(60 * 90) { cup.step(1f / 60f) }
        assertEquals(0f, cup.foam, 0.05f, "gone after ninety seconds")
    }
}

class DripsTest {
    @Test
    fun dropsFallUnderGravityAndHandTheirLiquidToTheFloor() {
        val drips = Drips()
        drips.emit(100f, 50f, 1e-6f); drips.emit(120f, 50f, 2e-6f)
        var landed = 0f; var where = 0f
        repeat(120) { drips.step(1f / 60f, floorY = 300f, gravity = 3000f) { x, v, _ -> landed += v; where = x } }
        assertEquals(3e-6f, landed, 1e-9f, "all of it arrived")
        assertEquals(0, drips.count)
        assertTrue(where in 99f..121f)
    }

    @Test
    fun aPuddleLosesVolumeWhatRunsOffTheEdge() {
        val p = Puddle(0f, Fluid.Water, 1e-4f); p.add(0f)
        p.remove(4e-5f)
        assertEquals(6e-5f, p.volume, 1e-9f)
        p.remove(1f)
        assertEquals(0f, p.volume)
    }
}

class DensityTest {
    private fun tipsAt(fluid: Fluid, fill: Float): Float {
        // The slowest knock that tips a glass over, found by bisection.
        var lo = 0.2f; var hi = 40f
        repeat(18) {
            val mid = (lo + hi) / 2f
            val v = LiquidVessel(0.07f, 0.1f, fluid, fill)
            v.knock(mid)
            repeat(720) { v.step(1f / 120f) }
            if (v.body.lying) hi = mid else lo = mid
        }
        return hi
    }

    @Test
    fun aDenseLiquidLowersTheCentreOfMassSoTheGlassIsHarderToKnockOver() {
        val water = tipsAt(Fluid.Water, 0.3f); val mercury = tipsAt(Fluid.Mercury, 0.3f); val empty = tipsAt(Fluid.Water, 0f)
        assertTrue(mercury > water * 1.5f, "mercury $mercury vs water $water")
        assertTrue(water > empty, "a little water steadies it: $water vs empty $empty")
    }

    @Test
    fun theWeightOfTheLiquidIsItsDensityTimesItsVolume() {
        val a = LiquidVessel(0.07f, 0.1f, Fluid.Water, 0.5f); val b = LiquidVessel(0.07f, 0.1f, Fluid.Mercury, 0.5f)
        assertEquals(Fluid.Mercury.density / Fluid.Water.density, b.liquidMass / a.liquidMass, 0.01f)
    }

    @Test
    fun heatThinsAThickLiquid() {
        val honey = LiquidVessel(0.07f, 0.1f, Fluid.Honey, 0.5f)
        val cold = honey.effectiveViscosity
        honey.heatTo(80f)
        assertTrue(honey.effectiveViscosity < cold * 0.4f, "${honey.effectiveViscosity} vs $cold")
    }

    @Test
    fun aVariationOfALiquidChangesOnlyWhatYouAskFor() {
        val thick = Fluid.Coffee.copy(viscosity = 0.8f, density = 1100f)
        assertEquals(0.8f, thick.viscosity); assertEquals(1100f, thick.density)
        assertEquals(Fluid.Coffee.color, thick.color); assertEquals(Fluid.Coffee.index, thick.index)
    }
}
