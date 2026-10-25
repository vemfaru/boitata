package br.com.faru.boitata.core.draw

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals

class ArtFrameTest {
    @Test
    fun aCroppedPictureGoesBackInItsPartOfTheCanvassBox() {
        // A 1000 x 500 canvas whose paint is the box (100, 50) .. (600, 450), drawn over a box twice the canvas's size at (10, 20).
        val frame = ArtFrame(1000, 500, 100, 50, 600, 450)
        assertEquals(Rect(210f, 120f, 1210f, 920f), frame.inBox(Rect(10f, 20f, 2010f, 1020f)))
        assertEquals(Rect(10f, 5f, 60f, 45f), frame.inBox(0f, 0f, 100f, 50f))
    }
}
