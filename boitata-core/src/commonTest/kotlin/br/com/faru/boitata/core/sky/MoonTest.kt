package br.com.faru.boitata.core.sky

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoonTest {
    @Test
    fun theLitSideFollowsWaxingAndTheHemisphere() {
        assertEquals(true, MoonShape.litOnRight(waxing = true, southernHemisphere = false))
        assertEquals(false, MoonShape.litOnRight(waxing = true, southernHemisphere = true), "south: a waxing moon is lit on the left")
        assertEquals(true, MoonShape.litOnRight(waxing = false, southernHemisphere = true))
        assertEquals(false, MoonShape.litOnRight(waxing = false, southernHemisphere = false))
    }

    @Test
    fun theEightPhasesRunThroughAMonthAndFeedTheSky() {
        assertEquals(8, MoonPhase.All.size)
        assertEquals(0f, MoonPhase.New.fraction); assertEquals(1f, MoonPhase.Full.fraction)
        assertTrue(MoonPhase.WaxingGibbous.waxing && !MoonPhase.WaningGibbous.waxing)
        val sky = SkyModel.at(23f, SkyWeather.Clear, MoonPhase.LastQuarter)
        assertEquals(0.5f, sky.moonPhase); assertEquals(false, sky.moonWaxing)
        assertTrue(SkyModel.at(23f, SkyWeather.Clear, MoonPhase.Full).lightStrength > SkyModel.at(23f, SkyWeather.Clear, MoonPhase.New).lightStrength, "a full moon lights more than a new one")
    }
}
