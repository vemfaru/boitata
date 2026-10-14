package br.com.faru.boitata.testing

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import br.com.faru.boitata.core.burn.BodyGeometry
import br.com.faru.boitata.core.burn.BurnStatus
import br.com.faru.boitata.core.burn.FireBody
import br.com.faru.boitata.core.burn.FireWorld
import br.com.faru.boitata.core.burn.Material
import br.com.faru.boitata.core.wind.Wind
import br.com.faru.boitata.light.drawBurningBody
import br.com.faru.boitata.light.tint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Fire shows: nothing is drawn for a cool thing, flames and smoke for a burning one, and a burnt one is darker than it was. */
class BurnDrawTest {
    private fun crateWorld(): Pair<FireWorld, FireBody> {
        val world = FireWorld()
        val crate = world.add(FireBody("crate", Material.Wood) { BodyGeometry(150f, 260f, 90f, 100f) })
        return world to crate
    }

    private fun draw(body: FireBody) = renderStill(width = 300, height = 300) {
        Spacer(Modifier.fillMaxSize().drawBehind { drawBurningBody(body, body.geometry(), 5f, Wind.Calm) })
    }

    private fun lightness(c: Color) = (c.red + c.green + c.blue) / 3f

    @Test
    fun aCoolThingDrawsNothingAndABurningOneDrawsFlames() {
        val (world, crate) = crateWorld()
        assertEquals(0, draw(crate).count(), "nothing to see on a cool thing")
        world.ignite("crate")
        var t = 0f
        repeat(120) { t += 0.05f; world.step(0.05f, t, Wind.Calm) }
        assertEquals(BurnStatus.Burning, crate.status)
        assertTrue(draw(crate).count() > 800, "flames and smoke: ${draw(crate).count()} pixels")
    }

    @Test
    fun aThingBurntToAshHasNoFlamesButStillSmoulders() {
        val (world, crate) = crateWorld()
        world.ignite("crate")
        var t = 0f
        while (crate.status != BurnStatus.Spent) { t += 0.05f; world.step(0.05f, t, Wind.Calm) }
        repeat(60) { t += 0.05f; world.step(0.05f, t, Wind.Calm) }
        assertEquals(0f, crate.burn, "no flame left")
        assertTrue(crate.smoulder > 0.5f, "but it smoulders: ${crate.smoulder}")
        assertTrue(draw(crate).count() > 0, "smoke still rises from the ash")
    }

    @Test
    fun fireDarkensAThingAsItChars() {
        val base = Color(0xFFC79B5E)
        val (world, crate) = crateWorld()
        assertEquals(lightness(base), lightness(crate.tint(base)), 1e-4f, "a whole thing keeps its colour")
        world.ignite("crate")
        var t = 0f
        while (crate.consumed < 0.5f) { t += 0.05f; world.step(0.05f, t, Wind.Calm) }
        val halfBurnt = lightness(crate.tint(base))
        while (crate.status != BurnStatus.Spent) { t += 0.05f; world.step(0.05f, t, Wind.Calm) }
        val ash = lightness(crate.tint(base))
        assertTrue(halfBurnt < lightness(base) && ash < halfBurnt && ash < 0.12f, "base ${lightness(base)}, half burnt $halfBurnt, ash $ash")
    }
}
