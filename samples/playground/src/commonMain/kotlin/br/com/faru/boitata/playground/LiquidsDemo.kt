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
import br.com.faru.boitata.core.fluid.Drips
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs

/** A container standing on the bench: the simulation, how it is drawn, and where along the table it stands (a share of the width). */
class BenchVessel(val vessel: LiquidVessel, val style: VesselStyle, var x: Float, val fill: Float, val temperature: Float = 20f) {
    internal var lastVelocity = 0f
    internal var lastX = x
    internal var lastSpilled = 0f
}

/**
 * The liquids bench: a glass of kerosene, a bottle of wine, a mug of hot coffee, a tumbler of water and a jug of milk on a glossy table with ends, in front of a lit wall. Everything is state held here so the controls can
 * reach it: [select] picks a vessel (tap it), [pour], [pourHot], [drop] (a drop falls in and ripples spread), [knock] (tip it over), [heat], [refill]. Dragging a vessel along the table pushes it, and the liquid lags and sloshes.
 * Spilled liquid makes a puddle that spreads over the table; where it reaches an end of the table it runs off in drops that fall to the floor and make a puddle there.
 */
class LiquidBench {
    val vessels: List<BenchVessel> = listOf(
        BenchVessel(LiquidVessel(0.07f, 0.10f, Fluid.Kerosene, 0.75f), VesselStyle(VesselShape.Tumbler, glass = Color(0xFFE9E3C8)), 0.22f, 0.75f),
        BenchVessel(LiquidVessel(0.07f, 0.17f, Fluid.Wine, 0.55f), VesselStyle(VesselShape.Bottle, glass = Color(0xFFBFD8C4)), 0.38f, 0.55f),
        BenchVessel(LiquidVessel(0.08f, 0.09f, Fluid.Coffee, 0.78f), VesselStyle(VesselShape.Mug, glass = Color(0xFFEFE6D2)), 0.53f, 0.78f, temperature = 84f),
        BenchVessel(LiquidVessel(0.07f, 0.10f, Fluid.Water, 0.6f), VesselStyle(VesselShape.Tumbler), 0.68f, 0.6f),
        BenchVessel(LiquidVessel(0.11f, 0.14f, Fluid.Milk, 0.7f), VesselStyle(VesselShape.Jug), 0.78f, 0.7f),
    )
    var selected by mutableIntStateOf(2)

    /** The ends of the table, as shares of the width. */
    internal val tableLeft = 0.07f
    internal val tableRight = 0.93f

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
    internal val world = FireWorld()
    private val bodies = HashMap<Puddle, FireBody>()
    internal fun body(p: Puddle): FireBody? = bodies[p]
    private var elapsed = 0f

