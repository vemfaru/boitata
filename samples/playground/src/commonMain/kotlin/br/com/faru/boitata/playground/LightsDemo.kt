package br.com.faru.boitata.playground

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import br.com.faru.boitata.core.Perspective
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.clock.rememberSceneQuality
import br.com.faru.boitata.core.depth.DepthItem
import br.com.faru.boitata.core.depth.DepthStage
import br.com.faru.boitata.core.sky.MoonPhase
import br.com.faru.boitata.light.DirectionalLight
import br.com.faru.boitata.light.FlamePalette
import br.com.faru.boitata.light.LightRig
import br.com.faru.boitata.light.Lights
import br.com.faru.boitata.light.PointLight
import br.com.faru.boitata.light.SpotLight
import br.com.faru.boitata.light.drawCastShadows
import br.com.faru.boitata.light.drawFlame
import br.com.faru.boitata.light.drawLightBulbs
import br.com.faru.boitata.light.drawLightPools
import br.com.faru.boitata.scene.depthDrag
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The sources a [LightsScene] can have on. */
enum class LightKind(val label: String) { Sun("Sun"), Moon("Moon"), Fire("Campfire"), Lamp("Hurricane lamp"), Candle("Candle"), Lantern("Lantern (spot)") }

/** A thing on the ground: which picture (null is the candle, drawn in code; "logs" is the campfire's), how tall it is at the reference depth (a share of the height) and where it starts. */
private class LightProp(val id: String, val sprite: String?, val heightShare: Float, val x: Float, val y: Float)

private val LightProps = listOf(
    LightProp("araucaria", "araucaria", 0.50f, 0.10f, 0.30f),
    LightProp("palm", "palm", 0.46f, 0.90f, 0.24f),
    LightProp("post", "lantern", 0.42f, 0.84f, 0.80f),
    LightProp("crate", "crate", 0.15f, 0.68f, 0.50f),
    LightProp("lampiao", "lampiao", 0.34f, 0.30f, 0.56f),
    LightProp("maria_bonita", "maria_bonita", 0.34f, 0.46f, 0.66f),
    LightProp("cacador", "cacador", 0.34f, 0.62f, 0.62f),
    LightProp("campfire", "logs", 0.09f, 0.50f, 0.84f),
    LightProp("candle", null, 0.07f, 0.20f, 0.88f),
)

/** The animals that wander the stage by themselves: none of them are draggable and none of them can burn. */
private val Animals = listOf(
    Creature("tamandua", "tamandua", "tamandua_walk", 0.15f, Gait.Patrol(0.08f, 0.55f, 0.92f, 0.07f, pause = 2.5f)),
    Creature("onca", "onca", null, 0.17f, Gait.Patrol(0.92f, 0.45f, 0.72f, 0.06f, pause = 3f, phase = 0.3f)),
    Creature("capivara", "capivara", null, 0.13f, Gait.Patrol(0.15f, 0.40f, 0.42f, 0.03f, pause = 4f, phase = 0.6f)),
    Creature("arara", "arara", "arara_fly", 0.13f, Gait.Fly(0.5f, 2.2f, 9f, rest = 3f)),
)

/** The lights, the stage they light and the swing of the lamp Lampião carries, built together. */
private class LightsSetup(val stage: DepthStage, val rig: LightRig, val sun: DirectionalLight, val moon: DirectionalLight, val fire: PointLight, val lamp: PointLight, val candle: PointLight, val lantern: SpotLight) {
    val swing = LampSwing()
    val poses = Array(Animals.size) { Pose() }
}

private fun buildLights(art: Sprites, size: IntSize): LightsSetup {
    val w = size.width.toFloat(); val h = size.height.toFloat()
    val top = h * (ART_HORIZON + 0.06f); val bottom = h * 0.97f
    val perspective = Perspective(frontEdgeY = bottom, backEdgeY = top, depth = 6f, eyeBack = 3f, eyeHeight = 2.5f, pitchDegrees = 28f, referenceY = bottom - 0.3f * (bottom - top))
    val stage = DepthStage(perspective, w * 0.03f, top, w * 0.97f, bottom)
    for (p in LightProps) {
        val aspect = when (p.sprite) { null -> 0.3f; "logs" -> art.logs.width.toFloat() / art.logs.height; else -> art.prop(p.sprite).let { it.width.toFloat() / it.height } }
        stage.add(DepthItem(p.id, w * p.x, top + (bottom - top) * p.y, h * p.heightShare, aspect))
    }
    val person = h * 0.34f
    val rig = LightRig()
    val sun = rig.add(Lights.sun(12f)) as DirectionalLight
    val moon = rig.add(Lights.moon(0f)) as DirectionalLight
    val fire = rig.add(Lights.fire(0f, 0f, person)) as PointLight
    val lamp = rig.add(Lights.hurricaneLamp(0f, 0f, person)) as PointLight
    val candle = rig.add(Lights.candle(0f, 0f, person)) as PointLight
    val lantern = rig.add(Lights.lantern(0f, 0f, 0f, person)) as SpotLight
    return LightsSetup(stage, rig, sun, moon, fire, lamp, candle, lantern)
}

