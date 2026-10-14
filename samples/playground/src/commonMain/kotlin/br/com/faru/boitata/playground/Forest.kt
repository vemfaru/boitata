package br.com.faru.boitata.playground

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import br.com.faru.boitata.core.FastNoise
import androidx.compose.runtime.remember
import br.com.faru.boitata.core.burn.BodyGeometry
import br.com.faru.boitata.core.burn.FireBody
import br.com.faru.boitata.core.burn.FireKind
import br.com.faru.boitata.core.burn.FireWorld
import br.com.faru.boitata.core.burn.Material
import br.com.faru.boitata.core.burn.RunFireWorld
import br.com.faru.boitata.light.drawFireAffected
import br.com.faru.boitata.light.LightSource
import androidx.compose.runtime.rememberUpdatedState
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.depth.Depth
import br.com.faru.boitata.core.draw.sway
import br.com.faru.boitata.core.wind.SceneWind
import br.com.faru.boitata.core.wind.Wind
import br.com.faru.boitata.core.wind.WindResponse
import br.com.faru.boitata.core.wind.rememberSceneWind
import br.com.faru.boitata.core.depth.DepthSlot
import br.com.faru.boitata.core.clock.rememberSceneQuality
import br.com.faru.boitata.core.sky.MoonPhase
import br.com.faru.boitata.core.sky.SkyModel
import br.com.faru.boitata.core.sky.SkyWeather
import br.com.faru.boitata.light.FlamePalette
import br.com.faru.boitata.light.drawCastShadow
import br.com.faru.boitata.light.drawFlame
import br.com.faru.boitata.light.drawFlameTrail
import br.com.faru.boitata.light.fireLight
import br.com.faru.boitata.weather.drawFog
import br.com.faru.boitata.weather.drawRain
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Boitatá in its forest: a night wood in carved woodcut, the fire serpent weaving between the trees. It lights the trunks near it and every
 * tree throws its shadow away from the serpent, so the shadows swing round as it passes. Rain and mist are optional layers.
 *
 * A [hunter] stands in the wood, and he is **flammable**: the serpent's head is a fire that never burns down (and rain cannot put it out), so when it passes close enough, and the wind carries its heat to him, he warms,
 * smokes, catches and chars. The [wildlife] (a tamandua walking, a jaguar and a capybara on patrol, a macaw crossing the sky) is not flammable: the same fire never touches it. [resetKey] changing puts the hunter back;
 * [timeScale] runs the fire faster than the clock.
 */
@Composable
fun ForestScene(
    seconds: () -> Float,
    modifier: Modifier = Modifier.fillMaxSize(),
    rain: Float = 0f,
    fog: Float = 0.35f,
    hour: Float = 23.5f,
    moon: MoonPhase = MoonPhase.Full,
    southern: Boolean = true,
    hunter: Boolean = true,
    wildlife: Boolean = true,
    resetKey: Int = 0,
    timeScale: Float = 1f,
) {
    val art = LocalSprites.current ?: return
    val quality = rememberSceneQuality()
    val track = remember { SerpentTrack() }
    val sceneWind = rememberSceneWind()
    // The fire world: the serpent's head (always alight, the Boitatá's kind, waterproof) and the hunter (a person, who burns). Their places are read from the track each step.
    val world = remember(track, resetKey) {
        FireWorld().apply {
            add(FireBody("head", Material.Spirit) { track.headBox() })
            add(FireBody("hunter", Material.Person) { track.hunterBox() })
            ignite("head", FireKind.Guardian)
        }
    }
    val currentRain = rememberUpdatedState(rain)
    RunFireWorld(world, rain = { currentRain.value }, timeScale = timeScale)
    val extras = ForestExtras(world.body("hunter")!!, hunter, wildlife)
    Spacer(modifier.drawBehind { drawForest(art, seconds(), rain, fog, hour, quality(), track, moon, southern, sceneWind, extras) })
}

