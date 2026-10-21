package br.com.faru.boitata.testing

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import br.com.faru.boitata.core.AdvancedBoitataApi
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.draw.drawGlow
import br.com.faru.boitata.core.draw.unitGlow
import br.com.faru.boitata.light.shadowBlurScale
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A stamped glow draws the same picture as the gradient it replaces (one per ember, fog bank and cloud puff), without a shader each time. */
@OptIn(AdvancedBoitataApi::class)
class GlowTest {
    private val c = Offset(120f, 90f)

    private fun gradient(alpha: Float, middle: Boolean) = renderStill(width = 240, height = 180) {
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                val col = Color(0xFFE8B970)
                val brush = if (middle) Brush.radialGradient(0f to col.copy(alpha = 0.75f * alpha), 0.7f to col.copy(alpha = 0.35f * alpha), 1f to Color.Transparent, center = c, radius = 70f)
                else Brush.radialGradient(0f to col.copy(alpha = alpha), 1f to Color.Transparent, center = c, radius = 70f)
                drawCircle(brush, radius = 70f, center = c)
            },
        )
    }

    private fun stamped(alpha: Float, middle: Boolean) = renderStill(width = 240, height = 180) {
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                val col = Color(0xFFE8B970)
                if (middle) drawGlow(unitGlow(col.copy(alpha = 0.75f * alpha), col.copy(alpha = 0.35f * alpha)), c, 70f)
                else drawGlow(unitGlow(col), c, 70f, alpha)
            },
        )
    }

    private fun maxDifference(a: Still, b: Still): Int {
        var worst = 0
        for (y in 0 until a.height) for (x in 0 until a.width) {
            worst = maxOf(worst, abs(a.alpha(x, y) - b.alpha(x, y)), abs(a.luma(x, y) - b.luma(x, y)))
        }
        return worst
    }

    @Test
    fun aStampedGlowLooksLikeItsGradient() {
        val d = maxDifference(gradient(0.6f, middle = false), stamped(0.6f, middle = false))
        assertTrue(d <= 3, "the stamped glow differs by up to $d of 255")
    }

    @Test
    fun aStampedGlowWithAMiddleStopLooksLikeItsGradient() {
        val d = maxDifference(gradient(0.8f, middle = true), stamped(0.8f, middle = true))
        assertTrue(d <= 3, "the stamped glow differs by up to $d of 255")
    }

    @Test
    fun shadowsBlurLessAsTheSceneGetsLighter() {
        assertEquals(1f, shadowBlurScale(Quality.High))
        assertTrue(shadowBlurScale(Quality.Medium) in 0.01f..0.99f)
        assertEquals(0f, shadowBlurScale(Quality.Low), "no blur layer at Low")
    }
}
