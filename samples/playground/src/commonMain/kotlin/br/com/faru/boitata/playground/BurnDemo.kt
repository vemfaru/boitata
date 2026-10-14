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
import androidx.compose.ui.graphics.drawscope.withTransform
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
import br.com.faru.boitata.core.burn.Material
import br.com.faru.boitata.core.burn.PartRegion
import br.com.faru.boitata.core.burn.PartSpec
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
import br.com.faru.boitata.light.drawFireAffected
import br.com.faru.boitata.light.drawCastShadow
import br.com.faru.boitata.light.fireLight
import br.com.faru.boitata.scene.depthDrag
import br.com.faru.boitata.weather.Rain
import kotlin.math.hypot

/** What a thing looks like: one of the sprites from the art sheet, or a picture drawn in code (a stand-in until there is art). Both draw with the feet at (0, 0), up negative. */
private sealed interface BurnArt {
    class Sprite(val name: String?) : BurnArt
    class Drawn(val aspect: Float, val draw: DrawScope.(colour: Color, height: Float) -> Unit) : BurnArt
}

/**
 * A thing in the burn scene, as configuration: its picture, what it is made of ([material], or several [parts] laid over its picture), how the wind moves it, where it starts (as shares of the
 * ground), the colour it leans toward ([tone]) and which of its bodies starts alight ([lit]). New things, with new art, are one more entry here.
 */
private class BurnProp(
    val id: String, val art: BurnArt, val heightShare: Float, val x: Float, val y: Float, val material: Material,
    val wind: WindResponse = WindResponse.Rigid, val parts: List<PartSpec> = emptyList(), val tone: Color? = null, val lit: String? = null,
)

private fun sprite(name: String?, heightShare: Float, x: Float, y: Float, material: Material, wind: WindResponse = WindResponse.Rigid) =
    BurnProp(name ?: "campfire", BurnArt.Sprite(name), heightShare, x, y, material, wind)

/** A candle: a slab of wax with a wick on top. Only the wick burns; the wax melts under it. */
private val CandleArt = BurnArt.Drawn(0.3f) { c, h ->
    val w = h * 0.3f
    drawRect(c, Offset(-w / 2f, -h * 0.88f), androidx.compose.ui.geometry.Size(w, h * 0.88f))
    drawRect(c, Offset(-w * 0.06f, -h), androidx.compose.ui.geometry.Size(w * 0.12f, h * 0.12f))
}

/** A sheet of paper standing up, with lines of writing. */
private val PaperArt = BurnArt.Drawn(0.75f) { c, h ->
    val w = h * 0.75f
    drawRect(c, Offset(-w / 2f, -h), androidx.compose.ui.geometry.Size(w, h))
    for (i in 1..5) drawLine(Color(0x55000000), Offset(-w * 0.38f, -h + h * 0.15f * i), Offset(w * 0.38f, -h + h * 0.15f * i), 1.5f)
}

/** A hanging cloth, longer than wide, with a folded hem. */
private val ClothArt = BurnArt.Drawn(0.6f) { c, h ->
    val w = h * 0.6f
    drawRect(c, Offset(-w / 2f, -h), androidx.compose.ui.geometry.Size(w, h))
    drawRect(Color(0x33000000), Offset(-w / 2f, -h * 0.16f), androidx.compose.ui.geometry.Size(w, h * 0.04f))
}

private val BurnProps = listOf(
    sprite(null, 0.1f, 0.26f, 0.76f, Material.Campfire),
    sprite("fern", 0.11f, 0.40f, 0.80f, Material.DryGrass, WindResponse.Grass),
    sprite("crate", 0.15f, 0.54f, 0.78f, Material.Wood),
    sprite("stool", 0.19f, 0.72f, 0.86f, Material.Wood),
    sprite("plant", 0.24f, 0.90f, 0.72f, Material.Plant, WindResponse.Plant),
    sprite("araucaria", 0.5f, 0.11f, 0.42f, Material.Pine, WindResponse.Pine),
    sprite("palm", 0.46f, 0.80f, 0.26f, Material.Palm, WindResponse.Palm),
    sprite("lantern", 0.4f, 0.56f, 0.48f, Material.Metal),
    sprite("figure", 0.32f, 0.42f, 0.30f, Material.None),
    BurnProp("candle", CandleArt, 0.13f, 0.34f, 0.90f, Material.Wax, tone = Color(0xFFEADFC4), lit = "candle.wick", parts = listOf(
        PartSpec("wax", Material.Wax, PartRegion(0f, 0.12f, 1f, 1f)),
        PartSpec("wick", Material.Wick, PartRegion(0.44f, 0f, 0.56f, 0.12f), fedBy = "wax"),
    )),
    BurnProp("paper", PaperArt, 0.12f, 0.48f, 0.90f, Material.Paper, WindResponse.Grass, tone = Color(0xFFE8DDBF)),
    BurnProp("cloth", ClothArt, 0.16f, 0.64f, 0.88f, Material.Cloth, WindResponse.Plant, tone = Color(0xFF9B4A3C)),
)

