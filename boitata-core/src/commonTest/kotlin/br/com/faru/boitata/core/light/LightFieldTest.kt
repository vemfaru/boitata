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
    fun lightFallsOffWithTheSquareOfTheDistance() {
        // A lamp of reference distance 10 cm, straight above the desk: at 20 cm a quarter of what it gives at 10 cm reaches the top... at 40 cm a sixteenth (the inverse square).
        val near = LightField.arriving(room, 0, FieldLight(RoomLight(0f, 30f, 87f, 0.5f), 0xFFFFFF, 1f, 10f), 0f, 30f, 67f)
        val far = LightField.arriving(room, 0, FieldLight(RoomLight(0f, 30f, 107f, 0.5f), 0xFFFFFF, 1f, 10f), 0f, 30f, 67f)
        assertEquals(4f, near / far, 0.15f)
        // Off to the side the surface turns away from it: the cosine.
        val aside = LightField.arriving(room, 0, FieldLight(RoomLight(0f, 30f, 87f, 0.5f), 0xFFFFFF, 1f, 10f), 20f, 30f, 67f)
        assertTrue(aside < near / 2f)
        assertEquals(1f / 16f, Falloff.of(40f, 10f) / Falloff.of(10f, 10f), 0.01f)
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
        assertEquals(0f, near); assertTrue(far > 0.8f, "farther out the flame is (nearly) all seen: $far")
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

    @Test
    fun sideWallsFaceTheMiddleOfTheRoom() {
        // A room 200 cm wide: side walls at -100 and +100, from the front to the back wall, up to 230 cm.
        val left = ShadowReceiver.side(-100f, -40f, 0f, 60f, 0f, 60f, 230f, -40f, 230f)
        val right = ShadowReceiver.side(100f, -40f, 0f, 60f, 0f, 60f, 230f, -40f, 230f)
        val r = ShadowRoom(cam, listOf(desk, wall, floor, left, right))
        val l = FieldLight(RoomLight(-80f, 30f, 100f), 0xFFFFFF, 1f, 400f)
        val onRight = LightField.arriving(r, 4, l, 100f, 30f, 120f)
        val onLeft = LightField.arriving(r, 3, l, -100f, 30f, 120f)
        assertTrue(onRight > 0f && onLeft > 0f, "both walls face the room")
        // The right wall sees the lamp almost face-on but far; the left is near but at a graze: the cosine and the distance both count.
        val nearGraze = LightField.arriving(r, 3, l, -100f, 30f, 100f)
        assertTrue(nearGraze > onLeft, "on the left wall, the point level with the lamp gets more than one above it")
        assertEquals(0f, LightField.arriving(r, 3, FieldLight(RoomLight(-120f, 30f, 100f), 0xFFFFFF, 1f, 400f), -100f, 30f, 100f), "a lamp behind a side wall does not light its face")
    }

    @Test
    fun aLightInABoxDoesNotLightTheWallBehindIt() {
        // A candle inside a crate against the wall: the crate's front is 30 cm out from the wall; the wall round the crate is behind the candle, in the crate's shade.
        val inBox = RoomLight(0f, 45f, 120f, 0.5f, Opening(30f, -20f, 115f, 20f, 140f))
        val l = FieldLight(inBox, 0xFFFFFF, 1f, 200f)
        assertEquals(0f, LightField.visibility(room, 1, inBox, 40f, 60f, 150f), "the wall beside the crate")
        assertTrue(LightField.visibility(room, 2, inBox, 0f, -10f, 0f) >= 0f)
        assertTrue(LightField.arriving(room, 0, l, 0f, 10f, 67f) >= 0f)
    }

    @Test
    fun aLitSurfaceLightsTheOthersThroughOneBounce() {
        // A lamp low over the desk: the wall gets some of it straight and more once the lit desk gives some back.
        val l = FieldLight(RoomLight(0f, 30f, 75f), 0xFFFFFF, 1f, 60f)
        val desk2 = SurfaceLight(0, 6, 6); val wall2 = SurfaceLight(1, 6, 6)
        LightField.compute(room, listOf(l), desk2); LightField.compute(room, listOf(l), wall2, view = floatArrayOf(-40f, 0f, 40f, 120f))
        val before = wall2.amount[3]
        LightField.bounce(room, listOf(desk2, wall2))
        assertTrue(wall2.amount[3] > before, "${wall2.amount[3]} > $before")
    }
}
