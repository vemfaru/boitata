package br.com.faru.boitata.playground

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import br.com.faru.boitata.core.burn.BodyGeometry
import br.com.faru.boitata.core.burn.BurnStatus
import br.com.faru.boitata.core.burn.FireBody
import br.com.faru.boitata.core.burn.FireWorld
import br.com.faru.boitata.core.burn.Material
import br.com.faru.boitata.core.fluid.Puddle
import br.com.faru.boitata.core.fluid.Spill
import br.com.faru.boitata.core.wind.Wind
import br.com.faru.boitata.light.drawBurningBody
import br.com.faru.boitata.light.drawCaustic
import br.com.faru.boitata.light.drawPuddle
import br.com.faru.boitata.light.drawStains
import br.com.faru.boitata.core.stain.SetDown
import br.com.faru.boitata.core.pour.PourJob
import br.com.faru.boitata.core.pour.VesselAction
import br.com.faru.boitata.light.VesselWheel
import br.com.faru.boitata.core.stain.SurfaceMaterial
import br.com.faru.boitata.core.stain.StainField
import br.com.faru.boitata.core.stain.VesselBase
import androidx.compose.runtime.mutableStateOf
import br.com.faru.boitata.core.fluid.PourSide
import br.com.faru.boitata.core.pour.PourLevel
import br.com.faru.boitata.core.sip.Drinker
import br.com.faru.boitata.plants.PlantEnvironment
import br.com.faru.boitata.plants.PlantPatch
import br.com.faru.boitata.plants.PlantSpecies
import br.com.faru.boitata.plants.Planter
import br.com.faru.boitata.plants.Soil
import br.com.faru.boitata.plants.drawPlant
import br.com.faru.boitata.light.SoilLook
import br.com.faru.boitata.core.sip.SipJob
import br.com.faru.boitata.core.sip.SipPose
import br.com.faru.boitata.light.drawSipOverlay
import br.com.faru.boitata.light.drawVesselAt
import br.com.faru.boitata.light.SpillPart
import br.com.faru.boitata.light.drawSpillStream
import androidx.compose.ui.graphics.drawscope.clipRect
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.clock.RunOnSceneClock
import br.com.faru.boitata.core.clock.rememberSceneQuality
import br.com.faru.boitata.core.fluid.Fluid
import br.com.faru.boitata.core.fluid.LiquidVessel
import br.com.faru.boitata.light.FlamePalette
import br.com.faru.boitata.light.VesselShape
import br.com.faru.boitata.light.VesselStyle
import br.com.faru.boitata.light.drawFlame
import br.com.faru.boitata.light.drawVessel
import br.com.faru.boitata.core.fluid.Drips
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs

/** A container standing on the bench: the simulation, how it is drawn, and where along the table it stands (a share of the width). */
class BenchVessel(
    val vessel: LiquidVessel, val style: VesselStyle, var x: Float, val fill: Float, val temperature: Float = 20f, val art: String? = null,
    /** What the wheel calls it, and the vessels (by index on the bench) it can be poured into. */
    val label: String = "", val destinations: List<Int> = emptyList(),
    /** A pot of soil and plants, if this container is one: what is poured into it soaks in and the plants answer. */
    val planter: Planter? = null,
) {
    internal val baseFluid: Fluid = vessel.fluid
    internal var job: PourJob? = null
    internal var sip: SipJob? = null
    internal var drops = 0
    internal var lastVelocity = 0f
    internal var lastX = x
    internal var lastSpilled = 0f
    internal val setDown = SetDown()
}

/**
 * The liquids bench: a glass of kerosene, a bottle of wine, a mug of hot coffee, a tumbler of water and a jug of milk on a glossy table with ends, in front of a lit wall. Everything is state held here so the controls can
 * reach it: [select] picks a vessel (tap it), [pour], [pourHot], [drop] (a drop falls in and ripples spread), [knock] (tip it over), [heat], [refill]. Dragging a vessel along the table pushes it, and the liquid lags and sloshes.
 * Spilled liquid makes a puddle that spreads over the table; where it reaches an end of the table it runs off in drops that fall to the floor and make a puddle there.
 */
class LiquidBench {
    private val fernPot = Planter(LiquidVessel(0.14f, 0.13f, Fluid.Water, 0f), PlantPatch(Soil.Loam, listOf(PlantSpecies.Fern), 0.1f))

