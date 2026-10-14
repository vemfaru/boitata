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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import br.com.faru.boitata.core.Perspective
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.clock.rememberSceneQuality
import br.com.faru.boitata.core.depth.DepthItem
import br.com.faru.boitata.core.depth.DepthStage
import br.com.faru.boitata.core.draw.sway
import br.com.faru.boitata.core.wind.Wind
import br.com.faru.boitata.core.wind.WindResponse
import br.com.faru.boitata.core.wind.rememberSceneWind
import br.com.faru.boitata.light.Light
import br.com.faru.boitata.light.LightRig
import br.com.faru.boitata.light.PointLight
import br.com.faru.boitata.light.SpotLight
import br.com.faru.boitata.light.drawCastShadows
import br.com.faru.boitata.light.Wall
import br.com.faru.boitata.light.drawFaceShadows
import br.com.faru.boitata.core.light.Billboard
import br.com.faru.boitata.core.light.Optics
import br.com.faru.boitata.light.drawLightBulbs
import br.com.faru.boitata.light.drawWallLight
import androidx.compose.ui.geometry.Size
import br.com.faru.boitata.light.drawLightPools
import br.com.faru.boitata.scene.depthDrag
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The four lamps of the stage: each its own colour and kind, and where it starts (shares of the ground). */
enum class StageLamp(val label: String, val color: Color, val x: Float, val y: Float) {
    Amber("Amber lamp", Color(0xFFFFC46B), 0.14f, 0.80f),
    Blue("Blue lamp", Color(0xFF7FA8FF), 0.86f, 0.80f),
    Red("Red fire", Color(0xFFFF5A3C), 0.50f, 0.97f),
    Spot("Green spot", Color(0xFF9CFFC0), 0.50f, 0.12f),
}

private class StageProp(val id: String, val sprite: String, val heightShare: Float, val x: Float, val y: Float, val wind: WindResponse = WindResponse.Rigid)

private val StageProps = listOf(
    StageProp("bananeira", "bananeira", 0.38f, 0.07f, 0.45f, WindResponse.Palm),
    StageProp("palm", "palm", 0.46f, 0.93f, 0.40f, WindResponse.Palm),
    StageProp("lampiao", "lampiao", 0.34f, 0.40f, 0.60f),
    StageProp("maria_bonita", "maria_bonita", 0.34f, 0.55f, 0.58f),
    StageProp("cacador", "cacador", 0.34f, 0.70f, 0.66f),
    StageProp("crate", "crate", 0.15f, 0.26f, 0.68f),
    StageProp("stool", "stool", 0.17f, 0.50f, 0.82f),
)

private class StageSetup(val stage: DepthStage, val rig: LightRig, val lights: List<Light>) {
    /** The back wall of the room: a surface the shadows can climb, that nothing stands behind. */
    val backWall = Wall(0f, 0f, 0f, 0f).also { it.blocksLight = false }

    /** Every prop is a surface too: it blocks the light that would reach what is behind it, and the shadows of what is in front of it fall across its face. */
    val bodies = HashMap<String, Billboard>()
}

private fun DepthStage.item(id: String): DepthItem = items.first { it.id == id }

private fun buildStage(art: Sprites, size: IntSize): StageSetup {
    val w = size.width.toFloat(); val h = size.height.toFloat()
    val top = h * (ART_HORIZON + 0.06f); val bottom = h * 0.97f
    val perspective = Perspective(frontEdgeY = bottom, backEdgeY = top, depth = 6f, eyeBack = 3f, eyeHeight = 2.5f, pitchDegrees = 28f, referenceY = bottom - 0.3f * (bottom - top))
    val stage = DepthStage(perspective, w * 0.03f, top, w * 0.97f, bottom)
    for (p in StageProps) {
        val image = art.prop(p.sprite)
        stage.add(DepthItem(p.id, w * p.x, top + (bottom - top) * p.y, h * p.heightShare, image.width.toFloat() / image.height))
    }
    // The lamps are things you can pick up too: a small glowing orb each.
    for (l in StageLamp.entries) stage.add(DepthItem("lamp:${l.name}", w * l.x, top + (bottom - top) * l.y, h * 0.04f, 1f))
    val rig = LightRig()
    val lights = StageLamp.entries.map { l ->
        rig.add(when (l) {
            StageLamp.Spot -> SpotLight(0f, 0f, 0f, l.color, 0.95f, w * 0.6f, 0f, cone = 0.5f, penumbra = 0.5f, size = 0.12f)
            StageLamp.Red -> PointLight(0f, 0f, 0f, l.color, 0.95f, w * 0.5f, size = 0.4f, flicker = 0.5f)
            else -> PointLight(0f, 0f, 0f, l.color, 0.95f, w * 0.55f, size = 0.2f, flicker = 0.06f)
        })
    }
    val setup = StageSetup(stage, rig, lights)
    for (p in StageProps) {
        // Trees and plants let a little light through between their leaves (their own transparency says where); a person, a crate and a stool are solid.
        val optics = if (p.wind != WindResponse.Rigid) Optics(0.85f) else Optics.Opaque
        setup.bodies[p.id] = Billboard(0f, 0f, 0f, 0f, owner = p.id, optics = optics, mask = art.mask(p.sprite)).also { it.receivesShadows = true }
    }
    return setup
}

