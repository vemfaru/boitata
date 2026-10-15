package br.com.faru.boitata.playground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.clock.rememberSceneQuality
import br.com.faru.boitata.core.draw.sway
import br.com.faru.boitata.core.wind.Wind
import br.com.faru.boitata.core.wind.WindResponse
import br.com.faru.boitata.core.wind.rememberSceneWind
import br.com.faru.boitata.light.FlamePalette
import br.com.faru.boitata.light.LightRig
import br.com.faru.boitata.light.PointLight
import br.com.faru.boitata.light.drawCastShadows
import br.com.faru.boitata.light.drawFlame
import br.com.faru.boitata.light.drawLightBulbs
import br.com.faru.boitata.light.drawLightPools
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

private val FestaArt = listOf(
    "festa_bonfire", "festa_stones", "festa_chapel", "festa_pole", "festa_fence", "festa_balloon", "festa_corn_1", "festa_corn_2", "festa_corn_3",
    "festa_flag_1", "festa_flag_2", "festa_flag_3", "festa_lantern_1", "festa_lantern_2", "festa_lantern_3",
    "festa_firework_1", "festa_firework_2", "festa_firework_3", "festa_firework_4", "festa_sanfona", "festa_zabumba", "festa_triangle",
    "back_serra", "back_arraial",
)

private val LanternColours = listOf(Color(0xFFFF7A4A), Color(0xFFFFD34A), Color(0xFF7ACBFF))
private val FlagColours = listOf(Color(0xFFE5483A), Color(0xFFF2C14E), Color(0xFF3FA7D6), Color(0xFF5CB85C), Color(0xFFE08ACB))

/** A rocket: when it goes up (seconds into a 14 second cycle), where it bursts (shares of the picture), in what colour. */
private class Burst(val at: Float, val x: Float, val y: Float, val colour: Color)

private val Bursts = listOf(
    Burst(1.0f, 0.30f, 0.22f, Color(0xFFFF6B5A)), Burst(3.2f, 0.62f, 0.16f, Color(0xFF7ACBFF)), Burst(5.0f, 0.48f, 0.26f, Color(0xFFFFD34A)),
    Burst(7.4f, 0.78f, 0.22f, Color(0xFF9CFFC0)), Burst(9.0f, 0.22f, 0.15f, Color(0xFFE08ACB)), Burst(11.5f, 0.55f, 0.18f, Color(0xFFFFA63C)),
)
private const val BurstCycle = 14f

/** The lights of the arraial: the bonfire, three paper lanterns, whichever rocket has just burst, and the glow inside the balloon. */
private class FestaSetup {
    val rig = LightRig(ambient = 0.05f)
    val fire = rig.add(PointLight(0f, 0f, 0f, Color(0xFFFF8A2E), 0.95f, 600f, size = 0.4f, flicker = 0.5f)) as PointLight
    val lanterns = LanternColours.map { rig.add(PointLight(0f, 0f, 0f, it, 0.7f, 300f, size = 0.12f, flicker = 0.1f)) as PointLight }
    val flash = rig.add(PointLight(0f, 0f, 0f, Color.White, 0f, 700f, size = 0.3f)) as PointLight
    val balloon = rig.add(PointLight(0f, 0f, 0f, Color(0xFFFFB36B), 0.4f, 260f, size = 0.2f, flicker = 0.3f)) as PointLight
}

/**
 * The night of São João in the arraial: a bonfire (the good fire), a couple dancing beside it, a chapel and the band, flags strung from pole to pole that flutter in the wind, paper lanterns, a balloon that climbs and
 * rockets that burst in colour. Every one of these is a **light** in the same rig, so the colours mix on whoever stands in them, every shadow swings with each light, and each burst throws a flash of its colour over the
 * whole square for as long as it lasts.
 */
@Composable
fun FestaScene(seconds: () -> Float, modifier: Modifier = Modifier.fillMaxSize(), fireworks: Boolean = true, balloons: Boolean = true, lanterns: Boolean = true, bonfire: Float = 1f) {
    val art = LocalSprites.current ?: return
    val loaded by produceState(false, art) { art.ensure(FestaArt); value = true }
    if (!loaded) return
    val quality = rememberSceneQuality()
    val sceneWind = rememberSceneWind()
    val setup = remember { FestaSetup() }
    Spacer(modifier.drawBehind { drawFestaNight(art, setup, seconds(), fireworks, balloons, lanterns, bonfire, quality(), sceneWind.wind) })
}