    val vessels: List<BenchVessel> = listOf(
        BenchVessel(LiquidVessel(0.07f, 0.10f, Fluid.Kerosene, 0.75f), VesselStyle(VesselShape.Tumbler, glass = Color(0xFFE9E3C8)), 0.145f, 0.75f, art = "kerosene", label = "Kerosene glass", destinations = listOf(3, 6)),
        BenchVessel(LiquidVessel(0.09f, 0.17f, Fluid.Wine, 0.55f, pourSide = PourSide.Right), VesselStyle(VesselShape.Bottle, glass = Color(0xFFBFD8C4)), 0.2485f, 0.55f, art = "bottle", label = "Wine bottle", destinations = listOf(3, 6)),
        BenchVessel(LiquidVessel(0.11f, 0.09f, Fluid.Coffee, 0.78f), VesselStyle(VesselShape.Mug, glass = Color(0xFFEFE6D2)), 0.47f, 0.78f, temperature = 84f, art = "mug", label = "Mug"),
        BenchVessel(LiquidVessel(0.074f, 0.10f, Fluid.Water, 0.6f), VesselStyle(VesselShape.Tumbler), 0.585f, 0.6f, art = "tumbler", label = "Tumbler", destinations = listOf(6)),
        BenchVessel(LiquidVessel(0.10f, 0.14f, Fluid.Milk, 0.7f, pourSide = PourSide.Left), VesselStyle(VesselShape.Jug), 0.86f, 0.7f, art = "jug", label = "Milk jug", destinations = listOf(2, 6)),
        BenchVessel(LiquidVessel(0.075f, 0.15f, Fluid.Coffee, 0.85f), VesselStyle(VesselShape.Jar, glass = Color(0xFFD9CFB8)), 0.354f, 0.85f, temperature = 70f, label = "Coffee jar", destinations = listOf(2, 6)),
        // A pot of soil with a thirsty fern in it: what is poured in stands on the soil and soaks in, and the fern answers.
        BenchVessel(
            fernPot.pot, VesselStyle(VesselShape.Pot, glass = Color(0xFFB8643A)), 0.716f, 0f, label = "Fern pot", planter = fernPot,
        ),
    )
    var selected by mutableIntStateOf(2)

    /** Counts changes of a vessel's liquid, so the controls know to redraw. */
    var fluidVersion by mutableIntStateOf(0)
        private set

    /** The liquid in the picked vessel. */
    val fluid: Fluid get() = vessels[selected].vessel.fluid

    /** Changes the liquid in the picked vessel (a different one, or the same with other numbers: see [Fluid.copy]); what is in it stays. */
    fun setFluid(fluid: Fluid) { vessels[selected].vessel.fluid = fluid; fluidVersion++ }

    /** The ends of the table, as shares of the width. */
    internal val tableLeft = 0.07f
    internal val tableRight = 0.93f

    /** How deep the table is, front to back, in meters, and how far from its front edge the vessels stand: a puddle that spreads farther than that runs off the front or the back. */
    internal val tableDepth = 0.55f
    internal val standingDepth = 0.28f

    /** Which vessel has its wheel open (-1 none), and where its top is on screen, for the wheel to hang from. */
    var wheelFor by mutableIntStateOf(-1)
        private set
    var wheelAnchor: Offset = Offset.Zero
        private set
    internal val anchorX = FloatArray(vessels.size)
    internal val anchorY = FloatArray(vessels.size)

    /** What the picked vessel can be told to do: heat it, pour it into each vessel it can reach, drop something in, knock it over. */
    fun actionsFor(i: Int): List<VesselAction> {
        val b = vessels[i]; val busy = b.job != null || b.sip != null
        return buildList {
            add(VesselAction("heat", "Heat", enabled = !busy))
            if (b.vessel.fluid.potable) add(VesselAction("sip", "Hold to sip", enabled = !busy && b.vessel.sippable, hold = true))
            for (j in b.destinations) {
                val t = vessels[j]
                val ready = !busy && !b.vessel.empty && t.job == null && abs(t.vessel.body.angle) < 0.1f && ppm > 1f
                if (t.planter != null) {
                    // A plant is watered: a measure, or for as long as you hold.
                    add(VesselAction("pour:$j:top", "Water the ${t.label}", enabled = ready && t.vessel.fill < 0.9f))
                    add(VesselAction("pour:$j:hold", "Hold to water the ${t.label}", enabled = ready, hold = true))
                } else {
                    add(VesselAction("pour:$j:shot", "Pour a shot into ${t.label}", enabled = ready && t.vessel.fill < 0.9f))
                    add(VesselAction("pour:$j:top", "Top up ${t.label}", enabled = ready && t.vessel.fill < 0.9f))
                    add(VesselAction("pour:$j:hold", "Hold to pour into ${t.label}", enabled = ready, hold = true))
                }
            }
            if (busy) add(VesselAction("stop", "Stop pouring"))
            add(VesselAction("drop", "Drop in", enabled = !busy))
            add(VesselAction("knock", "Knock over", enabled = !busy))
        }
    }

    /** Opens the wheel on a vessel (and picks it), or closes it if it is open on that one. */
    fun toggleWheel(i: Int) {
        if (wheelFor == i) { wheelFor = -1; return }
        select(i); wheelFor = i; wheelAnchor = Offset(anchorX[i], anchorY[i])
    }

    fun closeWheel() { wheelFor = -1 }

