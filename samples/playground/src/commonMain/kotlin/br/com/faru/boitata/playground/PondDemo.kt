package br.com.faru.boitata.playground

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import br.com.faru.boitata.core.Perspective
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.clock.rememberSceneQuality
import br.com.faru.boitata.core.depth.DepthItem
import br.com.faru.boitata.core.depth.DepthStage
import br.com.faru.boitata.core.light.Liquid
import br.com.faru.boitata.core.sky.MoonPhase
import br.com.faru.boitata.core.sky.SkyModel
import br.com.faru.boitata.core.sky.SkyWeather
import br.com.faru.boitata.core.draw.sway
import br.com.faru.boitata.core.wind.WindResponse
import br.com.faru.boitata.core.wind.rememberSceneWind
import br.com.faru.boitata.light.DirectionalLight
import br.com.faru.boitata.light.FlamePalette
import br.com.faru.boitata.light.LightRig
import br.com.faru.boitata.light.Lights
import br.com.faru.boitata.light.PointLight
import br.com.faru.boitata.light.clipToLiquid
import br.com.faru.boitata.light.drawCastShadows
import br.com.faru.boitata.light.drawFlame
import br.com.faru.boitata.light.drawGlitter
import br.com.faru.boitata.light.drawLightBulbs
import br.com.faru.boitata.light.drawLightPools
import br.com.faru.boitata.light.drawReflection
import br.com.faru.boitata.light.drawStandingInWater
import br.com.faru.boitata.light.drawWaterSurface
import br.com.faru.boitata.scene.depthDrag
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The pond's dressing (file names): lily pads and flowers, reeds, stones, a mud bank, fish and rings. */
private val PondArt = listOf("pond_fish_1", "pond_fish_2", "pond_lily_flower", "pond_mud_bank") + (1..3).map { "pond_lilypad_$it" } + (1..3).map { "pond_reeds_$it" } + (1..3).map { "pond_rings_$it" } + (1..5).map { "pond_stone_$it" }

private class PondProp(val id: String, val sprite: String?, val heightShare: Float, val x: Float, val y: Float, val wind: WindResponse = WindResponse.Rigid)

private val PondProps = listOf(
    PondProp("araucaria", "araucaria", 0.50f, 0.07f, 0.28f, WindResponse.Pine),
    PondProp("palm", "palm", 0.46f, 0.93f, 0.22f, WindResponse.Palm),
    PondProp("bananeira", "bananeira", 0.34f, 0.80f, 0.16f, WindResponse.Palm),
    PondProp("cacador", "cacador", 0.30f, 0.50f, 0.16f),
    PondProp("lampiao", "lampiao", 0.34f, 0.18f, 0.80f),
    PondProp("maria_bonita", "maria_bonita", 0.34f, 0.84f, 0.60f),
    PondProp("campfire", "logs", 0.08f, 0.34f, 0.88f),
)

/** The animals that cross the pond by themselves: the caiman and capybara wade through it, the tamandua walks the near shore, the macaw flies over. */
private val PondAnimals = listOf(
    Creature("jacare", "jacare", "jacare_swim", 0.09f, Gait.Patrol(0.12f, 0.88f, 0.42f, 0.035f, pause = 2f)),
    Creature("capivara", "capivara", "capivara_swim", 0.12f, Gait.Patrol(0.9f, 0.25f, 0.5f, 0.03f, pause = 3f, phase = 0.4f)),
    Creature("tamandua", "tamandua", "tamandua_walk", 0.15f, Gait.Patrol(0.06f, 0.6f, 0.97f, 0.07f, pause = 2.5f, phase = 0.2f)),
    Creature("arara", "arara", "arara_fly", 0.13f, Gait.Fly(0.38f, 2.0f, 10f, rest = 2f)),
)

private class PondSetup(val stage: DepthStage, val rig: LightRig, val liquid: Liquid, val moon: DirectionalLight, val fire: PointLight, val lamp: PointLight) {
    val poses = Array(PondAnimals.size) { Pose() }
}

