package br.com.faru.boitata.core.draw

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReangleTest {
    private fun deg(d: Float) = (d * PI / 180.0).toFloat()

    @Test
    fun seenFromWhereItWasDrawnItIsUnchanged() {
        val r = Reangle(10f, 100f, deg(54f), deg(54f))
        assertEquals(1f, r.topScale, 1e-5f); assertEquals(1f, r.frontScale, 1e-5f); assertFalse(r.visible)
    }

    @Test
    fun seenFlatterTheTopShrinksAndTheFrontGrows() {
        // A shelf near eye height (13°) against art that looks right at the desk (54°).
        val r = Reangle(10f, 100f, deg(54f), deg(13.4f))
        assertEquals(Reangle.MIN_SCALE, r.topScale, 1e-5f, "sin 13.4° / sin 54° is 0.29: held at the least that still reads as turned")
        assertEquals(1.656f, r.frontScale, 0.01f)
        assertTrue(r.visible)
    }

    @Test
    fun theBandsNeverGoPastWhereATurnStillReads() {
        val flat = Reangle(10f, 100f, deg(54f), deg(2f))
        assertEquals(Reangle.MIN_SCALE, flat.topScale)
        val steep = Reangle(10f, 100f, deg(30f), deg(85f))
        assertEquals(Reangle.MIN_SCALE, steep.frontScale)
    }
}