/** What else is in the wood besides the serpent: the hunter (his [body] is what burns), and the animals. */
class ForestExtras(val hunterBody: FireBody, val hunter: Boolean, val wildlife: Boolean)

/** How each tree of `layer_mid` answers the wind, in the order of [MidTrees]: araucária, buriti, araucária, sumaúma (huge and stiff), araucária, embaúba, buriti, araucária. */
private val TreeWind = listOf(
    WindResponse.Pine, WindResponse.Palm, WindResponse.Pine, WindResponse.Trunk,
    WindResponse.Pine, WindResponse.Palm, WindResponse.Palm, WindResponse.Pine,
)

/** Where each tree of `layer_mid` stands, in the art's own pixels; its feet are at the bottom centre of the box. */
private val MidTrees = listOf(
    Crop(13, 344, 261, 428), Crop(292, 487, 195, 309), Crop(523, 423, 206, 337), Crop(745, 363, 266, 449),
    Crop(1034, 391, 241, 393), Crop(1309, 448, 137, 320), Crop(1490, 512, 204, 292), Crop(1706, 473, 193, 317),
)

fun DrawScope.drawForest(art: Sprites, t: Float, rain: Float, fog: Float, hour: Float, quality: Quality = Quality.High, track: SerpentTrack = SerpentTrack(), moon: MoonPhase = MoonPhase.Full, southern: Boolean = true, sceneWind: SceneWind? = null, extras: ForestExtras? = null) {
    val wind = sceneWind?.wind ?: Wind.Calm
    val w = size.width; val h = size.height
    val v = cover()
    val sc = v.scale
    val state = SkyModel.at(hour, SkyWeather(cloud = 0.2f + 0.4f * rain, precipitation = rain), moon)
    val day = state.daylight
    val horizon = v.py(1080f * ART_HORIZON)

    // Sky, in flat horizontal bands (a woodcut has no gradients), with stars and the moon.
    val bands = 5
    for (i in 0 until bands) {
        val y0 = horizon * i / bands; val y1 = horizon * (i + 1) / bands
        drawRect(lerp(state.zenith, state.horizon, (i + 0.5f) / bands), Offset(0f, y0), Size(w, y1 - y0 + 1f))
    }
    drawRect(lerp(state.horizon, Color.Black, 0.25f), Offset(0f, horizon), Size(w, h - horizon))
    drawNightStars(art, t, horizon, state.stars)
    if (state.moon.up) {
        val m = Offset(state.moon.x * w, state.moon.y * horizon)
        for (k in 1..3) drawCircle(Color(0xFFA9B8E8).copy(alpha = 0.22f / k), radius = h * (0.03f + 0.022f * k), center = m, style = Stroke(1.5f))
        drawMoonArt(art, m, h * 0.034f, Color(0xFFE9EEF8), moon, southern)
    }

    fun tint(night: Long, dayColor: Long) = lerp(Color(night), Color(dayColor), day)
    drawArt(art.far, Offset(v.x, v.y), sc, tint(0xFF0C1A22, 0xFF6F8794))
    drawArt(art.ground, Offset(v.x, v.y), sc, tint(0xFF070D0B, 0xFF26302A))

    // The serpent swims through the wood in depth as well as across it: back to the horizon behind the trees, between the trees, then close in front of everything.
    // Every point of its body has its own depth, so its size, its fade and the layers it passes behind are decided point by point.
    val head = track.follow(t, w, h, horizon, quality)
    val z0 = track.depth[0]
    val light = fireLight(
        head.x, head.y, t, reach = w * 0.55f * (0.65f + 0.35f * Depth.visibility(z0)), strength = (0.5f + 0.45f * Depth.visibility(z0)) * (1f - day),
        groundX = head.x, groundY = track.groundY(z0, h, horizon),
    )

    // Behind the mid trees: the part of the body that is far away.
    val sparks = WindResponse.Ember.drift(wind, t, 0.5f).coerceIn(-3f, 3f) * 0.6f
    // The head goes down first and the body's flame over it, so the fire runs into the head instead of stopping at its edge.
    track.drawHeadIfIn(DepthSlot.Behind, art, this, h, t, quality, wind)
    track.draw(Depth.slot(0.9f), this, h, t, quality, sparks)

    for (tr in MidTrees) {
        val feetX = v.px(tr.x + tr.w / 2f); val feetY = v.py((tr.y + tr.h).toFloat())
        val near = (1f - hypot(feetX - head.x, feetY - h * 0.1f - head.y) / light.reach).coerceIn(0f, 1f) * light.strength
        drawCastShadow(light, feetX, feetY, tr.h * sc, 0.6f, quality) { c -> drawArt(art.mid, Offset(-tr.w / 2f * sc, -tr.h * sc), sc, c, tr) }
        val base = tint(0xFF08130F, 0xFF33463C)
        // Each kind of tree answers the wind in its own way (a palm sways, a trunk hardly), and each is a little out of step with the next.
        val bend = TreeWind[MidTrees.indexOf(tr)].bend(wind, t, (feetX / w).coerceIn(0f, 1f), phase = MidTrees.indexOf(tr) * 0.173f)
        sway(feetX, feetY, bend, maxLean = 0.1f) {
            drawArt(art.mid, Offset(v.px(tr.x.toFloat()), v.py(tr.y.toFloat())), sc, lerp(base, Color(0xFF8A4B1F), (near * 0.6f).coerceIn(0f, 0.65f)), tr)
        }
    }

    if (extras != null) drawWood(art, track, extras, head, light, t, wind, quality) { night, dayColor -> tint(night, dayColor) }

    // A glow on the ground under the serpent, and rings of carved arcs around its head.
    val glow = Offset(head.x, track.groundY(z0, h, horizon))
    val glowRadius = w * 0.28f * Depth.scale(z0).coerceIn(0.5f, 1.5f)
    drawCircle(Brush.radialGradient(0f to Color(0x44FFA63C), 1f to Color.Transparent, center = glow, radius = glowRadius), radius = glowRadius, center = glow, blendMode = BlendMode.Plus)

    // Between the mid trees and the near foliage.
    // The head goes down first and the body's flame over it, so the fire runs into the head instead of stopping at its edge.
    track.drawHeadIfIn(DepthSlot.Between, art, this, h, t, quality, wind)
    track.draw(Depth.slot(0.4f), this, h, t, quality, sparks)

    val nearTint = tint(0xFF040A08, 0xFF16201A)
    // The foliage that frames the picture leans a little too.
    sway(w / 2f, v.y + 1080f * sc, WindResponse.Plant.bend(wind, t, 0.5f, 0.5f), maxLean = 0.03f) {
        drawArt(art.near, Offset(v.x, v.y), sc, lerp(nearTint, Color(0xFF5A3216), (0.25f * light.strength).coerceIn(0f, 0.3f)))
    }

    // In front of everything: close to the viewer, large, with the foliage behind it.
    // The head goes down first and the body's flame over it, so the fire runs into the head instead of stopping at its edge.
    track.drawHeadIfIn(DepthSlot.InFront, art, this, h, t, quality, wind)
    track.draw(Depth.slot(0.05f), this, h, t, quality, sparks)
    for (k in 1..3) drawCircle(Color(0xFFF2A83B).copy(alpha = 0.35f / k * (1f - day) * Depth.visibility(z0)), radius = h * 0.09f * k * Depth.scale(z0), center = head, style = Stroke(2f))

    if (fog > 0f) drawFog(t, fog, Color(0xFF8DA2B5), quality = quality, carried = (sceneWind?.travelled ?: 0f) * WindResponse.Fog.driftGain)
    if (rain > 0f) drawRain(t, rain, wind, WindResponse.Raindrop, Color(0xFFB7C8DC), quality = quality)
}

