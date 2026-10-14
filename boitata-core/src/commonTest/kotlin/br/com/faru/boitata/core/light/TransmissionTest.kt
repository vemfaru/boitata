package br.com.faru.boitata.core.light

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransmissionTest {
    private val out = FloatArray(4)

    // A lamp in front (y 500), a crate at y 300 (x 150..250, 80 high), and a person behind it at y 200, 200 across.
    private fun level(things: List<Billboard>, personX: Float = 200f, z: Float = 40f, self: Any? = null): Float {
        Transmission.fromPoint(200f, 500f, 100f, personX, 200f, z, things, self, out)
        return out[0]
    }

    @Test
    fun aCrateBetweenTheLampAndAPersonPutsThePersonInItsShadow() {
        val crate = Billboard(footY = 300f, left = 150f, right = 250f, height = 80f)
        assertEquals(0f, level(listOf(crate)), "the person is hidden from the lamp by the crate")
        assertEquals(1f, level(emptyList()), "no crate, full light")
    }

    @Test
    fun movingTheCrateAsideOrBehindThePersonLightsThemAgain() {
        assertEquals(1f, level(listOf(Billboard(footY = 300f, left = 400f, right = 500f, height = 80f))), "off to the side")
        assertEquals(1f, level(listOf(Billboard(footY = 100f, left = 150f, right = 250f, height = 80f))), "behind the person, between nothing and the lamp")
    }

    @Test
    fun aThingTallEnoughToBlockTheRayButNotTheWholeBodyShadowsOnlyPartOfIt() {
        // A low crate (60 high): the ray to the person's feet is stopped, the ray to their head passes over it.
        val low = Billboard(footY = 300f, left = 150f, right = 250f, height = 60f)
        assertEquals(0f, level(listOf(low), z = 2f))
        assertEquals(1f, level(listOf(low), z = 70f))
    }

    @Test
    fun glassLetsMostThroughAndTintsItAndPaperLetsHalf() {
        val glass = Billboard(footY = 300f, left = 100f, right = 300f, height = 200f, optics = Optics.Glass(Color(0xFF3366FF), opacity = 0.1f))
        Transmission.fromPoint(200f, 500f, 100f, 200f, 200f, 40f, listOf(glass), null, out)
        assertEquals(0.9f, out[0], 1e-3f)
        assertTrue(out[3] > out[1] + 0.3f, "blue light comes out: ${out.toList()}")
        val paper = Billboard(footY = 300f, left = 100f, right = 300f, height = 200f, optics = Optics.Paper())
        Transmission.fromPoint(200f, 500f, 100f, 200f, 200f, 40f, listOf(paper), null, out)
        assertEquals(0.55f, out[0], 1e-3f)
    }

    @Test
    fun twoSheetsOfPaperLetAQuarterThrough() {
        val a = Billboard(footY = 300f, left = 100f, right = 300f, height = 200f, optics = Optics(0.5f))
        val b = Billboard(footY = 250f, left = 100f, right = 300f, height = 200f, optics = Optics(0.5f))
        assertEquals(0.25f, level(listOf(a, b)), 1e-3f)
    }

    @Test
    fun theGapsOfAThingLetLightThroughAndAThingNeverBlocksItself() {
        // A lattice: a mask that is solid on the left half and empty on the right.
        val data = ByteArray(4 * 4) { i -> if (i % 4 < 2) 0xFF.toByte() else 0 }
        val lattice = Billboard(footY = 300f, left = 100f, right = 300f, height = 100f, mask = AlphaMask(4, 4, data))
        assertEquals(0f, level(listOf(lattice), personX = 140f, z = 40f), "through the solid half")
        assertEquals(1f, level(listOf(lattice), personX = 260f, z = 40f), "through the gap")
        val crate = Billboard(footY = 300f, left = 150f, right = 250f, height = 80f, owner = "crate")
        Transmission.fromPoint(200f, 500f, 100f, 200f, 300f, 40f, listOf(crate), "crate", out)
        assertEquals(1f, out[0], "the crate's own light")
    }

    @Test
    fun theSunReachesThePersonOnlyIfNothingIsInTheWay() {
        // The sun shines toward the viewer from behind: shadows fall toward +y. A crate behind the person (smaller y) is between them and the sun.
        val behind = Billboard(footY = 100f, left = 100f, right = 300f, height = 200f)
        Transmission.fromDirection(1.2f, 0.3f, 200f, 200f, 40f, listOf(behind), null, out)
        assertEquals(0f, out[0], "the crate is between the person and the sun")
        val front = Billboard(footY = 300f, left = 100f, right = 300f, height = 200f)
        Transmission.fromDirection(1.2f, 0.3f, 200f, 200f, 40f, listOf(front), null, out)
        assertEquals(1f, out[0], "a crate on the other side does not matter")
    }
}
