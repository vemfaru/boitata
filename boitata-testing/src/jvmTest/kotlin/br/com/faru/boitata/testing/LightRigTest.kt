package br.com.faru.boitata.testing

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import br.com.faru.boitata.core.sky.MoonPhase
import br.com.faru.boitata.light.LightRig
import br.com.faru.boitata.light.Lights
import br.com.faru.boitata.light.ShadowCasts
import br.com.faru.boitata.light.drawCastShadows
import br.com.faru.boitata.light.drawFaceShadows
import kotlin.math.abs
import kotlin.math.sign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Several lights at once: each lights in its own colour, each casts its own shadow, and the brighter one's shadow is the darker. */
class LightRigTest {
    private val dark = Color(0xFF14110F); private val lit = Color(0xFFC79B5E)

    @Test
    fun aFireAndTheMoonTintAThingBetweenThemWarmAndCold() {
        val rig = LightRig(ambient = 0.1f)
        rig.add(Lights.fire(100f, 300f)); rig.add(Lights.moon(0f, MoonPhase.Full))
        val nearFire = rig.shade(dark, lit, 120f, 300f, 0f)
        val faraway = rig.shade(dark, lit, 900f, 300f, 0f)
        assertTrue(nearFire.red > nearFire.blue + 0.15f, "warm beside the fire: $nearFire")
        assertTrue(faraway.blue / faraway.red > nearFire.blue / nearFire.red * 1.4f, "only the cold moon reaches far, so it is bluer than beside the fire: $faraway vs $nearFire")
        assertTrue(nearFire.red > faraway.red, "brighter beside the fire")
    }

    @Test
    fun twoLightsThrowTwoShadowsOnOppositeSides() {
        val rig = LightRig(ambient = 0.1f)
        rig.add(Lights.lamp(0f, 300f)); rig.add(Lights.lamp(400f, 300f))
        val out = ShadowCasts()
        val n = rig.shadows(200f, 300f, 100f, 0f, 0.6f, 3, out)
        assertEquals(2, n)
        assertEquals(setOf(1f, -1f), setOf(sign(out.casts[0].leanX), sign(out.casts[1].leanX)))
    }

    @Test
    fun theBrighterLightsShadowIsTheDarkerAndAHighAmbientWashesThemOut() {
        val rig = LightRig(ambient = 0.1f)
        val strong = Lights.lamp(0f, 300f); strong.strength = 0.9f
        val weak = Lights.lamp(400f, 300f); weak.strength = 0.3f
        rig.add(strong); rig.add(weak)
        val out = ShadowCasts()
        rig.shadows(200f, 300f, 100f, 0f, 0.6f, 3, out)
        val strongest = out.casts.take(2).maxBy { it.alpha }
        assertTrue(strongest.leanX > 0f && strongest.alpha > 1.5f * out.casts.take(2).minOf { it.alpha }, "the light from the left dominates")
        rig.ambient = 3f
        rig.shadows(200f, 300f, 100f, 0f, 0.6f, 3, out)
        assertTrue(out.casts[0].alpha < 0.15f, "by day a lamp's shadow barely shows: ${out.casts[0].alpha}")
    }

    @Test
    fun onlyTheStrongestFewLightsCast() {
        val rig = LightRig()
        repeat(5) { rig.add(Lights.lamp(it * 60f, 300f)) }
        assertEquals(2, rig.shadows(150f, 300f, 100f, 0f, 0.6f, 2, ShadowCasts()))
    }

    @Test
    fun theSunRisesLeftCrossesHighAndSetsRightAndIsOffAtNight() {
        val dawn = Lights.sun(7f); val noon = Lights.sun(12f); val dusk = Lights.sun(17f); val night = Lights.sun(23f)
        assertTrue(dawn.elevation < noon.elevation && dusk.elevation < noon.elevation, "highest at noon")
        assertTrue(Math.cos(dawn.azimuth.toDouble()) > 0.3 && Math.cos(dusk.azimuth.toDouble()) < -0.3, "shadows fall right at dawn, left at dusk")
        assertEquals(0f, night.strength)
        assertTrue(noon.color.blue > dawn.color.blue, "pale at noon, orange at dawn")
    }

