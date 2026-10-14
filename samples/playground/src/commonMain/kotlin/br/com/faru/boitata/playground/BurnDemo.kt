package br.com.faru.boitata.playground

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import br.com.faru.boitata.audio.AudioSink
import br.com.faru.boitata.audio.BoitataAudio
import br.com.faru.boitata.core.Perspective
import br.com.faru.boitata.core.burn.BodyGeometry
import br.com.faru.boitata.core.burn.BurnStatus
import br.com.faru.boitata.core.burn.FireBody
import br.com.faru.boitata.core.burn.FireKind
import br.com.faru.boitata.core.burn.FireWorld
import br.com.faru.boitata.core.burn.Fuel
import br.com.faru.boitata.core.burn.RunFireWorld
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.clock.rememberSceneQuality
import br.com.faru.boitata.core.depth.DepthItem
import br.com.faru.boitata.core.depth.DepthStage
import br.com.faru.boitata.core.draw.sway
import br.com.faru.boitata.core.sky.SkyWeather
import br.com.faru.boitata.core.wind.Wind
import br.com.faru.boitata.core.wind.WindResponse
import br.com.faru.boitata.core.wind.rememberSceneWind
import br.com.faru.boitata.light.LightSource
import br.com.faru.boitata.light.drawBurningBody
import br.com.faru.boitata.light.drawCastShadow
import br.com.faru.boitata.light.fireLight
import br.com.faru.boitata.light.tint
import br.com.faru.boitata.scene.depthDrag
import br.com.faru.boitata.weather.Rain
import kotlin.math.hypot

/** A thing in the burn scene: what it is made of, how the wind moves it, and where it starts (as shares of the ground). */
private class BurnProp(val id: String, val sprite: String?, val heightShare: Float, val x: Float, val y: Float, val fuel: Fuel, val wind: WindResponse = WindResponse.Rigid)

private val BurnProps = listOf(
    BurnProp("campfire", null, 0.1f, 0.26f, 0.76f, Fuel.Campfire),
    BurnProp("fern", "fern", 0.11f, 0.40f, 0.80f, Fuel.DryGrass, WindResponse.Grass),
    BurnProp("crate", "crate", 0.15f, 0.54f, 0.78f, Fuel.Wood),
    BurnProp("stool", "stool", 0.19f, 0.72f, 0.86f, Fuel.Wood),
    BurnProp("plant", "plant", 0.24f, 0.90f, 0.72f, Fuel.Plant, WindResponse.Plant),
    BurnProp("araucaria", "araucaria", 0.5f, 0.11f, 0.42f, Fuel.Pine, WindResponse.Pine),
    BurnProp("palm", "palm", 0.46f, 0.80f, 0.26f, Fuel.Palm, WindResponse.Palm),
    BurnProp("lantern", "lantern", 0.4f, 0.56f, 0.48f, Fuel.None),
    BurnProp("figure", "figure", 0.32f, 0.42f, 0.30f, Fuel.None),
)

/** The stage the things stand on and the fire world that burns them, built together so each thing's flames follow it as it is dragged. */
private class BurnSetup(val stage: DepthStage, val world: FireWorld)

private fun buildBurn(art: Sprites, size: IntSize): BurnSetup {
    val w = size.width.toFloat(); val h = size.height.toFloat()
    val top = h * (ART_HORIZON + 0.06f); val bottom = h * 0.97f
    val perspective = Perspective(frontEdgeY = bottom, backEdgeY = top, depth = 6f, eyeBack = 3f, eyeHeight = 2.5f, pitchDegrees = 28f, referenceY = bottom - 0.3f * (bottom - top))
    val stage = DepthStage(perspective, w * 0.03f, top, w * 0.97f, bottom)
    val world = FireWorld()
    for (p in BurnProps) {
        val image = if (p.sprite == null) art.logs else art.prop(p.sprite)
        val item = stage.add(DepthItem(p.id, w * p.x, top + (bottom - top) * p.y, h * p.heightShare, image.width.toFloat() / image.height))
        val flameHeight = if (p.id == "campfire") 1.9f else 1f
        world.add(FireBody(p.id, p.fuel) { BodyGeometry(item.feetX, item.feetY, stage.height(item) * item.aspect, stage.height(item) * flameHeight) })
    }
    world.ignite("campfire")
    return BurnSetup(stage, world)
}

/**
 * A campfire in the woods and things you can drag to it. Wind carries the fire's heat downwind, so a crate to the right of the fire in a wind blowing right slowly warms, smokes, catches, chars and
 * burns out, and sets the next thing alight; upwind it stays safe. Dry grass catches first, wood later, a pine last. [rain] wets things and puts fires out; [guardian] makes it the Boitatá's fire,
 * which spares every living plant and puts out ordinary fires it touches. [resetKey] changing starts the scene again. [timeScale] runs the fire faster than the clock (a time-lapse).
 */