    /** An action that goes on while it is held down is pressed ([down]) or let go: sipping takes a gulp now and one every half second after, pouring goes on until it is let go (and runs over if the destination fills). */
    fun hold(i: Int, action: VesselAction, down: Boolean) {
        select(i)
        if (down) {
            when {
                action.id == "sip" -> holdSip(true)
                action.id.startsWith("pour:") -> { val (to, _) = action.id.removePrefix("pour:").split(":"); pourInto(i, to.toInt(), PourLevel.UntilStopped) }
            }
        } else {
            holdSip(false)
            vessels[i].job?.stop()
            wheelFor = -1
        }
    }

    /** Starts ([down]) or stops sipping from the picked vessel: a gulp now and another every half second while it is held. */
    fun holdSip(down: Boolean) {
        if (down) {
            val b = vessels[selected]
            if (b.sip != null || b.job != null || !b.vessel.sippable || ppm <= 1f) return
            // It comes up from where it stands, seen as [drinker] sees it.
            b.sip = SipJob(b.vessel, drinker, SipPose(width * b.x, tableY + 6f, 1f, 0f), ppm, width, height)
            sipping = selected; sipActive = true
        } else { vessels.getOrNull(sipping)?.sip?.release(); sipping = -1 }
    }

    /** Who is sipping, and so how it is seen (first person for [Drinker.You]). */
    var drinker by mutableStateOf(Drinker.You)

    /** Whether someone is sipping right now (the wheel steps aside for it). */
    var sipActive by mutableStateOf(false)
        private set

    private var sipping = -1

    /** Does what the wheel's [action] says to vessel [i]. */
    fun act(i: Int, action: VesselAction) {
        wheelFor = -1
        select(i)
        when {
            action.id == "heat" -> heat()
            action.id == "drop" -> drop()
            action.id == "knock" -> knock()
            action.id == "sip" -> sip()
            action.id == "stop" -> vessels[i].job?.stop()
            action.id.startsWith("pour:") -> {
                val (to, how) = action.id.removePrefix("pour:").split(":")
                pourInto(i, to.toInt(), when (how) { "shot" -> PourLevel.Shot; "hold" -> PourLevel.UntilStopped; else -> PourLevel.TopUp })
            }
        }
    }

    /** Pours vessel [from] into vessel [to]: it slides beside it (on the side it can pour from), lifts, tips, pours as much as [level] says, and goes home. */
    fun pourInto(from: Int, to: Int, level: PourLevel = PourLevel.TopUp) {
        val src = vessels[from]; val dst = vessels[to]
        if (src.job != null || dst.job != null || ppm <= 1f) return
        val m = metersPerShare
        src.job = PourJob(src.vessel, dst.vessel, homeX = src.x * m, targetX = dst.x * m, level = level)
    }

    internal var metersPerShare = 1f
    internal var scriptT = -1f
    internal var ppm = 1f
    internal var tableY = 0f
    internal var floorY = 0f
    internal var width = 1f
    internal var height = 1f

    /** The puddles on the table and on the floor beneath it, the drops falling between, and the fire that spreads through the puddles that burn. */
    val puddles = ArrayList<Puddle>()
    val floorPuddles = ArrayList<Puddle>()
    internal val drips = Drips()

    /** Drops falling from a lip that has just poured, onto the table. */
    internal val tableDrips = Drips()
    internal val world = FireWorld()
    private val bodies = HashMap<Puddle, FireBody>()
    internal fun body(p: Puddle): FireBody? = bodies[p]
    private var elapsed = 0f

    /** What the table is made of, and the marks wet feet have left on it. */
    var surface by mutableStateOf(SurfaceMaterial.Wood)
    internal val stains = StainField()

    /** Cleans the table. */
    fun wipe() { stains.clear() }

    init {
        fernPot.reset(0.1f, 0.2f)
        // Feet: a tumbler and a bottle stand on a thin rim, a mug on a ring foot, a jug on its whole base.
        val feet = listOf(VesselBase(0.0315f, 0.12f), VesselBase(0.04f, 0.2f), VesselBase(0.045f, 0.3f), VesselBase(0.033f, 0.12f), VesselBase(0.045f), VesselBase(0.03f, 0.1f), VesselBase(0.055f))
        for ((b, foot) in vessels.zip(feet)) b.vessel.base = foot
        // A flame on the left of the bench: a spill that reaches it, if it burns, catches.
        world.add(FireBody("flame", Material.Campfire) { BodyGeometry(width * 0.1f, tableY, height * 0.14f, height * 0.3f) })
        world.ignite("flame")
        for (b in vessels) b.vessel.also { it.coolingSeconds = 150f; if (b.temperature > 25f) it.heatTo(b.temperature) }
    }

    /** Sets light to the spilled liquid, if it burns (the nearest puddle to the picked vessel). */
    fun light() {
        val v = vessels[selected]
        val p = puddles.filter { it.fluid.flammable }.minByOrNull { abs(it.x - (width * v.x)) } ?: return
        bodies[p]?.let { world.ignite(it.id) }
    }

    fun select(i: Int) { selected = i.coerceIn(0, vessels.size - 1) }
    /** Takes a sip from the picked vessel: the level drops by a mouthful. */
    fun sip() { vessels[selected].vessel.sip() }
    fun pour() { val v = vessels[selected].vessel; v.pour(v.width * v.height * 0.14f) }

