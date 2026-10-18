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
import br.com.faru.boitata.light.drawVessel
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