private fun DepthStage.item(id: String): DepthItem = items.first { it.id == id }

/**
 * A yard lit by every kind of light at once. The sun and the moon are far away, so their shadows are parallel and follow the hour (long at dawn, short at noon, none at night); the campfire is low
 * and unsteady, the candle small, the hurricane lamp (carried by Lampião) steady, and the lantern on its post throws a cone. Each light throws its own shadow of everything, in its own direction,
 * as dark as that light's share of all the light there, and lights things in its own colour.
 *
 * Drag anything: the fire, the candle and the lantern post are the lights, so moving them swings the shadows; Lampião carries his lamp. The animals wander by themselves and are never lit by any fire
 * (they cannot burn). [on] says which lights are lit.
 */
@Composable
fun LightsScene(
    seconds: () -> Float,
    modifier: Modifier = Modifier.fillMaxSize(),
    hour: Float = 19f,
    moon: MoonPhase = MoonPhase.Full,
    southern: Boolean = true,
    on: Set<LightKind> = LightKind.entries.toSet(),
) {
    val art = LocalSprites.current ?: return
    val quality = rememberSceneQuality()
    var size by remember { mutableStateOf(IntSize.Zero) }
    val setup = remember(size, art) { if (size.width == 0) null else buildLights(art, size) }
    Box(modifier.onSizeChanged { size = it }.let { if (setup != null) it.depthDrag(setup.stage) else it }) {
        Spacer(Modifier.fillMaxSize().drawBehind { if (setup != null) drawLights(art, setup, seconds(), hour, moon, southern, on, quality()) })
    }
}

