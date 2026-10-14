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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import br.com.faru.boitata.core.FastNoise
import br.com.faru.boitata.core.Perspective
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.clock.rememberSceneQuality
import androidx.compose.runtime.mutableStateMapOf
import br.com.faru.boitata.core.depth.DepthItem
import br.com.faru.boitata.core.draw.sway
import br.com.faru.boitata.core.wind.Wind
import br.com.faru.boitata.core.wind.WindResponse
import br.com.faru.boitata.core.wind.rememberSceneWind
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
 * grows and covers what is behind. Lampião carries the light, a lampião in his free hand: move him and the lamp swings, and every shadow swings with it. Nothing here
 * recomposes while you drag; the positions are read where it draws.
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
    val swing = remember(stage) { LampSwing() }
    if (script != null && stage != null) {
        val now = seconds()
        SideEffect { script(stage, now) }
    }
    val sceneWind = rememberSceneWind()
    // Which things the wind moves: the ones that can be moved by it, until you tap them off (or tap a stiff thing on).
    val reacts = remember { mutableStateMapOf<String, Boolean>() }
    Box(modifier.onSizeChanged { size = it }.let { if (stage != null) it.depthDrag(stage, onTap = { item -> reacts[item.id] = !(reacts[item.id] ?: (Props.first { p -> p.id == item.id }.wind.kind != WindResponse.Kind.Rigid)) }) else it }) {
        Spacer(Modifier.fillMaxSize().drawBehind { if (stage != null) drawDepth(art, stage, seconds(), hour, quality(), sceneWind.wind, reacts, swing) })
    }
}

/** The things on the ground, with the size each has at the reference depth (as a share of the height) and where it starts (as shares of the ground). */
private class Prop(val id: String, val sprite: String, val heightShare: Float, val x: Float, val y: Float, val wind: WindResponse = WindResponse.Rigid)

