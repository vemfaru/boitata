package br.com.faru.boitata.core.sky

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StormModelTest {
    @Test
    fun theSameSeedStrikesAtTheSameMoments() {
        val a = StormModel(7L); val b = StormModel(7L); val c = StormModel(8L)
        assertEquals((0..5).map { a.strike(it).at }, (0..5).map { b.strike(it).at })
        assertTrue((0..5).map { a.strike(it).at } != (0..5).map { c.strike(it).at })
    }

    @Test
    fun strikesComeInOrderAndAStormierSkyStrikesMoreOften() {
        val calm = StormModel(3L, intensity = 0.1f); val wild = StormModel(3L, intensity = 1f)
        val times = (0..9).map { wild.strike(it).at }
        assertEquals(times.sorted(), times)
        assertTrue(wild.strikes(0f, 120f).size > calm.strikes(0f, 120f).size)
    }

    @Test
    fun thunderTrailsTheFlashByTheDistance() {
        val s = StormModel(5L).strike(2)
        assertEquals(s.distanceKm / 0.343f, s.thunderDelay, 0.001f)
        assertEquals(2.9154f, Strike(0, 0f, 1f, 1f, listOf(0f), listOf(1f), 0.5f, 1L).thunderDelay, 0.001f)
        assertTrue((0..40).all { StormModel(5L).strike(it).distanceKm in 0.2f..7f })
    }

    @Test
    fun theFlashIsSharpFadesAndIsDarkBetweenStrikes() {
        val m = StormModel(9L)
        val s = m.strike(0)
        assertEquals(0f, m.flash(s.at - 0.5f), 0.001f)
        assertTrue(m.flash(s.at + 0.005f) > 0.5f)
        assertTrue(m.flash(s.at + 0.9f) < 0.02f, "gone within a second")
        assertTrue(m.flash(s.at + 0.1f) < m.flash(s.at + 0.005f), "it fades")
        var h = 0f
        while (h < 60f) { assertTrue(m.flash(h) in 0f..1f); h += 0.013f }
    }

    @Test
    fun theBoltShowsOnlyAtTheFlare() {
        val m = StormModel(9L)
        val s = m.strike(0)
        assertNull(m.bolt(s.at - 0.2f))
        val (bolt, strength) = assertNotNull(m.bolt(s.at + 0.01f))
        assertEquals(s.index, bolt.index)
        assertTrue(strength > 0.5f)
        assertNull(m.bolt(s.at + 0.7f).takeIf { s.strokeAt.size == 1 })
    }

    @Test
    fun strikesInARangeAreTheOnesThatStartInIt() {
        val m = StormModel(11L)
        val all = m.strikes(0f, 100f)
        val firstHalf = m.strikes(0f, 50f); val secondHalf = m.strikes(50f, 100f)
        assertEquals(all.map { it.index }, (firstHalf + secondHalf).map { it.index })
        assertTrue(all.zipWithNext().all { (a, b) -> a.at < b.at })
    }
}