    /** Pours hot coffee into the picked vessel: it mixes with what is there (by volume), steams, and the pour leaves foam. */
    fun pourHot() { val v = vessels[selected].vessel; v.pour(v.width * v.height * 0.2f, temperature = 93f, foamy = true) }

    /** Heats the picked vessel's liquid by [degrees] (a hot plate under it). */
    fun heat(degrees: Float = 22f) { vessels[selected].vessel.heat(degrees) }
    fun drop() { vessels[selected].vessel.drop(0.3f + 0.4f * ((vessels[selected].vessel.surface.energy() * 7f) % 1f), 0.9f) }

    /** Tips the picked vessel over, toward the way you say (+1 right, -1 left), or toward the middle of the table by default. */
    fun knock(direction: Float = vessels[selected].vessel.pourSide.let { side -> if (side == PourSide.Left) -1f else if (side == PourSide.Right) 1f else if (vessels[selected].x < 0.5f) 1f else -1f }) { vessels[selected].vessel.knock(9f, direction) }
    fun refill() {
        for (b in vessels) { b.job?.let { b.x = it.homeX / metersPerShare }; b.job = null; b.sip = null; b.vessel.reset(b.fill, b.temperature); b.vessel.fluid = b.baseFluid; b.planter?.reset(0.1f, 0.2f); b.lastSpilled = 0f }
        wheelFor = -1
        puddles.clear(); floorPuddles.clear(); drips.clear(); tableDrips.clear(); stains.clear(); sipping = -1; sipActive = false
        for (body in bodies.values) world.extinguish(body.id); bodies.clear()
    }

    private fun puddleAt(list: ArrayList<Puddle>, x: Float, fluid: Fluid): Puddle = list.firstOrNull { it.fluid === fluid && abs(it.x - x) < it.radius * ppm + 16f } ?: Puddle(x, fluid).also { list += it }

    /** [volume] (m^3) of [ves]'s liquid lands at pixel [landing]: on the table, joining a puddle there or starting one; beyond an end of the table, on the floor. */
    private fun landOnTable(ves: LiquidVessel, landing: Float, volume: Float) = landOnTable(ves.fluid, ves.effectiveViscosity, landing, volume)

    private fun landOnTable(fluid: Fluid, viscosity: Float, landing: Float, volume: Float) {
        if (landing < width * tableLeft || landing > width * tableRight) { puddleAt(floorPuddles, landing, fluid).add(volume); return }
        val puddle = puddles.firstOrNull { it.fluid === fluid && abs(it.x - landing) < it.radius * ppm + 16f } ?: Puddle(landing, fluid).also { p ->
            p.viscosity = viscosity
            puddles += p
            if (fluid.flammable) {
                val id = "puddle${puddles.size}-${elapsed}"
                bodies[p] = world.add(FireBody(id, Material.SpilledOil) { BodyGeometry(p.x, tableY, 2f * p.radius * ppm, height * 0.14f) })
            }
        }
        puddle.add(volume)
    }

    /**
     * A bead lets go of the lip of [b] ([area] m^2 of its liquid). Over the mouth of another container it falls in; from a tipped or lifted vessel it falls to the table; from one standing home it runs down the side
     * to the foot (which wets it, so it can leave a ring) and every other bead lets go beside the foot.
     */
    private fun dribbleDrop(b: BenchVessel, area: Float) {
        val ves = b.vessel; val volume = area * ves.width
        val s = if (ves.body.angle >= 0f) 1f else -1f
        val pivot = pivotPx(b, width, ppm)
        val x = pivot + s * ves.lipX() * ppm
        val tilted = abs(ves.body.angle) > 0.25f || ves.lift > 0.005f
        for (o in vessels) if (o !== b && abs(o.vessel.body.angle) < 0.1f && abs(x - width * o.x) < o.vessel.width * ppm * 0.45f && tilted) { o.vessel.receive(ves.fluid, volume / o.vessel.width, ves.temperature); return }
        if (tilted) { tableDrips.emit(x, tableY + 6f - (ves.lipHeight() + ves.lift) * ppm, volume, ves.fluid); return }
        ves.wetBottom(ves.fluid, area / (ves.width * ves.height) * 40f)
        b.drops++
        if (b.drops % 2 == 0) tableDrips.emit(pivot + s * 3f, tableY - 10f, volume, ves.fluid)
    }

    /** The plants' day: how bright, hot and windy it is where the pot stands (indoors by a window), and how fast the hours go (an hour in about a minute of watching). */
    private val indoors = PlantEnvironment(daylight = 1f, light = 0.35f, lightSide = 0.2f, rain = 0f, heat = 0.2f, wind = 0f)
    private val plantHoursPerSecond = 1f / 60f

