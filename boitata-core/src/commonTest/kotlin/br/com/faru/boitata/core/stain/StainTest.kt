package br.com.faru.boitata.core.stain

import br.com.faru.boitata.core.fluid.Fluid
import br.com.faru.boitata.core.fluid.LiquidVessel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StainTest {
    private fun wetGlass(fluid: Fluid = Fluid.Coffee, wet: Float = 1f) = LiquidVessel(0.07f, 0.1f, fluid, 0.5f).also { it.wetBottom(fluid, wet) }

    @Test
    fun aDryFootLeavesNothingAndAWetOneLeavesAMark() {
        val field = StainField()
        field.touch(LiquidVessel(0.07f, 0.1f, Fluid.Coffee, 0.5f), 0.3f, 0f, SurfaceMaterial.Wood)
        assertEquals(0, field.count)
        field.touch(wetGlass(), 0.3f, 0f, SurfaceMaterial.Wood)
        assertEquals(1, field.count)
    }

    @Test
    fun woodTakesTheStainMoreThanCeramicDoes() {
        val wood = StainField().also { it.touch(wetGlass(), 0f, 0f, SurfaceMaterial.Wood) }
        val ceramic = StainField().also { it.touch(wetGlass(), 0f, 0f, SurfaceMaterial.Ceramic) }
        assertTrue(wood.count == 1 && wood.strength[0] > 0.4f)
        assertTrue(ceramic.count == 0 || ceramic.strength[0] < wood.strength[0] * 0.2f)
    }

    @Test
    fun coffeeStaysOnWoodButAWaterRingDriesAway() {
        val coffee = StainField().also { it.touch(wetGlass(Fluid.Coffee), 0f, 0f, SurfaceMaterial.Wood) }
        val water = StainField().also { it.touch(wetGlass(Fluid.Water), 0f, 0f, SurfaceMaterial.Wood) }
        val before = coffee.strength[0]
        repeat(60 * 60 * 10) { coffee.step(1f / 60f); water.step(1f / 60f) }
        assertEquals(1, coffee.count)
        assertTrue(coffee.strength[0] > before * 0.3f, "coffee stays: ${coffee.strength[0]} of $before")
        assertEquals(0, water.count, "a water ring dries away")
    }

    @Test
    fun aFootSetDownOnItsOwnMarkAddsToItAndDoesNotPileUpANewOne() {
        val field = StainField()
        field.touch(wetGlass(wet = 0.4f), 0.3f, 0f, SurfaceMaterial.Wood)
        val first = field.strength[0]
        field.touch(wetGlass(wet = 0.4f), 0.305f, 0f, SurfaceMaterial.Wood)
        assertEquals(1, field.count)
        assertTrue(field.strength[0] > first)
        field.touch(wetGlass(wet = 0.4f), 0.6f, 0f, SurfaceMaterial.Wood)
        assertEquals(2, field.count)
    }

    @Test
    fun theFieldHoldsNoMoreThanItsCapacityAndDropsTheWeakest() {
        val field = StainField(capacity = 3)
        for (k in 0 until 6) field.touch(wetGlass(wet = 0.2f + 0.1f * k), k * 0.5f, 0f, SurfaceMaterial.Wood)
        assertEquals(3, field.count)
    }

    @Test
    fun theWetOfTheFootRisesWhenItSpillsAndDriesAndIsUsedUpByTheMark() {
        val v = LiquidVessel(0.07f, 0.1f, Fluid.Wine, 0.95f)
        v.body.angle = 0.7f
        repeat(30) { v.step(1f / 60f) }
        assertTrue(v.spilled > 0f && v.bottomWet > 0.05f, "spilled ${v.spilled}, wet ${v.bottomWet}")
        v.body.angle = 0f; v.body.omega = 0f
        val volume = v.volume + v.spilled
        val before = v.bottomWet
        StainField().touch(v, 0f, 0f, SurfaceMaterial.Wood, amount = 0.5f)
        assertTrue(v.bottomWet < before)
        assertEquals(volume, v.volume + v.spilled, 1e-9f, "staining does not take liquid from the vessel")
        repeat(60 * 60) { v.step(1f / 60f) }
        assertEquals(0f, v.bottomWet)
    }

    @Test
    fun aTippedOverContainerLeavesNoFootprint() {
        val v = wetGlass(); v.body.angle = 1.2f
        val field = StainField().also { it.touch(v, 0f, 0f, SurfaceMaterial.Wood) }
        assertEquals(0, field.count)
    }

    @Test
    fun aVesselIsSetDownWhenItComesToRestAfterMoving() {
        val d = SetDown()
        var fired = 0
        repeat(30) { if (d.step(0f, 1f / 60f)) fired++ }
        assertEquals(0, fired, "never moved")
        repeat(30) { if (d.step(0.4f, 1f / 60f)) fired++ }
        assertEquals(0, fired, "still moving")
        repeat(60) { if (d.step(0f, 1f / 60f)) fired++ }
        assertEquals(1, fired)
    }
}
