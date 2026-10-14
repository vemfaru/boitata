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
import kotlin.math.abs

/** A container standing on the bench: the simulation, how it is drawn, and where along the table it stands (a share of the width). */
class BenchVessel(val vessel: LiquidVessel, val style: VesselStyle, var x: Float, val fill: Float) {
    internal var lastVelocity = 0f
    internal var lastX = x
    internal var lastSpilled = 0f
}

/**
 * The liquids bench: a tumbler of water, a bottle of wine, a jug of milk and a glass of kerosene on a glossy table, in front of a lit wall. Everything is state held here so the controls can reach it: [select] picks
 * a vessel (tap it), [pour], [drop] (a drop falls in and ripples spread), [knock] (tip it over), [refill]. Dragging a vessel along the table pushes it, and the liquid lags and sloshes.
 */
class LiquidBench {
    val vessels: List<BenchVessel> = listOf(
        BenchVessel(LiquidVessel(0.07f, 0.10f, Fluid.Water, 0.6f), VesselStyle(VesselShape.Tumbler), 0.2f, 0.6f),
        BenchVessel(LiquidVessel(0.07f, 0.17f, Fluid.Wine, 0.55f), VesselStyle(VesselShape.Bottle, glass = Color(0xFFBFD8C4)), 0.4f, 0.55f),
        BenchVessel(LiquidVessel(0.11f, 0.14f, Fluid.Milk, 0.7f), VesselStyle(VesselShape.Jug), 0.62f, 0.7f),
        BenchVessel(LiquidVessel(0.07f, 0.10f, Fluid.Kerosene, 0.75f), VesselStyle(VesselShape.Tumbler, glass = Color(0xFFE9E3C8)), 0.82f, 0.75f),
    )
    var selected by mutableIntStateOf(0)

    /** How many meters one share of the table's width is, set by the drawing, to turn a drag into an acceleration. */
    internal var metersPerShare = 1f
    internal var scriptT = -1f
    internal var ppm = 1f
    internal var tableY = 0f
    internal var width = 1f
    internal var height = 1f

    /** The puddles that spilled liquid has made, and the fire that spreads through the ones that burn. */
    val puddles = ArrayList<Puddle>()
    internal val world = FireWorld()
    private val bodies = HashMap<Puddle, FireBody>()
    internal fun body(p: Puddle): FireBody? = bodies[p]
    private var elapsed = 0f

    init {
        // A flame on the left of the bench: a spill that reaches it, if it burns, catches.
        world.add(FireBody("flame", Material.Campfire) { BodyGeometry(width * 0.1f, tableY, height * 0.14f, height * 0.3f) })
        world.ignite("flame")
    }

    /** Sets light to the spilled liquid, if it burns (the nearest puddle to the picked vessel). */
    fun light() {
        val v = vessels[selected]
        val p = puddles.filter { it.fluid.flammable }.minByOrNull { abs(it.x - (width * v.x)) } ?: return
        bodies[p]?.let { world.ignite(it.id) }
    }

    fun select(i: Int) { selected = i.coerceIn(0, vessels.size - 1) }
    fun pour() { vessels[selected].vessel.pour(vessels[selected].vessel.width * vessels[selected].vessel.height * 0.14f) }
    fun drop() { vessels[selected].vessel.drop(0.3f + 0.4f * ((vessels[selected].vessel.surface.energy() * 7f) % 1f), 0.9f) }
    /** Tips the picked vessel over, toward the way you say (+1 right, -1 left), or toward the middle of the table by default. */
    fun knock(direction: Float = if (vessels[selected].x < 0.5f) 1f else -1f) { vessels[selected].vessel.knock(9f, direction) }
    fun refill() { for (b in vessels) { b.vessel.reset(b.fill); b.lastSpilled = 0f }; puddles.clear(); for (body in bodies.values) world.extinguish(body.id); bodies.clear() }

    internal fun step(dt: Float) {
        // (called every frame by the scene)
        for (b in vessels) {
            // A drag moves the vessel; the liquid feels the change in its speed.
            val v = (b.x - b.lastX) * metersPerShare / dt
            b.vessel.accelerate(-(v - b.lastVelocity) / dt * 0.0f + (v - b.lastVelocity) / dt)
            b.lastVelocity = v; b.lastX = b.x
            b.vessel.step(dt)
            // What ran over the lip lands on the table and joins a puddle there (or starts one).
            val ran = b.vessel.spilled - b.lastSpilled
            b.lastSpilled = b.vessel.spilled
            if (ran > 0f && ppm > 1f) {
                val v = b.vessel
                val s = if (v.body.angle >= 0f) 1f else -1f
                val landing = width * b.x + v.width * ppm / 2f + s * (v.lipX() + Spill.reach(v.lipHeight(), v.jetSpeed)) * ppm
                val puddle = puddles.firstOrNull { it.fluid === v.fluid && abs(it.x - landing) < it.radius * ppm + 16f } ?: Puddle(landing, v.fluid).also { p ->
                    puddles += p
                    if (v.fluid.flammable) {
                        val id = "puddle${puddles.size}-${elapsed}"
                        bodies[p] = world.add(FireBody(id, Material.SpilledOil) { BodyGeometry(p.x, tableY, 2f * p.radius * ppm, height * 0.14f) })
                    }
                }
                puddle.add(ran * v.width)
            }
        }
        for (p in puddles) { p.step(dt); bodies[p]?.let { p.burnt = it.consumed } }
        elapsed += dt
        world.step(dt, elapsed, Wind.Calm)
    }
}