    /** Lets the pot soak up what stands in it, the plants live on, and what runs out of the hole in the bottom run onto the table. */
    private fun tendPlanter(b: BenchVessel, planter: Planter, dt: Float) {
        planter.step(dt)
        planter.patch.step(indoors, dt * plantHoursPerSecond)
        val out = planter.takeDrained()
        if (out > 0f && ppm > 1f) {
            val pot = b.vessel
            pot.wetBottom(Fluid.Water, out / (pot.width * pot.height) * 40f)
            landOnTable(Fluid.Water, 0f, width * b.x + pot.width * ppm * 0.5f + 6f, out * pot.width)
        }
    }

    /** Lets [hours] go by for the plants in every pot at once: a thirsty fern wilts, a watered one perks up. */
    fun waitHours(hours: Float) { for (b in vessels) b.planter?.let { p -> repeat(4) { p.patch.step(indoors, hours / 4f) } } }

    internal fun step(dt: Float) {
        for (b in vessels) {
            // A hand pouring it carries it along the table.
            b.job?.let { b.x = it.x / metersPerShare }
            // A drag moves the vessel; the liquid feels the change in its speed.
            val v = (b.x - b.lastX) * metersPerShare / dt
            b.vessel.accelerate((v - b.lastVelocity) / dt)
            b.lastVelocity = v; b.lastX = b.x
            b.vessel.step(dt)
            b.planter?.let { tendPlanter(b, it, dt) }
            b.sip?.let { it.step(dt); if (it.done) { b.sip = null; sipActive = vessels.any { v -> v.sip != null } } }
            b.vessel.takeDribbleDrop().let { if (it > 0f && ppm > 1f) dribbleDrop(b, it) }
            // A foot standing in a puddle gets wet; a wet one set down leaves its ring, and one that stands a while seeps a little more.
            if (ppm > 1f && b.vessel.lift < 0.002f) {
                val centre = width * b.x
                for (p in puddles) if (abs(p.x - centre) < p.radius * ppm) b.vessel.wetBottom(p.fluid, dt * 0.8f)
                val atX = centre / ppm
                if (b.setDown.step(v, dt)) stains.touch(b.vessel, atX, 0f, surface, 0.8f)
                else if (abs(v) < 0.03f) stains.touch(b.vessel, atX, 0f, surface, dt * 0.15f)
            }
            b.job?.let { job ->
                job.step(dt)
                b.lastSpilled = b.vessel.spilled
                if (job.missed > 0f) landOnTable(b.vessel, job.missX * ppm, job.missed)
                if (job.done) { b.x = job.homeX / metersPerShare; b.job = null }
                continue
            }
            // What ran over the lip lands: on the table, and joins a puddle there (or starts one); beyond the end of the table, it falls to the floor.
            val ran = b.vessel.spilled - b.lastSpilled
            b.lastSpilled = b.vessel.spilled
            if (ran > 0f && ppm > 1f) {
                val ves = b.vessel
                // The corner it tips on is half a width from its middle, toward the way it leans.
                val s = if (ves.body.angle >= 0f) 1f else -1f
                landOnTable(ves, width * b.x + s * ves.width * ppm / 2f + s * (ves.lipX() + Spill.reach(ves.fallHeight, ves.jetSpeed)) * ppm, ran * ves.width)
            }
        }
        // A puddle that has spread past an end, the front or the back of the table runs off it in drops, in proportion to how much of it hangs over. Off the front the drops fall down in front of the table;
        // off an end they fall beside it; off the back they fall behind it, out of sight (the liquid is simply lost there).
        val left = width * tableLeft; val right = width * tableRight
        for (p in puddles) {
            p.step(dt); bodies[p]?.let { p.burnt = it.consumed }
            val rM = p.radius                                   // meters
            val r = rM * ppm
            if (r < 2f) continue
            val overLeft = max(0f, left - (p.x - r)) / ppm; val overRight = max(0f, (p.x + r) - right) / ppm
            val overFront = max(0f, rM - standingDepth); val overBack = max(0f, rM - (tableDepth - standingDepth))
            val over = overLeft + overRight + overFront + overBack
            if (over > 0f) {
                val leak = min(p.volume, p.volume * 0.9f * min(0.9f, over / (2f * rM)) * dt)
                p.remove(leak)
                if (overBack > overFront && overBack > overLeft && overBack > overRight) continue
                var n = (leak / 2e-6f).toInt().coerceIn(0, 3)
                if (n == 0 && leak > 1e-8f && (elapsed * 12f).toInt() != ((elapsed - dt) * 12f).toInt()) n = 1
                // Where it leaves: the front edge below the puddle (a random spot along the part that hangs over), or the nearer end.
                val frontEdgeY = height * 0.72f
                repeat(n) { k ->
                    val share = leak / max(1, n)
                    if (overFront >= overLeft && overFront >= overRight) drips.emit(p.x + (((elapsed * 37f + k * 11f) % 1f) - 0.5f) * r * 1.4f, frontEdgeY, share, p.fluid)
                    else { val edge = if (overLeft > overRight) left else right; drips.emit(edge + (if (edge == left) -3f else 3f), tableY + height * 0.1f, share, p.fluid) }
                }
            }
        }
        // The drops fall to the floor and join a puddle there.
        tableDrips.step(dt, tableY + 6f, height * 4.5f) { x, volume, tag -> (tag as? Fluid ?: Fluid.Water).let { landOnTable(it, it.viscosity, x, volume) } }
        drips.step(dt, floorY, height * 4.5f) { x, volume, tag -> puddleAt(floorPuddles, x, tag as? Fluid ?: Fluid.Water).add(volume) }
        for (p in floorPuddles) p.step(dt)
        stains.step(dt)
        elapsed += dt
        world.step(dt, elapsed, Wind.Calm)
    }
}