    @Test
    fun theMoonIsUpAtMidnightWhenFullAndNotWhenNewAndTheQuarterSetsByMidnight() {
        assertTrue(Lights.moon(0f, MoonPhase.Full).strength > 0.3f)
        assertEquals(0f, Lights.moon(0f, MoonPhase.New).strength)
        assertTrue(Lights.moon(20f, MoonPhase.FirstQuarter).strength > 0f, "a first quarter is up in the evening")
        assertEquals(0f, Lights.moon(5f, MoonPhase.FirstQuarter).strength, "and gone by dawn")
    }

    @Test
    fun aFlameFlickersAndALampBarelyDoes() {
        val fire = Lights.fire(0f, 0f); val lamp = Lights.lamp(0f, 0f)
        fun spread(l: br.com.faru.boitata.light.Light) = (0 until 100).map { l.flickerAt(it * 0.07f) }.let { it.max() - it.min() }
        assertTrue(spread(fire) > 2f * spread(lamp), "${spread(fire)} vs ${spread(lamp)}")
    }

    @Test
    fun aSpotLightLightsOnlyInsideItsCone() {
        val rig = LightRig(ambient = 0f)
        val spot = Lights.lantern(100f, 300f, aim = 0f); rig.add(spot)
        assertTrue(rig.level(300f, 300f, 0f) > 0.3f, "in front of it")
        assertEquals(0f, rig.level(-100f, 300f, 0f), "behind it")
        assertEquals(0f, rig.level(250f, 500f, 0f), "off to the side of the beam")
    }

    @Test
    fun twoLightsDrawTwoShadowsOnBothSidesOfAThing() {
        val rig = LightRig(ambient = 0.05f)
        rig.add(Lights.lamp(20f, 220f, scale = 60f)); rig.add(Lights.lamp(380f, 220f, scale = 60f))
        val still = renderStill(width = 400, height = 300) {
            Spacer(Modifier.fillMaxSize().drawBehind {
                drawCastShadows(rig, 200f, 220f, 80f, 0f) { c -> drawRect(c, Offset(-8f, -80f), Size(16f, 80f)) }
            })
        }
        fun mass(from: Int, to: Int) = (0 until still.height).sumOf { y -> (from until to).count { x -> still.alpha(x, y) > 10 } }
        val left = mass(0, 192); val right = mass(208, 400)
        assertTrue(left > 200 && right > 200, "a shadow each side: $left and $right")
        assertTrue(abs(left - right) < 0.3f * (left + right), "balanced: $left and $right")
    }


    private fun wallScene(withWall: Boolean, ignore: Any? = null, wallOwner: Any? = null) = run {
        val rig = LightRig(ambient = 0.05f)
        rig.add(Lights.lamp(200f, 280f, scale = 60f))
        if (withWall) rig.add(br.com.faru.boitata.light.Wall(footY = 100f, left = 0f, right = 400f, height = 100f, owner = wallOwner))
        renderStill(width = 400, height = 300) {
            Spacer(Modifier.fillMaxSize().drawBehind {
                drawCastShadows(rig, 200f, 200f, 60f, 0f, ignore = ignore) { c -> drawRect(c, Offset(-10f, -60f), Size(20f, 60f)) }
            })
        }
    }

    private fun massAbove(s: Still, y: Int) = (0 until y).sumOf { r -> (0 until s.width).count { x -> s.alpha(x, r) > 10 } }

    @Test
    fun aShadowClimbsTheWallBehindTheThingExactlyWhereTheGeometrySaysItLands() {
        val walled = wallScene(withWall = true)
        // The lamp is Lights.lamp(200, 280, scale = 60): 72 high. The shadow on the wall is the thing's 20 pixel width times the enlargement, centred where the ray through its feet lands.
        val wall = br.com.faru.boitata.core.light.WallCast()
        assertTrue(br.com.faru.boitata.core.light.ShadowMath.onWallFromPoint(200f, 280f, 72f, 200f, 200f, 100f, 0.15f, wall))
        val half = wall.scale * 10f + 4f
        var inside = 0; var outside = 0
        for (y in 0 until 96) for (x in 0 until walled.width) if (walled.alpha(x, y) > 10) { if (kotlin.math.abs(x - wall.x) <= half) inside++ else outside++ }
        assertTrue(inside > 100, "a shadow on the wall above its foot: $inside")
        assertEquals(0, outside, "and nothing on the wall but the shadow of that thing, where the geometry puts it")
    }

    @Test
    fun theFloorShadowStopsAtTheWallFootAndTheWallsOwnerDoesNotShadowItself() {
        val owner = Any()
        val self = wallScene(withWall = true, ignore = owner, wallOwner = owner)
        val open = wallScene(withWall = false)
        // The thing is the wall (a crate): nothing lands on its own face, so what is on screen is the floor shadow as if there were no wall.
        assertEquals(massAbove(open, 300), massAbove(self, 300), "no change for the wall's own owner")
    }