private const val TABLE = 0.74f

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
                onDragStart = { at -> grabbed = pick(bench, at.x / this.size.width); if (grabbed >= 0) bench.select(grabbed) },
                onDragEnd = { grabbed = -1 }, onDragCancel = { grabbed = -1 },
            ) { change, delta -> if (grabbed >= 0) { change.consume(); bench.vessels[grabbed].x = (bench.vessels[grabbed].x + delta.x / this.size.width).coerceIn(0.08f, 0.92f) } }
        }.pointerInput(bench) {
            detectTapGestures { at -> val i = pick(bench, at.x / this.size.width); if (i >= 0) { bench.select(i); bench.vessels[i].vessel.drop(((at.x / this.size.width) - bench.vessels[i].x) / 0.14f + 0.5f, 0.7f) } }
        },
    ) {
        Spacer(Modifier.fillMaxSize().drawBehind { drawBench(art, bench, behind, seconds(), quality()) })
    }
}

private fun pick(bench: LiquidBench, share: Float): Int = bench.vessels.indices.minByOrNull { abs(bench.vessels[it].x - share) }?.takeIf { abs(bench.vessels[it].x - share) < 0.1f } ?: -1

private fun DrawScope.drawBench(art: Sprites, bench: LiquidBench, behind: androidx.compose.ui.graphics.layer.GraphicsLayer, t: Float, quality: Quality) {
    val w = size.width; val h = size.height
    val tableY = h * TABLE
    val ppm = h * 2.6f
    bench.metersPerShare = w / ppm
    bench.ppm = ppm; bench.tableY = tableY; bench.width = w; bench.height = h
    // Everything behind the glasses, recorded once: the lit wall, a lamp with a flame, and Maria Bonita behind the table, so there is something to see through the liquid.
    behind.record(size = IntSize(w.toInt(), h.toInt())) {
        drawRect(Brush.verticalGradient(0f to Color(0xFF3A2D26), 1f to Color(0xFF1A1411)), size = Size(w, tableY))
        drawTiled(art.wall, Rect(0f, 0f, w, tableY), tile = w * 0.34f, tint = Color(0xFF4A3A30))
        val image = art.prop("maria_bonita"); val ph = h * 0.5f; val k = ph / image.height
        drawArt(image, Offset(w * 0.5f - image.width * k / 2f, tableY - ph), k, Color(0xFFC8A06A))
        drawFlame(w * 0.1f, tableY - h * 0.03f, h * 0.12f, h * 0.24f, t, 0.9f, FlamePalette.Campfire, 8L, quality)
        drawRect(Color(0xFFFFD9A0).copy(alpha = 0.5f), Offset(w * 0.9f, tableY - h * 0.32f), Size(w * 0.012f, h * 0.3f))
        drawRect(Color(0xFF14110F), Offset(w * 0.3f, tableY - h * 0.45f), Size(w * 0.012f, h * 0.4f))
    }
    drawLayer(behind)
    // The table: a dark top with a glossy sheen, and its front edge.
    drawRect(Brush.verticalGradient(0f to Color(0xFF3A2A1C), 1f to Color(0xFF1B130C), startY = tableY, endY = h), Offset(0f, tableY), Size(w, h - tableY))
    drawRect(Color(0xFFFFE2B0).copy(alpha = 0.06f), Offset(0f, tableY), Size(w, h * 0.02f))
    // Puddles on the table, and the fire on the ones that burn.
    clipRect(0f, tableY - h * 0.1f, w, h) { for (p in bench.puddles) drawPuddle(p.fluid.color, p.x, tableY + h * 0.03f, p.radius * ppm, p.burnt) }
    for (p in bench.puddles) bench.body(p)?.let { body -> if (body.status == BurnStatus.Burning) drawBurningBody(body, body.geometry().let { g -> BodyGeometry(g.feetX, tableY + h * 0.03f, g.width, g.height) }, t, Wind.Calm, quality) }
    for ((i, b) in bench.vessels.withIndex()) {
        val px = w * b.x + b.vessel.width * ppm / 2f
        // Selected: a faint pool under it.
        if (i == bench.selected) drawOval(Color(0xFFFFE9B0).copy(alpha = 0.12f), Offset(px - b.vessel.width * ppm, tableY - 4f), Size(b.vessel.width * ppm * 2f, 14f))
        // The lamp is the flame on the left: the glass throws its coloured shadow and its caustic away from it.
        drawCaustic(b.vessel, px, tableY + 8f, ppm, t, fromLeft = b.x > 0.12f, quality = quality)
        drawVessel(b.vessel, b.style, px, tableY + 6f, ppm, t, quality, behind = behind, mirrored = 0.22f)
        drawSpillStream(b.vessel, px, tableY + 6f, ppm, t, quality)
    }
}

/** What a recording shows: a drop in the water, the jug slid along the table (the milk sloshes), the tumbler knocked over, then the kerosene. */
fun scriptedBench(bench: LiquidBench, t: Float) {
    fun crossed(at: Float) = bench.scriptT < at && t >= at
    if (crossed(0.8f)) { bench.select(0); bench.drop() }
    if (crossed(1.6f)) { bench.select(1); bench.pour() }
    if (t in 2.2f..4.2f) bench.vessels[2].x = 0.62f + 0.12f * kotlin.math.sin((t - 2.2f) * 2.2f) * kotlin.math.min(1f, (t - 2.2f)) * kotlin.math.min(1f, 4.2f - t)
    if (crossed(3.6f)) { bench.select(0); bench.knock() }
    if (t in 4.0f..5.4f) bench.vessels[3].x = 0.82f - (0.82f - 0.26f) * ((t - 4.0f) / 1.4f).coerceIn(0f, 1f)
    if (crossed(5.6f)) { bench.select(3); bench.knock(-1f) }
    bench.scriptT = t
}