/** The liquids bench scene: see [LiquidBench]. [script], if given, drives it for a recording. */
@Composable
fun LiquidsScene(seconds: () -> Float, bench: LiquidBench, modifier: Modifier = Modifier.fillMaxSize(), script: ((LiquidBench, Float) -> Unit)? = null) {
    val art = LocalSprites.current ?: return
    val quality = rememberSceneQuality()
    val behind = rememberGraphicsLayer()
    RunOnSceneClock(bench) { dt, _ -> bench.step(dt) }
    if (script != null) { val now = seconds(); SideEffect { script(bench, now) } }
    Box(
        modifier.pointerInput(bench) {
            var grabbed = -1
            detectDragGestures(
                onDragStart = { at -> grabbed = pick(bench, at.x / this.size.width).takeIf { it < 0 || bench.vessels[it].job == null } ?: -1; if (grabbed >= 0) { bench.select(grabbed); bench.closeWheel() } },
                onDragEnd = { grabbed = -1 }, onDragCancel = { grabbed = -1 },
            ) { change, delta -> if (grabbed >= 0) { change.consume(); bench.vessels[grabbed].x = (bench.vessels[grabbed].x + delta.x / this.size.width).coerceIn(0.1f, 0.9f) } }
        }.pointerInput(bench) {
            detectTapGestures { at -> val i = pick(bench, at.x / this.size.width); if (i >= 0) bench.toggleWheel(i) }
        },
    ) {
        Spacer(Modifier.fillMaxSize().drawBehind { drawBench(art, bench, behind, seconds(), quality()) })
        // The wheel of things the picked vessel can do, hanging from its top.
        val open = bench.wheelFor
        if (open >= 0) VesselWheel(bench.actionsFor(open), bench.wheelAnchor, onAction = { bench.act(open, it) }, onDismiss = { bench.closeWheel() }, onHold = { a, down -> bench.hold(open, a, down) }, hidden = bench.sipActive)
    }
}

private fun styleOf(art: Sprites, b: BenchVessel): VesselStyle = b.planter?.let { VesselStyle(b.style.shape, b.style.glass, b.style.ink, soil = SoilLook(it.patch.wet.moisture, it.patch.wet.contamination)) } ?: b.art?.let { art.vessels[it] }?.let { VesselStyle(b.style.shape, b.style.glass, b.style.ink, art = it) } ?: b.style

/** Where the corner a vessel tips on is, in pixels: half a width from its middle, toward the way it leans. */
private fun pivotPx(b: BenchVessel, canvasWidth: Float, ppm: Float): Float = canvasWidth * b.x + (if (b.vessel.body.angle >= 0f) 1f else -1f) * b.vessel.width * ppm / 2f

private fun pick(bench: LiquidBench, share: Float): Int = bench.vessels.indices.minByOrNull { abs(bench.vessels[it].x - share) }?.takeIf { abs(bench.vessels[it].x - share) < 0.1f } ?: -1

