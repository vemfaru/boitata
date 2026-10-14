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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import br.com.faru.boitata.core.Perspective
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.clock.rememberSceneQuality
import br.com.faru.boitata.core.depth.DepthItem
import br.com.faru.boitata.core.depth.DepthStage
import br.com.faru.boitata.core.light.Billboard
import br.com.faru.boitata.core.light.Optics
import br.com.faru.boitata.light.LightRig
import br.com.faru.boitata.light.Lights
import br.com.faru.boitata.light.PointLight
import br.com.faru.boitata.light.drawCastShadows
import br.com.faru.boitata.light.drawLightPools
import br.com.faru.boitata.light.drawWallLight
import br.com.faru.boitata.scene.depthDrag
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The lamps a shadow theatre can use: how big the flame is decides how sharp the shadows are, and how it flickers decides how much they breathe. */
enum class TheatreLamp(val label: String, val color: Color, val flicker: Float, val size: Float) {
    Candle("Candle", Color(0xFFFFC46B), 0.7f, 0.05f),
    Hurricane("Hurricane lamp", Color(0xFFFFC978), 0.2f, 0.12f),
    Boitata("Boitatá's fire", Color(0xFFFF8A2E), 0.85f, 0.4f),
}

/** The sheet the shadows fall on: what colour it lets through. */
enum class TheatrePaper(val label: String, val tint: Color) {
    White("White paper", Color.White),
    Amber("Amber paper", Color(0xFFFFC66B)),
    Red("Red paper", Color(0xFFFF6B5A)),
}

private val Puppets = listOf("lampiao" to 0.30f, "maria_bonita" to 0.30f, "cacador" to 0.30f, "onca" to 0.15f, "tamandua" to 0.13f, "capivara" to 0.12f)
private val PuppetStart = mapOf(
    "lampiao" to (0.30f to 0.62f), "maria_bonita" to (0.50f to 0.74f), "cacador" to (0.70f to 0.55f),
    "onca" to (0.22f to 0.9f), "tamandua" to (0.78f to 0.92f), "capivara" to (0.5f to 0.40f),
)

private class TheatreSetup(val stage: DepthStage, val rig: LightRig, val lamp: PointLight, val screen: Billboard) {
    val bodies = HashMap<String, Billboard>()
}

private fun DepthStage.item(id: String): DepthItem = items.first { it.id == id }

private fun buildTheatre(art: Sprites, size: IntSize): TheatreSetup {
    val w = size.width.toFloat(); val h = size.height.toFloat()
    // The puppets and the lamp live behind the screen, in a band of ground between the back of the stage and the screen.
    val top = h * 0.46f; val bottom = h * 0.80f
    val perspective = Perspective(frontEdgeY = bottom, backEdgeY = top, depth = 6f, eyeBack = 3f, eyeHeight = 2.5f, pitchDegrees = 28f, referenceY = bottom - 0.3f * (bottom - top))
    val stage = DepthStage(perspective, w * 0.14f, top, w * 0.86f, bottom)
    for ((id, share) in Puppets) {
        val image = art.prop(id)
        val (x, y) = PuppetStart.getValue(id)
        stage.add(DepthItem(id, stage.groundLeft + (stage.groundRight - stage.groundLeft) * x, top + (bottom - top) * y, h * share, image.width.toFloat() / image.height))
    }
    stage.add(DepthItem("lamp", w * 0.5f, top + (bottom - top) * 0.04f, h * 0.05f, 1f))
    val rig = LightRig(ambient = 0.03f)
    val lamp = rig.add(Lights.hurricaneLamp(0f, 0f, h * 0.3f).also { it.reach = w * 1.4f }) as PointLight
    val screen = Billboard(footY = h * 0.86f, left = w * 0.1f, right = w * 0.9f, height = h * 0.64f, optics = Optics.Paper())
    screen.maxShadowScale = 14f
    rig.add(screen)
    val setup = TheatreSetup(stage, rig, lamp, screen)
    for ((id, _) in Puppets) setup.bodies[id] = Billboard(0f, 0f, 0f, 0f, owner = id, mask = art.mask(id))
    return setup
}