    private fun crateAndPerson(receives: Boolean, crateX: Float = 200f): Triple<LightRig, br.com.faru.boitata.core.light.Billboard, br.com.faru.boitata.core.light.Billboard> {
        val rig = LightRig(ambient = 0.05f)
        rig.add(Lights.lamp(200f, 500f, scale = 60f).also { it.reach = 900f })
        val crate = br.com.faru.boitata.core.light.Billboard(footY = 300f, left = crateX - 50f, right = crateX + 50f, height = 60f, owner = "crate")
        crate.silhouette = { c -> drawRect(c, Offset(-50f, -60f), Size(100f, 60f)) }
        val person = br.com.faru.boitata.core.light.Billboard(footY = 200f, left = 180f, right = 220f, height = 80f, owner = "person")
        person.silhouette = { c -> drawRect(c, Offset(-20f, -80f), Size(40f, 80f)) }
        person.receivesShadows = receives
        rig.add(crate); rig.add(person)
        return Triple(rig, crate, person)
    }

    @Test
    fun aCratesShadowLiesAcrossAPersonBehindItAndOnlyWhereThePersonIs() {
        val (rig, _, person) = crateAndPerson(receives = true)
        val still = renderStill(width = 400, height = 300) { Spacer(Modifier.fillMaxSize().drawBehind { drawFaceShadows(rig, person, 0f) }) }
        var inside = 0; var outside = 0
        for (y in 0 until 300) for (x in 0 until 400) if (still.alpha(x, y) > 10) { if (x in 180..220 && y in 120..200) inside++ else outside++ }
        assertTrue(inside > 1200, "the person's body is in the crate's shadow: $inside")
        assertEquals(0, outside, "and nothing outside the person's outline")
    }

    @Test
    fun moveTheCrateAwayAndThePersonIsLitAgain() {
        val (rig, _, person) = crateAndPerson(receives = true, crateX = 400f)
        val still = renderStill(width = 400, height = 300) { Spacer(Modifier.fillMaxSize().drawBehind { drawFaceShadows(rig, person, 0f) }) }
        assertEquals(0, still.count(10), "no shadow on the person with the crate out of the way")
    }

    @Test
    fun aThingBehindACrateIsDarkerWhenItDoesNotShowFaceShadows() {
        val (rig, _, person) = crateAndPerson(receives = false)
        val hidden = rig.level(200f, 200f, 0f, height = 80f, self = person.owner)
        val (rig2, crate2, person2) = crateAndPerson(receives = false)
        crate2.blocksLight = false
        val lit = rig2.level(200f, 200f, 0f, height = 80f, self = person2.owner)
        assertTrue(lit > hidden + 0.02f, "the crate takes the lamp's light from the person: $hidden vs $lit")
    }

    @Test
    fun aPuppetBetweenALampAndAPaperScreenThrowsAShadowOnTheScreenAndTheNearerTheLampTheBiggerIt() {
        fun shadowWidth(puppetY: Float): Int {
            val rig = LightRig(ambient = 0.02f)
            rig.add(Lights.lamp(200f, 60f, scale = 40f).also { it.reach = 2000f })
            rig.add(br.com.faru.boitata.core.light.Billboard(footY = 290f, left = 0f, right = 400f, height = 250f, optics = br.com.faru.boitata.core.light.Optics.Paper()).also { it.maxShadowScale = 14f })
            val still = renderStill(width = 400, height = 300) {
                Spacer(Modifier.fillMaxSize().drawBehind {
                    drawCastShadows(rig, 200f, puppetY, 40f, 0f, maxAlpha = 1f, floor = false) { c -> drawRect(c, Offset(-10f, -40f), Size(20f, 40f)) }
                })
            }
            val row = 270
            return (0 until still.width).count { x -> still.alpha(x, row) > 10 }
        }
        val nearScreen = shadowWidth(puppetY = 250f)
        val nearLamp = shadowWidth(puppetY = 110f)
        assertTrue(nearScreen in 15..40, "pressed against the paper the shadow is about the puppet's own width: $nearScreen")
        assertTrue(nearLamp > 2.5f * nearScreen, "near the lamp it is far bigger: $nearLamp vs $nearScreen")
    }
}
