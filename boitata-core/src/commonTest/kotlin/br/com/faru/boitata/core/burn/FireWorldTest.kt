package br.com.faru.boitata.core.burn

import br.com.faru.boitata.core.wind.Wind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FireWorldTest {
    private val toRight = Wind(speed = 0.5f, direction = 1f, gustiness = 0f, turbulence = 0f)
    private val toLeft = Wind(speed = 0.5f, direction = -1f, gustiness = 0f, turbulence = 0f)
    private val gale = Wind(speed = 0.95f, direction = 1f, gustiness = 0f, turbulence = 0f)

    private fun body(id: String, x: Float, fuel: Material, height: Float = 70f, y: Float = 0f) = FireBody(id, fuel) { BodyGeometry(x, y, 50f, height) }

    private fun world(vararg bodies: FireBody, campfireKind: FireKind = FireKind.Ordinary): FireWorld {
        val w = FireWorld()
        bodies.forEach { w.add(it) }
        w.ignite("campfire", campfireKind)
        return w
    }

    private var clock = 0f
    private fun FireWorld.run(seconds: Float, wind: Wind, rain: Float = 0f, until: (FireWorld) -> Boolean = { false }): Float {
        val dt = 0.05f
        var elapsed = 0f
        while (elapsed < seconds) { clock += dt; elapsed += dt; step(dt, clock, wind, rain); if (until(this)) break }
        return elapsed
    }

    @Test
    fun aThingDownwindOfAFireWarmsSlowlyBeforeItCatches() {
        val crate = body("crate", 110f, Material.Wood)
        val w = world(body("campfire", 0f, Material.Campfire, 80f), crate)
        w.run(0.2f, toRight)
        assertEquals(BurnStatus.Cold, crate.status, "the flame has only just started: nothing yet")
        w.run(2.8f, toRight)
        assertEquals(BurnStatus.Heating, crate.status, "after a few seconds it is warming, not alight")
        assertTrue(crate.heat > 0.1f)
        val took = w.run(60f, toRight) { crate.status == BurnStatus.Burning } + 3f
        assertEquals(BurnStatus.Burning, crate.status, "it catches in the end")
        assertTrue(took in 3f..30f, "slowly: it took $took s")
    }

    @Test
    fun theSameThingUpwindIsSafeAndInACalmTheFireDoesNotReachIt() {
        val upwind = body("upwind", -110f, Material.Wood); val calmSide = body("calm", 110f, Material.Wood)
        val w = world(body("campfire", 0f, Material.Campfire, 80f), upwind)
        w.run(120f, toRight)
        assertTrue(upwind.status != BurnStatus.Burning && upwind.heat < upwind.material.ignitionHeat * 0.3f, "upwind it stays cool: ${upwind.heat}")
        val w2 = world(body("campfire", 0f, Material.Campfire, 80f), calmSide)
        w2.run(120f, Wind.Calm)
        assertTrue(calmSide.status != BurnStatus.Burning, "in a calm the flame does not reach this far")
    }

    @Test
    fun aThingCloseEnoughCatchesEvenInACalm() {
        val near = body("near", 45f, Material.Wood)
        val w = world(body("campfire", 0f, Material.Campfire, 80f), near)
        w.run(120f, Wind.Calm)
        assertTrue(near.status in listOf(BurnStatus.Burning, BurnStatus.Spent), "it caught, and in two minutes it has burnt itself out")
        assertEquals(1f, near.consumed)
    }

    @Test
    fun stoneNeverCatchesHoweverLongItStandsInTheFire() {
        val stone = body("stone", 30f, Material.None)
        val w = world(body("campfire", 0f, Material.Campfire, 80f), stone)
        w.run(200f, toRight)
        assertEquals(BurnStatus.Cold, stone.status); assertEquals(0f, stone.heat)
    }

    @Test
    fun drierThingsCatchSoonerThanWood() {
        fun timeToCatch(fuel: Material): Float {
            val thing = body("thing", 70f, fuel)
            val w = world(body("campfire", 0f, Material.Campfire, 80f), thing)
            return w.run(200f, toRight) { thing.status == BurnStatus.Burning }
        }
        val grass = timeToCatch(Material.DryGrass); val wood = timeToCatch(Material.Wood); val pine = timeToCatch(Material.Pine)
        assertTrue(grass < wood && wood < pine, "grass $grass, wood $wood, pine $pine")
    }

    @Test
    fun aBurningThingCharsBurnsOutAndCannotBurnAgain() {
        val crate = body("crate", 500f, Material.Wood)
        val w = world(body("campfire", 0f, Material.Campfire, 80f), crate)
        w.ignite("crate")
        w.run(5f, Wind.Calm)
        assertEquals(BurnStatus.Burning, crate.status); assertTrue(crate.consumed in 0.05f..0.5f && crate.burn > 0.9f, "alight and part charred: ${crate.consumed}")
        var previous = crate.consumed
        repeat(100) { w.run(0.5f, Wind.Calm); assertTrue(crate.consumed >= previous - 1e-6f, "charring never goes back"); previous = crate.consumed }
        assertEquals(BurnStatus.Spent, crate.status); assertEquals(1f, crate.consumed)
        w.run(10f, Wind.Calm)
        assertEquals(0f, crate.burn, "the flames are gone")
        w.ignite("crate")
        assertEquals(BurnStatus.Spent, crate.status, "ash does not light again")
    }

    @Test
    fun fireCreepsFromOneThingToTheNextAlongTheWind() {
        val crate = body("crate", 100f, Material.Wood); val stool = body("stool", 195f, Material.Wood); val log = body("log", 290f, Material.Wood)
        val w = world(body("campfire", 0f, Material.Campfire, 80f), crate, stool, log)
        w.run(400f, toRight)
        assertTrue(crate.status in listOf(BurnStatus.Burning, BurnStatus.Spent), "the crate caught")
        assertTrue(stool.status in listOf(BurnStatus.Burning, BurnStatus.Spent), "from the crate to the stool")
        assertTrue(log.status in listOf(BurnStatus.Burning, BurnStatus.Spent), "and on to the log: ${log.heat}")
    }

    @Test
    fun aThingThatIsMovedAwayCoolsDown() {
        var x = 110f
        val crate = FireBody("crate", Material.Wood) { BodyGeometry(x, 0f, 50f, 70f) }
        val w = world(body("campfire", 0f, Material.Campfire, 80f), crate)
        w.run(3f, toRight)
        val warm = crate.heat
        assertTrue(warm > 0.1f && crate.status != BurnStatus.Burning)
        x = 700f
        w.run(30f, toRight)
        assertTrue(crate.heat < warm * 0.1f, "dragged away it cools: $warm to ${crate.heat}")
        assertEquals(BurnStatus.Cold, crate.status)
    }

    @Test
    fun rainKeepsThingsFromCatchingAndPutsFiresOut() {
        val dry = body("dry", 100f, Material.Wood)
        val w = world(body("campfire", 0f, Material.Campfire, 80f), dry)
        w.run(20f, Wind.Calm, rain = 1f)
        val w2Crate = body("crate", 100f, Material.Wood)
        val w2 = world(body("campfire", 0f, Material.Campfire, 80f), w2Crate)
        w2.run(20f, Wind.Calm, rain = 1f)
        w2.run(150f, toRight, rain = 1f)
        assertTrue(w2Crate.status != BurnStatus.Burning, "in heavy rain a thing downwind of a fire does not catch: heat ${w2Crate.heat}")
        val burning = body("burning", 600f, Material.Wood)
        val w3 = world(body("campfire", 0f, Material.Campfire, 80f), burning)
        w3.ignite("burning"); w3.run(6f, Wind.Calm)
        assertEquals(BurnStatus.Burning, burning.status)
        w3.run(60f, Wind.Calm, rain = 1f)
        assertTrue(burning.status != BurnStatus.Burning, "the rain puts it out")
        assertTrue(burning.consumed < 1f, "and it was not burnt away: part of it is left")
    }

    @Test
    fun theBoitatasFireNeverLightsALivingPlantButOrdinaryFireDoes() {
        val plant = body("plant", 80f, Material.Plant); val palm = body("palm", 150f, Material.Palm)
        val guardian = world(body("campfire", 0f, Material.Campfire, 80f), plant, palm, campfireKind = FireKind.Guardian)
        guardian.run(200f, toRight)
        assertTrue(plant.status != BurnStatus.Burning && palm.status != BurnStatus.Burning && plant.heat == 0f, "the guardian's fire spares living things")
        val plant2 = body("plant", 80f, Material.Plant)
        val ordinary = world(body("campfire", 0f, Material.Campfire, 80f), plant2)
        ordinary.run(200f, toRight)
        assertTrue(plant2.status in listOf(BurnStatus.Burning, BurnStatus.Spent), "ordinary fire burns it")
    }

    @Test
    fun theBoitatasFireStillLightsDeadWoodAndPutsOutOrdinaryFiresNearIt() {
        val crate = body("crate", 80f, Material.Wood)
        val g = world(body("campfire", 0f, Material.Campfire, 80f), crate, campfireKind = FireKind.Guardian)
        g.run(200f, toRight)
        assertTrue(crate.status in listOf(BurnStatus.Burning, BurnStatus.Spent), "dead wood burns even in the guardian's fire")
        assertEquals(FireKind.Guardian, crate.kind, "with the guardian's fire")
        val ordinaryFire = body("people", 90f, Material.Wood)
        val g2 = world(body("campfire", 0f, Material.Campfire, 80f), ordinaryFire, campfireKind = FireKind.Guardian)
        g2.ignite("people", FireKind.Ordinary)
        g2.run(1f, Wind.Calm)
        assertEquals(BurnStatus.Burning, ordinaryFire.status)
        g2.run(40f, Wind.Calm)
        assertTrue(ordinaryFire.status != BurnStatus.Burning || ordinaryFire.kind == FireKind.Guardian, "the guardian's fire puts out the fire of people it touches")
    }

    @Test
    fun inAGaleSparksCarryFireFarBeyondTheFlamesReachButNotUpwindOrInACalm() {
        fun caught(wind: Wind, x: Float): Boolean {
            val far = body("far", x, Material.DryGrass)
            val w = world(body("campfire", 0f, Material.Campfire, 80f), far)
            w.run(400f, wind)
            return far.status in listOf(BurnStatus.Burning, BurnStatus.Spent)
        }
        assertTrue(caught(gale, 480f), "a spark finds it in a gale")
        assertFalse(caught(Wind.Calm, 480f), "but not in a calm")
        assertFalse(caught(gale, -480f), "and not upwind")
    }

    @Test
    fun itIsTheSameEveryTime() {
        fun run(): List<Float> {
            val crate = body("crate", 110f, Material.Wood); val stool = body("stool", 200f, Material.Wood)
            val w = world(body("campfire", 0f, Material.Campfire, 80f), crate, stool)
            clock = 0f
            w.run(90f, Wind.Windy)
            return listOf(crate.heat, crate.burn, crate.consumed, stool.heat, stool.burn, stool.consumed, w.totalFlame)
        }
        assertEquals(run(), run())
    }

    @Test
    fun resetPutsEverythingBack() {
        val crate = body("crate", 40f, Material.Wood)
        val w = world(body("campfire", 0f, Material.Campfire, 80f), crate)
        w.run(100f, toRight)
        w.reset()
        assertEquals(BurnStatus.Cold, crate.status); assertEquals(0f, crate.consumed); assertEquals(0f, w.totalFlame)
    }
}
