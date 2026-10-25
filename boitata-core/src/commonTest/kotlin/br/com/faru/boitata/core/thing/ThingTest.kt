package br.com.faru.boitata.core.thing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThingTest {
    private val candle = Thing(
        "candle",
        listOf(
            ThingLayer("holder", "holder.png", Materials.Brass),
            ThingLayer("wax", "wax.png", Materials.Wax, Share(0.4f, 0.3f, 0.6f, 0.9f)),
            ThingLayer("flame", "flame.png", Materials.CandleFlame, Share(0.45f, 0.1f, 0.55f, 0.3f)),
        ),
    )

    @Test
    fun aFlameLayerIsTheThingsLightAndCastsNoShadow() {
        assertEquals(listOf("holder", "wax"), candle.casting.map { it.name })
        assertEquals(listOf("flame"), candle.emitting.map { it.name })
        assertEquals(1f, candle.light!!.flame)
        assertTrue(candle.layer("flame")!!.receivesLight.not())
    }

    @Test
    fun glassStopsLittleOfTheLightAndIronAllOfIt() {
        assertTrue(Materials.Glass.opacity < 0.2f)
        assertEquals(1f, Materials.Iron.opacity)
        val lantern = Thing("lantern", listOf(ThingLayer("frame", "f", Materials.Iron), ThingLayer("globe", "g", Materials.Glass)))
        assertEquals(1f, lantern.opacity, "where the iron is, nothing passes")
    }

    @Test
    fun aLayerCastsFromItsOutlineWhenItHasOne() {
        val l = ThingLayer("mug", "mug.webp", Materials.Enamel, outline = "mug_outline.webp")
        assertEquals("mug_outline.webp", l.castFrom)
        assertEquals("plain.webp", ThingLayer("plain", "plain.webp", Materials.Paper).castFrom)
    }

    @Test
    fun materialsAreSharesAndAThingHasLayers() {
        assertFailsWith<IllegalArgumentException> { Material("bad", reflectance = 1.5f) }
        assertFailsWith<IllegalArgumentException> { Thing<String>("empty", emptyList()) }
        assertNull(Thing("tin", listOf(ThingLayer("tin", "t", Materials.Tin))).light)
        assertTrue(Materials.all.all { it.reflectance in 0f..1f })
    }
}
