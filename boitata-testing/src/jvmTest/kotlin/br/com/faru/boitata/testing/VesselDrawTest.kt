package br.com.faru.boitata.testing

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.fluid.Fluid
import br.com.faru.boitata.core.fluid.LiquidVessel
import br.com.faru.boitata.light.VesselStyle
import br.com.faru.boitata.light.drawSpillStream
import br.com.faru.boitata.light.drawVessel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A vessel draws its liquid at the level the simulation says, and a stream only while it spills. */
class VesselDrawTest {
    private fun render(v: LiquidVessel, stream: Boolean = false) = renderStill(width = 300, height = 300) {
        Spacer(Modifier.fillMaxSize().drawBehind {
            drawVessel(v, VesselStyle(), 190f, 260f, 1500f, 0f, Quality.High)
            if (stream) drawSpillStream(v, 190f, 260f, 1500f, 0f, Quality.High)
        })
    }

    @Test
    fun theLiquidFillsTheGlassUpToItsLevelAndMoreLiquidMeansMorePixels() {
        val low = render(LiquidVessel(0.07f, 0.1f, Fluid.Water, fill = 0.25f)).count(100)
        val high = render(LiquidVessel(0.07f, 0.1f, Fluid.Water, fill = 0.85f)).count(100)
        assertTrue(high > low * 1.4f, "$high vs $low")
    }

    @Test
    fun aTippedGlassKeepsItsLiquidLevelAndAnEmptyOneHasNone() {
        val tipped = LiquidVessel(0.07f, 0.1f, Fluid.Wine, fill = 0.4f); tipped.body.angle = 0.5f
        val upright = LiquidVessel(0.07f, 0.1f, Fluid.Wine, fill = 0.4f)
        assertTrue(render(tipped).count(30) > 0)
        val empty = LiquidVessel(0.07f, 0.1f, Fluid.Wine, fill = 0f)
        // Only the glass outline: far fewer opaque pixels than with wine in it.
        assertTrue(render(empty).count(120) < render(upright).count(120) * 0.5f)
    }

    @Test
    fun aStreamIsDrawnOnlyWhileTheVesselSpills() {
        val v = LiquidVessel(0.07f, 0.1f, Fluid.Water, fill = 0.95f); v.body.angle = 0.7f
        v.step(1f / 60f)
        assertTrue(v.spilling)
        val without = render(v, stream = false).count(30)
        val with = render(v, stream = true).count(30)
        assertTrue(with > without + 40, "a stream adds pixels: $with vs $without")
        val still = LiquidVessel(0.07f, 0.1f, Fluid.Water, fill = 0.4f)
        assertEquals(render(still, stream = false).count(30), render(still, stream = true).count(30), "nothing spills from a glass that is standing")
    }
}