/**
 * The serpent's body as numbers, one entry per point from the head to the tail, made once and reused every frame (nothing is allocated while it swims): where each point is,
 * how deep (0 near, 1 at the horizon), how big and how faded for that depth, and which layer slot it is in.
 */
private const val HUNTER_DEPTH = 0.45f

private const val POINT_SECONDS = 0.07f / 1.5f

class SerpentTrack(val count: Int = 72) {
    val pts = FloatArray(count * 2)
    val depth = FloatArray(count)
    val scale = FloatArray(count)
    val visibility = FloatArray(count)
    private val slot = Array(count) { DepthSlot.Between }
    private var baseWidth = 0f
    private var sceneW = 0f
    private var sceneH = 0f
    private var horizonY = 0f

    /** Where each animal is, this instant. */
    val poses = Array(Wildlife.size) { Pose() }

    /** The head as the fire sees it: standing on the ground below it, as tall as its reach (a flame much bigger than its body), so a hunter on the ground within that catches. */
    fun headBox(): BodyGeometry {
        val reach = sceneH * 0.55f * scale[0].coerceIn(0.5f, 1.5f)
        return BodyGeometry(pts[0], groundY(depth[0], sceneH, horizonY), reach * 0.4f, reach)
    }

    /** The hunter's feet and size: a little way into the wood, standing at a middle depth. */
    fun hunterBox(): BodyGeometry {
        val z = HUNTER_DEPTH
        val height = sceneH * 0.3f * Depth.scale(z)
        return BodyGeometry(sceneW * 0.62f, groundY(z, sceneH, horizonY), height * 0.45f, height)
    }

