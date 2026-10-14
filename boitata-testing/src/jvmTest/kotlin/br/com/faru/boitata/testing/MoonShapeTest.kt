package br.com.faru.boitata.testing

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import br.com.faru.boitata.core.sky.MoonShape
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The lit part of the moon is a real shape: its area is the phase (a third lit lights a third of the disc), it grows from nothing to the whole disc, and the lit side flips.
 * Paths need Skia, so these draw the shape into a bitmap and count pixels.
 */
class MoonShapeTest {
    private val size = 240
    private val r = 100f

    /** Lit pixels, and the mean x of them relative to the centre. */
    private fun lit(fraction: Float, right: Boolean): Pair<Int, Float> {
        val bitmap = ImageBitmap(size, size)
        val path = MoonShape.litPath(size / 2f, size / 2f, r, fraction, right)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(size.toFloat(), size.toFloat())) { drawPath(path, Color.White) }
        val pixels = bitmap.toPixelMap()
        var count = 0; var sumX = 0.0
        for (y in 0 until size) for (x in 0 until size) if (pixels[x, y].alpha > 0.5f) { count++; sumX += x - size / 2.0 }
        return count to if (count == 0) 0f else (sumX / count).toFloat()
    }

    @Test
    fun theLitAreaIsThePhase() {
        val disc = PI.toFloat() * r * r
        for (k in listOf(0.1f, 0.2f, 0.35f, 0.5f, 0.65f, 0.8f, 0.95f, 1f)) {
            val area = lit(k, true).first
            assertEquals(k * disc, area.toFloat(), 0.04f * disc, "a moon $k lit has $area lit pixels of ${disc.toInt()}")
        }
    }

    @Test
    fun newIsDarkAndItGrowsStepByStepToTheWholeDisc() {
        assertEquals(0, lit(0f, true).first, "new: nothing lit")
        var previous = 0
        for (i in 1..20) { val a = lit(i / 20f, true).first; assertTrue(a >= previous, "growing: $a after $previous"); previous = a }
    }

    @Test
    fun theLitPartIsOnTheSideYouAskFor() {
        val right = lit(0.3f, true).second
        val left = lit(0.3f, false).second
        assertTrue(right > 20f, "lit on the right: mean x $right")
        assertTrue(left < -20f, "lit on the left: mean x $left")
        assertEquals(0f, lit(1f, true).second, 1.5f, "a full moon is lit evenly, on neither side")
    }
}
