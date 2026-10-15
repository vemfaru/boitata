package br.com.faru.boitata.playground

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
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

private val PuppetArt = listOf("puppet_cacador_body", "puppet_cacador_arm", "puppet_cacador_leg_1", "puppet_cacador_leg_2", "puppet_onca", "puppet_onca_tail", "puppet_moon", "puppet_boitata_head", "puppet_boitata_tail") + (1..5).map { "puppet_boitata_seg_$it" }

/** The frame, paper, curtains and audience of the theatre (file names). */
private val TheatreArt = PuppetArt + listOf("theatre_frame_post", "theatre_frame_bar", "theatre_frame_corner", "theatre_frame_foot", "theatre_paper", "theatre_valance", "theatre_curtain_1", "theatre_curtain_2") +
    (1..6).map { "theatre_audience_$it" }

private val Puppets = listOf("lampiao" to 0.30f, "maria_bonita" to 0.30f, "cacador" to 0.30f, "onca" to 0.17f, "tamandua" to 0.13f, "capivara" to 0.12f, "boitata" to 0.15f, "moon" to 0.13f)

/** The puppets cut in pieces and pinned at the joints (so they move as they are carried), and how wide each is against its height. */
private val JointedAspect = mapOf("cacador" to 0.62f, "onca" to 1.6f, "boitata" to 3.8f, "moon" to 1f)
private val JointedMaster = mapOf("cacador" to "puppet_cacador_body", "onca" to "puppet_onca", "boitata" to "puppet_boitata_head", "moon" to "puppet_moon")

private fun DrawScope.drawCentred(image: ImageBitmap, cx: Float, cy: Float, size: Float, colour: Color, degrees: Float = 0f, pivot: Offset = Offset(cx, cy)) {
    val k = size / maxOf(image.width, image.height)
    withTransform({ rotate(degrees, pivot) }) { drawArt(image, Offset(cx - image.width * k / 2f, cy - image.height * k / 2f), k, colour) }
}

/** A cut-paper puppet drawn from its pieces at time [t]: feet at the origin, up negative, [height] tall. The legs swing, the tail flicks, the serpent's body ripples. */
private fun jointedSilhouette(art: Sprites, id: String, height: Float, t: Float): DrawScope.(Color) -> Unit = { c ->
    val h = height; val w = h * JointedAspect.getValue(id)
    when (id) {
        "moon" -> drawCentred(art.art("puppet_moon"), 0f, -h / 2f, h, c)
        "onca" -> {
            val body = art.art("puppet_onca"); val k = h / body.height
            drawArt(body, Offset(-body.width * k / 2f, -h), k, c)
            val tail = art.art("puppet_onca_tail"); val tk = h * 0.32f / tail.height
            val root = Offset(-w / 2f + w * 0.03f, -h * 0.72f)
            withTransform({ rotate(sin(t * 3f) * 18f, root) }) { drawArt(tail, Offset(root.x - tail.width * tk * 0.9f, root.y - tail.height * tk / 2f), tk, c) }
        }
        "cacador" -> {
            val body = art.art("puppet_cacador_body"); val bk = h * 0.66f / body.height
            val hip = Offset(0f, -h * 0.36f)
            for ((i, leg) in listOf("puppet_cacador_leg_1", "puppet_cacador_leg_2").withIndex()) {
                val img = art.art(leg); val lk = h * 0.38f / img.height
                withTransform({ rotate((if (i == 0) 1f else -1f) * sin(t * 3f) * 22f, hip) }) { drawArt(img, Offset(hip.x - img.width * lk / 2f, hip.y), lk, c) }
            }
            drawArt(body, Offset(-body.width * bk / 2f, -h), bk, c)
            val arm = art.art("puppet_cacador_arm"); val ak = h * 0.3f / arm.height
            val shoulder = Offset(h * 0.04f, -h * 0.82f)
            withTransform({ rotate(-20f + sin(t * 2f) * 8f, shoulder) }) { drawArt(arm, Offset(shoulder.x - arm.width * ak / 2f, shoulder.y), ak, c) }
        }
        "boitata" -> {
            for (i in 6 downTo 0) {
                val img = if (i == 6) art.art("puppet_boitata_tail") else art.art("puppet_boitata_seg_${i + 1 - if (i == 5) 1 else 0}".replace("seg_6", "seg_5"))
                val cx = w / 2f - h * (0.62f + 0.43f * (i + 1)); val cy = -h * 0.5f + sin(t * 2.2f - (i + 1) * 0.9f) * h * 0.14f
                drawCentred(img, cx, cy, h * (0.62f - 0.03f * i), c, degrees = cos(t * 2.2f - (i + 1) * 0.9f) * 14f)
            }
            drawCentred(art.art("puppet_boitata_head"), w / 2f - h * 0.5f, -h * 0.5f + sin(t * 2.2f) * h * 0.06f, h, c)
        }
    }
}
private val PuppetStart = mapOf(
    "lampiao" to (0.30f to 0.62f), "maria_bonita" to (0.50f to 0.74f), "cacador" to (0.70f to 0.55f),
    "onca" to (0.22f to 0.9f), "tamandua" to (0.78f to 0.92f), "capivara" to (0.5f to 0.40f), "boitata" to (0.5f to 0.30f), "moon" to (0.12f to 0.18f),
)

