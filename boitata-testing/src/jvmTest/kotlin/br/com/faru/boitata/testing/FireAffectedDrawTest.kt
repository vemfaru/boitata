package br.com.faru.boitata.testing

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import br.com.faru.boitata.core.burn.BodyGeometry
import br.com.faru.boitata.core.burn.BurnStatus
import br.com.faru.boitata.core.burn.FireBody
import br.com.faru.boitata.core.burn.FireWorld
import br.com.faru.boitata.core.burn.Material
import br.com.faru.boitata.core.burn.PartRegion
import br.com.faru.boitata.core.burn.PartSpec
import br.com.faru.boitata.core.wind.Wind
import br.com.faru.boitata.light.BurnStates
import br.com.faru.boitata.light.drawFireAffected
import br.com.faru.boitata.light.tint
import kotlin.test.Test
import kotlin.test.assertTrue

/** What fire does to a picture depends on what it is made of: paper is eaten away, wax sags, metal glows, and your own art for a stage replaces ours. */
class FireAffectedDrawTest {
    private val box = BodyGeometry(150f, 260f, 80f, 120f)
    private val base = Color(0xFF3A3A3A)

    private fun step(world: FireWorld, until: () -> Boolean) {
        var t = 0f; var n = 0
        while (!until() && n++ < 6000) { t += 0.05f; world.step(0.05f, t, Wind.Calm) }
        check(until()) { "never reached the state" }
    }

    private fun draw(body: FireBody, parts: List<FireBody> = emptyList(), states: BurnStates? = null) = renderStill(width = 300, height = 300) {
        Spacer(Modifier.fillMaxSize().drawBehind {
            drawFireAffected(body, box, 5f, Wind.Calm, base, parts = parts, states = states) { c ->
                drawRect(c, Offset(-box.width / 2f, -box.height), Size(box.width, box.height))
            }
        })
    }

    private fun top(s: Still): Int = (0 until s.height).first { y -> (0 until s.width).any { x -> s.alpha(x, y) > 200 } }

    @Test
    fun burningPaperIsEatenAwayAndWoodIsNot() {
        fun burnt(material: Material): Pair<Int, Int> {
            val world = FireWorld(); val b = world.add(FireBody("x", material) { box })
            val whole = draw(b).count(200)
            world.ignite("x")
            step(world) { b.consumed >= 0.6f }
            return whole to draw(b).count(200)
        }
        val (paperWhole, paperNow) = burnt(Material.Paper)
        assertTrue(paperNow < paperWhole * 0.6f, "paper loses its body: $paperNow of $paperWhole")
        val (woodWhole, woodNow) = burnt(Material.Wood)
        assertTrue(woodNow > woodWhole * 0.85f, "wood keeps its shape, only charred: $woodNow of $woodWhole")
    }

    @Test
    fun aCandleMeltsDownWhileOnlyTheWickBurns() {
        val world = FireWorld()
        val bodies = world.addComposite("candle", { box }, listOf(
            PartSpec("wax", Material.Wax, PartRegion(0f, 0.12f, 1f, 1f)),
            PartSpec("wick", Material.Wick, PartRegion(0.44f, 0f, 0.56f, 0.12f), fedBy = "wax"),
        ))
        val wax = bodies[0]; val wick = bodies[1]
        val before = top(draw(wax, listOf(wick)))
        world.ignite("candle.wick")
        step(world) { wax.melt >= 0.5f }
        assertTrue(wick.status == BurnStatus.Burning && wax.status != BurnStatus.Burning, "the wick burns, the wax does not")
        val after = top(draw(wax, listOf(wick)))
        assertTrue(after > before + 20, "the candle is lower: top row $before -> $after")
    }

    @Test
    fun hotMetalGlowsAndYourOwnArtReplacesOurs() {
        val world = FireWorld()
        world.add(FireBody("fire", Material.Campfire) { BodyGeometry(150f, 260f, 80f, 120f) })
        val metal = world.add(FireBody("metal", Material.Metal) { BodyGeometry(170f, 260f, 80f, 120f) })
        world.ignite("fire")
        step(world) { metal.glow > 0.5f }
        assertTrue(metal.tint(base).red > base.red + 0.3f, "hot metal turns orange: ${metal.tint(base)}")

        val w = FireWorld(); val wood = w.add(FireBody("w", Material.Wood) { box })
        w.ignite("w"); step(w) { wood.status == BurnStatus.Burning }
        val ours = draw(wood).count(200)
        val mine = draw(wood, states = BurnStates(burning = { c -> drawRect(c, Offset(-140f, -280f), Size(280f, 280f)) })).count(200)
        assertTrue(mine > ours + 20000, "own art for the burning stage is drawn instead: $mine vs $ours")
    }
}
