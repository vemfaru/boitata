package br.com.faru.boitata.core.sky

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkyModelTest {
    @Test
    fun theSunIsHighAtMiddayAndBelowTheHorizonAtMidnight() {
        assertTrue(SkyModel.sunElevation(12.25f) > 0.97f)
        assertTrue(SkyModel.sunElevation(0f) < -0.5f)
        assertEquals(0f, SkyModel.sunElevation(SkyModel.SUNRISE), 0.001f)
        assertEquals(0f, SkyModel.sunElevation(SkyModel.SUNSET), 0.001f)
    }

    @Test
    fun daylightRisesThroughDawnAndFallsThroughDusk() {
        assertEquals(0f, SkyModel.daylight(2f), 0.001f)
        assertEquals(1f, SkyModel.daylight(13f), 0.001f)
        assertTrue(SkyModel.daylight(6.5f) in 0.2f..0.8f, "half light at dawn")
        assertTrue(SkyModel.daylight(7f) < SkyModel.daylight(8f))
        assertTrue(SkyModel.daylight(17f) > SkyModel.daylight(18.5f))
        assertEquals(SkyModel.daylight(3f), SkyModel.daylight(27f), 0.0001f, "the clock wraps")
    }

    @Test
    fun theSunCrossesTheWindowLeftToRightAndTheMoonTakesOver() {
        val morning = SkyModel.at(8f).sun
        val afternoon = SkyModel.at(16f).sun
        assertTrue(morning.up && afternoon.up && morning.x < afternoon.x)
        assertFalse(SkyModel.at(23f).sun.up)
        val moon = SkyModel.at(23f).moon
        assertTrue(moon.up)
        assertTrue(moon.y < SkyModel.HORIZON_Y, "the moon is above the horizon of the view")
        assertFalse(SkyModel.at(13f).moon.up)
    }

    @Test
    fun starsShowOnlyOnAClearDarkNight() {
        assertEquals(1f, SkyModel.at(1f).stars, 0.001f)
        assertEquals(0f, SkyModel.at(13f).stars, 0.001f)
        assertTrue(SkyModel.at(1f, SkyWeather(cloud = 1f)).stars < 0.15f, "cloud hides them")
        assertTrue(SkyModel.at(1f, SkyWeather(precipitation = 1f)).stars < 0.15f, "so does rain")
    }

    @Test
    fun theLightFollowsTheSunByDayAndTheMoonByNightAndIsWeakerUnderCloud() {
        val noon = SkyModel.at(12.5f)
        val midnight = SkyModel.at(0.5f)
        assertTrue(noon.lightFrom === noon.sun || noon.lightFrom == noon.sun)
        assertTrue(noon.lightStrength > midnight.lightStrength * 2)
        assertTrue(midnight.lightFrom.up || midnight.lightFrom == midnight.moon)
        assertTrue(SkyModel.at(12.5f, SkyWeather(cloud = 1f, precipitation = 1f)).lightStrength < noon.lightStrength * 0.6f)
        assertTrue(SkyModel.at(12.5f).lightStrength <= 1f && SkyModel.at(12.5f).lightStrength > 0.5f)
    }

    @Test
    fun theSkyIsWarmAtTheHorizonAtDawnAndDuskAndColdAtNight() {
        val noon = SkyModel.at(12.5f).horizon
        val dusk = SkyModel.at(SkyModel.SUNSET).horizon
        val night = SkyModel.at(1f).horizon
        assertTrue(dusk.red > dusk.blue, "warm at dusk")
        assertTrue(night.blue > night.red, "cold at night")
        assertTrue(noon.red + noon.green + noon.blue > night.red + night.green + night.blue + 1f, "brighter by day")
    }

    @Test
    fun theWindowsAcrossTheStreetLightUpAsDayGoes() {
        assertEquals(0f, SkyModel.at(13f).windowsLit, 0.001f)
        assertTrue(SkyModel.at(0f).windowsLit > 0.5f)
        assertTrue(SkyModel.at(18.5f).windowsLit > SkyModel.at(17f).windowsLit)
    }

    @Test
    fun theSkyChangesSmoothlyWithTheClock() {
        var previous = SkyModel.at(0f)
        var h = 0f
        while (h < 24f) {
            h += 0.1f
            val s = SkyModel.at(h)
            assertTrue(kotlin.math.abs(s.daylight - previous.daylight) < 0.12f, "daylight jumps at $h")
            assertTrue(kotlin.math.abs(s.horizon.red - previous.horizon.red) < 0.12f, "colour jumps at $h")
            assertTrue(kotlin.math.abs(s.lightStrength - previous.lightStrength) < 0.12f, "light jumps at $h")
            previous = s
        }
    }
}