private fun DrawScope.drawStripFrame(strip: SpriteStrip, frame: Int, feetX: Float, feetY: Float, height: Float, colour: Color, facing: Float = 1f) {
    val width = height * strip.aspect; val k = height / strip.image.height
    withTransform({ translate(feetX, feetY); scale(facing, 1f, Offset.Zero) }) {
        drawArt(strip.image, Offset(-width / 2f, -height), k, colour, Crop((frame % strip.frames) * strip.frameWidth, 0, strip.frameWidth, strip.image.height))
    }
}

private fun DrawScope.drawFestaNight(art: Sprites, s: FestaSetup, t: Float, fireworks: Boolean, balloons: Boolean, lanterns: Boolean, bonfire: Float, quality: Quality, wind: Wind) {
    val w = size.width; val h = size.height
    val rig = s.rig
    val ground = h * 0.72f
    // The sky, the stars, the serra and the roofs of the arraial.
    drawRect(Brush.verticalGradient(0f to Color(0xFF070B1A), 1f to Color(0xFF1A2038), endY = ground), Offset.Zero, Size(w, ground))
    drawNightStars(art, t, ground * 0.7f, 0.9f, count = 70, seed = 4L)
    val serra = art.art("back_serra"); drawArt(serra, Offset(0f, ground - h * 0.3f), 1f, Color(0xFF111627), scaleX = w / serra.width, scaleY = h * 0.3f / serra.height)
    val town = art.art("back_arraial"); drawArt(town, Offset(0f, ground - h * 0.17f), 1f, Color(0xFF0D1020), scaleX = w / town.width, scaleY = h * 0.17f / town.height)
    drawRect(Color(0xFF14100E), Offset(0f, ground), Size(w, h - ground))

    // Where the lights are this instant.
    val fireX = w * 0.5f; val fireY = h * 0.86f
    s.fire.x = fireX; s.fire.y = fireY; s.fire.height = h * 0.12f * bonfire; s.fire.strength = 0.95f * bonfire; s.fire.reach = w * 0.6f
    val poleA = Offset(w * 0.2f, h * 0.36f); val poleB = Offset(w * 0.88f, h * 0.33f)
    fun stringAt(u: Float) = Offset(poleA.x + (poleB.x - poleA.x) * u, poleA.y + (poleB.y - poleA.y) * u + h * 0.07f * 4f * u * (1f - u))
    for ((i, l) in s.lanterns.withIndex()) {
        val p = stringAt(0.28f + 0.22f * i); l.enabled = lanterns
        l.x = p.x; l.y = h * 0.84f; l.height = h * 0.3f
    }
    var flashStrength = 0f; var flashColour = Color.White; var flashAt = Offset.Zero
    if (fireworks) for (b in Bursts) {
        val local = (t % BurstCycle) - b.at
        if (local in 0f..2.4f) { val e = exp(-local * 2.4f) * 0.95f; if (e > flashStrength) { flashStrength = e; flashColour = b.colour; flashAt = Offset(w * b.x, h * 0.8f) } }
    }
    s.flash.strength = flashStrength; s.flash.color = flashColour; s.flash.x = flashAt.x; s.flash.y = flashAt.y; s.flash.height = h * 0.7f
    val balloonP = ((t + 3f) % 18f) / 18f
    val balloonX = w * (0.15f + 0.7f * balloonP) + sin(t * 0.7f) * w * 0.012f + wind.direction * wind.speed * w * 0.08f * balloonP
    val balloonY = h * (0.8f - 0.95f * balloonP)
    s.balloon.enabled = balloons; s.balloon.x = balloonX; s.balloon.y = h * 0.84f; s.balloon.height = (h * 0.84f - balloonY).coerceAtLeast(h * 0.1f)
    s.balloon.strength = 0.4f * (1f - balloonP)
    rig.faceShadows = quality != Quality.Low
    drawLightPools(rig, t, bulbs = false)

    val dark = Color(0xFF2A2442); val lit = Color(0xFFF2D9A8)
    // The chapel and the fence at the back.
    val chapel = art.art("festa_chapel"); val chH = h * 0.4f; val chW = chH * chapel.width / chapel.height
    drawArt(chapel, Offset(w * 0.1f - chW / 2f, ground + h * 0.02f - chH), chH / chapel.height, rig.shade(dark, lit, w * 0.1f, ground + h * 0.05f, t, chH))
    val fence = art.art("festa_fence"); drawArt(fence, Offset(0f, ground + h * 0.0f - h * 0.05f), 1f, rig.shade(dark, Color(0xFFB89A74), w * 0.5f, ground + h * 0.03f, t, 0f), scaleX = w / fence.width, scaleY = h * 0.06f / fence.height)

    // The stones and the fire.
    val stones = art.art("festa_stones"); val stW = h * 0.34f
    drawArt(stones, Offset(fireX - stW / 2f, fireY - stW * stones.height / stones.width * 0.55f), stW / stones.width, Color(0xFF6B5A48))
    val logs = art.art("festa_bonfire"); val lgH = h * 0.2f * bonfire; val lgW = lgH * logs.width / logs.height
    drawArt(logs, Offset(fireX - lgW / 2f, fireY - lgH), lgH / logs.height, Color(0xFF3A2416))
    if (bonfire > 0.05f) drawFlame(fireX, fireY - lgH * 0.25f, h * 0.18f * bonfire, h * 0.36f * bonfire, t, 0.95f * bonfire, FlamePalette.Campfire, 11L, quality)

    // The band and the couple, lit by whatever lights reach them, each throwing a shadow from every light.
    data class Cast(val x: Float, val y: Float, val height: Float, val draw: DrawScope.(Color) -> Unit)
    val casts = ArrayList<Cast>()
    for ((i, name) in listOf("festa_sanfona", "festa_zabumba", "festa_triangle").withIndex()) {
        val img = art.art(name); val ph = h * 0.27f; val pw = ph * img.width / img.height; val x = w * (0.72f + 0.085f * i); val y = h * (0.9f + 0.01f * (i % 2))
        casts += Cast(x, y, ph) { c -> drawArt(img, Offset(-pw / 2f, -ph), ph / img.height, c) }
    }
    val xote = art.strips.getValue("couple_xote")
    val coupleX = w * 0.36f + sin(t * 0.5f) * w * 0.02f; val coupleY = h * 0.92f; val coupleH = h * 0.32f
    casts += Cast(coupleX, coupleY, coupleH) { c -> drawStripFrame(xote, floor(t * 5f).toInt(), 0f, 0f, coupleH, c) }
    for (c in casts) drawCastShadows(rig, c.x, c.y, c.height, t, 0.85f, quality) { col -> c.draw(this, col) }
    for (c in casts.sortedBy { it.y }) {
        val colour = rig.shade(dark, lit, c.x, c.y - c.height * 0.3f, t, c.height)
        withTransform({ translate(c.x, c.y) }) { c.draw(this, colour) }
    }
    // Corn at the edges, swaying.
    for ((i, x) in listOf(0.04f, 0.1f, 0.96f).withIndex()) {
        val corn = art.art("festa_corn_${i % 3 + 1}"); val ch = h * 0.3f; val cw = ch * corn.width / corn.height; val cy = h * (0.96f + 0.01f * i)
        val bend = WindResponse.Plant.bend(wind, t, x, 0.4f + i)
        sway(w * x, cy, bend, maxLean = 0.1f) { drawArt(corn, Offset(w * x - cw / 2f, cy - ch), ch / corn.height, rig.shade(dark, Color(0xFFB4C26B), w * x, cy, t, ch)) }
    }

    // The poles and the string of flags between them, each flag swaying in the wind in its own time; the lanterns hang from it.
    val pole = art.art("festa_pole")
    for (p in listOf(poleA, poleB)) { val ph = h * 0.55f; val pw = ph * pole.width / pole.height; drawArt(pole, Offset(p.x - pw / 2f, p.y - h * 0.02f), ph / pole.height, Color(0xFF2A1D14)) }
    for (i in 0..40) { val a = stringAt(i / 40f); val b = stringAt((i + 1) / 40f); drawLine(Color(0xFF3A2A1C), a, b, 1.5f) }
    for (i in 0 until 14) {
        val u = (i + 0.5f) / 14f; val at = stringAt(u)
        val flag = art.art("festa_flag_${i % 3 + 1}"); val fh = h * 0.06f; val fw = fh * flag.width / flag.height
        val bend = WindResponse.Plant.bend(wind, t, u, i * 0.37f)
        val glow = rig.level(at.x, h * 0.8f, t, 0f)
        sway(at.x, at.y, bend, maxLean = 0.5f) { drawArt(flag, Offset(at.x - fw / 2f, at.y), fh / flag.height, lerp(FlagColours[i % FlagColours.size].copy(alpha = 0.55f), FlagColours[i % FlagColours.size], glow.coerceIn(0f, 1f))) }
    }
    if (lanterns) for ((i, c) in LanternColours.withIndex()) {
        val at = stringAt(0.28f + 0.22f * i); val lan = art.art("festa_lantern_${i + 1}"); val lh = h * 0.1f; val lw = lh * lan.width / lan.height
        val bend = WindResponse.Plant.bend(wind, t, 0.3f + i * 0.2f, i * 0.9f)
        sway(at.x, at.y, bend, maxLean = 0.25f) {
            val mid = Offset(at.x, at.y + h * 0.05f + lh * 0.4f)
            drawCircle(Brush.radialGradient(0f to c.copy(alpha = 0.6f), 1f to Color.Transparent, center = mid, radius = lh * 1.4f), radius = lh * 1.4f, center = mid)
            drawArt(lan, Offset(at.x - lw / 2f, at.y + h * 0.05f), lh / lan.height, c)
            drawLine(Color(0xFF3A2A1C), at, Offset(at.x, at.y + h * 0.05f), 1.2f)
        }
    }

    // The balloon, a lit paper bubble climbing the sky.
    if (balloons && balloonY > -h * 0.2f) {
        val b = art.art("festa_balloon"); val bh = h * 0.16f; val bw = bh * b.width / b.height
        val mid = Offset(balloonX, balloonY - bh * 0.5f)
        drawCircle(Brush.radialGradient(0f to Color(0xFFFFB36B).copy(alpha = 0.5f), 1f to Color.Transparent, center = mid, radius = bh * 1.2f), radius = bh * 1.2f, center = mid)
        sway(balloonX, balloonY, sin(t * 0.9f) * 0.2f, maxLean = 0.15f) { drawArt(b, Offset(balloonX - bw / 2f, balloonY - bh), bh / b.height, Color(0xFFFFC98A)) }
    }

    // Rockets: a spark climbing, then a burst that opens, hangs and falls, in the colour of its light.
    if (fireworks) for ((k, b) in Bursts.withIndex()) {
        val local = (t % BurstCycle) - b.at
        val bx = w * b.x; val by = h * b.y
        if (local in -0.9f..0f) { val u = (local + 0.9f) / 0.9f; val y = h * 0.8f + (by - h * 0.8f) * u; drawLine(Color(0xFFFFE9B0).copy(alpha = 0.8f), Offset(bx, y), Offset(bx, y + h * 0.05f), 2.5f); drawCircle(Color.White, 3f, Offset(bx, y)) }
        if (local in 0f..2.4f) {
            val img = art.art("festa_firework_${k % 4 + 1}")
            val open = 1f - exp(-local * 3.5f); val size = h * 0.4f * (0.2f + 0.8f * open); val a = (1f - local / 2.4f).coerceIn(0f, 1f)
            drawCircle(Brush.radialGradient(0f to b.colour.copy(alpha = 0.5f * a), 1f to Color.Transparent, center = Offset(bx, by), radius = size), radius = size, center = Offset(bx, by))
            drawArt(img, Offset(bx - size / 2f, by - size / 2f + local * h * 0.03f), size / img.width, b.colour, alpha = a * a)
        }
    }
    drawLightBulbs(rig, t)
}

/** The festa with its controls: what is lit, how big the fire is. The wind (set at the top) moves the flags, the balloon and the corn. */
@Composable
internal fun FestaDemo(seconds: () -> Float) {
    var fireworks by remember { mutableStateOf(true) }
    var balloons by remember { mutableStateOf(true) }
    var lanterns by remember { mutableStateOf(true) }
    var bonfire by remember { mutableFloatStateOf(1f) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { FestaScene(seconds, fireworks = fireworks, balloons = balloons, lanterns = lanterns, bonfire = bonfire) }
        Controls {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = fireworks, onClick = { fireworks = !fireworks }, label = { Text("Fireworks") })
                FilterChip(selected = balloons, onClick = { balloons = !balloons }, label = { Text("Balloon") })
                FilterChip(selected = lanterns, onClick = { lanterns = !lanterns }, label = { Text("Lanterns") })
            }
            Labelled("Bonfire", bonfire, 0f..1.4f) { bonfire = it }
            Text("Every flame is a light: the bonfire, three paper lanterns, the balloon and each rocket as it bursts. Colours mix on whoever stands in them, each light throws its own shadow, and a burst floods the square with its colour. Change the wind above: the flags, the balloon and the corn follow it.", color = controlText())
        }
    }
}