    /** How fast the head moves, in pixels a second, and the speed that counts as "fast" (a third of the width a second); both worked out from the body, which is where the head was a moment ago. */
    var headVx = 0f; private set
    var headVy = 0f; private set
    var fastSpeed = 1f; private set

    /** The ground line at depth [z]: the bottom of the picture up close, a little below the horizon far off. */
    fun groundY(z: Float, h: Float, horizon: Float): Float = h * 0.97f + (horizon + h * 0.03f - h * 0.97f) * z

    /** Moves the whole body to time [t] and returns the head. The head swings in depth; each point behind it is where the head was a moment earlier. */
    fun follow(t: Float, w: Float, h: Float, horizon: Float, quality: Quality): Offset {
        baseWidth = h * 0.042f
        sceneW = w; sceneH = h; horizonY = horizon
        for (n in 0 until count) {
            val tt = t * 1.5f - n * 0.07f   // POINT_SECONDS apart in real time
            val z = Depth.swing(tt, period = 12f, phase = 0.25f)
            val sc = Depth.scale(z).coerceAtMost(1.7f)
            // Sideways it sweeps less when far away, as everything does toward the vanishing point.
            val across = 0.34f * sin(tt * 0.62f) + 0.04f * sin(tt * 1.9f)
            val x = w * (0.5f + across * (0.35f + 0.65f * (1f - z)))
            val hover = h * 0.2f * sc
            val weave = sin(n * 0.32f - t * 3.5f) * h * 0.04f * (0.3f + n / count.toFloat()) * sc
            pts[n * 2] = x
            pts[n * 2 + 1] = groundY(z, h, horizon) - hover + weave
            depth[n] = z; scale[n] = sc; visibility[n] = Depth.visibility(z); slot[n] = Depth.slot(z)
        }
        headVx = (pts[0] - pts[2]) / POINT_SECONDS; headVy = (pts[1] - pts[3]) / POINT_SECONDS
        fastSpeed = w * 0.3f
        return Offset(pts[0], pts[1])
    }

    /** Draws the runs of the body that are in the same slot as [which] (a representative depth in it). */
    fun draw(which: DepthSlot, scope: DrawScope, h: Float, t: Float, quality: Quality, sparkDrift: Float = 0f) {
        var i = 0
        while (i < count) {
            val s = slot[i]
            var j = i
            while (j + 1 < count && slot[j + 1] == s) j++
            if (s == which) with(scope) {
                // One point more at the end of the run, so the pieces join where the body crosses from one layer to the next.
                drawFlameTrail(pts, count, baseWidth, t, FlamePalette.Boitata, quality = quality, scales = scale, visibility = visibility, range = i..minOf(j + 1, count - 1), sparkDrift = sparkDrift)
            }
            i = j + 1
        }
    }