private fun DepthStage.item(id: String): DepthItem = items.first { it.id == id }

private fun buildPond(art: Sprites, size: IntSize): PondSetup {
    val w = size.width.toFloat(); val h = size.height.toFloat()
    val top = h * (ART_HORIZON + 0.06f); val bottom = h * 0.97f
    val perspective = Perspective(frontEdgeY = bottom, backEdgeY = top, depth = 6f, eyeBack = 3f, eyeHeight = 2.5f, pitchDegrees = 28f, referenceY = bottom - 0.3f * (bottom - top))
    val stage = DepthStage(perspective, w * 0.03f, top, w * 0.97f, bottom)
    for (p in PondProps) {
        val aspect = if (p.sprite == "logs") art.logs.width.toFloat() / art.logs.height else art.prop(p.sprite!!).let { it.width.toFloat() / it.height }
        stage.add(DepthItem(p.id, w * p.x, top + (bottom - top) * p.y, h * p.heightShare, aspect))
    }
    val liquid = Liquid(cx = w * 0.5f, cy = top + (bottom - top) * 0.46f, rx = w * 0.32f, ry = (bottom - top) * 0.3f, maxDepth = h * 0.1f)
    val rig = LightRig(ambient = 0.06f)
    val moon = rig.add(Lights.moon(22f)) as DirectionalLight
    val fire = rig.add(Lights.fire(0f, 0f, h * 0.3f).also { it.reach = w * 0.45f }) as PointLight
    val lamp = rig.add(Lights.hurricaneLamp(0f, 0f, h * 0.3f).also { it.reach = w * 0.35f }) as PointLight
    return PondSetup(stage, rig, liquid, moon, fire, lamp)
}

/**
 * A pond in the woods, with water that behaves as water does. Everything at its edge is **mirrored** in it, faint and rippling and fading with depth (as strong as the water's Fresnel reflectance), the moon is mirrored too and the
 * lamp and the fire leave a column of **glitter** on it. Whatever wades in is partly **under water**: the part under the surface looks nearer to it than it is (the water's index of refraction), squashed toward the waterline, tinted
 * by the water and shaken by the ripples, with rings where it meets the surface. Drag the people into the pond to wade; the caiman and the capybara wade through it by themselves. [hour] sets the sky the water mirrors.
 */
@Composable
fun PondScene(seconds: () -> Float, modifier: Modifier = Modifier.fillMaxSize(), hour: Float = 22f, moon: MoonPhase = MoonPhase.Full, southern: Boolean = true) {
    val art = LocalSprites.current ?: return
    val loaded by produceState(false, art) { art.ensure(PondArt); value = true }
    if (!loaded) return
    val quality = rememberSceneQuality()
    val sceneWind = rememberSceneWind()
    var size by remember { mutableStateOf(IntSize.Zero) }
    val setup = remember(size, art) { if (size.width == 0) null else buildPond(art, size) }
    Box(modifier.onSizeChanged { size = it }.let { if (setup != null) it.depthDrag(setup.stage) else it }) {
        Spacer(Modifier.fillMaxSize().drawBehind { if (setup != null) drawPond(art, setup, seconds(), hour, moon, southern, quality(), sceneWind.wind) })
    }
}