/**
 * Teatro de Sombras: a sheet of paper stretched in a frame, a lamp behind it, and puppets between the lamp and the paper. The audience sees the light that comes through the paper, a soft glow brightest
 * where the lamp is nearest, and on it the shadows of the puppets: huge and soft near the lamp, small and sharp when a puppet is pressed against the paper, exactly as a hand behind a sheet. Every
 * shadow is worked out from where the lamp, the puppet and the paper are (the same geometry as a shadow on a wall, with the lamp on the other side). [lamp] is how big and how steady the flame is,
 * [paper] what colour it lets through. With [backstage] the paper is nearly clear and you see the puppets and the lamp as the puppeteers do. [script], if given, moves things by itself (for recordings).
 */
@Composable
fun ShadowTheatreScene(
    seconds: () -> Float,
    modifier: Modifier = Modifier.fillMaxSize(),
    lamp: TheatreLamp = TheatreLamp.Hurricane,
    paper: TheatrePaper = TheatrePaper.White,
    backstage: Boolean = false,
    script: ((DepthStage, Float) -> Unit)? = null,
) {
    val art = LocalSprites.current ?: return
    val quality = rememberSceneQuality()
    var size by remember { mutableStateOf(IntSize.Zero) }
    val setup = remember(size, art) { if (size.width == 0) null else buildTheatre(art, size) }
    if (script != null && setup != null) {
        val now = seconds()
        SideEffect { script(setup.stage, now) }
    }
    Box(modifier.onSizeChanged { size = it }.let { if (setup != null) it.depthDrag(setup.stage) else it }) {
        Spacer(Modifier.fillMaxSize().drawBehind { if (setup != null) drawTheatre(art, setup, seconds(), lamp, paper, backstage, quality()) })
    }
}

