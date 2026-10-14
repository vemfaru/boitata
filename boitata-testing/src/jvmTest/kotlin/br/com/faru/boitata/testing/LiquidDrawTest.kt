package br.com.faru.boitata.testing

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.light.Liquid
import br.com.faru.boitata.light.drawReflection
import br.com.faru.boitata.light.drawStandingInWater
import kotlin.test.Test
import kotlin.test.assertTrue

/** Water mirrors what stands at its edge, and what stands in it looks nearer the surface than it is. */
class LiquidDrawTest {
    private val pond = Liquid(cx = 200f, cy = 200f, rx = 150f, ry = 70f, maxDepth = 40f)
    private val person: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit = { c -> drawRect(c, Offset(-10f, -80f), Size(20f, 80f)) }

    private fun lowest(s: Still): Int = (s.height - 1 downTo 0).first { y -> (0 until s.width).any { x -> s.alpha(x, y) > 10 } }
    private fun highest(s: Still): Int = (0 until s.height).first { y -> (0 until s.width).any { x -> s.alpha(x, y) > 10 } }

    @Test
    fun aThingAtTheEdgeIsMirroredBelowItsFeetOverTheWaterAndNeverAbove() {
        // The person stands on the far shore of the pond: the water lies below him on the screen.
        val still = renderStill(width = 400, height = 300) {
            Spacer(Modifier.fillMaxSize().drawBehind { drawReflection(pond, 200f, 150f, 80f, 0f, 0f, Quality.High, Color.Black, person) })
        }
        assertTrue(still.count(10) > 300, "a reflection: ${still.count(10)} pixels")
        assertTrue(highest(still) >= 150, "nothing above his feet: the top of it is at ${highest(still)}")
        assertTrue(lowest(still) > 200, "it runs down into the pond: ${lowest(still)}")
    }

    @Test
    fun noReflectionOfAThingThatIsNotAtTheWater() {
        val still = renderStill(width = 400, height = 300) {
            Spacer(Modifier.fillMaxSize().drawBehind { drawReflection(pond, 20f, 40f, 80f, 0f, 0f, Quality.High, Color.Black, person) })
        }
        assertTrue(still.count(10) == 0, "his mirror image is nowhere near the water: ${still.count(10)}")
    }

    @Test
    fun whatStandsInTheWaterIsRefractedItsSubmergedPartLooksNearerTheSurface() {
        val dry = renderStill(width = 400, height = 300) { Spacer(Modifier.fillMaxSize().drawBehind { withTransformAt(200f, 220f) { person(Color.Black) } }) }
        val wet = renderStill(width = 400, height = 300) {
            Spacer(Modifier.fillMaxSize().drawBehind { drawStandingInWater(pond, 200f, 220f, 80f, 40f, 0f, Quality.High, Color.Black, person) })
        }
        assertTrue(lowest(dry) >= 218, "on dry ground his feet reach his feet line: ${lowest(dry)}")
        // 40 under the surface looks 40 / 1.33 = 30 under: his feet appear 10 higher.
        assertTrue(lowest(wet) in 200..213, "in the water his feet look nearer the surface: ${lowest(wet)}")
        assertTrue(highest(wet) <= 142 && highest(wet) >= 135, "his head is where it was: ${highest(wet)}")
    }

    private fun androidx.compose.ui.graphics.drawscope.DrawScope.withTransformAt(x: Float, y: Float, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
        withTransform({ translate(x, y) }) { block() }
    }
}
