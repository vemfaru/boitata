package br.com.faru.boitata.core.surface

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaneWeatherTest {
    private fun run(pane: PaneWeather, climate: SurfaceClimate, seconds: Float, heat: ((Float, Float) -> Float)? = null) = repeat((seconds * 20).toInt()) { pane.step(1f / 20f, climate, heat) }

    @Test
    fun theDewPointOfRoomAirIsWhereMagnusSaysItIs() {
        assertEquals(9.3f, SurfaceClimate.dewPoint(20f, 0.5f), 0.3f)
        assertEquals(20f, SurfaceClimate.dewPoint(20f, 1f), 0.1f)
        assertTrue(SurfaceClimate.dewPoint(20f, 0.8f) > SurfaceClimate.dewPoint(20f, 0.4f), "damper air condenses on warmer glass")
    }

    @Test
    fun aColdPaneInHumidRoomAirFogsAndAWarmOneStaysClear() {
        val foggy = PaneWeather(); run(foggy, SurfaceClimate(outsideTemp = 2f, insideTemp = 22f, humidity = 0.7f), 60f)
        assertTrue(foggy.averageFog() > 0.6f, "fogged: ${foggy.averageFog()}")
        val clear = PaneWeather(); run(clear, SurfaceClimate(outsideTemp = 20f, insideTemp = 22f, humidity = 0.45f), 60f)
        assertEquals(0f, clear.averageFog(), 0.01f)
    }

    @Test
    fun anOpenWindowKeepsTheGlassClearer() {
        val shut = PaneWeather(); run(shut, SurfaceClimate(outsideTemp = 6f, insideTemp = 22f, humidity = 0.6f, draught = 0f), 40f)
        val open = PaneWeather(); run(open, SurfaceClimate(outsideTemp = 6f, insideTemp = 22f, humidity = 0.6f, draught = 1f), 40f)
        assertTrue(open.averageFog() < shut.averageFog() * 0.5f, "${open.averageFog()} vs ${shut.averageFog()}")
    }

    @Test
    fun belowFreezingTheIceGrowsInFromTheEdgesAndItThawsWhenItWarms() {
        val pane = PaneWeather(); val cold = SurfaceClimate(outsideTemp = -12f, insideTemp = 20f, humidity = 0.6f)
        run(pane, cold, 30f)
        val edge = pane.frost[(pane.rows / 2) * pane.cols + 1]; val middle = pane.frost[(pane.rows / 2) * pane.cols + pane.cols / 2]
        assertTrue(edge > middle, "the edges freeze first: $edge vs $middle")
        assertTrue(pane.averageFrost() > 0.15f, "ice: ${pane.averageFrost()}")
        val before = pane.averageFrost()
        run(pane, SurfaceClimate(outsideTemp = 8f, insideTemp = 21f, humidity = 0.3f), 60f)
        assertTrue(pane.averageFrost() < before * 0.3f, "it melted: ${pane.averageFrost()} of $before")
    }

    @Test
    fun heatNearAPatchOfGlassMeltsIceAndClearsFogThereOnly() {
        val pane = PaneWeather(); run(pane, SurfaceClimate(outsideTemp = -16f, insideTemp = 20f, humidity = 0.6f), 40f)
        val fire: (Float, Float) -> Float = { x, y -> if (x < 0.3f && y > 0.6f) 1f else 0f }
        run(pane, SurfaceClimate(outsideTemp = -16f, insideTemp = 20f, humidity = 0.6f), 15f, fire)
        val near = pane.frost[(pane.rows * 0.8f).toInt() * pane.cols + 3]; val far = pane.frost[(pane.rows * 0.8f).toInt() * pane.cols + pane.cols - 4]
        assertTrue(near < far, "melted near the flame: $near vs $far")
    }

    @Test
    fun rainBeadsRunDownAndLeaveAClearStreakThroughTheFog() {
        val pane = PaneWeather()
        run(pane, SurfaceClimate(outsideTemp = 5f, insideTemp = 22f, humidity = 0.7f), 50f)
        val fogBefore = pane.averageFog()
        run(pane, SurfaceClimate(outsideTemp = 5f, insideTemp = 22f, humidity = 0.7f, rain = 1f), 10f)
        assertTrue(pane.drops > 0, "there are drops: ${pane.drops}")
        assertTrue(pane.averageFog() < fogBefore, "streaks cleared the fog: ${pane.averageFog()} vs $fogBefore")
    }

    @Test
    fun wipingClearsTheFogUnderTheHandAndItReturns() {
        val pane = PaneWeather(); val climate = SurfaceClimate(outsideTemp = 2f, insideTemp = 22f, humidity = 0.7f)
        run(pane, climate, 60f)
        pane.wipe(0.5f, 0.5f, 0.15f)
        assertEquals(0f, pane.fog[(pane.rows / 2) * pane.cols + pane.cols / 2])
        assertTrue(pane.fog[3 * pane.cols + 3] > 0.5f, "the corner is still fogged")
        run(pane, climate, 40f)
        assertTrue(pane.fog[(pane.rows / 2) * pane.cols + pane.cols / 2] > 0.4f, "and it fogs up again")
    }

    @Test
    fun snowPilesOnTheSillInTheColdAndMeltsInTheWarm() {
        val pane = PaneWeather(); run(pane, SurfaceClimate(outsideTemp = -5f, insideTemp = 18f, snow = 1f), 60f)
        assertTrue(pane.sill > 0.4f, "${pane.sill}")
        val peak = pane.sill
        run(pane, SurfaceClimate(outsideTemp = 12f, insideTemp = 22f, snow = 0f), 90f)
        assertTrue(pane.sill < peak * 0.6f)
    }
}
