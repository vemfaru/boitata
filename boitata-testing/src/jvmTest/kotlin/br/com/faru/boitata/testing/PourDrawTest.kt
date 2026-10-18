package br.com.faru.boitata.testing

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.fluid.Fluid
import br.com.faru.boitata.core.fluid.LiquidVessel
import br.com.faru.boitata.core.pour.VesselAction
import br.com.faru.boitata.light.VesselStyle
import br.com.faru.boitata.light.VesselWheel
import br.com.faru.boitata.light.drawSipOverlay
import br.com.faru.boitata.light.drawVessel
import br.com.faru.boitata.light.drawVesselAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A lifted vessel is drawn higher, and the wheel puts its choices above the vessel it hangs from. */
class PourDrawTest {
    private fun render(v: LiquidVessel) = renderStill(width = 300, height = 300) {
        Spacer(Modifier.fillMaxSize().drawBehind { drawVessel(v, VesselStyle(), 190f, 260f, 1500f, 0f, Quality.High) })
    }

    private fun lowestRow(s: Still): Int = (0 until s.height).lastOrNull { y -> (0 until s.width).any { x -> s.alpha(x, y) > 30 } } ?: -1

    @Test
    fun liftingAVesselMovesItUpByTheLift() {
        val v = LiquidVessel(0.07f, 0.1f, Fluid.Water, 0.5f)
        val down = lowestRow(render(v))
        v.lift = 0.04f
        val up = lowestRow(render(v))
        assertTrue(down - up in 55..65, "40 mm at 1500 px/m is 60 px: $down -> $up")
    }

    @Test
    fun theWheelHangsItsChoicesAboveTheAnchor() {
        val actions = listOf(VesselAction("a", "Heat"), VesselAction("b", "Pour into Mug"), VesselAction("c", "Knock over"))
        val still = renderStill(width = 400, height = 300) {
            VesselWheel(actions, Offset(200f, 250f), onAction = {}, onDismiss = {}, animate = false)
        }
        val above = (0 until 240).sumOf { y -> (0 until 400).count { x -> still.alpha(x, y) > 40 } }
        val below = (270 until 300).sumOf { y -> (0 until 400).count { x -> still.alpha(x, y) > 40 } }
        assertTrue(above > 500, "choices drawn above: $above")
        assertEquals(0, below, "nothing under the anchor")
    }
}

/** A sip is drawn big and low, and the first-person overlay darkens the edges more than the middle. */
class SipDrawTest {
    private fun render(pose: br.com.faru.boitata.core.sip.SipPose) = renderStill(width = 400, height = 400) {
        Spacer(Modifier.fillMaxSize().drawBehind {
            drawVesselAt(LiquidVessel(0.07f, 0.1f, Fluid.Water, 0.5f), VesselStyle(), pose, 1000f, 0f, Quality.High)
        })
    }

    @Test
    fun atScaleThreeTheGlassCoversAboutNineTimesThePixels() {
        val small = render(br.com.faru.boitata.core.sip.SipPose(200f, 390f, 1f, 0f)).count(30)
        val big = render(br.com.faru.boitata.core.sip.SipPose(200f, 390f, 3f, 0f)).count(30)
        assertTrue(big > small * 5, "$big vs $small")
    }

    @Test
    fun leaningInForeshortensTheBodyAndOpensTheRimAsAnEllipse() {
        val top = { st: Still -> (0 until st.height).firstOrNull { y -> (0 until st.width).any { x -> st.alpha(x, y) > 30 } } ?: st.height }
        val upright = render(br.com.faru.boitata.core.sip.SipPose(200f, 390f, 2.5f, 0f))
        val leaning = render(br.com.faru.boitata.core.sip.SipPose(200f, 390f, 2.5f, 1.0f))
        assertTrue(top(leaning) > top(upright) + 20, "shorter: ${top(leaning)} vs ${top(upright)}")
        assertTrue(leaning.count(30) > 2000, "an opening is drawn")
    }

    @Test
    fun theOverlayDarkensTheEdgesMoreThanTheMiddleAndNothingAtZero() {
        val still = renderStill(width = 400, height = 400) { Spacer(Modifier.fillMaxSize().drawBehind { drawSipOverlay(1f, 0.3f, 0.7f) }) }
        assertTrue(still.alpha(2, 2) > still.alpha(200, 220), "${still.alpha(2, 2)} vs ${still.alpha(200, 220)}")
        val none = renderStill(width = 400, height = 400) { Spacer(Modifier.fillMaxSize().drawBehind { drawSipOverlay(0f, 0.3f, 0.7f) }) }
        assertEquals(0, none.count(1))
    }
}

/** The soil in a pot is darker wet than dry, and a pot with plants' soil shows it through its opening. */
class PotDrawTest {
    private fun render(moisture: Float) = renderStill(width = 300, height = 300) {
        Spacer(Modifier.fillMaxSize().drawBehind {
            drawVessel(
                LiquidVessel(0.14f, 0.13f, Fluid.Water, 0f), VesselStyle(br.com.faru.boitata.light.VesselShape.Pot, glass = androidx.compose.ui.graphics.Color(0xFFB8643A), soil = br.com.faru.boitata.light.SoilLook(moisture)),
                250f, 280f, 1000f, 0f, Quality.High,
            )
        })
    }

    @Test
    fun wetSoilIsDarkerThanDryAndAnEmptyPotStillHasItsOpening() {
        val dry = render(0f); val wet = render(1f)
        assertTrue(wet.luma(180, 148) < dry.luma(180, 148) - 30, "${wet.luma(180, 148)} vs ${dry.luma(180, 148)}")
        assertTrue(dry.count(30) > 3000, "the pot is drawn")
    }
}
