package br.com.faru.boitata.plants

import br.com.faru.boitata.core.sky.DayClock
import br.com.faru.boitata.core.sky.SkyModel
import br.com.faru.boitata.core.sky.SkyWeather
import br.com.faru.boitata.core.wind.Wind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlantModelTest {
    private fun env(hour: Float, weather: SkyWeather = SkyWeather.Clear, wind: Float = 0f) = PlantEnvironment.of(SkyModel.at(hour, weather), wind)

    private fun live(patch: PlantPatch, days: Int, weatherAt: (Float) -> SkyWeather = { SkyWeather.Clear }) {
        var clock = DayClock.at(6f)
        repeat(days * 24 * 4) {
            patch.step(env(clock.hour, weatherAt(clock.hour)), 0.25f)
            clock = clock.advanced(0.25f)
        }
    }

    @Test
    fun theDayClockCountsDaysAndWrapsTheHour() {
        val c = DayClock.at(23f).advanced(2f)
        assertEquals(1, c.day)
        assertEquals(1f, c.hour, 0.0001f)
        assertEquals(0, DayClock.at(5f).day)
    }

    @Test
    fun aDroughtDriesTheSoilAndStressesThePlant() {
        val patch = PlantPatch(Soil.Loam, listOf(PlantSpecies.Flower), moisture = 0.6f)
        live(patch, days = 12)
        assertTrue(patch.wet.moisture < 0.15f, "soil ${patch.wet.moisture}")
        assertTrue(patch.plants[0].stress > 0.4f, "stress ${patch.plants[0].stress}")
        assertTrue(patch.plants[0].hydration < 0.5f)
    }

    @Test
    fun rainRefillsTheSoilAndRelievesThePlant() {
        val patch = PlantPatch(Soil.Loam, listOf(PlantSpecies.Flower), moisture = 0.05f)
        live(patch, days = 3)
        val dry = patch.plants[0].stress
        live(patch, days = 2) { SkyWeather(cloud = 0.8f, precipitation = 0.9f) }
        live(patch, days = 3)
        assertTrue(patch.wet.moisture > 0.2f, "soil ${patch.wet.moisture}")
        assertTrue(patch.plants[0].stress < dry, "was $dry, now ${patch.plants[0].stress}")
    }

    @Test
    fun sandDriesFasterThanClay() {
        val sand = PlantPatch(Soil.Sand, listOf(PlantSpecies.Grass), 0.8f); val clay = PlantPatch(Soil.Clay, listOf(PlantSpecies.Grass), 0.8f)
        live(sand, 3); live(clay, 3)
        assertTrue(sand.wet.moisture < clay.wet.moisture, "sand ${sand.wet.moisture}, clay ${clay.wet.moisture}")
    }

    @Test
    fun aShadePlantIsScorchedInFullSunAndASunPlantStarvesInTheDark() {
        val wet = SoilMoisture(0.6f)
        val fern = PlantState(); val cactus = PlantState()
        repeat(40) {
            PlantModel.step(fern, PlantSpecies.Fern, Soil.Loam, wet, env(12.25f), 0.25f)
            PlantModel.step(cactus, PlantSpecies.Succulent, Soil.Loam, wet, env(12.25f, SkyWeather(cloud = 1f)), 0.25f)
        }
        assertTrue(fern.stress > 0.3f, "fern in the sun ${fern.stress}")
        assertTrue(cactus.stress > 0.3f, "cactus in the dark ${cactus.stress}")
    }

    @Test
    fun aLowSunGivesLessLightThanAHighOne() {
        assertTrue(env(7.5f).light < env(12.25f).light)
        assertEquals(0f, env(2f).light, 0.0001f, "no sun at night")
    }

    @Test
    fun nothingGrowsAtNightAndAHealthyPlantGrowsByDay() {
        val wet = SoilMoisture(0.6f)
        val night = PlantState(growth = 0.3f); val day = PlantState(growth = 0.3f)
        repeat(40) {
            PlantModel.step(night, PlantSpecies.Grass, Soil.Loam, wet, env(1f), 0.25f)
            PlantModel.step(day, PlantSpecies.Grass, Soil.Loam, wet, env(11f), 0.25f)
        }
        assertEquals(0.3f, night.growth, 0.0001f)
        assertTrue(day.growth > 0.3f)
    }

    @Test
    fun leavesTurnTowardTheSideTheSunIsOn() {
        val wet = SoilMoisture(0.6f)
        val morning = PlantState(); val evening = PlantState()
        repeat(20) {
            PlantModel.step(morning, PlantSpecies.Flower, Soil.Loam, wet, env(8f), 0.25f)
            PlantModel.step(evening, PlantSpecies.Flower, Soil.Loam, wet, env(17f), 0.25f)
        }
        assertTrue(morning.leafTurn < 0f && evening.leafTurn > 0f, "morning ${morning.leafTurn}, evening ${evening.leafTurn}")
    }

    @Test
    fun aThirstyPlantBendsMoreInTheSameWind() {
        val wind = Wind(speed = 0.5f, gustiness = 0f, turbulence = 0f)
        val full = PlantSpecies.Palm.windResponse(turgor = 1f).bend(wind, 5f)
        val thirsty = PlantSpecies.Palm.windResponse(turgor = 0.1f).bend(wind, 5f)
        val wetLeaves = PlantSpecies.Palm.windResponse(turgor = 1f, wetLeaves = 1f).bend(wind, 5f)
        assertTrue(thirsty > full, "thirsty $thirsty, full $full")
        assertTrue(wetLeaves > full, "wet $wetLeaves")
    }

    @Test
    fun aThirstyPlantDroopsMoreThanAFullOne() {
        val full = PlantState(hydration = 1f); val thirsty = PlantState(hydration = 0.1f)
        assertTrue(thirsty.droop(PlantSpecies.Flower) > full.droop(PlantSpecies.Flower))
        assertEquals(0f, thirsty.droop(PlantSpecies.Pine) , 0.1f, "a pine hardly droops")
    }

    @Test
    fun theSameDaysGiveTheSamePlant() {
        val a = PlantPatch(Soil.Loam, listOf(PlantSpecies.Flower), 0.4f); val b = PlantPatch(Soil.Loam, listOf(PlantSpecies.Flower), 0.4f)
        live(a, 5); live(b, 5)
        assertEquals(a.plants[0].stress, b.plants[0].stress)
        assertEquals(a.wet.moisture, b.wet.moisture)
        assertEquals(a.plants[0].growth, b.plants[0].growth)
    }
}
