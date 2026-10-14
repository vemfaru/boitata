package br.com.faru.boitata.core.burn

import br.com.faru.boitata.core.wind.Wind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MaterialsTest {
    private val toRight = Wind(speed = 0.5f, direction = 1f, gustiness = 0f, turbulence = 0f)

    private fun at(id: String, x: Float, material: Material, height: Float = 70f) = FireBody(id, material) { BodyGeometry(x, 0f, 50f, height) }

    private var clock = 0f
    private fun FireWorld.run(seconds: Float, wind: Wind = Wind.Calm, rain: Float = 0f, until: (FireWorld) -> Boolean = { false }): Float {
        var elapsed = 0f
        while (elapsed < seconds) { clock += 0.05f; elapsed += 0.05f; step(0.05f, clock, wind, rain); if (until(this)) break }
        return elapsed
    }

    private fun campfire() = FireBody("campfire", Material.Campfire) { BodyGeometry(0f, 0f, 80f, 80f) }

    @Test
    fun paperCatchesFasterThanWoodAndBurnsAwayFasterToo() {
        fun catchAndBurn(m: Material): Pair<Float, Float> {
            val thing = at("thing", 70f, m)
            val w = FireWorld().apply { add(campfire()); add(thing); ignite("campfire") }
            val toCatch = w.run(120f, toRight) { thing.status == BurnStatus.Burning }
            val toBurn = w.run(300f, toRight) { thing.status == BurnStatus.Spent }
            return toCatch to toBurn
        }
        val (paperCatch, paperBurn) = catchAndBurn(Material.Paper); val (woodCatch, woodBurn) = catchAndBurn(Material.Wood)
        assertTrue(paperCatch < woodCatch && paperBurn < woodBurn / 3f, "paper $paperCatch then $paperBurn s, wood $woodCatch then $woodBurn s")
    }

    @Test
    fun metalGlowsInTheHeatHoldsItAndNeverBurns() {
        val metal = at("metal", 45f, Material.Metal)
        val w = FireWorld().apply { add(campfire()); add(metal); ignite("campfire") }
        val other = at("stone", 45f, Material.Metal.let { Material("Slow metal", Float.POSITIVE_INFINITY, 0f, 0f, style = BurnStyle.Glow, glowHeat = 0.5f) })
        val w2 = FireWorld().apply { add(campfire()); add(other); ignite("campfire") }
        w.run(6f, Wind.Calm); w2.run(6f, Wind.Calm)
        assertTrue(metal.heat > other.heat * 1.3f, "metal takes heat faster than a material that does not: ${metal.heat} against ${other.heat}")
        w.run(20f, Wind.Calm)
        assertTrue(metal.glow > 0.4f, "it glows in the fire: ${metal.glow}")
        assertTrue(metal.status != BurnStatus.Burning && metal.status != BurnStatus.Spent && metal.melt == 0f, "but it does not burn or melt")
        w.extinguish("campfire")
        val atOnce = metal.glow
        w.run(4f, Wind.Calm)
        assertTrue(metal.glow > 0.2f && metal.glow <= atOnce, "it keeps its glow a while after the fire is out: $atOnce then ${metal.glow}")
        w.run(120f, Wind.Calm)
        assertEquals(0f, metal.glow, "and in the end it has cooled")
        assertEquals(BurnStatus.Cold, metal.status)
    }

    @Test
    fun waxMeltsForeverAndDoesNotBurn() {
        val wax = at("wax", 40f, Material.Wax)
        val w = FireWorld().apply { add(campfire()); add(wax); ignite("campfire") }
        w.run(200f, Wind.Calm)
        assertTrue(wax.melt > 0.5f && wax.status != BurnStatus.Burning, "it melts, it does not catch: ${wax.melt}")
        val melted = wax.melt
        w.extinguish("campfire")
        w.run(120f, Wind.Calm)
        assertEquals(melted, wax.melt, 1e-4f, "melting does not undo when it cools: it stays")
    }

    @Test
    fun plasticMeltsLongBeforeItWouldCatch() {
        val plastic = at("plastic", 60f, Material.Plastic)
        val w = FireWorld().apply { add(campfire()); add(plastic); ignite("campfire") }
        w.run(500f, toRight) { plastic.melt > 0.05f }
        assertTrue(plastic.melt > 0.05f && plastic.status != BurnStatus.Burning, "melting began while it was still not alight: heat ${plastic.heat}")
    }

    private fun candle(): Triple<FireWorld, FireBody, FireBody> {
        val w = FireWorld()
        val parts = w.addComposite(
            "candle", { BodyGeometry(300f, 0f, 30f, 100f) },
            listOf(
                PartSpec("wax", Material.Wax, PartRegion(0f, 0.1f, 1f, 1f)),
                PartSpec("wick", Material.Wick, PartRegion(0.44f, 0f, 0.56f, 0.14f), fedBy = "wax"),
            ),
        )
        return Triple(w, parts[0], parts[1])
    }

    @Test
    fun anUnlitCandleStaysWhole() {
        val (w, wax, wick) = candle()
        w.run(300f, Wind.Calm)
        assertEquals(0f, wax.melt); assertEquals(BurnStatus.Cold, wick.status)
    }

    @Test
    fun aLitCandleBurnsTheWickAndMeltsTheWaxSlowlyAndSinks() {
        val (w, wax, wick) = candle()
        w.ignite("candle.wick")
        assertEquals(BurnStatus.Burning, wick.status)
        w.run(20f, Wind.Calm)
        assertTrue(wick.status == BurnStatus.Burning && wax.status != BurnStatus.Burning, "only the wick burns: wax ${wax.status}")
        assertTrue(wax.melt > 0.01f && wax.melt < 0.3f, "and the wax has only just begun to melt: ${wax.melt}")
        val topAt20 = wick.geometry().feetY - wick.geometry().height
        w.run(60f, Wind.Calm)
        assertTrue(wax.melt in 0.3f..0.95f, "much later, much melted: ${wax.melt}")
        assertTrue(wick.geometry().feetY - wick.geometry().height > topAt20 + 5f, "the candle has sunk: the wick has come down with it")
        assertEquals(wax.melt, wick.consumed, 0.02f, "the wick wears down as the wax does")
    }

    @Test
    fun theFlameLastsAsLongAsTheWaxAndThenGoesOut() {
        val (w, wax, wick) = candle()
        w.ignite("candle.wick")
        w.run(600f, Wind.Calm) { wick.status == BurnStatus.Spent }
        assertEquals(BurnStatus.Spent, wick.status, "the flame ends when the wax has gone")
        assertTrue(wax.melt > 0.95f)
        assertEquals(BurnStatus.Spent, wax.status, "the wax is a puddle")
        w.run(20f, Wind.Calm)
        assertEquals(0f, wick.burn)
        w.ignite("candle.wick")
        assertEquals(BurnStatus.Spent, wick.status, "a spent candle cannot be lit again")
    }

    @Test
    fun aWickWithNoWaxCannotBeLit() {
        val w = FireWorld()
        val parts = w.addComposite("candle", { BodyGeometry(0f, 0f, 30f, 100f) }, listOf(PartSpec("wax", Material.Wax, PartRegion(0f, 0.1f, 1f, 1f)), PartSpec("wick", Material.Wick, PartRegion(0.44f, 0f, 0.56f, 0.14f), fedBy = "wax")))
        parts[0].melt = 1f; parts[0].spent = true          // the wax is all gone
        w.ignite("candle.wick"); w.run(700f, Wind.Calm) { parts[1].status == BurnStatus.Spent }
        w.ignite("candle.wick")
        assertTrue(parts[1].status != BurnStatus.Burning)
    }

    @Test
    fun aCandleIsLitByAFlameNextToItAndTheRainPutsItOut() {
        val (w, wax, wick) = candle()
        val taper = FireBody("match", Material.Campfire) { BodyGeometry(322f, -80f, 30f, 40f) }
        w.add(taper); w.ignite("match")
        w.run(40f, Wind.Calm) { wick.status == BurnStatus.Burning }
        assertEquals(BurnStatus.Burning, wick.status, "a flame beside the wick lights it")
        w.extinguish("match")
        w.run(10f, Wind.Calm)
        assertEquals(BurnStatus.Burning, wick.status, "and it goes on burning by itself")
        w.run(60f, Wind.Calm, rain = 1f)
        assertTrue(wick.status != BurnStatus.Burning, "heavy rain puts the candle out")
        assertTrue(wax.melt < 0.5f, "with the candle only partly melted")
    }

    @Test
    fun theNewMaterialsHaveTheStylesTheyShould() {
        assertEquals(BurnStyle.AshAway, Material.Paper.style); assertEquals(BurnStyle.Char, Material.Wood.style); assertEquals(BurnStyle.Shrivel, Material.Plant.style)
        assertEquals(BurnStyle.Melt, Material.Wax.style); assertEquals(BurnStyle.Glow, Material.Metal.style); assertEquals(BurnStyle.Smoulder, Material.Cloth.style)
        assertTrue(!Material.Metal.flammable && !Material.Wax.flammable && Material.Wax.meltable && !Material.None.flammable)
    }
}