/** The stage the things stand on and the fire world that burns them, built together so each thing's flames follow it as it is dragged. */
private class BurnSetup(val stage: DepthStage, val world: FireWorld, val parts: Map<String, List<FireBody>>)

private fun BurnProp.aspect(art: Sprites): Float = when (val a = this.art) {
    is BurnArt.Drawn -> a.aspect
    is BurnArt.Sprite -> (if (a.name == null) art.logs else art.prop(a.name)).let { it.width.toFloat() / it.height }
}

private fun buildBurn(art: Sprites, size: IntSize): BurnSetup {
    val w = size.width.toFloat(); val h = size.height.toFloat()
    val top = h * (ART_HORIZON + 0.06f); val bottom = h * 0.97f
    val perspective = Perspective(frontEdgeY = bottom, backEdgeY = top, depth = 6f, eyeBack = 3f, eyeHeight = 2.5f, pitchDegrees = 28f, referenceY = bottom - 0.3f * (bottom - top))
    val stage = DepthStage(perspective, w * 0.03f, top, w * 0.97f, bottom)
    val world = FireWorld()
    val others = HashMap<String, List<FireBody>>()
    for (p in BurnProps) {
        val item = stage.add(DepthItem(p.id, w * p.x, top + (bottom - top) * p.y, h * p.heightShare, p.aspect(art)))
        val flameHeight = if (p.id == "campfire") 1.9f else 1f
        val geometry = { BodyGeometry(item.feetX, item.feetY, stage.height(item) * item.aspect, stage.height(item) * flameHeight) }
        if (p.parts.isEmpty()) world.add(FireBody(p.id, p.material, geometry = geometry))
        else others[p.id] = world.addComposite(p.id, geometry, p.parts).drop(1)
        p.lit?.let { world.ignite(it) }
    }
    world.ignite("campfire")
    return BurnSetup(stage, world, others)
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
        val height = stage.height(item)
        val lifted = item.lift * height * 0.05f
        val image = (prop.art as? BurnArt.Sprite)?.let { if (it.name == null) art.logs else art.prop(it.name) }
        val k = if (image != null) height / image.height else 1f
        // One picture for the shadow and the fire to work from, whatever the thing is: a sprite, or something drawn in code.
        val silhouette: DrawScope.(Color) -> Unit = { c ->
            when (val a = prop.art) {
                is BurnArt.Sprite -> drawArt(image!!, Offset(-image.width * k / 2f, -height), k, c)
                is BurnArt.Drawn -> a.draw(this, c, height)
            }
        }
        if (item.id != "campfire") drawCastShadow(light, item.feetX, item.feetY, height, 0.7f * (1f - 0.25f * item.lift), quality) { c -> withTransform({ translate(0f, 0f) }) { silhouette(c) } }
        val near = (1f - hypot(item.feetX - lamp.x, item.feetY - height * 0.5f - lamp.y) / light.reach).coerceIn(0f, 1f) * light.strength
        val lighting = lerp(dark, lit, (0.2f + 0.8f * near * near).coerceAtMost(1f))
        val base = prop.tone?.let { lerp(lighting, it, 0.55f) } ?: lighting
        // Fire has the last word: the material says how it warms, burns, melts or glows, and a burnt thing no longer sways: there is nothing left to bend.
        val bend = if (body.status == BurnStatus.Spent) 0f else prop.wind.bend(wind, t, item.feetX / size.width, phase = item.id.length * 0.21f) * (1f - body.consumed)
        val box = BodyGeometry(item.feetX, item.feetY - lifted, height * item.aspect, height)
        sway(item.feetX, item.feetY - lifted, bend, maxLean = 0.12f) { drawFireAffected(body, box, t, wind, base, quality, parts = setup.parts[item.id].orEmpty(), silhouette = silhouette) }
    }
    // Rings of carved arcs show the fire's glow, as everywhere else.
    for (r in 1..3) drawCircle(Color(0xFFF2A83B).copy(alpha = 0.28f / r * glowAmount), radius = campHeight * 0.9f * r, center = lamp, style = Stroke(2f))
}