/**
 * Four lamps lighting one stage from four places at once, each in its own colour, and every shadow reacting to every lamp: each thing throws a shadow away from each light that reaches it, longer
 * the lower and nearer the lamp, and as dark as that lamp's share of all the light there. Drag a lamp (the glowing orbs) and its shadows swing round while the others stay; drag a thing and its
 * shadows stretch and turn as it crosses between the lamps. Where two lamps overlap their colours mix on the things standing there. [heightScale] raises or lowers all the lamps (low lamps
 * throw long shadows). With [walls] the stage is a room: the back wall and the front of the crate are surfaces the shadows can land on, so a shadow thrown backward climbs the wall (bigger, the farther the wall
 * is behind) instead of lying flat, and one thrown at the crate bends onto its face. [on] says which lamps are lit. [script], if given, moves things by itself for a recording.
 */
@Composable
fun LightStageScene(
    seconds: () -> Float,
    modifier: Modifier = Modifier.fillMaxSize(),
    on: Set<StageLamp> = StageLamp.entries.toSet(),
    heightScale: Float = 0.65f,
    walls: Boolean = false,
    script: ((DepthStage, Float) -> Unit)? = null,
) {
    val art = LocalSprites.current ?: return
    val quality = rememberSceneQuality()
    val sceneWind = rememberSceneWind()
    var size by remember { mutableStateOf(IntSize.Zero) }
    val setup = remember(size, art) { if (size.width == 0) null else buildStage(art, size) }
    if (script != null && setup != null) {
        val now = seconds()
        SideEffect { script(setup.stage, now) }
    }
    Box(modifier.onSizeChanged { size = it }.let { if (setup != null) it.depthDrag(setup.stage) else it }) {
        Spacer(Modifier.fillMaxSize().drawBehind { if (setup != null) drawLightStage(art, setup, seconds(), on, heightScale, quality(), sceneWind.wind, walls) })
    }
}

