package br.com.faru.boitata.core.light

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiquidTest {
    @Test
    fun waterReflectsAboutTwoPercentLookingDownAndMostAtAGrazingLook() {
        assertEquals(0.02f, LiquidMath.reflectance(1f), 0.003f, "straight down")
        assertTrue(LiquidMath.reflectance(0.47f) in 0.04f..0.09f, "at our camera angle (62 degrees from the normal): ${LiquidMath.reflectance(0.47f)}")
        assertTrue(LiquidMath.reflectance(0.05f) > 0.6f, "grazing: ${LiquidMath.reflectance(0.05f)}")
        assertTrue(LiquidMath.reflectance(0.2f) > LiquidMath.reflectance(0.6f), "more reflective the flatter the look")
    }

    @Test
    fun thingsUnderWaterLookNearerTheSurfaceByTheIndex() {
        assertEquals(0.75f, LiquidMath.apparentDepth(1f), 0.01f)
        assertEquals(1f, LiquidMath.apparentDepth(1.33f, 1.33f), 1e-4f)
    }

    @Test
    fun aPondIsADeepBowlWithNoDepthAtTheShoreOrOutside() {
        val pond = Liquid(cx = 200f, cy = 200f, rx = 100f, ry = 40f, maxDepth = 30f)
        assertTrue(pond.contains(200f, 200f) && pond.contains(260f, 210f) && !pond.contains(330f, 200f) && !pond.contains(200f, 260f))
        assertEquals(1f, pond.depthAt(200f, 200f), 1e-4f)
        assertEquals(0f, pond.depthAt(300f, 200f), 1e-4f, "none at the shore")
        assertEquals(0f, pond.depthAt(400f, 200f), "none outside")
        assertTrue(pond.depthAt(250f, 200f) in 0.7f..0.8f, "shallower toward the edge: ${pond.depthAt(250f, 200f)}")
    }
}
