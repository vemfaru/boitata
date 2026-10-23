package br.com.faru.boitata.testing

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import br.com.faru.boitata.scene.focus.FocusMotion
import br.com.faru.boitata.scene.focus.FocusPasses
import br.com.faru.boitata.scene.focus.FocusStrength
import br.com.faru.boitata.scene.focus.focusBlur
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The focus passes: the thing in focus stays sharp, the rest blurs and darkens, and with no focus nothing changes. */
class FocusDrawTest {
    private val strength = FocusStrength(6.dp, 0.3f)
    private val instant = FocusMotion(reduced = true)

    /** Two light squares, "a" at 40..120 and "b" at 180..260 (on a 300 × 160 canvas, rows 40..120). */
    private fun square(x: Float) = Modifier.fillMaxSize().drawBehind { drawRect(Color(0xFFE0E0E0), Offset(x, 40f), Size(80f, 80f)) }

    private fun render(focus: String?, blurEnabled: Boolean = true) = renderStill(width = 300, height = 160) {
        if (blurEnabled) {
            FocusPasses(
                focus, if (focus != null) strength else null, Modifier.fillMaxSize(), motion = instant,
                scene = { Spacer(square(40f)); Spacer(square(180f)) },
                sharp = { k -> Spacer(square(if (k == "a") 40f else 180f)) },
            )
        } else {
            Spacer(Modifier.focusBlur(strength, instant, blurEnabled = false).then(square(180f)))
        }
    }

    @Test
    fun withNoFocusNothingIsBlurredOrDarkened() {
        val still = render(null)
        assertEquals(0, still.alpha(176, 80), "nothing bleeds past b's edge")
        assertTrue(still.luma(220, 80) > 200)
    }

    @Test
    fun theOneInFocusStaysSharpAndTheOtherBlursAndDarkens() {
        val plain = render(null)
        val focused = render("a")
        assertTrue(focused.alpha(176, 80) > 0, "b is blurred: it bleeds past its edge")
        assertTrue(focused.luma(220, 80) < plain.luma(220, 80) - 20, "b is darkened: ${focused.luma(220, 80)} vs ${plain.luma(220, 80)}")
        assertTrue(kotlin.math.abs(focused.luma(80, 80) - plain.luma(80, 80)) <= 2, "a is as it was: ${focused.luma(80, 80)} vs ${plain.luma(80, 80)}")
    }

    @Test
    fun withoutBlurOnlyTheDarkeningStays() {
        val still = render("b", blurEnabled = false)
        assertEquals(0, still.alpha(176, 80), "no blur")
        assertTrue(still.luma(220, 80) < 200, "but darker: ${still.luma(220, 80)}")
    }
}
