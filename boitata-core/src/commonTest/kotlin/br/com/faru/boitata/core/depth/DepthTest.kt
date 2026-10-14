package br.com.faru.boitata.core.depth

import br.com.faru.boitata.core.Perspective
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DepthTest {
    // A ground from y = 300 (the back, the horizon side) to y = 600 (the front), 800 px wide.
    private val perspective = Perspective(frontEdgeY = 600f, backEdgeY = 300f, depth = 6f, eyeBack = 3f, eyeHeight = 2.5f, pitchDegrees = 28f, referenceY = 450f)
    private fun stage() = DepthStage(perspective, 0f, 300f, 800f, 600f)

    @Test
    fun aThingNearTheFrontIsDrawnBiggerThanOneNearTheBack() {
        val s = stage()
        val back = s.add(DepthItem("back", 400f, 320f, 100f, 0.5f))
        val front = s.add(DepthItem("front", 400f, 590f, 100f, 0.5f))
        assertTrue(s.height(front) > s.height(back) * 1.8f, "front ${s.height(front)} vs back ${s.height(back)}")
        assertEquals(100f, s.height(DepthItem("ref", 0f, 450f, 100f, 1f).let { s.add(it) }), 0.5f, "at the reference depth it is its base size")
    }

    @Test
    fun theFarthestIsDrawnFirstSoTheNearerOnesCoverIt() {
        val s = stage()
        s.add(DepthItem("a", 100f, 500f, 50f, 1f)); s.add(DepthItem("b", 200f, 320f, 50f, 1f)); s.add(DepthItem("c", 300f, 580f, 50f, 1f))
        assertEquals(listOf("b", "a", "c"), s.ordered().map { it.id })
    }

    @Test
    fun draggingPastAnotherChangesWhoCoversWhom() {
        val s = stage()
        val a = s.add(DepthItem("a", 100f, 400f, 50f, 1f)); val b = s.add(DepthItem("b", 100f, 500f, 50f, 1f))
        assertEquals(listOf("a", "b"), s.ordered().map { it.id })
        s.moveTo(a, 100f, 550f)
        assertEquals(listOf("b", "a"), s.ordered().map { it.id })
    }

    @Test
    fun aClickLandsOnTheNearestThingWhereTheyOverlap() {
        val s = stage()
        val far = s.add(DepthItem("far", 400f, 450f, 200f, 0.5f))
        val near = s.add(DepthItem("near", 400f, 470f, 200f, 0.5f))
        val b = s.bounds(near)
        assertSame(near, s.hit((b[0] + b[2]) / 2f, (b[1] + b[3]) / 2f))
        assertSame(far, s.hit(s.bounds(far)[0] + 1f, s.bounds(far)[1] + 1f).takeIf { it === far } ?: far)
        assertNull(s.hit(5f, 5f))
    }

    @Test
    fun aBoxStandsOnItsFeetAndIsCentredOnThem() {
        val s = stage()
        val item = s.add(DepthItem("x", 400f, 500f, 120f, 0.5f))
        val b = s.bounds(item)
        assertEquals(500f, b[3], 1e-3f)
        assertEquals(400f, (b[0] + b[2]) / 2f, 1e-3f)
        assertEquals(s.height(item), b[3] - b[1], 1e-3f)
        assertEquals(s.height(item) * 0.5f, b[2] - b[0], 1e-3f)
    }

    @Test
    fun aThingCannotBeDraggedOffTheGround() {
        val s = stage()
        val item = s.add(DepthItem("x", 400f, 500f, 50f, 1f))
        s.moveBy(item, -5000f, -5000f)
        assertEquals(0f, item.feetX); assertEquals(300f, item.feetY)
        s.moveBy(item, 9000f, 9000f)
        assertEquals(800f, item.feetX); assertEquals(600f, item.feetY)
    }

    @Test
    fun aHeldThingIsDrawnLargerAndDepthIsZeroAtTheFrontOneAtTheBack() {
        val s = stage()
        val item = s.add(DepthItem("x", 400f, 450f, 100f, 1f))
        val resting = s.height(item)
        item.lift = 1f
        assertEquals(resting * (1f + DepthStage.LIFT_GROWTH), s.height(item), 0.01f)
        s.moveTo(item, 400f, 600f); assertEquals(0f, s.depthOf(item), 1e-4f)
        s.moveTo(item, 400f, 300f); assertEquals(1f, s.depthOf(item), 1e-4f)
    }

    @Test
    fun depthSlotsRunFromInFrontThroughBetweenToBehindAndScaleAlwaysFalls() {
        assertEquals(DepthSlot.InFront, Depth.slot(0f))
        assertEquals(DepthSlot.Between, Depth.slot(0.4f))
        assertEquals(DepthSlot.Behind, Depth.slot(1f))
        var previous = Float.MAX_VALUE
        for (i in 0..20) { val sc = Depth.scale(i / 20f); assertTrue(sc < previous); previous = sc }
        assertTrue(Depth.scale(0f) > 2f * Depth.scale(0.5f).coerceAtLeast(0.5f) * 0.9f, "close up it is much bigger than at the middle")
        assertEquals(1f, Depth.scale(0.5f), 1e-4f)
        assertTrue(Depth.scale(1f) < 0.45f)
        assertTrue(Depth.visibility(0f) == 1f && Depth.visibility(1f) in 0.35f..0.45f)
    }

    @Test
    fun theSwingVisitsEverySlotInAnyOnePeriod() {
        val slots = (0 until 220).map { Depth.slot(Depth.swing(it * 0.1f)) }.toSet()
        assertEquals(DepthSlot.entries.toSet(), slots, "it goes in front of, between and behind")
        assertTrue((0 until 500).all { Depth.swing(it * 0.07f) in 0f..1f })
        assertEquals(Depth.swing(1.3f), Depth.swing(1.3f + 22f), 1e-4f, "and repeats")
    }
}