    /** The head, once, in the slot it is in. */
    fun drawHeadIfIn(which: DepthSlot, art: Sprites, scope: DrawScope, h: Float, seconds: Float, quality: Quality, wind: Wind) {
        if (slot[0] != which) return
        with(scope) { drawSerpentHead(art, pts, baseWidth * scale[0], visibility[0], seconds, quality, wind, headVx / fastSpeed, headVy / fastSpeed) }
    }
}

/** The head sprite on the front of the flame, turned the way the serpent is going, its eyes lit from behind through the sockets cut in it. */
private fun DrawScope.drawSerpentHead(art: Sprites, pts: FloatArray, width: Float, visibility: Float = 1f, seconds: Float = 0f, quality: Quality = Quality.High, wind: Wind = Wind.Calm, speedX: Float = 0f, speedY: Float = 0f) {
    val head = Offset(pts[0], pts[1])
    val angle = (atan2((pts[1] - pts[3]).toDouble(), (pts[0] - pts[2]).toDouble()) * 180.0 / PI).toFloat()
    val k = 2f * width / 80f * 0.6f
    // Only the head of the sprite (from its back at x = 250): the body is the flame.
    val headCrop = Crop(250, 184, 226, 156)
    withTransform({ translate(head.x, head.y); rotate(angle, Offset.Zero) }) {
        for (sy in floatArrayOf(219f, 291f)) {
            val e = Offset((347f - 255f) * k, (sy - 256f) * k)
            drawCircle(Brush.radialGradient(0f to Color(0xCCFFF1C2).copy(alpha = 0.8f * visibility), 1f to Color.Transparent, center = e, radius = 70f * k), radius = 70f * k, center = e, blendMode = BlendMode.Plus)
            drawCircle(Color(0xFFFFF1C2).copy(alpha = visibility), radius = 30f * k, center = e)
        }
        // An Ember rim first, so the dark head stands out of the dark wood; then the carved head itself.
        val rim = 1.12f
        drawArt(art.head, Offset((250f - 255f) * k * rim - 0.5f * (rim - 1f) * 226f * k, (184f - 256f) * k * rim - 0.5f * (rim - 1f) * 156f * k), k * rim, Color(0xFFC2410C).copy(alpha = visibility), headCrop)
        drawArt(art.head, Offset((250f - 255f) * k, (184f - 256f) * k), k, Color(0xFF14110F), headCrop)
    }
    // The head is fire too, and it never burns down: one line of flames along its crown like a mohawk, tallest at the back and smaller toward the snout, drawn over the carved head, the same flame as the body's.
    // They answer the air like any flame: the scene's wind leans them (as it leans a campfire), and so does the head's own motion, the way a torch carried at a run streams behind it:
    // the faster it goes the flatter and longer they stream against the way it moves, and moving up squashes them while diving stretches them. [speedX], [speedY] are the head's speed in "fast" units.
    val rad = angle * PI.toFloat() / 180f
    val cosA = cos(rad); val sinA = sin(rad)
    val place = (head.x / size.width).coerceIn(0f, 1f)
    val windLean = WindResponse.Flame.bend(wind, seconds, place) * 0.45f
    val windEmbers = WindResponse.Ember.drift(wind, seconds, place).coerceIn(-3f, 3f)
    val motionLean = (-speedX * 0.7f).coerceIn(-0.8f, 0.8f)
    val stretch = (1f + 0.4f * speedY).coerceIn(0.65f, 1.35f) * (1f + 0.25f * abs(speedX).coerceAtMost(1f))
    for ((i, f) in HEAD_FLAMES.withIndex()) {
        val lx = f[0] * k; val ly = f[1] * k
        val bx = head.x + lx * cosA - ly * sinA; val by = head.y + lx * sinA + ly * cosA
        val own = 0.85f + 0.3f * ((i * 7) % 5) / 4f         // each flame answers a little differently, so they do not all lean as one
        drawFlame(bx, by, width * f[2], width * f[3] * stretch, seconds + i * 0.37f, intensity = visibility, palette = FlamePalette.Boitata, seed = 30L + i, quality = quality,
            lean = ((windLean + motionLean) * own).coerceIn(-0.95f, 0.95f), emberDrift = (windEmbers + motionLean * 4f).coerceIn(-4f, 4f))
    }
}