    init {
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
    fun pour() { val v = vessels[selected].vessel; v.pour(v.width * v.height * 0.14f) }

    /** Pours hot coffee into the picked vessel: it mixes with what is there (by volume), steams, and the pour leaves foam. */
    fun pourHot() { val v = vessels[selected].vessel; v.pour(v.width * v.height * 0.2f, temperature = 93f, foamy = true) }

    /** Heats the picked vessel's liquid by [degrees] (a hot plate under it). */
    fun heat(degrees: Float = 22f) { vessels[selected].vessel.heat(degrees) }
    fun drop() { vessels[selected].vessel.drop(0.3f + 0.4f * ((vessels[selected].vessel.surface.energy() * 7f) % 1f), 0.9f) }

    /** Tips the picked vessel over, toward the way you say (+1 right, -1 left), or toward the middle of the table by default. */
    fun knock(direction: Float = if (vessels[selected].x < 0.5f) 1f else -1f) { vessels[selected].vessel.knock(9f, direction) }
    fun refill() {
        for (b in vessels) { b.vessel.reset(b.fill, b.temperature); b.lastSpilled = 0f }
        puddles.clear(); floorPuddles.clear(); drips.clear()
        for (body in bodies.values) world.extinguish(body.id); bodies.clear()
    }

    private fun puddleAt(list: ArrayList<Puddle>, x: Float, fluid: Fluid): Puddle = list.firstOrNull { it.fluid === fluid && abs(it.x - x) < it.radius * ppm + 16f } ?: Puddle(x, fluid).also { list += it }

    internal fun step(dt: Float) {
        for (b in vessels) {
            // A drag moves the vessel; the liquid feels the change in its speed.
            val v = (b.x - b.lastX) * metersPerShare / dt
            b.vessel.accelerate((v - b.lastVelocity) / dt)
            b.lastVelocity = v; b.lastX = b.x
            b.vessel.step(dt)
            // What ran over the lip lands: on the table, and joins a puddle there (or starts one); beyond the end of the table, it falls to the floor.
            val ran = b.vessel.spilled - b.lastSpilled
            b.lastSpilled = b.vessel.spilled
            if (ran > 0f && ppm > 1f) {
                val ves = b.vessel
                val s = if (ves.body.angle >= 0f) 1f else -1f
                val landing = width * b.x + ves.width * ppm / 2f + s * (ves.lipX() + Spill.reach(ves.lipHeight(), ves.jetSpeed)) * ppm
                if (landing < width * tableLeft || landing > width * tableRight) puddleAt(floorPuddles, landing, ves.fluid).add(ran * ves.width)
                else {
                    val puddle = puddles.firstOrNull { it.fluid === ves.fluid && abs(it.x - landing) < it.radius * ppm + 16f } ?: Puddle(landing, ves.fluid).also { p ->
                        puddles += p
                        if (ves.fluid.flammable) {
                            val id = "puddle${puddles.size}-${elapsed}"
                            bodies[p] = world.add(FireBody(id, Material.SpilledOil) { BodyGeometry(p.x, tableY, 2f * p.radius * ppm, height * 0.14f) })
                        }
                    }
                    puddle.add(ran * ves.width)
                }
            }
        }
        // A puddle that has spread to an end of the table runs off it in drops, in proportion to how much of it hangs over.
        val left = width * tableLeft; val right = width * tableRight
        for (p in puddles) {
            p.step(dt); bodies[p]?.let { p.burnt = it.consumed }
            val r = p.radius * ppm
            if (r < 2f) continue
            val over = max(0f, left - (p.x - r)) + max(0f, (p.x + r) - right)
            if (over > 0f) {
                val leak = min(p.volume, p.volume * 0.9f * min(0.9f, over / (2f * r)) * dt)
                p.remove(leak)
                val edge = if (left - (p.x - r) > (p.x + r) - right) left else right
                var n = (leak / 2e-6f).toInt().coerceIn(0, 3)
                if (n == 0 && leak > 1e-8f && (elapsed * 12f).toInt() != ((elapsed - dt) * 12f).toInt()) n = 1
                repeat(n) { drips.emit(edge + (if (edge == left) -3f else 3f), tableY + height * 0.1f, leak / max(1, n), p.fluid) }
            }
        }
        // The drops fall to the floor and join a puddle there.
        drips.step(dt, floorY, height * 4.5f) { x, volume, tag -> puddleAt(floorPuddles, x, tag as? Fluid ?: Fluid.Water).add(volume) }
        for (p in floorPuddles) p.step(dt)
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
                onDragStart = { at -> grabbed = pick(bench, at.x / this.size.width); if (grabbed >= 0) bench.select(grabbed) },
                onDragEnd = { grabbed = -1 }, onDragCancel = { grabbed = -1 },
            ) { change, delta -> if (grabbed >= 0) { change.consume(); bench.vessels[grabbed].x = (bench.vessels[grabbed].x + delta.x / this.size.width).coerceIn(0.1f, 0.9f) } }
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
    // The layout: a wall behind, a table with ends whose top slopes away toward the back, an apron in front, and the floor under it.
    val topBack = h * 0.56f; val topFront = h * 0.72f; val tableY = h * 0.645f; val apron = h * 0.8f; val floorLine = h * 0.86f; val floorY = h * 0.93f
    val left = w * bench.tableLeft; val right = w * bench.tableRight
    val ppm = h * 2.0f
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
    val wood = Color(0xFF2A1C12)
    drawRect(wood, Offset(left + w * 0.01f, apron), Size(w * 0.018f, floorLine + h * 0.08f - apron))
    drawRect(wood, Offset(right - w * 0.028f, apron), Size(w * 0.018f, floorLine + h * 0.08f - apron))
    drawRect(Color(0xFF1B130C), Offset(left, topFront), Size(right - left, apron - topFront))
    drawRect(Color(0xFF0F0A06).copy(alpha = 0.6f), Offset(left, topFront), Size(right - left, h * 0.008f))
    drawRect(Brush.verticalGradient(0f to Color(0xFF3A2A1C), 1f to Color(0xFF2A1D12), startY = topBack, endY = topFront), Offset(left, topBack), Size(right - left, topFront - topBack))
    drawRect(Color(0xFFFFE2B0).copy(alpha = 0.07f), Offset(left, topBack), Size(right - left, h * 0.018f))
    // Puddles on the floor under the table.
    for (p in bench.floorPuddles) drawPuddle(p.fluid.color, p.x, floorY, p.radius * ppm * 0.9f)
    // Puddles on the table, kept to its top, and the fire on the ones that burn.
    clipRect(left, topBack, right, topFront) { for (p in bench.puddles) drawPuddle(p.fluid.color, p.x, tableY + h * 0.02f, p.radius * ppm, p.burnt) }
    for (p in bench.puddles) bench.body(p)?.let { body -> if (body.status == BurnStatus.Burning) drawBurningBody(body, body.geometry().let { g -> BodyGeometry(g.feetX, tableY + h * 0.02f, g.width, g.height) }, t, Wind.Calm, quality) }
    // The mirror image of each vessel in the glossy top.
    clipRect(left, tableY, right, topFront) {
        for (b in bench.vessels) drawVessel(b.vessel, b.style, w * b.x + b.vessel.width * ppm / 2f, tableY + 6f, ppm, t, quality, behind = behind, mirrored = 0.22f, mirrorOnly = true)
    }
    for ((i, b) in bench.vessels.withIndex()) {
        val px = w * b.x + b.vessel.width * ppm / 2f
        // Selected: a faint pool under it.
        if (i == bench.selected) drawOval(Color(0xFFFFE9B0).copy(alpha = 0.12f), Offset(px - b.vessel.width * ppm, tableY - 4f), Size(b.vessel.width * ppm * 2f, 14f))
        // The lamp is the flame on the left: the glass throws its coloured shadow and its caustic away from it.
        drawCaustic(b.vessel, px, tableY + 8f, ppm, t, fromLeft = b.x > 0.12f, quality = quality)
        drawVessel(b.vessel, b.style, px, tableY + 6f, ppm, t, quality, behind = behind)
        drawSpillStream(b.vessel, px, tableY + 6f, ppm, t, quality)
    }
    // Drops falling off the table's edge.
    val drips = bench.drips
    for (k in 0 until drips.count) {
        drawLine(Color(0xFFCFE6E6).copy(alpha = 0.55f), Offset(drips.x[k], drips.y[k] - 9f), Offset(drips.x[k], drips.y[k]), 2.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(Color(0xFFE8F3F3).copy(alpha = 0.8f), 2.6f, Offset(drips.x[k], drips.y[k]))
    }
}

/** What a recording shows: hot coffee poured into the mug (steam, foam), the jug of milk slid to the edge of the table and knocked over (it runs off the end in drops), then the kerosene tipped toward the flame. */
fun scriptedBench(bench: LiquidBench, t: Float) {
    fun crossed(at: Float) = bench.scriptT < at && t >= at
    if (crossed(0.6f)) { bench.select(2); bench.pourHot() }
    if (crossed(2.4f)) { bench.select(3); bench.drop() }
    if (t in 3.0f..4.4f) bench.vessels[4].x = 0.78f + 0.08f * ((t - 3.0f) / 1.4f).coerceIn(0f, 1f)
    if (crossed(4.8f)) { bench.select(4); bench.knock(1f) }
    if (crossed(10.2f)) { bench.select(0); bench.knock(-1f) }
    bench.scriptT = t
}