private fun DrawScope.drawPond(art: Sprites, s: PondSetup, t: Float, hour: Float, phase: MoonPhase, southern: Boolean, quality: Quality, wind: br.com.faru.boitata.core.wind.Wind) {
    val w = size.width; val h = size.height
    val stage = s.stage; val rig = s.rig; val liquid = s.liquid
    val day = drawWoodsBackdrop(art, t, hour)
    val v = cover(); val horizon = v.py(1080f * ART_HORIZON)
    s.moon.followMoon(hour, phase)
    rig.ambient = 0.06f + 0.5f * day
    val sky = SkyModel.at(hour, SkyWeather.Clear)

    val fireItem = stage.item("campfire"); val fireH = stage.height(fireItem)
    s.fire.x = fireItem.feetX; s.fire.y = fireItem.feetY; s.fire.height = fireH * 1.4f
    val bearer = stage.item("lampiao"); val bearerImage = art.prop("lampiao"); val bh = stage.height(bearer)
    val hand = HandAnchors.getValue("lampiao")
    val fist = Offset(bearer.feetX + bh * bearerImage.width / bearerImage.height * (hand.x - 0.5f), bearer.feetY - bh * (1f - hand.y))
    val lampImage = art.prop("lampiao_lamp"); val lampHeight = bh * Lamp.OF_HOLDER; val lampK = lampHeight / lampImage.height
    val flame = Offset(fist.x + (Lamp.flame.x - Lamp.handle.x) * lampImage.width * lampK, fist.y + (Lamp.flame.y - Lamp.handle.y) * lampHeight)
    s.lamp.x = flame.x; s.lamp.y = bearer.feetY + bh * 0.03f; s.lamp.height = (s.lamp.y - flame.y).coerceAtLeast(bh * 0.1f)

    // The water, and the sky in it.
    drawWaterSurface(liquid, sky.horizon, sky.zenith, t, quality)
    if (s.moon.arc >= 0f && s.moon.strength > 0f) {
        val mx = w * (0.08f + 0.84f * s.moon.arc); val my = horizon * (1f - 0.8f * sin(PI.toFloat() * s.moon.arc) + 0.05f)
        // The moon in the water: where the sky above the far shore is mirrored, flattened by the slant.
        val rx = mx; val ry = liquid.cy - liquid.ry + (horizon - my) * 0.2f
        clipToLiquid(liquid) { drawMoonArt(art, Offset(rx, ry), h * 0.03f, Color(0xFFE9EEF8).copy(alpha = 0.5f), phase, southern) }
    }
    drawPondLife(art, liquid, t, day, wind)
    drawLightPools(rig, t, bulbs = false)

    val dark = lerp(Color(0xFF1A2420), Color(0xFF3D5045), day); val lit = lerp(Color(0xFFE6DCC4), Color(0xFFEFE9DA), day)
    // Everything that stands: the props and the animals, with where they are this instant, in order from far to near.
    class Entry(val y: Float, val feetX: Float, val height: Float, val lift: Float, val colourAt: (Float, Float) -> Color, val draw: DrawScope.(Color) -> Unit, val flame: (() -> Unit)? = null)
    val entries = ArrayList<Entry>()
    for (item in stage.items) {
        val prop = PondProps.first { it.id == item.id }
        val height = stage.height(item); val lifted = item.lift * height * 0.05f
        val image = if (prop.sprite == "logs") art.logs else art.prop(prop.sprite!!)
        val k = height / image.height
        val bend = prop.wind.bend(wind, t, item.feetX / w, phase = item.id.length * 0.21f)
        val silhouette: DrawScope.(Color) -> Unit = { c -> sway(0f, 0f, bend, maxLean = 0.1f) { drawArt(image, Offset(-image.width * k / 2f, -height), k, c) } }
        val flameDraw: (() -> Unit)? = if (item.id == "campfire") ({ drawFlame(item.feetX, item.feetY - height * 0.1f, height * 1.2f, height * 2.1f, t, 0.9f, FlamePalette.Campfire, 3L, quality) }) else null
        entries += Entry(item.feetY, item.feetX, height, lifted, { x, y -> rig.shade(dark, lit, x, y - height * 0.3f, t, height, item.id) }, silhouette, flameDraw)
    }
    for ((i, c) in PondAnimals.withIndex()) {
        val strip = c.strip?.let { art.strips.getValue(it) }
        val aspect = strip?.aspect ?: art.prop(c.sprite).let { it.width.toFloat() / it.height }
        val share = (c.gait as? Gait.Patrol)?.y ?: (c.gait as Gait.Fly).y
        val height = h * c.heightShare * stage.perspective.scale(stage.groundTop + (stage.groundBottom - stage.groundTop) * share)
        val pose = s.poses[i]
        c.poseAt(t, stage.groundLeft, stage.groundRight, stage.groundTop, stage.groundBottom, height * aspect, pose)
        entries += Entry(pose.y, pose.x, height + pose.lift, 0f, { x, y -> rig.shade(dark, lit, x, y, t) }, { col -> drawCreature(art, c, pose, height, col) })
    }
    entries.sortBy { it.y }
    // Reflections first, under every thing: each mirrored about its own waterline.
    for (e in entries) {
        val depth = (liquid.depthAt(e.feetX, e.y) * liquid.maxDepth * stage.perspective.scale(e.y)).coerceAtMost(e.height * 0.6f)
        drawReflection(liquid, e.feetX, e.y, e.height, if (depth > 2f) depth else 0f, t, quality, e.colourAt(e.feetX, e.y)) { c -> e.draw(this, c) }
    }
    for (e in entries) {
        val depth = (liquid.depthAt(e.feetX, e.y) * liquid.maxDepth * stage.perspective.scale(e.y)).coerceAtMost(e.height * 0.6f)
        val colour = e.colourAt(e.feetX, e.y)
        if (depth > 2f) {
            drawStandingInWater(liquid, e.feetX, e.y, e.height, depth, t, quality, colour) { c -> e.draw(this, c) }
        } else {
            drawCastShadows(rig, e.feetX, e.y, e.height, t, 0.8f, quality) { c -> e.draw(this, c) }
            withTransform({ translate(e.feetX, e.y - e.lift) }) { e.draw(this, colour) }
        }
        e.flame?.invoke()
        // Lampião's lamp hangs from his fist.
        if (e.feetX == bearer.feetX && e.y == bearer.feetY) drawArt(lampImage, Offset(fist.x - Lamp.handle.x * lampImage.width * lampK, fist.y - Lamp.handle.y * lampHeight), lampK, rig.shade(dark, lit, s.lamp.x, s.lamp.y, t))
    }
    // The lights' glitter on the water, over everything.
    drawGlitter(liquid, s.fire.x, s.fire.y, s.fire.height, Color(0xFFFFA63C), 0.8f, t, quality)
    drawGlitter(liquid, s.lamp.x, s.lamp.y, s.lamp.height, Color(0xFFFFD08A), 0.55f, t, quality)
    drawLightBulbs(rig, t)
    drawPondShore(art, liquid, t, day, wind)
}