@Composable
fun BurnScene(
    seconds: () -> Float,
    modifier: Modifier = Modifier.fillMaxSize(),
    rain: Float = 0f,
    guardian: Boolean = false,
    resetKey: Int = 0,
    timeScale: Float = 1f,
    hour: Float = 23.5f,
    sink: AudioSink? = null,
    sound: Boolean = false,
) {
    val art = LocalSprites.current ?: return
    val quality = rememberSceneQuality()
    val sceneWind = rememberSceneWind()
    var size by remember { mutableStateOf(IntSize.Zero) }
    val setup = remember(size, art, resetKey) { if (size.width == 0) null else buildBurn(art, size) }
    val currentRain = rememberUpdatedState(rain)
    if (setup != null) {
        RunFireWorld(setup.world, rain = { currentRain.value }, timeScale = timeScale)
        androidx.compose.runtime.LaunchedEffect(setup, guardian) { setup.world.ignite("campfire", if (guardian) FireKind.Guardian else FireKind.Ordinary) }
    }
    Box(modifier.onSizeChanged { size = it }.let { if (setup != null) it.depthDrag(setup.stage) else it }) {
        Spacer(Modifier.fillMaxSize().drawBehind { if (setup != null) drawBurn(art, setup, seconds(), hour, quality(), sceneWind.wind, rain) })
        if (rain > 0.01f) Rain(Modifier.fillMaxSize(), intensity = rain, seconds = seconds)
        if (setup != null && sink != null) {
            // The crackle grows with how much there is burning (quantised, so it recomposes a few times a second at most).
            val crackle by remember(setup) { derivedStateOf { ((setup.world.totalFlame / 3f).coerceIn(0f, 1f) * 20).toInt() / 20f } }
            BoitataAudio(sink, weather = SkyWeather(precipitation = rain, cloud = rain), fire = crackle, enabled = sound)
        }
    }
}

private fun DrawScope.drawBurn(art: Sprites, setup: BurnSetup, t: Float, hour: Float, quality: Quality, wind: Wind, rain: Float) {
    val stage = setup.stage; val world = setup.world
    val day = drawWoodsBackdrop(art, t, hour)
    val dark = lerp(Color(0xFF2A3A33), Color(0xFF3D5045), day); val lit = Color(0xFFD9A867)

    // The light is the campfire's, as big as it is bright; things nearer it are warmer.
    val campItem = stage.items.first { it.id == "campfire" }; val camp = world.body("campfire")!!
    val campHeight = stage.height(campItem)
    val glowAmount = 0.3f + 0.7f * camp.burn
    val lamp = Offset(campItem.feetX, campItem.feetY - campHeight * 0.8f)
    val light = fireLight(lamp.x, lamp.y, t, reach = size.width * 0.5f * glowAmount, strength = 0.95f * glowAmount, color = if (camp.kind == FireKind.Guardian) Color(0xFFFFC46B) else Color(0xFFFFA63C), groundX = campItem.feetX, groundY = campItem.feetY)
    drawCircle(Brush.radialGradient(0f to Color(0x55FFA63C).copy(alpha = 0.33f * glowAmount), 1f to Color.Transparent, center = Offset(campItem.feetX, campItem.feetY), radius = light.reach * 0.9f), radius = light.reach * 0.9f, center = Offset(campItem.feetX, campItem.feetY), blendMode = BlendMode.Plus)

    for (item in stage.ordered()) {
        val prop = BurnProps.first { it.id == item.id }
        val body = world.body(item.id)!!
        val image = if (prop.sprite == null) art.logs else art.prop(prop.sprite)
        val height = stage.height(item)
        val k = height / image.height
        val lifted = item.lift * height * 0.05f
        if (item.id != "campfire") drawCastShadow(light, item.feetX, item.feetY, height, 0.7f * (1f - 0.25f * item.lift), quality) { c -> drawArt(image, Offset(-image.width * k / 2f, -height), k, c) }
        val near = (1f - hypot(item.feetX - lamp.x, item.feetY - height * 0.5f - lamp.y) / light.reach).coerceIn(0f, 1f) * light.strength
        val base = lerp(dark, lit, (0.2f + 0.8f * near * near).coerceAtMost(1f))
        // Fire has the last word on colour: warming, glowing, charred. A thing that has burnt out no longer sways: there is nothing left to bend.
        val colour = body.tint(base)
        val bend = if (body.status == BurnStatus.Spent) 0f else prop.wind.bend(wind, t, item.feetX / size.width, phase = item.id.length * 0.21f) * (1f - body.consumed)
        sway(item.feetX, item.feetY - lifted, bend, maxLean = 0.12f) { drawArt(image, Offset(item.feetX - image.width * k / 2f, item.feetY - height - lifted), k, colour) }
        drawBurningBody(body, BodyGeometry(item.feetX, item.feetY - lifted, height * item.aspect, height * (if (item.id == "campfire") 1.9f else 1f)), t, wind, quality)
    }
    // Rings of carved arcs show the fire's glow, as everywhere else.
    for (r in 1..3) drawCircle(Color(0xFFF2A83B).copy(alpha = 0.28f / r * glowAmount), radius = campHeight * 0.9f * r, center = lamp, style = Stroke(2f))
}