private fun DrawScope.drawTheatre(art: Sprites, s: TheatreSetup, t: Float, kind: TheatreLamp, paper: TheatrePaper, backstage: Boolean, quality: Quality) {
    val w = size.width; val h = size.height
    val stage = s.stage; val rig = s.rig; val screen = s.screen
    // Behind the screen: a dark stage, a curtain, boards.
    drawRect(Color(0xFF09080A), Offset.Zero, Size(w, h))
    drawTiled(art.wall, Rect(0f, 0f, w, h * 0.5f), tile = w * 0.34f, tint = Color(0xFF17131A))
    drawTiled(art.floor, Rect(0f, h * 0.5f, w, h), tile = w * 0.3f, tint = Color(0xFF1B1511))

    val orb = stage.item("lamp")
    s.lamp.color = kind.color; s.lamp.flicker = kind.flicker; s.lamp.size = kind.size
    s.lamp.x = orb.feetX; s.lamp.y = orb.feetY; s.lamp.height = h * 0.28f * stage.perspective.scale(orb.feetY) * (1f + 0.3f * orb.lift)
    screen.optics = Optics.Paper(paper.tint)
    rig.surfaces.removeAll { it !== screen }
    for ((id, _) in Puppets) {
        val item = stage.item(id)
        val image = art.prop(id)
        val height = stage.height(item); val half = height * item.aspect / 2f; val k = height / image.height
        val body = s.bodies.getValue(id)
        body.footY = item.feetY; body.left = item.feetX - half; body.right = item.feetX + half; body.height = height
        body.silhouette = { c -> drawArt(image, Offset(-image.width * k / 2f, -height), k, c) }
        rig.add(body)
    }

    // Behind the paper: the floor shadows and the puppets themselves, lit by the lamp (the audience barely sees them through the sheet).
    drawLightPools(rig, t, bulbs = false)
    val dark = Color(0xFF1A1512); val lit = Color(0xFFE6DCC4)
    for (item in stage.items) {
        if (item.id == "lamp") continue
        val body = s.bodies.getValue(item.id)
        drawCastShadows(rig, item.feetX, item.feetY, body.height, t, 0.9f, quality, ignore = item.id, silhouette = body.silhouette!!)
    }
    for (item in stage.ordered()) {
        if (item.id == "lamp") continue
        val body = s.bodies.getValue(item.id)
        val colour = rig.shade(dark, lit, item.feetX, item.feetY - body.height * 0.3f, t, body.height, item.id)
        withTransform({ translate(item.feetX, item.feetY - item.lift * body.height * 0.05f) }) { body.silhouette!!(colour) }
    }
    if (backstage) {
        val c = Offset(orb.feetX, orb.feetY - s.lamp.height)
        drawCircle(Brush.radialGradient(0f to Color(0xFFFFF4D6), 0.3f to kind.color.copy(alpha = 0.7f), 1f to Color.Transparent, center = c, radius = h * 0.05f), radius = h * 0.05f, center = c)
        drawCircle(Color(0xFF14110F).copy(alpha = 0.5f), radius = h * 0.012f, center = Offset(orb.feetX, orb.feetY))
    }

    // The sheet: paper over everything behind it, glowing where the lamp lights it, and the puppets' shadows on it.
    val sheet = Rect(screen.left, screen.footY - screen.height, screen.right, screen.footY)
    drawRect(paper.tint.copy(alpha = 0.10f).compositeOver(Color(0xFF15110D)).copy(alpha = if (backstage) 0.3f else 0.93f), sheet.topLeft, sheet.size)
    drawWallLight(rig, screen, t, gain = 2.2f)
    for (item in stage.items) {
        if (item.id == "lamp") continue
        val body = s.bodies.getValue(item.id)
        drawCastShadows(rig, item.feetX, item.feetY, body.height, t, 1f, quality, ignore = item.id, floor = false, silhouette = body.silhouette!!)
    }
    // The frame: two posts, a top bar and a lower bar, in dark wood.
    val wood = Color(0xFF1A110B); val post = w * 0.018f
    drawRect(wood, Offset(sheet.left - post, sheet.top - post), Size(post, sheet.height + 2f * post))
    drawRect(wood, Offset(sheet.right, sheet.top - post), Size(post, sheet.height + 2f * post))
    drawRect(wood, Offset(sheet.left - post, sheet.top - post), Size(sheet.width + 2f * post, post))
    drawRect(wood, Offset(sheet.left - post, sheet.bottom), Size(sheet.width + 2f * post, post * 2f))
    drawRect(Color(0xFF0B0907), Offset(0f, sheet.bottom + post * 2f), Size(w, h))
}

/** What a recording shows: the lamp drifts a little, the jaguar walks across right behind the paper (small and sharp), and the hunter and Lampião move toward and away from the lamp (huge and soft). */
fun scriptedTheatre(stage: DepthStage, t: Float) {
    val span = stage.groundRight - stage.groundLeft; val depth = stage.groundBottom - stage.groundTop
    val a = 2f * PI.toFloat() * t / 12f
    fun move(id: String, x: Float, y: Float) { stage.items.first { it.id == id }.apply { feetX = stage.groundLeft + span * x; feetY = stage.groundTop + depth * y } }
    move("lamp", 0.5f + 0.12f * sin(a), 0.05f)
    move("onca", 0.1f + 0.8f * (0.5f - 0.5f * cos(a)), 0.97f)
    move("lampiao", 0.35f + 0.1f * sin(a * 2f), 0.2f + 0.45f * (0.5f - 0.5f * cos(a)))
    move("cacador", 0.72f, 0.45f + 0.35f * (0.5f + 0.5f * sin(a + 1f)))
    move("maria_bonita", 0.5f + 0.2f * cos(a), 0.7f)
    move("capivara", 0.2f, 0.5f)
    move("tamandua", 0.85f, 0.85f)
}
