package br.com.faru.boitata.playground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import br.com.faru.boitata.core.Perspective
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.clock.rememberSceneQuality
import br.com.faru.boitata.core.wind.Wind
import br.com.faru.boitata.core.wind.rememberSceneWind
import br.com.faru.boitata.light.FlamePalette
import br.com.faru.boitata.light.LightRig
import br.com.faru.boitata.light.PointLight
import br.com.faru.boitata.light.drawCastShadows
import br.com.faru.boitata.light.drawFlame
import br.com.faru.boitata.light.drawFlameTrail
import br.com.faru.boitata.light.drawLightPools
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private val ChaseArt = listOf("burn_ember_1", "burn_ember_2", "burn_ember_3")

private class ChaseSetup {
    val rig = LightRig(ambient = 0.06f)
    val serpent = rig.add(PointLight(0f, 0f, 0f, Color(0xFFFF9A3A), 0.95f, 700f, size = 0.5f, flicker = 0.4f)) as PointLight
}

/** An animal that bolts across the wood: when it starts and ends (seconds), the way it goes (1 right, -1 left), how deep in the wood (0 far, 1 near) and how tall. */
private class Bolt(val strip: String, val from: Float, val to: Float, val dir: Float, val depth: Float, val share: Float, val speed: Float)

private val Bolts = listOf(
    Bolt("lobo_run", 1.5f, 6.5f, -1f, 0.35f, 0.2f, 3.4f), Bolt("onca_run", 5.0f, 9.5f, 1f, 0.6f, 0.2f, 3.2f), Bolt("tamandua_flee", 7.5f, 12.5f, -1f, 0.85f, 0.16f, 2.3f),
)

/** The point where the hunter is at [t] (a loop through the wood that goes back and forth in depth), as shares: x across, depth 0 far to 1 near. Slows to a stagger once the serpent closes in. */
private fun chasePath(t: Float, into: FloatArray) {
    val tau = if (t < 11f) t else 11f + (t - 11f) * 0.15f
    into[0] = 0.5f + 0.4f * sin(0.55f * tau - 0.5f)
    into[1] = 0.5f + 0.5f * sin(0.33f * tau + 1f)
}

/**
 * **A Fuga.** The hunter runs through the wood with the guardian's fire on his heels: the serpent follows the very path he took, a flame chain that bends in depth (small and high far away, big and low near), then loops round
 * and closes on him. Animals bolt out of its way. The picture never moves: depth alone, the size and height of everything at each distance, is what makes the wood run past. [rain] wets the night.
 */
@Composable
fun ChaseScene(seconds: () -> Float, modifier: Modifier = Modifier.fillMaxSize(), rain: Float = 0f) {
    val art = LocalSprites.current ?: return
    val loaded by produceState(false, art) { art.ensure(ChaseArt); value = true }
    if (!loaded) return
    val quality = rememberSceneQuality()
    val sceneWind = rememberSceneWind()
    val setup = remember { ChaseSetup() }
    Spacer(modifier.drawBehind { drawChase(art, setup, seconds(), rain, quality(), sceneWind.wind) })
}

private fun DrawScope.stripFrame(strip: SpriteStrip, frame: Int, height: Float, facing: Float, colour: Color) {
    val width = height * strip.aspect; val k = height / strip.image.height
    withTransform({ scale(facing, 1f, Offset.Zero) }) { drawArt(strip.image, Offset(-width / 2f, -height), k, colour, Crop((frame % strip.frames) * strip.frameWidth, 0, strip.frameWidth, strip.image.height)) }
}