private fun DrawScope.drawLights(art: Sprites, s: LightsSetup, t: Float, hour: Float, phase: MoonPhase, southern: Boolean, on: Set<LightKind>, quality: Quality) {
    val w = size.width; val h = size.height
    val stage = s.stage; val rig = s.rig
    val day = drawWoodsBackdrop(art, t, hour)
    val v = cover(); val horizon = v.py(1080f * ART_HORIZON)

    // The sun and moon are lights and are drawn where they are in the sky; their shadows come from the same numbers.
    s.sun.followSun(hour); s.sun.enabled = LightKind.Sun in on
    s.moon.followMoon(hour, phase); s.moon.enabled = LightKind.Moon in on
    if (s.sun.enabled && s.sun.arc >= 0f) drawSunArt(art, Offset(w * (0.08f + 0.84f * s.sun.arc), horizon * (1f - 0.8f * sin(PI.toFloat() * s.sun.arc) + 0.05f)), h * 0.04f, Color(0xFFFFE9B0))
    if (s.moon.enabled && s.moon.arc >= 0f) drawMoonArt(art, Offset(w * (0.08f + 0.84f * s.moon.arc), horizon * (1f - 0.8f * sin(PI.toFloat() * s.moon.arc) + 0.05f)), h * 0.034f, Color(0xFFE9EEF8), phase, southern)
    rig.ambient = 0.06f + 0.5f * day

    // The lights that are things: the fire, the candle and the lantern follow where you put them; the lamp follows Lampião's hand.
    val fireItem = stage.item("campfire"); val candleItem = stage.item("candle"); val post = stage.item("post")
    s.fire.enabled = LightKind.Fire in on
    s.fire.x = fireItem.feetX; s.fire.y = fireItem.feetY; s.fire.height = stage.height(fireItem) * 1.4f; s.fire.reach = w * 0.5f
    s.candle.enabled = LightKind.Candle in on
    s.candle.x = candleItem.feetX; s.candle.y = candleItem.feetY; s.candle.height = stage.height(candleItem) * 1.1f; s.candle.reach = w * 0.2f
    val postHeight = stage.height(post); val postWidth = postHeight * post.aspect
    s.lantern.enabled = LightKind.Lantern in on
    s.lantern.x = post.feetX + 0.32f * postWidth; s.lantern.y = post.feetY; s.lantern.height = postHeight * 0.6f; s.lantern.reach = w * 0.5f
    s.lantern.aim = (if (post.feetX < w / 2f) 0.3f else PI.toFloat() - 0.3f)

    val bearer = stage.item("lampiao"); val bearerImage = art.prop("lampiao"); val bearerHeight = stage.height(bearer)
    val hand = HandAnchors.getValue("lampiao")
    val fistUp = bearerHeight * (1f - hand.y) + bearer.lift * bearerHeight * 0.05f
    val fist = Offset(bearer.feetX + bearerHeight * bearerImage.width / bearerImage.height * (hand.x - 0.5f), bearer.feetY - fistUp)
    val lampImage = art.prop("lampiao_lamp"); val lampHeight = bearerHeight * Lamp.OF_HOLDER; val lampK = lampHeight / lampImage.height
    val angle = s.swing.step(t, fist.x, lampHeight)
    val fx = (Lamp.flame.x - Lamp.handle.x) * lampImage.width * lampK; val fy = (Lamp.flame.y - Lamp.handle.y) * lampHeight
    val flame = Offset(fist.x + fx * cos(angle) + fy * sin(angle), fist.y - fx * sin(angle) + fy * cos(angle))
    s.lamp.enabled = LightKind.Lamp in on
    s.lamp.x = flame.x; s.lamp.y = bearer.feetY + bearerHeight * 0.03f; s.lamp.height = (s.lamp.y - flame.y).coerceAtLeast(bearerHeight * 0.1f); s.lamp.reach = w * 0.4f

    // Light on the ground first, then everything standing on it, nearest last.
    drawLightPools(rig, t, bulbs = false)
    val dark = lerp(Color(0xFF1A2420), Color(0xFF3D5045), day); val lit = lerp(Color(0xFFE6DCC4), Color(0xFFEFE9DA), day)   // pale, so each light's own colour is what shows

    // The animals' places this instant, so they can be put in order with the things.
    val ground = Triple(stage.groundLeft, stage.groundRight, stage.groundTop)
    for ((i, c) in Animals.withIndex()) {
        val strip = c.strip?.let { art.strips.getValue(it) }
        val aspect = strip?.aspect ?: art.prop(c.sprite).let { it.width.toFloat() / it.height }
        val height = h * c.heightShare * stage.perspective.scale(ground.third + (stage.groundBottom - ground.third) * ((c.gait as? Gait.Patrol)?.y ?: (c.gait as Gait.Fly).y))
        c.poseAt(t, ground.first, ground.second, stage.groundTop, stage.groundBottom, height * aspect, s.poses[i])
    }
    val things = stage.ordered()
    var next = 0
    val animalOrder = Animals.indices.sortedBy { s.poses[it].y }
    var nextAnimal = 0
    while (next < things.size || nextAnimal < animalOrder.size) {
        val drawAnimal = nextAnimal < animalOrder.size && (next >= things.size || s.poses[animalOrder[nextAnimal]].y < things[next].feetY)
        if (drawAnimal) {
            val i = animalOrder[nextAnimal++]
            val c = Animals[i]; val pose = s.poses[i]
            val height = h * c.heightShare * stage.perspective.scale(pose.y)
            drawBeing(pose.x, pose.y, height + pose.lift, rig, t, dark, lit, quality, 0.65f) { colour -> drawCreature(art, c, pose, height, colour) }
        } else {
            val item = things[next++]
            val prop = LightProps.first { it.id == item.id }
            val height = stage.height(item)
            val lifted = item.lift * height * 0.05f
            val image = when (prop.sprite) { null -> null; "logs" -> art.logs; else -> art.prop(prop.sprite) }
            val k = if (image != null) height / image.height else 1f
            drawBeing(item.feetX, item.feetY, height, rig, t, dark, lit, quality, 0.7f * (1f - 0.25f * item.lift), lifted) { colour ->
                if (image != null) drawArt(image, Offset(-image.width * k / 2f, -height), k, colour)
                else {
                    // The candle: a stub of wax with a wick.
                    val cw = height * 0.3f
                    drawRect(colour, Offset(-cw / 2f, -height * 0.88f), Size(cw, height * 0.88f))
                    drawRect(colour, Offset(-cw * 0.06f, -height), Size(cw * 0.12f, height * 0.12f))
                }
            }
            // The flames of the things that burn, and Lampião's lamp in his hand.
            if (item.id == "campfire" && s.fire.enabled) drawFlame(item.feetX, item.feetY - height * 0.1f, height * 1.2f, height * 2.1f, t, 0.9f, FlamePalette.Campfire, 3L, quality)
            if (item.id == "candle" && s.candle.enabled) drawFlame(item.feetX, item.feetY - height * 0.95f, height * 0.16f, height * 0.4f, t, 0.8f, FlamePalette.Campfire, 4L, quality)
            if (item === bearer) rotate(-angle * 180f / PI.toFloat(), pivot = fist) {
                drawArt(lampImage, Offset(fist.x - Lamp.handle.x * lampImage.width * lampK, fist.y - Lamp.handle.y * lampHeight), lampK, rig.shade(dark, lit, s.lamp.x, s.lamp.y, t))
            }
        }
    }
    drawLightBulbs(rig, t)
}

/** One thing on the ground: every shadow it throws, then the thing itself, coloured by the light that reaches it. [draw] draws it with its feet at (0, 0) and up negative. */
private inline fun DrawScope.drawBeing(
    feetX: Float, feetY: Float, height: Float, rig: LightRig, t: Float, dark: Color, lit: Color, quality: Quality, shadow: Float, lifted: Float = 0f,
    crossinline draw: DrawScope.(Color) -> Unit,
) {
    drawCastShadows(rig, feetX, feetY, height, t, shadow, quality) { c -> draw(c) }
    val colour = rig.shade(dark, lit, feetX, feetY, t)
    withTransform({ translate(feetX, feetY - lifted) }) { draw(colour) }
}