private fun DrawScope.drawBench(art: Sprites, bench: LiquidBench, behind: androidx.compose.ui.graphics.layer.GraphicsLayer, t: Float, quality: Quality) {
    val w = size.width; val h = size.height
    // The layout: a wall behind, a table with ends whose top slopes away toward the back, an apron in front, and the floor under it.
    val topBack = h * 0.56f; val topFront = h * 0.72f; val tableY = h * 0.645f; val apron = h * 0.8f; val floorLine = h * 0.86f; val floorY = h * 0.93f
    val left = w * bench.tableLeft; val right = w * bench.tableRight
    val ppm = h * 1.8f
    bench.metersPerShare = w / ppm
    bench.ppm = ppm; bench.tableY = tableY; bench.floorY = floorY; bench.width = w; bench.height = h
    // Everything behind the glasses, recorded once: the lit wall and floor, a flame, and Maria Bonita behind the table, so there is something to see through the liquid.
    behind.record(size = IntSize(w.toInt(), h.toInt())) {
        drawRect(Brush.verticalGradient(0f to Color(0xFF3A2D26), 1f to Color(0xFF1A1411)), size = Size(w, floorLine))
        drawTiled(art.wall, Rect(0f, 0f, w, floorLine), tile = w * 0.34f, tint = Color(0xFF4A3A30))
        drawRect(Color(0xFF16100B), Offset(0f, floorLine), Size(w, h - floorLine))
        drawTiled(art.floor, Rect(0f, floorLine, w, h), tile = w * 0.3f, tint = Color(0xFF1C140E))
        val image = art.prop("maria_bonita"); val ph = h * 0.48f; val k = ph / image.height
        drawArt(image, Offset(w * 0.5f - image.width * k / 2f, tableY - ph), k, Color(0xFFC8A06A))
        drawFlame(w * 0.1f, tableY - h * 0.03f, h * 0.12f, h * 0.24f, t, 0.9f, FlamePalette.Campfire, 8L, quality)
        drawRect(Color(0xFFFFD9A0).copy(alpha = 0.5f), Offset(w * 0.9f, tableY - h * 0.3f), Size(w * 0.012f, h * 0.28f))
        drawRect(Color(0xFF14110F), Offset(w * 0.3f, tableY - h * 0.42f), Size(w * 0.012f, h * 0.38f))
    }
    drawLayer(behind)
    // The table: legs, the apron, and the top with a glossy sheen. It ends at both sides.
    val tint = Color(0xFF6B4A2E)
    val legImg = art.bench.getValue("table_leg"); val legH = floorLine + h * 0.08f - apron; val legW = legImg.width * legH / legImg.height
    drawArt(legImg, Offset(left + w * 0.01f, apron), legH / legImg.height, tint.copy(alpha = 0.9f))
    drawArt(legImg, Offset(right - w * 0.01f - legW, apron), legH / legImg.height, tint.copy(alpha = 0.9f))
    val apronImg = art.bench.getValue("table_apron")
    drawStretched(apronImg, Rect(left, topFront, right, apron), Color(0xFF3F2C1C))
    drawRect(Color(0xFF0F0A06).copy(alpha = 0.6f), Offset(left, topFront), Size(right - left, h * 0.008f))
    drawStretched(art.bench.getValue("table_top"), Rect(left, topBack, right, topFront), tint)
    drawRect(Color(0xFFFFE2B0).copy(alpha = 0.07f), Offset(left, topBack), Size(right - left, h * 0.018f))
    // Puddles on the floor under the table.
    for (p in bench.floorPuddles) drawPuddle(p.fluid.color, p.x, floorY, p.radius * ppm * 0.9f)
    // Puddles on the table, kept to its top, and the fire on the ones that burn. The rings of wet feet lie under them.
    clipRect(left, topBack, right, topFront) { drawStains(bench.stains, 0f, tableY + 6f, ppm) }
    clipRect(left, topBack, right, topFront) { for (p in bench.puddles) drawPuddle(p.fluid.color, p.x, tableY + h * 0.02f, p.radius * ppm, p.burnt) }
    for (p in bench.puddles) bench.body(p)?.let { body -> if (body.status == BurnStatus.Burning) drawBurningBody(body, body.geometry().let { g -> BodyGeometry(g.feetX, tableY + h * 0.02f, g.width, g.height) }, t, Wind.Calm, quality) }
    // The mirror image of each vessel in the glossy top.
    clipRect(left, tableY, right, topFront) {
        for (b in bench.vessels) if (b.sip == null) drawVessel(b.vessel, styleOf(art, b), pivotPx(b, w, ppm), tableY + 6f, ppm, t, quality, behind = behind, mirrored = 0.22f, mirrorOnly = true)
    }
    // A container being poured into is drawn after the one pouring, so the stream runs behind its glass and into its liquid, and the splash is drawn over the liquid it lands on.
    val pouringInto = bench.vessels.mapNotNull { it.job?.target }
    val order = bench.vessels.withIndex().sortedBy { (_, b) -> if (pouringInto.any { it === b.vessel }) 2 else if (b.job != null) 1 else 0 }
    for ((i, b) in order) {
        if (b.sip != null) continue
        val style = styleOf(art, b)
        val px = pivotPx(b, w, ppm)
        bench.anchorX[i] = w * b.x; bench.anchorY[i] = tableY + 6f - (b.vessel.height * 1.15f + b.vessel.lift) * ppm
        // Selected: a faint pool under it.
        if (i == bench.selected) drawOval(Color(0xFFFFE9B0).copy(alpha = 0.12f), Offset(px - b.vessel.width * ppm, tableY - 4f), Size(b.vessel.width * ppm * 2f, 14f))
        // The lamp is the flame on the left: the glass throws its coloured shadow and its caustic away from it.
        // (kept to the table's top: past its ends there is nothing for the light to land on)
        clipRect(left, topBack, right, topFront) { drawCaustic(b.vessel, px, tableY + 8f, ppm, t, fromLeft = b.x > 0.12f, quality = quality) }
        drawVessel(b.vessel, style, px, tableY + 6f, ppm, t, quality, behind = behind)
        b.planter?.let { drawFernIn(it, w * b.x, tableY + 6f - b.vessel.height * ppm * 0.98f + b.vessel.width * ppm * 0.02f, b.vessel.height * ppm, t) }
        // A stream into another container is drawn before it (see above); any other spill, whole.
        drawSpillStream(b.vessel, px, tableY + 6f, ppm, t, quality, if (b.job != null) SpillPart.Stream else SpillPart.Whole)
        // Over this container's liquid: the splash of whoever is pouring into it.
        for (src in bench.vessels) if (src.job?.target === b.vessel) drawSpillStream(src.vessel, pivotPx(src, w, ppm), tableY + 6f, ppm, t, quality, SpillPart.Splash)
    }
    // Drops falling off the table's edge.
    for (drips in listOf(bench.drips, bench.tableDrips)) for (k in 0 until drips.count) {
        drawLine(Color(0xFFCFE6E6).copy(alpha = 0.55f), Offset(drips.x[k], drips.y[k] - 9f), Offset(drips.x[k], drips.y[k]), 2.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(Color(0xFFE8F3F3).copy(alpha = 0.8f), 2.6f, Offset(drips.x[k], drips.y[k]))
    }
    // Someone sipping: the scene dims and the container is brought up to the eye, over everything.
    for (b in bench.vessels) b.sip?.let { job ->
        val view = job.drinker.viewpoint
        drawSipOverlay(job.progress, view.dim, view.vignette)
        drawVesselAt(b.vessel, styleOf(art, b), job.pose, ppm, t, quality)
    }
}

/**
 * What a recording shows: the coffee jar's wheel opens and the jar pours into the mug (it slides over, lifts, tips, pours and goes home), the tumbler waters the thirsty fern and half a day goes by, the kerosene is tipped
 * toward the flame, the jug of milk is knocked over, then the mug, its foot wet with coffee, is set to stand a while and slid aside, which leaves the ring of its foot on the wood, and is sipped from in first person.
 */
fun scriptedBench(bench: LiquidBench, t: Float) {
    fun crossed(at: Float) = bench.scriptT < at && t >= at
    if (crossed(0.4f)) bench.toggleWheel(5)
    if (crossed(1.8f)) bench.act(5, bench.actionsFor(5).first { it.id == "pour:2:top" })
    // The tumbler of water waters the thirsty fern, and half a day goes by.
    if (crossed(11f)) bench.act(3, bench.actionsFor(3).first { it.id == "pour:6:top" })
    if (crossed(17.5f)) bench.waitHours(12f)
    if (crossed(19f)) { bench.select(0); bench.knock(-1f) }
    if (crossed(21f)) { bench.select(4); bench.knock(1f) }
    if (crossed(22.5f)) bench.vessels[2].vessel.wetBottom(Fluid.Coffee, 1f)
    if (t in 24f..25.1f) bench.vessels[2].x = 0.47f + 0.035f * ((t - 24f) / 1.1f).coerceIn(0f, 1f)
    if (crossed(26.2f)) { bench.select(2); bench.holdSip(true) }
    if (crossed(29.5f)) bench.holdSip(false)
    bench.scriptT = t
}

/** Draws [image] stretched to fill [target], painted [tint]. */
private fun DrawScope.drawStretched(image: androidx.compose.ui.graphics.ImageBitmap, target: Rect, tint: Color) {
    drawArt(image, target.topLeft, 1f, tint, scaleX = target.width / image.width, scaleY = target.height / image.height)
}

/** The fern in a pot, growing out of the soil at ([feetX], [feetY]): as grown, thirsty or well as the plant is; [potHeight] is how tall the pot is on screen. */
@OptIn(br.com.faru.boitata.core.AdvancedBoitataApi::class)
private fun DrawScope.drawFernIn(planter: Planter, feetX: Float, feetY: Float, potHeight: Float, t: Float) {
    val plant = planter.patch.plants[0]
    val species = planter.patch.species[0]
    val bend = 0.04f * kotlin.math.sin(t * 0.9f)
    drawPlant(plant, species, 1f, feetX, feetY, bend) {
        val green = androidx.compose.ui.graphics.lerp(Color(0xFF3E7A36), Color(0xFF8A7A3A), plant.stress.coerceIn(0f, 1f))
        for (k in -3..3) {
            val angle = k * 0.34f
            val len = potHeight * (1.25f - 0.1f * kotlin.math.abs(k))
            val tipX = feetX + kotlin.math.sin(angle) * len * 0.8f
            val tipY = feetY - kotlin.math.cos(angle) * len
            val frond = androidx.compose.ui.graphics.Path().apply {
                moveTo(feetX, feetY)
                quadraticTo(feetX + kotlin.math.sin(angle) * len * 0.25f, feetY - len * 0.65f, tipX, tipY)
            }
            drawPath(frond, green, style = androidx.compose.ui.graphics.drawscope.Stroke(2.4f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            // Leaflets in pairs down the frond.
            for (j in 1..6) {
                val u = j / 7f
                val px = feetX + (tipX - feetX) * u * (1f - 0.1f * u); val py = feetY + (tipY - feetY) * u
                val leaf = potHeight * 0.16f * (1f - u * 0.6f)
                drawLine(green, Offset(px, py), Offset(px - leaf, py - leaf * 0.5f), 1.8f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(green, Offset(px, py), Offset(px + leaf, py - leaf * 0.5f), 1.8f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
    }
}