/** Where flames rise on the head, in the head sprite's own pixels from its centre (x forward, y down): x, y, width and height as shares of the body's width. One line down the middle of the head, between the eyes, like a mohawk (the head is seen from above), tallest at the back of the neck and shrinking to the nose. */
private val HEAD_FLAMES = arrayOf(
    floatArrayOf(5f, 0f, 1.1f, 2.4f), floatArrayOf(40f, 0f, 1.0f, 2.2f), floatArrayOf(75f, 0f, 0.95f, 2.0f), floatArrayOf(110f, 0f, 0.85f, 1.7f),
    floatArrayOf(145f, 0f, 0.75f, 1.4f), floatArrayOf(178f, 0f, 0.65f, 1.1f), floatArrayOf(205f, 0f, 0.55f, 0.8f),
)


/**
 * The hunter and the animals, in the middle of the wood. All lit and shadowed by the serpent: nearer it they glow warm, and every one throws a shadow away from it.
 * The hunter is drawn by what fire does to him ([drawFireAffected]); the animals are not flammable and are drawn plain.
 */
private fun DrawScope.drawWood(art: Sprites, track: SerpentTrack, extras: ForestExtras, head: Offset, light: LightSource, t: Float, wind: Wind, quality: Quality, tint: (Long, Long) -> Color) {
    val h = size.height
    val base = tint(0xFF0A1410, 0xFF33463C)
    fun warm(feetX: Float, feetY: Float, height: Float): Color {
        val near = (1f - hypot(feetX - head.x, feetY - height * 0.5f - head.y) / light.reach).coerceIn(0f, 1f) * light.strength
        return lerp(base, Color(0xFF8A4B1F), (near * 0.6f).coerceIn(0f, 0.65f))
    }
    if (extras.wildlife) {
        val top = track.groundY(0.85f, h, h * ART_HORIZON); val bottom = track.groundY(0.2f, h, h * ART_HORIZON)
        for ((i, c) in Wildlife.withIndex()) {
            val strip = c.strip?.let { art.strips.getValue(it) }
            val aspect = strip?.aspect ?: art.prop(c.sprite).let { it.width.toFloat() / it.height }
            val pose = track.poses[i]
            val share = ((c.gait as? Gait.Patrol)?.y ?: (c.gait as Gait.Fly).y)
            val height = h * c.heightShare * Depth.scale(0.85f - 0.65f * share)
            c.poseAt(t, size.width * 0.03f, size.width * 0.97f, top, bottom, height * aspect, pose)
            drawCastShadow(light, pose.x, pose.y, height + pose.lift, 0.55f, quality) { col -> drawCreature(art, c, pose, height, col) }
            withTransform({ translate(pose.x, pose.y) }) { drawCreature(art, c, pose, height, warm(pose.x, pose.y, height)) }
        }
    }
    if (extras.hunter) {
        val g = track.hunterBox()
        val image = art.prop("cacador"); val k = g.height / image.height
        val silhouette: DrawScope.(Color) -> Unit = { col -> drawArt(image, Offset(-image.width * k / 2f, -g.height), k, col) }
        drawCastShadow(light, g.feetX, g.feetY, g.height, 0.6f, quality, silhouette)
        drawFireAffected(extras.hunterBody, g, t, wind, warm(g.feetX, g.feetY, g.height), quality, silhouette = silhouette)
    }
}
