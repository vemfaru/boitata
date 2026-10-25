package br.com.faru.boitata.core.light

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContactShadowTest {
    @Test fun halfTheSkyAtTheFootAndLessFartherOut() {
        assertEquals(0.5f, ContactShadow.occlusion(0f, 3f))
        assertTrue(abs(ContactShadow.occlusion(3f, 3f) - 0.1464f) < 1e-3f)
        assertTrue(ContactShadow.occlusion(9f, 3f) < 0.03f)
        // A round base spans less of the horizon as one steps away from it than a straight edge does.
        assertEquals(0.5f, ContactShadow.occlusion(0f, 9f, radius = 4f))
        assertTrue(ContactShadow.occlusion(2f, 9f, radius = 4f) < ContactShadow.occlusion(2f, 9f))
    }

    @Test fun onlyTheSoftLightIsTakenAway() {
        val o = ContactShadow.occlusion(0f, 3f)
        val moonlit = ContactShadow.darkness(o, ambient = 0.05f, direct = 0.3f)
        val candleClose = ContactShadow.darkness(o, ambient = 0.05f, direct = 3f)
        val day = ContactShadow.darkness(o, ambient = 0.5f, direct = 1f)
        assertTrue(moonlit < 0.05f, "moonlit $moonlit")
        assertTrue(candleClose < moonlit * 1.5f, "a lamp's light is not occluded: $candleClose")
        assertTrue(day > moonlit * 3f, "day $day vs moon $moonlit")
        assertEquals(0f, ContactShadow.darkness(o, 0f, 0f))
    }
}