private class TheatreSetup(val stage: DepthStage, val rig: LightRig, val lamp: PointLight, val screen: Billboard) {
    val baseStrength = lamp.strength
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
        val aspect = JointedAspect[id] ?: art.prop(id).let { it.width.toFloat() / it.height }
        val (x, y) = PuppetStart.getValue(id)
        stage.add(DepthItem(id, stage.groundLeft + (stage.groundRight - stage.groundLeft) * x, top + (bottom - top) * y, h * share, aspect))
    }
    stage.add(DepthItem("lamp", w * 0.5f, top + (bottom - top) * 0.04f, h * 0.05f, 1f))
    val rig = LightRig(ambient = 0.03f)
    val lamp = rig.add(Lights.hurricaneLamp(0f, 0f, h * 0.3f).also { it.reach = w * 1.4f }) as PointLight
    val screen = Billboard(footY = h * 0.86f, left = w * 0.1f, right = w * 0.9f, height = h * 0.64f, optics = Optics.Paper())
    screen.maxShadowScale = 14f
    rig.add(screen)
    val setup = TheatreSetup(stage, rig, lamp, screen)
    for ((id, _) in Puppets) setup.bodies[id] = Billboard(0f, 0f, 0f, 0f, owner = id, mask = JointedMaster[id]?.let { art.art(it).toAlphaMask() } ?: art.mask(id))
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
    /** 0 with the lamp lit, up to 1 with it blown out: the paper goes dark. */
    lampOut: () -> Float = { 0f },
) {
    val art = LocalSprites.current ?: return
    val loaded by produceState(false, art) { art.ensure(TheatreArt); value = true }
    if (!loaded) return
    val quality = rememberSceneQuality()
    var size by remember { mutableStateOf(IntSize.Zero) }
    val setup = remember(size, art) { if (size.width == 0) null else buildTheatre(art, size) }
    if (script != null && setup != null) {
        val now = seconds()
        SideEffect { script(setup.stage, now) }
    }
    Box(modifier.onSizeChanged { size = it }.let { if (setup != null) it.depthDrag(setup.stage) else it }) {
        Spacer(Modifier.fillMaxSize().drawBehind { if (setup != null) drawTheatre(art, setup, seconds(), lamp, paper, backstage, quality(), lampOut()) })
    }
}