/** Fish under the surface (dim, with rings where one rises), and lily pads and flowers floating, bobbing with the water. */
private fun DrawScope.drawPondLife(art: Sprites, liquid: Liquid, t: Float, day: Float, wind: br.com.faru.boitata.core.wind.Wind) {
    val deep = lerp(Color(0xFF0E1A1A), Color(0xFF2C4A48), day)
    clipToLiquid(liquid) {
        // Two fish circling slowly below the surface.
        for (i in 0..1) {
            val fish = art.art("pond_fish_${i + 1}")
            val a = t * (0.18f + 0.05f * i) + i * 2.4f
            val x = liquid.cx + liquid.rx * 0.6f * cos(a); val y = liquid.cy + liquid.ry * (0.15f + 0.35f * i) * sin(a)
            val fw = liquid.rx * 0.22f; val k = fw / fish.width
            val facing = if (-sin(a) * (0.18f + 0.05f * i) >= 0f) 1f else -1f
            withTransform({ translate(x, y); scale(facing, 1f, Offset.Zero) }) { drawArt(fish, Offset(-fw / 2f, -fish.height * k / 2f), k, deep.copy(alpha = 0.55f)) }
        }
        // Rings where a fish rises: each ring grows and fades over two seconds, every few seconds in another place.
        for (i in 0..2) {
            val ring = art.art("pond_rings_${i + 1}")
            val period = 5f + i * 1.7f; val life = ((t + i * 1.9f) % period) / 2.2f
            if (life >= 1f) continue
            val n = ((t + i * 1.9f) / period).toInt()
            val rx = liquid.cx + liquid.rx * 0.7f * (((n * 37 + i * 11) % 100) / 50f - 1f); val ry = liquid.cy + liquid.ry * 0.5f * (((n * 53 + i * 29) % 100) / 50f - 1f)
            val rw = liquid.rx * (0.1f + 0.25f * life)
            drawArt(ring, Offset(rx - rw / 2f, ry - ring.height * rw / ring.width / 2f), rw / ring.width, Color(0xFFDDEBEE), alpha = 0.7f * (1f - life))
        }
        // Lily pads and flowers on the surface.
        val green = lerp(Color(0xFF16291F), Color(0xFF4E7F58), day)
        for (i in 0 until 8) {
            val pad = art.art("pond_lilypad_${i % 3 + 1}")
            val px = liquid.cx + liquid.rx * (0.78f * (((i * 41) % 100) / 50f - 1f)); val py = liquid.cy + liquid.ry * (0.7f * (((i * 67 + 13) % 100) / 50f - 1f))
            val pw = liquid.rx * (0.13f + 0.04f * (i % 3)); val k = pw / pad.width
            val bob = sin(t * 0.9f + i * 1.7f) * liquid.ry * 0.012f
            drawArt(pad, Offset(px - pw / 2f + sin(t * 0.2f + i) * 3f, py - pad.height * k / 2f + bob), k, green)
            if (i % 4 == 1) { val fl = art.art("pond_lily_flower"); val fw = pw * 0.45f; drawArt(fl, Offset(px - fw / 2f, py - fl.height * fw / fl.width + bob), fw / fl.width, lerp(Color(0xFF8A6A70), Color(0xFFF3D6DA), day)) }
        }
    }
}

