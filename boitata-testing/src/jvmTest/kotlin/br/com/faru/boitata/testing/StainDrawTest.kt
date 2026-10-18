package br.com.faru.boitata.testing

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import br.com.faru.boitata.core.fluid.Fluid
import br.com.faru.boitata.core.stain.StainField
import br.com.faru.boitata.core.stain.SurfaceMaterial
import br.com.faru.boitata.core.stain.VesselBase
import br.com.faru.boitata.light.drawStains
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Stains are drawn where the foot was, as a ring or a disc, and as strongly as the mark is. */
class StainDrawTest {
    private fun render(field: StainField) = renderStill(width = 300, height = 300) {
        Spacer(Modifier.fillMaxSize().drawBehind { drawStains(field, 150f, 150f, 1000f) })
    }

    private fun field(base: VesselBase, wet: Float = 1f) = StainField().also { it.deposit(0f, 0f, base, Fluid.Coffee, wet, SurfaceMaterial.Wood) }

    @Test
    fun nothingIsDrawnWithoutMarks() {
        assertEquals(0, render(StainField()).count(1))
    }

    @Test
    fun aDiscFillsItsCentreAndARingLeavesItEmpty() {
        val disc = render(field(VesselBase(0.1f, ring = 0f)))
        val ring = render(field(VesselBase(0.1f, ring = 0.15f)))
        assertTrue(disc.alpha(150, 150) > 40, "disc centre ${disc.alpha(150, 150)}")
        assertEquals(0, ring.alpha(150, 150), "a ring is empty in the middle")
        assertTrue(ring.count(20) > 20, "but has a rim")
    }

    @Test
    fun aStrongerMarkIsDarker() {
        val faint = render(field(VesselBase(0.1f), wet = 0.2f)).alpha(150, 150)
        val strong = render(field(VesselBase(0.1f), wet = 1f)).alpha(150, 150)
        assertTrue(strong > faint, "$strong vs $faint")
    }
}
