package br.com.faru.boitata.core.light

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShadowMathTest {
    private fun point(lightX: Float, lightY: Float, height: Float, feetX: Float = 100f, feetY: Float = 200f, h: Float = 40f) =
        ShadowCast().also { ShadowMath.point(lightX, lightY, height, feetX, feetY, h, 0.2f, it) }

    @Test
    fun aSunLowInTheSkyThrowsALongerShadowThanOneHighUp() {
        val low = ShadowCast().also { ShadowMath.directional(0f, 0.3f, 0.3f, it) }
        val high = ShadowCast().also { ShadowMath.directional(0f, 1.2f, 0.3f, it) }
        assertEquals(1f / tan(0.3f), low.leanX, 1e-3f, "length is 1 / tan(elevation)")
        assertTrue(low.length(10f) > 2.5f * high.length(10f), "${low.length(10f)} vs ${high.length(10f)}")
    }

    @Test
    fun theSunsRaysAreParallelSoEveryThingGetsTheSameShadow() {
        // A directional light knows nothing of where a thing stands.
        val a = ShadowCast().also { ShadowMath.directional(0.7f, 0.6f, 0.3f, it) }
        val b = ShadowCast().also { ShadowMath.directional(0.7f, 0.6f, 0.3f, it) }
        assertEquals(a.leanX, b.leanX); assertEquals(a.leanY, b.leanY)
    }

    @Test
    fun theShadowOfAPointLightFallsAwayFromItAndGrowsAsItComesNear() {
        val farRight = point(lightX = 400f, lightY = 200f, height = 120f)
        assertTrue(farRight.leanX < 0f, "the light is to the right, the shadow goes left: ${farRight.leanX}")
        val left = point(lightX = -200f, lightY = 200f, height = 120f)
        assertTrue(left.leanX > 0f, "the light is to the left, the shadow goes right")
        val near = point(lightX = 40f, lightY = 200f, height = 120f)
        val far = point(lightX = -300f, lightY = 200f, height = 120f)
        assertTrue(abs(far.leanX) > abs(near.leanX) * 0.5f, "a point light's shadow lengthens with distance")
        val low = point(lightX = -100f, lightY = 200f, height = 50f)
        val high = point(lightX = -100f, lightY = 200f, height = 400f)
        assertTrue(low.length(40f) > 3f * high.length(40f), "a low light stretches it: ${low.length(40f)} vs ${high.length(40f)}")
    }

    @Test
    fun aLightOverheadMakesAShortShadowJustUnderTheThing() {
        val over = point(lightX = 100f, lightY = 200f, height = 400f)
        assertTrue(over.length(40f) < 8f, "${over.length(40f)}")
    }

    @Test
    fun twoLightsOnOppositeSidesThrowShadowsOnOppositeSides() {
        val fromLeft = point(lightX = -100f, lightY = 200f, height = 100f)
        val fromRight = point(lightX = 300f, lightY = 200f, height = 100f)
        assertEquals(1f, sign(fromLeft.leanX)); assertEquals(-1f, sign(fromRight.leanX))
    }

    @Test
    fun aLightInFrontMakesTheShadowFallBehindTheThingAndOneBehindMakesItFallTowardTheViewer() {
        val inFront = point(lightX = 100f, lightY = 400f, height = 100f)
        val behind = point(lightX = 100f, lightY = 0f, height = 100f)
        assertTrue(inFront.leanY < 0f, "in front of the thing the shadow goes up the screen: ${inFront.leanY}")
        assertTrue(behind.leanY > 0.3f, "behind it the shadow comes toward the viewer: ${behind.leanY}")
    }

    @Test
    fun theGroundIsSeenAtASlantSoAShadowIsFlatterGoingUpTheScreenThanAcross() {
        val sideways = ShadowCast().also { ShadowMath.directional(0f, 0.5f, 0.3f, it) }
        val toward = ShadowCast().also { ShadowMath.directional((PI / 2).toFloat(), 0.5f, 0.3f, it) }
        assertEquals(sideways.leanX * ShadowMath.GROUND_SQUASH, toward.leanY - sideways.leanY, 1e-3f)
    }

    @Test
    fun aBiggerLightGivesASofterEdge() {
        val small = ShadowCast().also { ShadowMath.directional(0f, 0.5f, 0.1f, it) }
        val big = ShadowCast().also { ShadowMath.directional(0f, 0.5f, 0.9f, it) }
        assertTrue(big.spread > 3f * small.spread)
    }
}