/** The near shore: stones and reeds around the water that sway in the wind, and a mud bank along the bottom of the picture. */
private fun DrawScope.drawPondShore(art: Sprites, liquid: Liquid, t: Float, day: Float, wind: br.com.faru.boitata.core.wind.Wind) {
    val h = size.height; val w = size.width
    val reedTint = lerp(Color(0xFF0F1A13), Color(0xFF3E5A3C), day); val stoneTint = lerp(Color(0xFF15191A), Color(0xFF5C625F), day)
    for (i in 0 until 8) {
        val a = PI.toFloat() * (0.05f + i * 0.125f)                         // along the near half of the shore
        val sx = liquid.cx + liquid.rx * 1.02f * cos(a); val sy = liquid.cy + liquid.ry * 1.02f * sin(a)
        val stone = art.art("pond_stone_${i % 5 + 1}"); val sw = w * (0.035f + 0.012f * (i % 3))
        drawArt(stone, Offset(sx - sw / 2f, sy - stone.height * sw / stone.width * 0.8f), sw / stone.width, stoneTint)
    }
    for (i in 0 until 7) {
        val a = PI.toFloat() * (if (i % 2 == 0) 0.02f + 0.06f * i else 1.0f - 0.05f * i) * (if (i < 4) 1f else -0.0f + 1f)
        val left = i % 2 == 0
        val sx = liquid.cx + (if (left) -1f else 1f) * liquid.rx * (0.9f + 0.1f * ((i * 37) % 10) / 10f)
        val sy = liquid.cy + liquid.ry * (0.15f + 0.12f * i) * (if (i % 3 == 0) -1f else 1f)
        val reeds = art.art("pond_reeds_${i % 3 + 1}"); val rh = h * (0.17f + 0.04f * (i % 3)); val rw = rh * reeds.width / reeds.height
        val bend = WindResponse.Plant.bend(wind, t, sx / w, 0.3f + i * 0.4f)
        withTransform({ translate(sx, sy) }) { sway(0f, 0f, bend, maxLean = 0.12f) { drawArt(reeds, Offset(-rw / 2f, -rh), rh / reeds.height, reedTint) } }
    }
    val bank = art.art("pond_mud_bank"); val bh = h * 0.1f
    drawArt(bank, Offset(0f, h - bh), 1f, lerp(Color(0xFF0C0B09), Color(0xFF3A2D22), day), scaleX = w / bank.width, scaleY = bh / bank.height)
}
