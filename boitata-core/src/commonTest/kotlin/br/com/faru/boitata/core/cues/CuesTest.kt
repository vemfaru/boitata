package br.com.faru.boitata.core.cues

import br.com.faru.boitata.core.sky.SkyWeather
import br.com.faru.boitata.core.sky.StormModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CuesTest {
    private val model = StormModel(seed = 17L, intensity = 1f)

    @Test
    fun thunderComesAfterTheFlashByTheTimeTheSoundTakes() {
        val strike = model.strike(3)
        val cues = StormCues.thunder(model, strike.at, strike.at + 60f)
        val cue = cues.first { it.strike.index == 3 }
        assertEquals(strike.at + strike.distanceKm / 0.343f, cue.playAt, 1e-4f)
        assertTrue(cue.playAt > strike.at, "thunder never comes before its flash")
    }

    @Test
    fun consecutiveWindowsGiveEveryCueExactlyOnce() {
        val whole = StormCues.thunder(model, 0f, 120f).map { it.strike.index to it.playAt }
        val parts = (0 until 120 step 7).flatMap { a -> StormCues.thunder(model, a.toFloat(), minOf(a + 7f, 120f)) }.map { it.strike.index to it.playAt }
        assertEquals(whole.sortedBy { it.second }, parts.sortedBy { it.second })
        assertEquals(whole.size, whole.toSet().size, "no cue twice")
        assertTrue(whole.isNotEmpty())
    }

    @Test
    fun aWindowThatStartsAfterTheFlashStillHearsTheThunder() {
        // The flash was long ago, the thunder is only now arriving: a timer started at the flash would have been lost to a pause or a seek.
        val far = (0 until 40).map { model.strike(it) }.first { it.distanceKm > 3f }
        val cues = StormCues.thunder(model, far.at + far.thunderDelay - 0.5f, far.at + far.thunderDelay + 0.5f)
        assertTrue(cues.any { it.strike.index == far.index })
    }

    @Test
    fun cuesAreInOrder() {
        val cues = StormCues.thunder(model, 0f, 200f)
        assertEquals(cues.sortedBy { it.playAt }.map { it.playAt }, cues.map { it.playAt })
    }

    @Test
    fun nearAndBrightIsLouderThanFarAndDim() {
        val near = ThunderCue(0f, model.strikes(0f, 400f).minBy { it.distanceKm })
        val far = ThunderCue(0f, model.strikes(0f, 400f).maxBy { it.distanceKm })
        assertTrue(near.loudness > far.loudness)
    }

    @Test
    fun stormWeatherIsLoudRainAndWindClearWeatherIsNeitherRainNorMuffled() {
        val storm = AmbienceLevels.of(SkyWeather(cloud = 1f, precipitation = 0.9f, storm = 1f))
        val clear = AmbienceLevels.of(SkyWeather.Clear)
        assertTrue(storm.rain > 0.8f && storm.wind > clear.wind)
        assertEquals(0f, clear.rain)
        assertEquals(0f, clear.muffle)
        assertTrue(AmbienceLevels.of(SkyWeather(fog = 1f)).muffle > 0.5f)
        assertEquals(0.6f, AmbienceLevels.of(SkyWeather.Clear, fire = 0.6f).fire)
    }

    @Test
    fun theWindBedFollowsTheSceneWindAndNeverGoesBelowTheWeather() {
        val clear = SkyWeather.Clear
        assertTrue(AmbienceLevels.of(clear, windStrength = 0.9f).wind > AmbienceLevels.of(clear).wind + 0.4f, "a strong wind is loud even on a clear day")
        val storm = SkyWeather(cloud = 1f, precipitation = 0.9f, storm = 1f)
        assertEquals(AmbienceLevels.of(storm).wind, AmbienceLevels.of(storm, windStrength = 0.1f).wind, "a weak wind does not quiet a storm")
        assertTrue(AmbienceLevels.of(clear, windStrength = 5f).wind <= 1f)
    }
}
