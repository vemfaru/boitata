package br.com.faru.boitata.testing

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.light.Optics
import br.com.faru.boitata.light.ThroughCaster
import br.com.faru.boitata.light.ThroughLamp
import br.com.faru.boitata.light.ThroughShadows
import br.com.faru.boitata.light.drawThroughShadows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The shadow theatre: things between a lamp behind a sheet and the sheet show on it, bigger and softer the nearer the lamp, never darker than a solid thing, fading up from a contact. */
class ThroughShadowsDrawTest {
    private val lamp = ThroughLamp(flameX = 200f, flameY = 20f, groundX = 200f, groundY = 200f, strength = 1f, size = 0.5f)

    private fun box(x: Float, y: Float, w: Float, h: Float, depth: Float, solid: Boolean = false, contact: Float = 0f) =
        ThroughCaster(x, y, depth, top = y, bottom = y + h, solid = solid, contact = contact) { colour, mode -> drawRect(colour, size = Size(w, h), blendMode = mode) }

    private fun render(casters: List<ThroughCaster>, lamps: List<ThroughLamp> = listOf(lamp), optics: Optics = Optics.Paper(), quality: Quality = Quality.Low) =
        renderStill(width = 400, height = 300) {
            val shadows = ThroughShadows(scale = 0.5f)
            Spacer(Modifier.fillMaxSize().drawBehind { drawThroughShadows(shadows, lamps, casters, surfaceFootY = 1000f, optics = optics, quality = quality, blend = BlendMode.SrcOver) })
        }

    @Test
    fun aLampInFrontOfTheSurfaceCastsNothingThrough() {
        val front = ThroughLamp(200f, 20f, 200f, groundY = 1100f)
        assertEquals(0, render(listOf(box(150f, 100f, 100f, 100f, depth = 900f)), listOf(front)).count(1))
    }

    @Test
    fun aThingNearTheLampCastsABiggerShadowThanOneAgainstTheSurface() {
        val near = render(listOf(box(170f, 100f, 60f, 60f, depth = 300f))).count(20)
        val far = render(listOf(box(170f, 100f, 60f, 60f, depth = 990f))).count(20)
        assertTrue(near > far * 1.5, "near $near vs far $far")
    }

    @Test
    fun aSolidThingIsNeverDarkerWhereAnotherShadowFallsOnIt() {
        val plate = box(100f, 150f, 200f, 100f, depth = 990f, solid = true)
        val alone = render(listOf(plate)).alpha(200, 200)
        val under = render(listOf(box(150f, 120f, 100f, 120f, depth = 900f), plate)).alpha(200, 200)
        assertTrue(alone > 40, "the plate casts ($alone)")
        assertTrue(kotlin.math.abs(under - alone) <= 3, "nothing adds on the plate: $under vs $alone")
    }

    @Test
    fun aThingTouchingTheSurfaceIsDarkestWhereItTouches() {
        val still = render(listOf(box(100f, 100f, 200f, 150f, depth = 995f, contact = 0.67f)))
        val bottom = still.alpha(200, 240); val top = still.alpha(200, 110)
        assertTrue(bottom > top + 30, "bottom $bottom vs top $top")
        assertTrue(top > 0, "the far edge still shows")
    }

    @Test
    fun heavierPaperShowsAFainterShadow() {
        val caster = listOf(box(150f, 100f, 100f, 100f, depth = 900f))
        val thin = render(caster, optics = Optics(opacity = 0.22f, scatter = 0.5f)).alpha(200, 150)
        val heavy = render(caster, optics = Optics(opacity = 0.62f, scatter = 0.8f)).alpha(200, 150)
        assertTrue(thin > heavy, "thin $thin vs heavy $heavy")
    }

    @Test
    fun aFarThingHasASofterEdgeThanAThingAgainstTheSurface() {
        fun edgeWidth(depth: Float): Int {
            val still = render(listOf(box(170f, 100f, 60f, 60f, depth = depth)), quality = Quality.High)
            // Pixels on the shadow's middle row that are neither empty nor full: the penumbra.
            val row = (0 until 300).maxByOrNull { y -> (0 until 400).count { x -> still.alpha(x, y) > 0 } } ?: 0
            val peak = (0 until 400).maxOf { still.alpha(it, row) }
            return (0 until 400).count { x -> still.alpha(x, row) in 3 until (peak * 0.9).toInt() }
        }
        val near = edgeWidth(500f); val touching = edgeWidth(995f)
        assertTrue(near > touching, "penumbra near the lamp $near vs against the sheet $touching")
    }
}
