package br.com.faru.boitata.core.light

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LightFieldTest {
    private val cam = RoomCamera.fromDesk(131f, 40f, 35f, 67f, 60f, frontY = 1092f, backY = 553f, referenceY = 1032f, unitsPerCmAtReference = 22.9f, centerX = 720f)
    private val desk = ShadowReceiver.level(67f, -40f, 0f, 40f, 0f, 40f, 60f, -40f, 60f)
    private val wall = ShadowReceiver.upright(60f, -500f, 0f, 500f, 0f, 500f, 300f, -500f, 300f)
    private val floor = ShadowReceiver.level(0f, -500f, -30f, 500f, -30f, 500f, 60f, -500f, 60f)
    private val room = ShadowRoom(cam, listOf(desk, wall, floor))

    @Test
    fun lightFallsOffAndTurnsAwayWithTheSurface() {
        val l = FieldLight(RoomLight(0f, 30f, 100f, 0.5f), 0xFFFFFF, 1f, 100f)
        val under = LightField.arriving(room, 0, l, 0f, 30f, 67f)
        val far = LightField.arriving(room, 0, l, 35f, 5f, 67f)
        assertTrue(under > far, "$under > $far")
        // Straight below, cos = 1 and r = 33: 1 / (1 + 0.33²).
        assertEquals(1f / (1f + 0.33f * 0.33f), under, 0.01f)
    }

    @Test
    fun theFloorUnderTheDeskIsInItsShadowAndBesideItIsLit() {
        val l = FieldLight(RoomLight(0f, 30f, 100f, 0.5f), 0xFFFFFF, 1f, 200f)
        assertEquals(0f, LightField.arriving(room, 2, l, 0f, 30f, 0f), "under the desk top")
        assertTrue(LightField.arriving(room, 2, l, 120f, 30f, 0f) > 0f, "beside the desk")
    }

    @Test
    fun aLightInARecessReachesOnlyThroughItsMouthAndTheEdgeIsSoft() {
        // The candle 20 cm back in a recess, its mouth from 90 to 160 cm up: the wall faces away; the desk is lit only where the mouth lets the rays down to it.
        val l = FieldLight(RoomLight(0f, 80f, 100f, 2f, Opening(60f, -20f, 90f, 20f, 160f)), 0xFFFFFF, 1f, 200f)
        assertEquals(0f, LightField.arriving(room, 1, l, 0f, 60f, 120f), "the room's wall")
        // Along the desk toward the front the light comes in: none just under the mouth, some farther out, with a soft edge between.
        val near = LightField.visibility(room, 0, l.light, 0f, 55f, 67f)
        val far = LightField.visibility(room, 0, l.light, 0f, -20f, 67f)
        assertEquals(0f, near); assertEquals(1f, far)
        val shares = (0..60).map { LightField.visibility(room, 0, l.light, 0f, 55f - it * 1.25f, 67f) }
        assertTrue(shares.any { it > 0f && it < 1f }, "a soft edge: some points see part of the flame")
    }

    @Test
    fun aGridAddsTheLightsUpAndTakesTheirColours() {
        val warm = FieldLight(RoomLight(-20f, 30f, 100f), 0xFF8000, 1f, 150f)
        val cool = FieldLight(RoomLight(20f, 30f, 100f), 0x0080FF, 1f, 150f)
        val g = SurfaceLight(0, 4, 4)
        LightField.compute(room, listOf(warm, cool), g, ambient = 0.05f)
        val mid = 2 * 5 + 2
        assertTrue(g.amount[mid] > 0.05f)
        assertTrue(g.red[0] > g.blue[0], "the left corner is warmer"); assertTrue(g.blue[4] > g.red[4], "the right corner cooler")
    }

    @Test
    fun aTopIsNotLitFromBelow() {
        val l = FieldLight(RoomLight(0f, 30f, 50f), 0xFFFFFF, 1f, 150f)
        assertEquals(0f, LightField.arriving(room, 0, l, 0f, 30f, 67f), "a light under the desk top does not light its top")
    }
}
