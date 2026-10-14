package br.com.faru.boitata.playground

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import br.com.faru.boitata.core.FastNoise
import br.com.faru.boitata.core.Perspective
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.clock.rememberSceneQuality
import br.com.faru.boitata.core.depth.DepthItem
import br.com.faru.boitata.core.depth.DepthStage
import br.com.faru.boitata.core.sky.SkyModel
import br.com.faru.boitata.core.sky.SkyWeather
import br.com.faru.boitata.light.LightSource
import br.com.faru.boitata.light.drawCastShadow
import br.com.faru.boitata.scene.depthDrag
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A stage in the woods: things on the ground that you drag about. Drag one toward the back and it shrinks toward the horizon and tucks behind what is nearer; drag it toward you and it
 * grows and covers what is behind. The lantern is the light: move it and every shadow swings. Nothing here recomposes while you drag; the positions are read where it draws.
 *
 * [script], if given, moves the things by itself (for the README's GIF): it is called with the stage and the time, in composition, so use it only for recordings.
 */
@Composable
fun DepthScene(
    seconds: () -> Float,
    modifier: Modifier = Modifier.fillMaxSize(),
    hour: Float = 23.5f,
    script: ((DepthStage, Float) -> Unit)? = null,
) {
    val art = LocalSprites.current ?: return
    val quality = rememberSceneQuality()
    var size by remember { mutableStateOf(IntSize.Zero) }
    val stage = remember(size, art) { if (size.width == 0) null else buildStage(art, size) }
    if (script != null && stage != null) {
        val now = seconds()
        SideEffect { script(stage, now) }
    }
    Box(modifier.onSizeChanged { size = it }.let { if (stage != null) it.depthDrag(stage) else it }) {
        Spacer(Modifier.fillMaxSize().drawBehind { if (stage != null) drawDepth(art, stage, seconds(), hour, quality()) })
    }
}

/** The things on the ground, with the size each has at the reference depth (as a share of the height) and where it starts (as shares of the ground). */
private class Prop(val id: String, val sprite: String, val heightShare: Float, val x: Float, val y: Float)

private val Props = listOf(
    Prop("araucaria", "araucaria", 0.50f, 0.17f, 0.30f),
    Prop("palm", "palm", 0.46f, 0.84f, 0.22f),
    Prop("log", "bromeliad_log", 0.12f, 0.52f, 0.08f),
    Prop("figure", "figure", 0.34f, 0.46f, 0.58f),
    Prop("lantern", "lantern", 0.42f, 0.30f, 0.80f),
    Prop("stool", "stool", 0.17f, 0.76f, 0.66f),
    Prop("crate", "crate", 0.14f, 0.62f, 0.92f),
    Prop("plant", "plant", 0.22f, 0.92f, 0.90f),
)

private fun buildStage(art: Sprites, size: IntSize): DepthStage {
    val w = size.width.toFloat(); val h = size.height.toFloat()
    val top = h * (ART_HORIZON + 0.06f); val bottom = h * 0.97f
    // The ground's bottom edge is the front. The eye looks down at it from a little above and behind: a thing at the back is about 40% the size of one at the front.
    val perspective = Perspective(frontEdgeY = bottom, backEdgeY = top, depth = 6f, eyeBack = 3f, eyeHeight = 2.5f, pitchDegrees = 28f, referenceY = bottom - 0.3f * (bottom - top))
    val stage = DepthStage(perspective, w * 0.03f, top, w * 0.97f, bottom)
    for (p in Props) {
        val image = art.prop(p.sprite)
        stage.add(DepthItem(p.id, w * p.x, top + (bottom - top) * p.y, h * p.heightShare, image.width.toFloat() / image.height))
    }
    return stage
}

private fun DepthStage.item(id: String): DepthItem = items.first { it.id == id }

fun DrawScope.drawDepth(art: Sprites, stage: DepthStage, t: Float, hour: Float, quality: Quality = Quality.High) {
    val w = size.width; val h = size.height
    val v = cover()
    val sc = v.scale
    val state = SkyModel.at(hour, SkyWeather.Clear)
    val day = state.daylight
    val horizon = v.py(1080f * ART_HORIZON)
    val bands = 5
    for (i in 0 until bands) drawRect(lerp(state.zenith, state.horizon, (i + 0.5f) / bands), Offset(0f, horizon * i / bands), Size(w, horizon / bands + 1f))
    drawRect(lerp(state.horizon, Color.Black, 0.25f), Offset(0f, horizon), Size(w, h - horizon))
    val stars = FastNoise(2L)
    for (i in 0 until 90) {
        val a = state.stars * (0.35f + 0.65f * (0.5f + 0.5f * sin(t * (0.8f + stars.at(i, 2)) + i)))
        drawCircle(Color(0xFFF1E6D0).copy(alpha = a * 0.8f), 1f + stars.at(i, 3) * 1.6f, Offset(stars.at(i, 0) * w, stars.at(i, 1) * horizon * 0.8f))
    }
    fun tint(night: Long, dayColor: Long) = lerp(Color(night), Color(dayColor), day)
    drawArt(art.far, Offset(v.x, v.y), sc, tint(0xFF0C1A22, 0xFF6F8794))
    drawArt(art.ground, Offset(v.x, v.y), sc, tint(0xFF0A110E, 0xFF2A3A30))

    // The lantern is the light: it stands where you put it, and lights and shades everything from there.
    val lantern = stage.item("lantern")
    val lanternHeight = stage.height(lantern)
    val flicker = 0.92f + 0.08f * sin(t * 11f) + 0.04f * cos(t * 17.3f)
    val lamp = Offset(lantern.feetX, lantern.feetY - lanternHeight * 0.74f - lantern.lift * lanternHeight * 0.06f)
    val reach = w * 0.62f * stage.perspective.scale(lantern.feetY).coerceIn(0.7f, 1.4f)
    val light = LightSource(lamp.x, lamp.y, Color(0xFFFFB35C), 0.95f * flicker, reach, groundX = lantern.feetX, groundY = lantern.feetY)
    drawCircle(Brush.radialGradient(0f to Color(0x55FFA63C), 1f to Color.Transparent, center = Offset(lantern.feetX, lantern.feetY), radius = reach * 0.8f), radius = reach * 0.8f, center = Offset(lantern.feetX, lantern.feetY), blendMode = BlendMode.Plus)

    val dark = tint(0xFF2A3A33, 0xFF3D5045); val lit = Color(0xFFD9A867)
    for (item in stage.ordered()) {
        val image = art.prop(Props.first { it.id == item.id }.sprite)
        val height = stage.height(item)
        val k = height / image.height
        val lifted = item.lift * height * 0.05f
        // The shadow stays on the ground while the thing lifts off it; a thing held up casts a shadow a little softer.
        if (item !== lantern) drawCastShadow(light, item.feetX, item.feetY, height, 0.7f * (1f - 0.25f * item.lift), quality) { c -> drawArt(image, Offset(-image.width * k / 2f, -height), k, c) }
        val near = (1f - hypot(item.feetX - lamp.x, item.feetY - height * 0.5f - lamp.y) / light.reach).coerceIn(0f, 1f) * light.strength
        // Light falls off quickly: close to the lantern a thing is warm and bright, a few steps away it is nearly as dark as the wood.
        val colour = if (item === lantern) lerp(dark, lit, 0.55f) else lerp(dark, lit, (0.2f + 0.8f * near * near).coerceAtMost(1f))
        drawArt(image, Offset(item.feetX - image.width * k / 2f, item.feetY - height - lifted), k, colour)
        // Depth shows in the air too: a thing far back is taken a little by the haze.
        val back = stage.depthOf(item)
        if (back > 0.5f) drawArt(image, Offset(item.feetX - image.width * k / 2f, item.feetY - height - lifted), k, Color(0xFF1B2C38).copy(alpha = 0.4f * (back - 0.5f) * 2f))
    }
    // The lantern's flame, and rings of carved arcs for its glow.
    drawCircle(Brush.radialGradient(0f to Color(0xFFFFF1C2), 0.3f to Color(0xAAFFC46B), 1f to Color.Transparent, center = lamp, radius = lanternHeight * 0.16f), radius = lanternHeight * 0.16f, center = lamp, blendMode = BlendMode.Plus)
    for (r in 1..3) drawCircle(Color(0xFFF2A83B).copy(alpha = 0.3f / r), radius = lanternHeight * 0.2f * r, center = lamp, style = Stroke(2f))
}

/** What the README's GIF shows: someone drags things about, back and forward and across, and the lantern is carried round. */
fun scriptedDrag(stage: DepthStage, t: Float) {
    val ground = Triple(stage.groundTop, stage.groundBottom, stage.groundRight - stage.groundLeft)
    val span = ground.second - ground.first
    fun y(u: Float) = ground.second - u * span                         // 0 is the front, 1 the back
    val cycle = (2f * PI.toFloat() * t / 8f)
    stage.item("figure").apply {
        val u = 0.5f - 0.5f * cos(cycle)
        feetX = stage.groundLeft + ground.third * (0.2f + 0.55f * u); feetY = y(0.05f + 0.85f * u)
        lift = if (u in 0.15f..0.85f) 1f else 0f
    }
    stage.item("crate").apply {
        val u = 0.5f - 0.5f * cos(cycle + 2.1f)
        feetX = stage.groundLeft + ground.third * (0.55f + 0.1f * sin(cycle)); feetY = y(0.05f + 0.7f * u)
        lift = if (u in 0.2f..0.8f) 1f else 0f
    }
    stage.item("lantern").apply {
        feetX = stage.groundLeft + ground.third * (0.5f + 0.32f * cos(cycle * 0.5f + 3f)); feetY = y(0.25f + 0.2f * (0.5f - 0.5f * cos(cycle * 0.5f)))
        lift = 0f
    }
}
