package br.com.faru.boitata.plants

import br.com.faru.boitata.core.fluid.Fluid
import br.com.faru.boitata.core.fluid.LiquidVessel
import br.com.faru.boitata.core.pour.PourJob
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanterTest {
    private val indoors = PlantEnvironment(daylight = 1f, light = 0.35f, lightSide = 0f, rain = 0f, heat = 0.2f, wind = 0f)

    private fun planter(soil: Soil = Soil.Loam, moisture: Float = 0.1f) =
        Planter(LiquidVessel(0.14f, 0.13f, Fluid.Water, fill = 0f), PlantPatch(soil, listOf(PlantSpecies.Fern), moisture))

    private fun run(p: Planter, seconds: Float) = repeat((seconds * 60).toInt()) { p.pot.step(1f / 60f); p.step(1f / 60f) }

    @Test
    fun theSoilTakesTheRoomUnderTheLiquidAndTheRimIsWhereItRunsOver() {
        val p = planter()
        assertEquals(p.pot.width * p.pot.height * 0.72f, p.pot.solid, 1e-6f)
        p.pot.pour(p.pot.width * p.pot.height * 0.28f)
        assertEquals(1f, p.pot.fill, 1e-3f, "full to the rim once the headroom is filled")
        p.pot.pour(p.pot.width * p.pot.height * 0.1f)
        repeat(60) { p.pot.step(1f / 60f) }
        assertTrue(p.pot.spilled > 0f, "more than the rim holds runs over")
    }

    @Test
    fun waterStandingOnTheSoilSoaksInAndWetsIt() {
        val p = planter()
        p.pot.pour(p.pot.width * p.pot.height * 0.1f)
        val before = p.patch.wet.moisture
        run(p, 20f)
        assertTrue(p.pot.volume < p.pot.width * p.pot.height * 0.003f, "nothing left standing: ${p.pot.volume}")
        assertTrue(p.patch.wet.moisture > before + 0.1f, "${p.patch.wet.moisture} vs $before")
    }

    @Test
    fun whatIsPouredIsSoakedUpOrDrainedOrStillStandingNothingIsLost() {
        val p = planter()
        val poured = p.pot.width * p.pot.height * 0.2f
        p.pot.pour(poured)
        val moistureBefore = p.patch.wet.moisture
        run(p, 40f)
        val soaked = (p.patch.wet.moisture - moistureBefore) * p.poreArea
        assertEquals(poured, soaked + p.drained + p.pot.volume + p.pot.spilled, poured * 0.02f)
    }

    @Test
    fun sandSoaksFasterThanClay() {
        fun standingAfter(soil: Soil): Float { val p = planter(soil, 0.1f); p.pot.pour(p.pot.width * p.pot.height * 0.1f); run(p, 3f); return p.pot.volume }
        assertTrue(standingAfter(Soil.Sand) < standingAfter(Soil.Clay))
    }

    @Test
    fun aSoakedSoilDrainsOutOfTheHoleInTheBottom() {
        val p = planter(moisture = 0.75f)
        repeat(3) { p.pot.pour(p.pot.width * p.pot.height * 0.1f); run(p, 6f) }
        assertTrue(p.drained > 0f, "the extra ran out of the hole")
        assertTrue(p.patch.wet.moisture <= 0.85f, "soil only keeps about what it can hold: ${p.patch.wet.moisture}")
        assertTrue(p.takeDrained() > 0f); assertEquals(0f, p.takeDrained())
    }

    @Test
    fun aThirstyPlantPerksUpWhenWateredAndStaysLimpWhenNot() {
        fun droopAfter(water: Boolean): Float {
            val p = planter(moisture = 0.05f)
            p.patch.plants[0].hydration = 0.1f
            if (water) { p.pot.pour(p.pot.width * p.pot.height * 0.25f); run(p, 30f) }
            repeat(24) { p.patch.step(indoors, 0.5f) }
            return p.patch.plants[0].droop(PlantSpecies.Fern, 1f)
        }
        assertTrue(droopAfter(true) < droopAfter(false) - 0.2f)
    }

    @Test
    fun kerosenePoisonsTheSoilAndTheWaterDoesNot() {
        fun after(fluid: Fluid): Pair<Float, Float> {
            val p = planter(moisture = 0.4f)
            p.pot.fluid = fluid
            p.pot.pour(p.pot.width * p.pot.height * 0.25f)
            run(p, 30f)
            repeat(12) { p.patch.step(indoors, 0.5f) }
            return p.patch.wet.contamination to p.patch.plants[0].stress
        }
        val water = after(Fluid.Water); val kerosene = after(Fluid.Kerosene); val milk = after(Fluid.Milk)
        assertEquals(0f, water.first)
        assertTrue(kerosene.first > 0.3f && kerosene.second > 0.3f, "kerosene $kerosene")
        assertTrue(milk.first > water.first && milk.first < kerosene.first, "milk is in between: $milk")
    }

    @Test
    fun aLiquidThatIsMostlyNotWaterWatersLess() {
        val coffee = PlantFluids.of(Fluid.Coffee); val water = PlantFluids.of(Fluid.Water)
        assertEquals(1f, water.hydrates); assertEquals(0f, water.toxicity)
        assertTrue(coffee.hydrates < 0.6f && coffee.toxicity > 0f)
        val mix = PlantFluids.of(Fluid.Water.mix(Fluid.Kerosene, 1f, 1f))
        assertTrue(mix.toxicity in 0.4f..0.6f && mix.hydrates < water.hydrates)
    }

    @Test
    fun aSoakedSoilThatStaysSoakedDrownsAPlantThatWantsLittleWater() {
        val patch = PlantPatch(Soil.Clay, listOf(PlantSpecies.Succulent), 1f)
        var most = 0f
        repeat(8) { PlantModel.stepSoil(patch.wet, Soil.Clay, indoors, 1f); most = maxOf(most, patch.wet.saturatedHours) }
        assertTrue(most >= 6f, "$most")
        // The succulent, which wants little water, is stressed by it; a fern, which wants a lot, is not.
        val succulent = PlantState(); val fern = PlantState()
        val wet = SoilMoisture(1f); wet.saturatedHours = 12f
        repeat(10) { PlantModel.step(succulent, PlantSpecies.Succulent, Soil.Clay, wet, indoors, 1f); PlantModel.step(fern, PlantSpecies.Fern, Soil.Clay, wet, indoors, 1f) }
        assertTrue(succulent.stress > fern.stress + 0.3f, "${succulent.stress} vs ${fern.stress}")
    }

    @Test
    fun aJarPoursIntoAPotAndTheSoilDrinksIt() {
        val p = planter(moisture = 0.2f)
        val jar = LiquidVessel(0.075f, 0.15f, Fluid.Water, 0.7f)
        val job = PourJob(jar, p.pot, homeX = 0.2f, targetX = 0.5f)
        var t = 0f
        val before = p.patch.wet.moisture
        while (!job.done && t < 60f) { jar.step(1f / 60f); p.pot.step(1f / 60f); p.step(1f / 60f); job.step(1f / 60f); t += 1f / 60f }
        assertTrue(job.done, "poured")
        run(p, 15f)
        assertTrue(p.patch.wet.moisture > before + 0.15f, "${p.patch.wet.moisture} vs $before")
        assertTrue(job.delivered > 0f)
    }

    @Test
    fun soakingIsHeardAsItGoesOnAndStopsWhenNothingStands() {
        val p = planter()
        val levels = mutableListOf<Float>()
        p.pot.events = br.com.faru.boitata.core.fluid.LiquidEvents { if (it.kind == br.com.faru.boitata.core.fluid.LiquidCueKind.Soak) levels += it.strength }
        p.pot.pour(p.pot.width * p.pot.height * 0.1f)
        run(p, 40f)
        assertTrue(levels.isNotEmpty() && levels.max() > 0f)
        val count = levels.size
        run(p, 20f)
        assertEquals(count, levels.size, "quiet once nothing is left standing")
    }
}
