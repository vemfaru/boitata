package br.com.faru.boitata.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerspectiveTest {
    private val desk = Perspective(frontEdgeY = 1092f, backEdgeY = 553f, depth = 60f, eyeBack = 40f, eyeHeight = 64f, pitchDegrees = 35f, referenceY = 1032f)

    @Test
    fun theReferenceIsDrawnAtScaleOne() = assertEquals(1f, desk.scale(1032f), 0.0001f)

    @Test
    fun thingsFartherBackAreSmallerAndNearerBigger() {
        assertEquals(0.63f, desk.scale(553f), 0.01f)
        assertEquals(1.08f, desk.scale(1092f), 0.01f)
        assertTrue(desk.scale(700f) < desk.scale(900f))
    }

    @Test
    fun beyondTheEdgesTheScaleStopsGrowing() {
        assertEquals(desk.scale(2000f), desk.scale(5000f), 0.0001f)
        assertEquals(desk.scale(-3000f), desk.scale(0f), 0.0001f)
        assertTrue(desk.scale(2000f) > desk.scale(1092f))
    }

    @Test
    fun aFlatterViewLooksFartherAlongTheTop() {
        val flat = Perspective(1092f, 553f, 60f, 40f, 64f, 20f, 1032f)
        assertTrue(flat.distance(553f) > desk.distance(553f) * 0.9f)
    }
}
