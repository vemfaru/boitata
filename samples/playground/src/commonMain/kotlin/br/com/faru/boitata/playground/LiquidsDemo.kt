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

    fun select(i: Int) { selected = i.coerceIn(0, vessels.size - 1) }
    fun pour() { vessels[selected].vessel.pour(vessels[selected].vessel.width * vessels[selected].vessel.height * 0.14f) }
    fun drop() { vessels[selected].vessel.drop(0.3f + 0.4f * ((vessels[selected].vessel.surface.energy() * 7f) % 1f), 0.9f) }
    fun knock() { val v = vessels[selected].vessel; v.knock(9f, if (vessels[selected].x < 0.5f) 1f else -1f) }
    fun refill() { for (b in vessels) b.vessel.reset(b.fill) }

    internal fun step(dt: Float) {
        for (b in vessels) {
            // A drag moves the vessel; the liquid feels the change in its speed.
            val v = (b.x - b.lastX) * metersPerShare / dt
            b.vessel.accelerate(-(v - b.lastVelocity) / dt * 0.0f + (v - b.lastVelocity) / dt)
            b.lastVelocity = v; b.lastX = b.x
            b.vessel.step(dt)
        }
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
    for ((i, b) in bench.vessels.withIndex()) {
        val px = w * b.x + b.vessel.width * ppm / 2f
        // Selected: a faint pool under it.
        if (i == bench.selected) drawOval(Color(0xFFFFE9B0).copy(alpha = 0.12f), Offset(px - b.vessel.width * ppm, tableY - 4f), Size(b.vessel.width * ppm * 2f, 14f))
        drawVessel(b.vessel, b.style, px, tableY + 6f, ppm, t, quality, behind = behind, mirrored = 0.22f)
    }
}

/** What a recording shows: a drop in the water, the jug slid along the table (the milk sloshes), the tumbler knocked over, then the kerosene. */
fun scriptedBench(bench: LiquidBench, t: Float) {
    fun crossed(at: Float) = bench.scriptT < at && t >= at
    if (crossed(0.8f)) { bench.select(0); bench.drop() }
    if (crossed(1.6f)) { bench.select(1); bench.pour() }
    if (t in 2.2f..4.2f) bench.vessels[2].x = 0.62f + 0.12f * kotlin.math.sin((t - 2.2f) * 2.2f) * kotlin.math.min(1f, (t - 2.2f)) * kotlin.math.min(1f, 4.2f - t)
    if (crossed(4.8f)) { bench.select(0); bench.knock() }
    if (crossed(7.2f)) { bench.select(3); bench.knock() }
    bench.scriptT = t
}