private fun DrawScope.drawLightStage(art: Sprites, s: StageSetup, t: Float, on: Set<StageLamp>, heightScale: Float, quality: Quality, wind: Wind, walls: Boolean) {
    val w = size.width; val h = size.height
    val stage = s.stage; val rig = s.rig
    val floorY = stage.groundTop
    if (walls) {
        // A room: plaster wall behind, boards on the floor, and the wall and the front of the crate are what the shadows can climb.
        drawRect(Color(0xFF0F1214), Offset.Zero, Size(w, h))
        drawTiled(art.wall, androidx.compose.ui.geometry.Rect(0f, 0f, w, floorY), tile = w * 0.34f, tint = Color(0xFF3A3F44))
        drawTiled(art.floor, androidx.compose.ui.geometry.Rect(0f, floorY, w, h), tile = w * 0.3f, tint = Color(0xFF211C17))
    } else drawWoodsBackdrop(art, t, 0f)
    rig.ambient = 0.05f
    rig.surfaces.clear()
    if (walls) {
        s.backWall.footY = floorY; s.backWall.left = 0f; s.backWall.right = w; s.backWall.height = floorY
        rig.add(s.backWall)
    }
    // Every prop is where it stands now, at the size it is drawn, with the picture that is its outline: it blocks light and takes shadows.
    for (item in stage.items) {
        if (item.id.startsWith("lamp:")) continue
        val prop = StageProps.first { it.id == item.id }
        val image = art.prop(prop.sprite)
        val height = stage.height(item); val half = height * item.aspect / 2f; val k = height / image.height
        val body = s.bodies.getValue(item.id)
        body.footY = item.feetY; body.left = item.feetX - half; body.right = item.feetX + half; body.height = height
        body.silhouette = { c -> drawArt(image, Offset(-image.width * k / 2f, -height), k, c) }
        rig.add(body)
    }
    rig.faceShadows = quality != Quality.Low
    val person = h * 0.34f
    for ((i, l) in StageLamp.entries.withIndex()) {
        val orb = stage.item("lamp:${l.name}"); val light = s.lights[i]
        light.enabled = l in on
        val scale = stage.perspective.scale(orb.feetY)
        val height = person * scale * (0.9f + 0.25f * orb.lift) * heightScale
        when (light) {
            is PointLight -> { light.x = orb.feetX; light.y = orb.feetY; light.height = height }
            // The spot is always aimed at the middle of the stage, wherever it is.
            is SpotLight -> { light.x = orb.feetX; light.y = orb.feetY; light.height = height; light.aim = kotlin.math.atan2((stage.groundTop * 0.5f + stage.groundBottom * 0.5f - orb.feetY) / 0.5f, w * 0.5f - orb.feetX) }
            else -> Unit
        }
    }
    if (walls) drawWallLight(rig, s.backWall, t)
    drawLightPools(rig, t, bulbs = false)

    val dark = Color(0xFF1A2420); val lit = Color(0xFFE6DCC4)
    // Floor shadows first, all together, under everything: whatever is drawn after covers the part of a shadow it stands on, and shows it where it is free.
    for (item in stage.items) {
        if (item.id.startsWith("lamp:")) continue
        val body = s.bodies.getValue(item.id)
        drawCastShadows(rig, item.feetX, item.feetY, body.height, t, 0.95f * (1f - 0.25f * item.lift), quality, ignore = item.id, silhouette = body.silhouette!!)
    }
    for (item in stage.ordered()) {
        val height = stage.height(item)
        if (item.id.startsWith("lamp:")) {
            // A lamp is a glowing orb standing on the ground, lifted when you hold it.
            val lamp = StageLamp.valueOf(item.id.removePrefix("lamp:"))
            val lift = item.lift * height * 0.5f
            val on1 = lamp in on
            val c = Offset(item.feetX, item.feetY - height * 0.6f - lift)
            drawCircle(Color(0xFF14110F).copy(alpha = 0.55f), radius = height * 0.35f, center = Offset(item.feetX, item.feetY), blendMode = BlendMode.SrcOver)
            drawCircle(Brush.radialGradient(0f to Color.White.copy(alpha = if (on1) 1f else 0.15f), 0.4f to lamp.color.copy(alpha = if (on1) 0.9f else 0.2f), 1f to Color.Transparent, center = c, radius = height * 1.1f), radius = height * 1.1f, center = c)
            continue
        }
        val prop = StageProps.first { it.id == item.id }
        val image = art.prop(prop.sprite)
        val k = height / image.height
        val lifted = item.lift * height * 0.05f
        val colour = rig.shade(dark, lit, item.feetX, item.feetY - height * 0.3f, t, height, item.id)
        val bend = prop.wind.bend(wind, t, item.feetX / w, phase = item.id.length * 0.21f)
        sway(item.feetX, item.feetY - lifted, bend, maxLean = 0.1f) { drawArt(image, Offset(item.feetX - image.width * k / 2f, item.feetY - height - lifted), k, colour) }
        // What stands in front of it throws its shadow across its face, only where it is.
        drawFaceShadows(rig, s.bodies.getValue(item.id), t, 0.75f, quality)
    }
    drawLightBulbs(rig, t)
}

/** What a recording shows: the amber and blue lamps swing round the stage in opposite directions, the red fire drifts side to side, and the shadows turn with each. */
fun scriptedLamps(stage: DepthStage, t: Float) {
    val span = stage.groundRight - stage.groundLeft; val depth = stage.groundBottom - stage.groundTop
    val a = 2f * PI.toFloat() * t / 10f
    fun move(id: String, x: Float, y: Float) { stage.items.first { it.id == id }.apply { feetX = stage.groundLeft + span * x; feetY = stage.groundTop + depth * y } }
    move("lamp:Amber", 0.5f - 0.38f * cos(a), 0.62f + 0.3f * sin(a))
    move("lamp:Blue", 0.5f + 0.38f * cos(a), 0.62f - 0.3f * sin(a))
    move("lamp:Red", 0.5f + 0.3f * sin(a * 1.5f), 0.96f)
}
