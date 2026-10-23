package br.com.faru.boitata.scene.focus

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HoverIntentTest {
    private fun HoverIntent<String>.run(seconds: Float): List<HoverEvent<String>> {
        val out = mutableListOf<HoverEvent<String>>()
        repeat((seconds * 100).toInt()) { step(0.01f)?.let(out::add) }
        return out
    }

    @Test
    fun aThingIsPreviewedOnlyAfterTheEnterDelay() {
        val h = HoverIntent<String>(enterDelay = 0.08f, dwell = 0.65f)
        h.move("lamp", Offset(10f, 10f))
        assertTrue(h.run(0.05f).isEmpty())
        assertEquals(listOf<HoverEvent<String>>(HoverEvent.Preview("lamp")), h.run(0.05f))
        assertEquals("lamp", h.preview)
    }

    @Test
    fun restingStillDwellsOnceAndMovingRestartsTheDwell() {
        val h = HoverIntent<String>(enterDelay = 0.08f, dwell = 0.65f)
        h.move("lamp", Offset(10f, 10f))
        h.run(0.4f)
        h.move("lamp", Offset(40f, 10f))                      // moved: the dwell starts again
        assertTrue(h.run(0.5f).none { it is HoverEvent.Dwell })
        assertEquals(listOf<HoverEvent<String>>(HoverEvent.Dwell("lamp")), h.run(0.3f))
        assertTrue(h.run(2f).isEmpty(), "a dwell fires once per visit")
    }

    @Test
    fun aSmallTremorDoesNotRestartTheDwell() {
        val h = HoverIntent<String>(enterDelay = 0.08f, dwell = 0.65f, slop = 6f)
        h.move("mug", Offset(10f, 10f))
        h.run(0.4f)
        h.move("mug", Offset(13f, 12f))
        assertTrue(h.run(0.3f).any { it is HoverEvent.Dwell })
    }

    @Test
    fun leavingToNothingEndsThePreviewAtOnceAndSweepingAcrossDoesNotFlicker() {
        val h = HoverIntent<String>()
        h.move("lamp", Offset.Zero); h.run(0.2f)
        assertEquals(HoverEvent.Preview<String>(null), h.move(null, Offset.Zero))
        assertNull(h.preview)
        h.move("lamp", Offset.Zero); h.run(0.2f)
        h.move("radio", Offset(1f, 0f)); h.run(0.03f)
        h.move("mug", Offset(2f, 0f)); h.run(0.03f)
        assertEquals("lamp", h.preview, "passing over the radio for 30 ms does not preview it")
        assertEquals(listOf<HoverEvent<String>>(HoverEvent.Preview("mug")), h.run(0.06f))
    }

    @Test
    fun aConsumedVisitDoesNotDwell() {
        val h = HoverIntent<String>()
        h.move("lamp", Offset.Zero); h.consume()
        assertTrue(h.run(2f).none { it is HoverEvent.Dwell })
    }
}

class PopMotionTest {
    private val m = PopMotion()

    @Test
    fun itemsOpenInRankOrderAndALaterGroupFollows() {
        assertEquals(0, m.openStart(0)); assertEquals(24, m.openStart(1)); assertEquals(72, m.openStart(3))
        assertEquals(60 + 24, m.openStart(1, group = 1))
    }

    @Test
    fun anItemPopsPastItsSizeAndSettles() {
        assertEquals(m.from, m.openScale(0f))
        val peak = (0..140).maxOf { m.openScale(it.toFloat()) }
        assertTrue(peak > 1.01f, "overshoots: $peak")
        assertEquals(1f, m.openScale(m.popMillis.toFloat()), 1e-4f)
        assertEquals(1f, m.openAlpha(70f)); assertEquals(0f, m.openLabel(30f)); assertEquals(1f, m.openLabel(120f))
    }

    @Test
    fun closingGoesLastFirstAndEndsInTime() {
        assertEquals(0, m.closeStart(rank = 5, lastRank = 5))
        assertEquals(75, m.closeStart(rank = 0, lastRank = 5))
        assertEquals(175, m.closeTotal(5))
        assertEquals(0f, m.closeAlpha(100f)); assertEquals(m.from, m.closeScale(100f), 1e-4f)
    }

    @Test
    fun aSixKeyWheelIsOpenWithinTheSpecsWindow() {
        // The spec has the keys strike in from 80 to about 280 ms after the blur starts: six keys and their labels are in place within 260 ms of the first.
        assertTrue(m.openTotal(5) <= 260, "${m.openTotal(5)}")
    }
}