private fun DrawScope.drawTheatre(art: Sprites, s: TheatreSetup, t: Float, kind: TheatreLamp, paper: TheatrePaper, backstage: Boolean, quality: Quality, lampOut: Float = 0f) {
    val w = size.width; val h = size.height
    val stage = s.stage; val rig = s.rig; val screen = s.screen
    // Behind the screen: a dark stage, a curtain, boards.
    drawRect(Color(0xFF09080A), Offset.Zero, Size(w, h))
    drawTiled(art.wall, Rect(0f, 0f, w, h * 0.5f), tile = w * 0.34f, tint = Color(0xFF17131A))
    drawTiled(art.floor, Rect(0f, h * 0.5f, w, h), tile = w * 0.3f, tint = Color(0xFF1B1511))

    val orb = stage.item("lamp")
    s.lamp.color = kind.color; s.lamp.flicker = kind.flicker * (1f - lampOut); s.lamp.size = kind.size; s.lamp.strength = s.baseStrength * (1f - lampOut)
    s.lamp.x = orb.feetX; s.lamp.y = orb.feetY; s.lamp.height = h * 0.28f * stage.perspective.scale(orb.feetY) * (1f + 0.3f * orb.lift)
    screen.optics = Optics.Paper(paper.tint)
    rig.surfaces.removeAll { it !== screen }
    for ((id, _) in Puppets) {
        val item = stage.item(id)
        val height = stage.height(item); val half = height * item.aspect / 2f
        val body = s.bodies.getValue(id)
        body.footY = item.feetY; body.left = item.feetX - half; body.right = item.feetX + half; body.height = height
        body.silhouette = if (id in JointedAspect) jointedSilhouette(art, id, height, t) else { val image = art.prop(id); val k = height / image.height; { c -> drawArt(image, Offset(-image.width * k / 2f, -height), k, c) } }
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
    // The paper's fibres, over the glow.
    run {
        val paperArt = art.art("theatre_paper"); val tile = sheet.width * 0.5f
        clipRect(sheet.left, sheet.top, sheet.right, sheet.bottom) {
            var y = sheet.top
            while (y < sheet.bottom) { var x = sheet.left; while (x < sheet.right) { drawArt(paperArt, Offset(x, y), tile / paperArt.width, Color(0xFF3A2A18).copy(alpha = if (backstage) 0.06f else 0.15f)); x += tile }; y += tile }
        }
    }
    // The frame: two carved posts, a top bar and a lower bar, corners and feet, in dark wood.
    val wood = Color(0xFF3A2616); val postImg = art.art("theatre_frame_post"); val barImg = art.art("theatre_frame_bar")
    val post = w * 0.03f; val framed = sheet.height + 2f * post
    drawStretchedArt(postImg, Rect(sheet.left - post, sheet.top - post, sheet.left, sheet.top - post + framed), wood)
    drawStretchedArt(postImg, Rect(sheet.right, sheet.top - post, sheet.right + post, sheet.top - post + framed), wood)
    drawStretchedArt(barImg, Rect(sheet.left - post, sheet.top - post, sheet.right + post, sheet.top), wood)
    drawStretchedArt(barImg, Rect(sheet.left - post, sheet.bottom, sheet.right + post, sheet.bottom + post), wood)
    val corner = art.art("theatre_frame_corner")
    for ((cx, cy) in listOf(sheet.left - post to sheet.top - post, sheet.right - post * 0.2f to sheet.top - post, sheet.left - post to sheet.bottom - post * 0.2f, sheet.right - post * 0.2f to sheet.bottom - post * 0.2f))
        drawStretchedArt(corner, Rect(cx, cy, cx + post * 1.2f, cy + post * 1.2f), wood)
    val foot = art.art("theatre_frame_foot"); val footW = post * 3.2f; val footH = footW * foot.height / foot.width
    drawStretchedArt(foot, Rect(sheet.left - post * 2.1f, sheet.bottom + post - footH * 0.3f, sheet.left - post * 2.1f + footW, sheet.bottom + post + footH * 0.7f), wood)
    drawStretchedArt(foot, Rect(sheet.right + post * 2.1f - footW, sheet.bottom + post - footH * 0.3f, sheet.right + post * 2.1f, sheet.bottom + post + footH * 0.7f), wood)
    // The curtains at the sides, and the valance across the top.
    val cloth = Color(0xFF5A1F1A)
    val cur = art.art("theatre_curtain_1"); val curH = h * 0.86f; val curW = curH * cur.width / cur.height
    drawArt(cur, Offset(-curW * 0.45f, h * 0.06f), curH / cur.height, cloth)
    val cur2 = art.art("theatre_curtain_2")
    drawArt(cur2, Offset(w - curW * 0.55f, h * 0.06f), curH / cur2.height, cloth)
    drawStretchedArt(art.art("theatre_valance"), Rect(0f, 0f, w, h * 0.17f), cloth)
    // The front of the audience: the backs of heads at the bottom edge, dark against the glow of the sheet.
    val heads = (1..6)
    for ((k, i) in heads.withIndex()) {
        val head = art.art("theatre_audience_$i"); val hh = h * (0.2f + 0.03f * ((k * 7) % 3)); val hw = hh * head.width / head.height
        drawArt(head, Offset(w * (0.06f + k * 0.17f) - hw / 2f, h - hh * 0.72f), hh / head.height, Color(0xFF060507))
    }
}

/** Draws [image] stretched to fill [target], painted [tint]. */
private fun DrawScope.drawStretchedArt(image: androidx.compose.ui.graphics.ImageBitmap, target: Rect, tint: Color) {
    drawArt(image, target.topLeft, 1f, tint, scaleX = target.width / image.width, scaleY = target.height / image.height)
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
    move("boitata", 0.5f + 0.28f * sin(a * 0.8f), 0.3f + 0.3f * (0.5f - 0.5f * cos(a * 0.8f)))
    move("moon", 0.12f, 0.12f + 0.1f * sin(a))
}