private val Props = listOf(
    Prop("araucaria", "araucaria", 0.50f, 0.17f, 0.30f, WindResponse.Pine),
    Prop("palm", "palm", 0.46f, 0.84f, 0.22f, WindResponse.Palm),
    Prop("log", "bromeliad_log", 0.12f, 0.52f, 0.08f),
    Prop("lampiao", "lampiao", 0.34f, 0.44f, 0.58f),
    Prop("maria_bonita", "maria_bonita", 0.34f, 0.31f, 0.46f),
    Prop("cacador", "cacador", 0.34f, 0.70f, 0.34f),
    Prop("onca", "onca", 0.17f, 0.22f, 0.88f),
    Prop("capivara", "capivara", 0.14f, 0.08f, 0.64f),
    Prop("tamandua", "tamandua", 0.15f, 0.62f, 0.14f),
    Prop("tatu", "tatu", 0.07f, 0.50f, 0.86f),
    Prop("stool", "stool", 0.17f, 0.80f, 0.66f),
    Prop("crate", "crate", 0.14f, 0.66f, 0.92f),
    Prop("plant", "plant", 0.22f, 0.92f, 0.90f, WindResponse.Plant),
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

/**
 * The lamp hanging from a fist, as a pendulum of about a second a swing. Move the fist sideways and the lamp lags behind, then swings back past it and slowly settles;
 * a little sway of its own keeps it from hanging dead still. Plain fields, not state: it is stepped where it draws, once a frame, so it never recomposes anything.
 */
class LampSwing {
    private var angle = 0f
    private var spin = 0f
    private var lastX = Float.NaN
    private var lastT = 0f
    private var velocity = 0f

    /** Steps to time [t] with the fist at [x] pixels, the lamp [length] pixels long. Returns its angle in radians: 0 hanging straight down, positive swung to the right. */
    fun step(t: Float, x: Float, length: Float): Float {
        if (lastX.isNaN()) { lastX = x; lastT = t }
        val dt = (t - lastT).coerceIn(0f, 0.05f)
        if (dt > 0f && length > 0f) {
            // The fist's speed, smoothed a little: a drag arrives in uneven steps, and the lamp should feel the hand, not the mouse.
            val before = velocity
            velocity += ((x - lastX) / dt - velocity) * (dt * 20f).coerceAtMost(1f)
            // A push on the fist is a push the other way on the lamp; then its weight brings it back, and the hinge takes a little each swing.
            spin = (spin - (velocity - before) / length * 0.5f * cos(angle)).coerceIn(-MAX_SPIN, MAX_SPIN)
            spin += (-GRAVITY * sin(angle) - DAMPING * spin) * dt
            angle = (angle + spin * dt).coerceIn(-MAX_ANGLE, MAX_ANGLE)
        }
        lastX = x; lastT = t
        return angle + 0.04f * sin(t * 1.7f)
    }

    private companion object {
        const val GRAVITY = 28f      // (2π / period)²: a swing of about 1.2 s, like a lamp held at arm's length
        const val DAMPING = 1.1f
        const val MAX_SPIN = 6f
        const val MAX_ANGLE = 0.6f
    }
}

fun DrawScope.drawDepth(
    art: Sprites, stage: DepthStage, t: Float, hour: Float, quality: Quality = Quality.High, wind: Wind = Wind.Calm, reacts: Map<String, Boolean> = emptyMap(),
    swing: LampSwing = LampSwing(),
) {
    val w = size.width; val h = size.height
    val day = drawWoodsBackdrop(art, t, hour)
    fun tint(night: Long, dayColor: Long) = lerp(Color(night), Color(dayColor), day)
    // Trees, palms and plants lean in the wind; a stiff thing does not, unless you tapped it on.
    fun bendOf(item: DepthItem): Float {
        val prop = Props.first { it.id == item.id }
        val response = if (reacts[item.id] ?: (prop.wind.kind != WindResponse.Kind.Rigid)) (if (prop.wind.kind == WindResponse.Kind.Rigid) WindResponse.Pine else prop.wind) else WindResponse.Rigid
        return response.bend(wind, t, item.feetX / w, phase = item.id.length * 0.21f)
    }
    val lean = 0.12f

    // Lampião carries the light: his lamp hangs from his free fist, swings as he is moved, and lights and shades everything from its flame.
    val bearer = stage.item("lampiao")
    val bearerImage = art.prop("lampiao")
    val bearerHeight = stage.height(bearer)
    val hand = HandAnchors.getValue("lampiao")
    val fistUp = bearerHeight * (1f - hand.y) + bearer.lift * bearerHeight * 0.05f
    // The fist leans with him if you have let the wind move him.
    val fist = Offset(bearer.feetX + bearerHeight * bearerImage.width / bearerImage.height * (hand.x - 0.5f) + bendOf(bearer) * lean * fistUp, bearer.feetY - fistUp)
    val lampImage = art.prop("lampiao_lamp")
    val lampHeight = bearerHeight * Lamp.OF_HOLDER
    val lampK = lampHeight / lampImage.height
    val angle = swing.step(t, fist.x, lampHeight)
    // From the handle to the flame, turned by the swing.
    val fx = (Lamp.flame.x - Lamp.handle.x) * lampImage.width * lampK; val fy = (Lamp.flame.y - Lamp.handle.y) * lampHeight
    val lamp = Offset(fist.x + fx * cos(angle) + fy * sin(angle), fist.y - fx * sin(angle) + fy * cos(angle))
    val flicker = 0.92f + 0.08f * sin(t * 11f) + 0.04f * cos(t * 17.3f)
    val reach = w * 0.62f * stage.perspective.scale(bearer.feetY).coerceIn(0.7f, 1.4f)
    // On the ground the lamp is at his side, held a little forward, so his own shadow falls behind him.
    val ground = Offset(lamp.x, bearer.feetY + bearerHeight * 0.03f)
    val light = LightSource(lamp.x, lamp.y, Color(0xFFFFB35C), 0.95f * flicker, reach, groundX = ground.x, groundY = ground.y)
    drawCircle(Brush.radialGradient(0f to Color(0x55FFA63C), 1f to Color.Transparent, center = ground, radius = reach * 0.8f), radius = reach * 0.8f, center = ground, blendMode = BlendMode.Plus)

    val dark = tint(0xFF2A3A33, 0xFF3D5045); val lit = Color(0xFFD9A867)
    for (item in stage.ordered()) {
        val image = art.prop(Props.first { it.id == item.id }.sprite)
        val height = stage.height(item)
        val k = height / image.height
        val lifted = item.lift * height * 0.05f
        // The shadow stays on the ground while the thing lifts off it; a thing held up casts a shadow a little softer.
        drawCastShadow(light, item.feetX, item.feetY, height, 0.7f * (1f - 0.25f * item.lift), quality) { c -> drawArt(image, Offset(-image.width * k / 2f, -height), k, c) }
        val near = (1f - hypot(item.feetX - lamp.x, item.feetY - height * 0.5f - lamp.y) / light.reach).coerceIn(0f, 1f) * light.strength
        // Light falls off quickly: close to the lamp a thing is warm and bright, a few steps away it is nearly as dark as the wood.
        val colour = lerp(dark, lit, (0.2f + 0.8f * near * near).coerceAtMost(1f))
        // The shadow keeps to the ground while the thing leans.
        val bend = bendOf(item)
        sway(item.feetX, item.feetY - lifted, bend, maxLean = lean) { drawArt(image, Offset(item.feetX - image.width * k / 2f, item.feetY - height - lifted), k, colour) }
        // Depth shows in the air too: a thing far back is taken a little by the haze.
        val back = stage.depthOf(item)
        val haze = Color(0xFF1B2C38).copy(alpha = 0.4f * (back - 0.5f) * 2f)
        if (back > 0.5f) sway(item.feetX, item.feetY - lifted, bend, maxLean = lean) { drawArt(image, Offset(item.feetX - image.width * k / 2f, item.feetY - height - lifted), k, haze) }
        // His lamp hangs in front of him from the top of its handle. It is the light, so it throws no shadow of its own.
        if (item === bearer) rotate(-angle * 180f / PI.toFloat(), pivot = fist) {
            val at = Offset(fist.x - Lamp.handle.x * lampImage.width * lampK, fist.y - Lamp.handle.y * lampHeight)
            drawArt(lampImage, at, lampK, lerp(dark, lit, 0.55f))
            if (back > 0.5f) drawArt(lampImage, at, lampK, haze)
        }
    }
    // The lamp's flame, and rings of carved arcs for its glow.
    drawCircle(Brush.radialGradient(0f to Color(0xFFFFF1C2), 0.3f to Color(0xAAFFC46B), 1f to Color.Transparent, center = lamp, radius = bearerHeight * 0.2f), radius = bearerHeight * 0.2f, center = lamp, blendMode = BlendMode.Plus)
    for (r in 1..3) drawCircle(Color(0xFFF2A83B).copy(alpha = 0.3f / r), radius = bearerHeight * 0.25f * r, center = lamp, style = Stroke(2f))
}

/** What the README's GIF shows: someone drags things about, back and forward and across, and Lampião is carried round the stage with his lamp swinging. */
fun scriptedDrag(stage: DepthStage, t: Float) {
    val ground = Triple(stage.groundTop, stage.groundBottom, stage.groundRight - stage.groundLeft)
    val span = ground.second - ground.first
    fun y(u: Float) = ground.second - u * span                         // 0 is the front, 1 the back
    val cycle = (2f * PI.toFloat() * t / 8f)
    stage.item("lampiao").apply {
        // A wide loop: across the front, round to the back and home again, picked up for the long way round and set down near the front.
        val u = 0.5f - 0.5f * cos(cycle)
        feetX = stage.groundLeft + ground.third * (0.45f + 0.3f * sin(cycle)); feetY = y(0.1f + 0.6f * u)
        lift = if (u in 0.15f..0.85f) 1f else 0f
    }
    stage.item("crate").apply {
        val u = 0.5f - 0.5f * cos(cycle + 2.1f)
        feetX = stage.groundLeft + ground.third * (0.6f + 0.1f * sin(cycle)); feetY = y(0.05f + 0.7f * u)
        lift = if (u in 0.2f..0.8f) 1f else 0f
    }
}

/** The night woods behind a scene: the sky in bands with your stars, the far trees and the ground. Returns how much daylight there is (0 night, 1 day), for tinting what stands in it. */
fun DrawScope.drawWoodsBackdrop(art: Sprites, t: Float, hour: Float): Float {
    val w = size.width; val h = size.height
    val v = cover()
    val sc = v.scale
    val state = SkyModel.at(hour, SkyWeather.Clear)
    val day = state.daylight
    val horizon = v.py(1080f * ART_HORIZON)
    val bands = 5
    for (i in 0 until bands) drawRect(lerp(state.zenith, state.horizon, (i + 0.5f) / bands), Offset(0f, horizon * i / bands), Size(w, horizon / bands + 1f))
    drawRect(lerp(state.horizon, Color.Black, 0.25f), Offset(0f, horizon), Size(w, h - horizon))
    drawNightStars(art, t, horizon, state.stars)
    drawArt(art.far, Offset(v.x, v.y), sc, lerp(Color(0xFF0C1A22), Color(0xFF6F8794), day))
    drawArt(art.ground, Offset(v.x, v.y), sc, lerp(Color(0xFF0A110E), Color(0xFF2A3A30), day))
    return day
}