private fun DrawScope.drawChase(art: Sprites, s: ChaseSetup, t: Float, rain: Float, quality: Quality, wind: Wind) {
    val w = size.width; val h = size.height
    val rig = s.rig
    val day = drawWoodsBackdrop(art, t, 23.5f)
    val cv = cover()
    drawArt(art.mid, Offset(cv.x, cv.y), cv.scale, lerp(Color(0xFF0A1410), Color(0xFF1B2A22), 0.3f), alpha = 0.9f)
    val top = h * 0.64f; val bottom = h * 0.96f
    val perspective = Perspective(frontEdgeY = bottom, backEdgeY = top, depth = 6f, eyeBack = 3f, eyeHeight = 2.5f, pitchDegrees = 28f, referenceY = bottom - 0.3f * (bottom - top))
    fun yAt(depth: Float) = top + (bottom - top) * depth
    fun xAt(share: Float) = w * (0.06f + 0.88f * share)
    val person = h * 0.34f
    val p = FloatArray(2)
    chasePath(t, p)
    val hx = xAt(p[0]); val hy = yAt(p[1]); val hs = perspective.scale(hy)
    val heading = run { val q = FloatArray(2); chasePath(t + 0.05f, q); if (q[0] >= p[0]) 1f else -1f }

    // The serpent: a chain of points along the hunter's own path, a little behind, then looping round him.
    val count = 26
    val pts = FloatArray(count * 2); val scales = FloatArray(count) { 1f }; val vis = FloatArray(count) { 1f }
    val ring = ((t - 9f) / 2f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    val q = FloatArray(2)
    for (k in 0 until count) {
        chasePath(t - 0.55f - k * 0.14f, q)
        var x = xAt(q[0]); var y = yAt(q[1]) - person * 0.35f * perspective.scale(yAt(q[1])) * (0.4f + 0.6f * (1f - k / count.toFloat()))
        if (ring > 0f) {
            val a = (t - 9f) * 2.6f + k * 0.28f; val r = h * 0.2f * (1f - 0.45f * ((t - 9f) / 6f).coerceIn(0f, 1f)) * hs
            val rx = hx + cos(a) * r * 1.4f; val ry = hy - person * hs * 0.45f + sin(a) * r * 0.55f
            x += (rx - x) * ring; y += (ry - y) * ring
        }
        pts[k * 2] = x; pts[k * 2 + 1] = y
        scales[k] = perspective.scale((y + person * 0.3f).coerceIn(top, bottom)).coerceIn(0.5f, 1.0f)
    }
    s.serpent.x = pts[0]; s.serpent.y = (pts[1] + person * 0.35f).coerceIn(top, bottom); s.serpent.height = h * 0.12f
    rig.surfaces.clear()
    drawLightPools(rig, t, bulbs = false)

    val dark = Color(0xFF1A2420); val lit = Color(0xFFF2D9A8)
    // The hunter and the animals, far to near, each throwing a shadow away from the serpent's light.
    class Body(val y: Float, val x: Float, val height: Float, val draw: DrawScope.(Color) -> Unit, val flames: Boolean = false)
    val bodies = ArrayList<Body>()
    val hunterH = person * hs
    val stage = when { t < 4f -> "cacador_run"; t < 10f -> "cacador_burning"; else -> "cacador_panic" }
    val hunterStrip = art.strips.getValue(stage)
    val step = floor(t * (if (t < 11f) 7f else 2.5f)).toInt()
    bodies += Body(hy, hx, hunterH, { c -> stripFrame(hunterStrip, step, hunterH, heading * -1f, c) }, flames = t > 3.5f)
    for (b in Bolts) {
        if (t < b.from || t > b.to) continue
        val u = (t - b.from) / (b.to - b.from)
        val x = if (b.dir > 0) -0.1f + 1.2f * u else 1.1f - 1.2f * u
        val by = yAt(b.depth); val bs = perspective.scale(by); val bh = h * b.share * bs
        val strip = art.strips.getValue(b.strip)
        bodies += Body(by, w * x, bh, { c -> stripFrame(strip, floor(t * 9f).toInt(), bh, b.dir, c) })
    }
    bodies.sortBy { it.y }
    for (b in bodies) drawCastShadows(rig, b.x, b.y, b.height, t, 0.8f, quality) { c -> b.draw(this, c) }
    // What is behind the hunter in depth is drawn before him, the rest after, so the serpent passes through the layers.
    val behind = (0 until count).filter { pts[it * 2 + 1] + person * 0.3f < hy }
    fun trail(range: IntRange) { if (!range.isEmpty()) drawFlameTrail(pts, count, h * 0.032f, t, FlamePalette.Boitata, 21L, quality, scales, vis, range) }
    trail(0 until count)
    for (b in bodies) {
        val c = rig.shade(dark, lit, b.x, b.y - b.height * 0.3f, t, b.height)
        withTransform({ translate(b.x, b.y) }) { b.draw(this, c) }
        if (b.flames) {
            val hh = b.height
            drawFlame(b.x, b.y - hh * 0.25f, hh * 0.22f, hh * 0.5f, t, 0.9f, FlamePalette.Campfire, 31L, quality)
            drawFlame(b.x + heading * hh * 0.05f, b.y - hh * 0.75f, hh * 0.12f, hh * 0.28f, t, 0.9f, FlamePalette.Campfire, 32L, quality)
        }
    }
    @Suppress("UNUSED_VARIABLE") val unused = behind
    drawArt(art.near, Offset(cv.x, cv.y), cv.scale, Color(0xFF060A08), alpha = 0.92f)
    // Rain, once the weather turns (and sooner if the viewer asks for it).
    val rainNow = (rain + ((t - 6f) / 4f).coerceIn(0f, 0.5f)).coerceIn(0f, 1f)
    if (rainNow > 0.02f) {
        val n = (160 * rainNow).toInt()
        for (i in 0 until n) {
            val fx = (i * 0.6180339f) % 1f; val fy = ((i * 0.4142136f) + t * (1.6f + (i % 5) * 0.12f)) % 1f
            val x = w * fx + fy * w * 0.05f; val y = h * fy
            drawLine(Color(0xFFCFE0F0).copy(alpha = 0.18f), Offset(x, y), Offset(x - w * 0.006f, y + h * 0.05f), 1.2f)
        }
    }
}

/** The chase with its controls: how fast it runs and how hard it rains. */
@Composable
internal fun ChaseDemo(seconds: () -> Float) {
    var speed by remember { mutableFloatStateOf(1f) }
    var rain by remember { mutableFloatStateOf(0f) }
    var origin by remember { mutableFloatStateOf(0f) }
    var anchor by remember { mutableFloatStateOf(0f) }
    val timeline: () -> Float = { (anchor + (seconds() - origin) * speed) % 16f }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { ChaseScene(timeline, rain = rain) }
        Controls {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, v) in listOf("Slow 0.5x" to 0.5f, "Real time" to 1f, "Fast 2x" to 2f)) FilterChip(selected = speed == v, onClick = { anchor = (anchor + (seconds() - origin) * speed); origin = seconds(); speed = v }, label = { Text(label) })
                FilterChip(selected = false, onClick = { origin = seconds(); anchor = 0f }, label = { Text("Run again") })
            }
            Labelled("Rain", rain, 0f..1f) { rain = it }
            Text("The picture does not move: the size and height of each thing at its depth do all the work. The serpent follows the hunter's own path through the layers, then circles in; the animals bolt out of its way.", color = Color(0xFFB7BEC8))
        }
    }
}
