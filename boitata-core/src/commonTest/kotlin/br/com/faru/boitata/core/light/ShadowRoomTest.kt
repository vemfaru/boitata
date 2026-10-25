package br.com.faru.boitata.core.light

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShadowRoomTest {
    // The Typist's viewpoint: the eye 131 cm up and 40 cm in front of a 67 cm desk 60 cm deep, drawn from y 1092 back to 553.
    private val cam = RoomCamera.fromDesk(131f, 40f, 35f, 67f, 60f, frontY = 1092f, backY = 553f, referenceY = 1032f, unitsPerCmAtReference = 22.9f, centerX = 720f)
    private val p = FloatArray(2)

    // A desk 80 cm wide against a wall 60 cm back, on a floor.
    private val desk = ShadowReceiver.level(67f, -40f, 0f, 40f, 0f, 40f, 60f, -40f, 60f)
    private val wall = ShadowReceiver.upright(60f, -500f, 0f, 500f, 0f, 500f, 300f, -500f, 300f)
    private val floor = ShadowReceiver.level(0f, -500f, -30f, 500f, -30f, 500f, 60f, -500f, 60f)
    private val room = ShadowRoom(cam, listOf(desk, wall, floor))

    @Test
    fun theCameraDrawsTheDesksEdgesWhereThePictureHasThem() {
        cam.project(0f, 0f, 67f, p); assertEquals(1092f, p[1], 0.01f)
        cam.project(0f, 60f, 67f, p); assertEquals(553f, p[1], 0.01f)
        assertEquals(720f, p[0], 1e-3f)
        // 22.9 units to the centimetre across, at the machine's feet.
        cam.onLevel(720f, 1032f, 67f, p); val d = p[1]
        assertEquals(22.9f, cam.unitsPerCm(d, 67f), 1e-3f)
    }

    @Test
    fun aPointOnAPlaneComesBackFromThePicture() {
        cam.project(12f, 33f, 67f, p); val (x, y) = p[0] to p[1]
        assertTrue(cam.onLevel(x, y, 67f, p)); assertEquals(12f, p[0], 1e-3f); assertEquals(33f, p[1], 1e-3f)
        cam.project(-7f, 60f, 110f, p); val (wx, wy) = p[0] to p[1]
        assertTrue(cam.onUpright(wx, wy, 60f, p)); assertEquals(-7f, p[0], 1e-3f); assertEquals(110f, p[1], 1e-3f)
    }

    @Test
    fun aShadowOnTheDeskIsTheHeightOverTheTangentOfTheLightsAngle() {
        // A light 40 cm above the desk top, a thin post 10 cm tall 24 cm to its right: its tip lands 24 * 10 / (40 - 10) = 8 cm beyond it (L = H / tan θ).
        val light = RoomLight(0f, 20f, 107f)
        val t = room.along(0, light.x, light.d, light.z, 24f, 20f, 77f)
        assertEquals(40f / 30f, t, 1e-5f)
        assertEquals(0, room.firstHit(light.x, light.d, light.z, 24f, 20f, 77f))
        assertEquals(32f, light.x + t * (24f - light.x), 1e-4f)
    }

    @Test
    fun pastTheDesksEdgeTheShadowFallsToTheFloor() {
        // The same post at the desk's right edge: its tip's ray crosses the desk's level beyond the edge, and goes on down to the floor.
        val light = RoomLight(0f, 20f, 107f)
        assertEquals(2, room.firstHit(light.x, light.d, light.z, 38f, 20f, 77f))
        // Its foot still lands on the desk.
        assertEquals(0, room.firstHit(light.x, light.d, light.z, 38f, 20f, 67.5f))
    }

    @Test
    fun pastTheBackEdgeTheShadowClimbsTheWallAndMeetsItsDeskPartThere() {
        // A light at the front, a post near the back: its upper part goes to the wall, its lower part to the desk, and the two meet on the back edge's line.
        val light = RoomLight(0f, 0f, 107f)
        assertEquals(1, room.firstHit(light.x, light.d, light.z, 0f, 50f, 87f))
        assertEquals(0, room.firstHit(light.x, light.d, light.z, 0f, 50f, 68f))
        // The height on the post whose ray reaches the desk exactly at its back edge: z - 67 = 40 * (60 - 50) / 60.
        val zEdge = 67f + 40f * 10f / 60f
        room.onPlane(0, light.x, light.d, light.z, 0f, 50f, zEdge, p); val onDesk = p[1]
        room.onPlane(1, light.x, light.d, light.z, 0f, 50f, zEdge, p); val onWall = p[1]
        assertEquals(onDesk, onWall, 0.01f)
        assertEquals(553f, onWall, 0.01f)
    }

    @Test
    fun aWallShadowIsHigherTheFartherTheWallAndTheLowerTheLight() {
        // H_wall = H − d tan θ: from a light 40 cm above the desk 50 cm in front of a 20 cm post that is 10 cm from the wall, the tip lands at 87 - 10 * (40 - 20) / 50 cm.
        val light = RoomLight(0f, 0f, 107f)
        val t = room.along(1, light.x, light.d, light.z, 0f, 50f, 87f)
        assertEquals(87f + (t - 1f) * (87f - 107f), 87f - 10f * 20f / 50f, 1e-4f)
    }

    @Test
    fun aMeshPutsEachPartOfThePictureOnTheSurfaceItReallyFallsOn() {
        val light = RoomLight(0f, 10f, 107f)
        cam.project(0f, 50f, 67f, p)
        val cx = p[0]; val footY = p[1]
        val px = cam.unitsPerCm(50f, 67f)
        // A 20 cm card near the back edge: some of it on the desk, the top on the wall.
        cam.project(0f, 50f, 87f, p)
        val caster = RoomCaster(50f, 67f, cx - 5f * px, p[1], cx + 5f * px, footY)
        val mesh = ShadowMesh(4, 8, 3)
        mesh.build(room, light, caster)
        assertTrue(mesh.owner.count { it == 0 } > 0, "some on the desk")
        assertTrue(mesh.owner.count { it == 1 } > 0, "some on the wall")
        assertTrue(mesh.share.all { it in 0f..1f })
        // The top row is on the wall; the bottom row on the desk.
        assertEquals(1, mesh.owner[2]); assertEquals(0, mesh.owner[mesh.corners - 3])
    }

    @Test
    fun theDesksOwnShadeOnTheFloorIsWhereAPropsShadowStops() {
        val light = RoomLight(0f, 20f, 107f)
        val shade = room.shadeOf(0, 2, light)
        assertTrue(shade != null && shade.size == 8)
        assertTrue(room.shadeOf(2, 0, light) == null, "the floor is not between the light and the desk")
    }

    @Test
    fun thePictureSaysWhereTheBaseIs() {
        // A 10 x 10 mask: a post three cells wide from row 2 to row 8 (inclusive), its base on row 8.
        val data = ByteArray(100) { i -> val x = i % 10; val y = i / 10; if (x in 4..6 && y in 2..8) 0xFF.toByte() else 0 }
        val m = SilhouetteMetrics.of(AlphaMask(10, 10, data))
        assertEquals(0.2f, m.top, 1e-6f); assertEquals(0.9f, m.base, 1e-6f)
        assertEquals(0.4f, m.baseLeft, 1e-6f); assertEquals(0.7f, m.baseRight, 1e-6f)
        // Drawn on the desk: its footprint is as deep as it is wide unless the size says otherwise.
        cam.project(0f, 30f, 67f, p)
        val u = cam.unitsPerCm(30f, 67f)
        val left = p[0] - 5f * u; val bottom = p[1] + 1f * u
        val guess = m.caster(cam, left, bottom - 10f * u, left + 10f * u, bottom, 67f)
        val told = m.caster(cam, left, bottom - 10f * u, left + 10f * u, bottom, 67f, ObjectSize(depth = 12f))
        assertTrue(abs(told.d - guess.d - (12f - 3f) / 2f) < 0.3f, "${told.d} vs ${guess.d}")
    }
}
