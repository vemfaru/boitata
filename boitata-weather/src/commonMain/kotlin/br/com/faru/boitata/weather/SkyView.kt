package br.com.faru.boitata.weather

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import br.com.faru.boitata.core.AdvancedBoitataApi
import br.com.faru.boitata.core.FastNoise
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.clock.rememberSceneQuality
import br.com.faru.boitata.core.clock.rememberSceneSeconds
import br.com.faru.boitata.core.draw.drawImageSmooth
import br.com.faru.boitata.core.sky.MoonShape
import br.com.faru.boitata.core.wind.WindResponse
import br.com.faru.boitata.core.wind.rememberSceneWind
import br.com.faru.boitata.core.sky.SkyModel
import br.com.faru.boitata.core.sky.SkyState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Seconds on the scene's clock, for the things that move on their own (clouds, twinkle, drift). Inside a [br.com.faru.boitata.core.clock.BoitataScene] this is the
 * shared clock; outside one it makes a clock for you. Frozen under [frozen].
 */
@Composable
public fun rememberSkySeconds(frozen: Boolean = false): () -> Float {
    val seconds = rememberSceneSeconds()
    return if (frozen) ({ 0f }) else seconds
}

/**
 * Pictures for the sky's bodies, if you have drawn them. Each is tinted by the sky (a pale disc for the moon, a warm one for the sun, white stars), so draw them as a solid shape with
 * the detail cut out: the sky shows through the cuts. Any may be null, and the sky paints that body itself.
 */
public class SkyArt(
    public val sun: ImageBitmap? = null,
    public val moon: ImageBitmap? = null,
    public val stars: StarAtlas? = null,
)

/**
 * Stars as one picture of equal [cell]-pixel squares, [columns] to a row, each star centred in its cell. The stars come in a few sizes, [perSize] of each in turn, and [sizes] says how
 * many pixels across the star itself is in each size (the rest of its cell is empty). The sky picks one at random for each star and scales it.
 */
public class StarAtlas(
    public val image: ImageBitmap,
    public val cell: Int = 80,
    public val columns: Int = 6,
    public val sizes: IntArray = intArrayOf(48, 64, 80),
    public val perSize: Int = 4,
) {
    public val count: Int get() = sizes.size * perSize
}

/** One layer of the view: a picture the size of the view, how near it is (0 far to 1 near: how far it shifts with [parallax]) and how much of the sky's veil it takes. */
class ViewLayer(val image: ImageBitmap, val depth: Float, val veil: Float = 1f, val lit: ImageBitmap? = null)

/**
 * The view out of a window: the [sky] (gradient, stars, sun, moon, clouds), the [layers] far to near each shifted by the [parallax] in proportion to
 * its depth, mist between them, and the lit windows over the near layer. Everything is drawn in the draw phase: moving the sky, the clock or the
 * parallax does not recompose anything.
 *
 * [parallax] is -1..1 on each axis (the viewer's head and the idle sway); [seconds] drives the clouds and the twinkle; [clouds] is an optional
 * strip of cloud pictures, else clouds are painted in code.
 *
 * [horizon] is where the horizon stands, as a fraction of the height (a window above a desk keeps it high, a whole-scene view puts it near 0.6, where
 * the art is drawn); the layers are shifted so the art's own horizon (60%) lands on it.
 */
@Composable
fun SkyWindow(
    sky: () -> SkyState,
    layers: List<ViewLayer>,
    parallax: () -> Offset,
    seconds: (() -> Float)? = null,
    modifier: Modifier = Modifier.fillMaxSize(),
    clouds: ImageBitmap? = null,
    storm: StormView? = null,
    horizon: Float = SkyModel.HORIZON_Y,
    quality: (() -> Quality)? = null,
    art: SkyArt? = null,
    /** Which hemisphere the viewer is in: it decides which side of the moon is lit (a waxing moon is lit on its right from the north, on its left from the south). */
    southernHemisphere: Boolean = false,
) {
    val time = seconds ?: rememberSceneSeconds()
    val level = quality ?: rememberSceneQuality()
    val sceneWind = rememberSceneWind()
    Box(modifier) {
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                drawSky(sky(), time(), parallax(), clouds, horizon, level(), art, southernHemisphere, sceneWind.travelled)
                // Lightning: the sky flares and the bolt stands behind the roofs.
                storm?.let { st ->
                    drawFlash(st.flash() * 0.9f)
                    st.bolt()?.let { (strike, strength) -> drawBolt(strike, strength, size.height * horizon) }
                }
            },
        )
        layers.forEachIndexed { index, layer ->
            Spacer(
                Modifier.fillMaxSize().drawBehind {
                    val s = sky()
                    val shift = parallax()
                    drawLayer(layer.image, layer.depth, shift, layerVeil(s, layer), horizon = horizon, quality = level())
                    // Mist stands in front of what is behind it: thicker over the far layers.
                    if (s.weather.fog > 0f) {
                        val mist = s.weather.fog * (1f - layer.depth * 0.75f) * 0.55f
                        drawRect(lerp(s.horizon, Color(0xFFB8BEC6), 0.5f).copy(alpha = mist.coerceIn(0f, 0.8f)), blendMode = BlendMode.SrcAtop.takeIf { false } ?: BlendMode.SrcOver)
                    }
                    storm?.let { st -> val f = st.flash(); if (f > 0.01f) drawRect(Color(0xFFCFDCFF).copy(alpha = (f * 0.22f * (0.4f + layer.depth)).coerceAtMost(0.4f)), blendMode = BlendMode.SrcAtop) }
                    layer.lit?.let { lit ->
                        if (s.windowsLit > 0.01f) {
                            // The lights breathe a little, window by window as one: a slow flicker of the street's power.
                            val flicker = 0.94f + 0.06f * sin(time() * 1.7f)
                            drawLayer(lit, layer.depth, shift, null, alpha = (s.windowsLit * flicker).coerceIn(0f, 1f), horizon = horizon, quality = level())
                        }
                    }
                },
            )
        }
    }
}

/** The veil one layer takes from the sky: dark and blue at night, grey in fog, a warm cast at the twilights. */
internal fun layerVeil(sky: SkyState, layer: ViewLayer): Color {
    val night = 1f - sky.daylight
    val dusk = lerp(Color(0xFF05080F), Color(0xFF2A2230), sky.twilight * 0.6f)
    var veil = dusk.copy(alpha = (0.78f * night * layer.veil).coerceIn(0f, 0.9f))
    if (sky.twilight > 0f) veil = lerp(veil, sky.horizon.copy(alpha = 0.18f * sky.twilight * layer.veil), 0.35f * sky.twilight)
    // Far things take the colour of the air.
    val air = sky.horizon.copy(alpha = ((1f - layer.depth) * 0.3f * layer.veil * (0.4f + 0.6f * sky.daylight)).coerceIn(0f, 0.5f))
    return if (veil.alpha > air.alpha) veil else lerp(veil, air, 0.6f).copy(alpha = maxOf(veil.alpha, air.alpha))
}

private fun DrawScope.drawLayer(image: ImageBitmap, depth: Float, shift: Offset, veil: Color?, alpha: Float = 1f, horizon: Float = SkyModel.HORIZON_Y, quality: Quality = Quality.High) {
    // Enlarged a little so the picture still covers the view when it has shifted; nearer layers shift more.
    val margin = 0.07f
    val w = size.width * (1f + 2f * margin); val h = size.height * (1f + 2f * margin)
    val reach = size.width * margin * (0.25f + 0.75f * depth)
    val x = -size.width * margin + shift.x * reach
    // The art has its horizon at ART_HORIZON_Y; the view puts it at HORIZON_Y, so the roofs clear what stands in front of the window.
    val y = -size.height * margin + (horizon - SkyModel.ART_HORIZON_Y) * size.height + shift.y * reach * 0.6f
    // Placed with a float translation (drawImageSmooth), never an IntOffset: the parallax moves a fraction of a pixel per frame, and snapping it to whole
    // pixels makes the layer sit still for tens of frames and then jump.
    drawImageSmooth(
        image, x, y, w.toInt(), h.toInt(), alpha = alpha,
        colorFilter = veil?.takeIf { it.alpha > 0.005f }?.let { ColorFilter.tint(it, BlendMode.SrcAtop) }, quality = quality,
    )
}

private fun DrawScope.drawSky(sky: SkyState, seconds: Float, parallax: Offset, clouds: ImageBitmap?, horizon: Float, quality: Quality, art: SkyArt?, southern: Boolean, travelled: Float) {
    val w = size.width; val h = size.height
    val horizonY = h * horizon
    val arc = horizon / SkyModel.HORIZON_Y
    // The gradient: zenith at the top to the horizon colour at the horizon, and the horizon's own colour below it.
    drawRect(Brush.verticalGradient(0f to sky.zenith, 1f to sky.horizon, startY = 0f, endY = horizonY))
    drawRect(sky.horizon, topLeft = Offset(0f, horizonY), size = androidx.compose.ui.geometry.Size(w, h - horizonY))
    val shift = parallax.x * w * 0.004f

    // Stars: a fixed field that turns slowly, each twinkling in its own time, fewer near the horizon.
    if (sky.stars > 0.01f) {
        val noise = FastNoise(31L)
        repeat(160) { i ->
            val bx = noise.at(i, 0); val by = noise.at(i, 1)
            val x = ((bx + seconds * 0.0025f) % 1f) * w + shift
            val y = by * horizonY * 0.96f
            val low = (1f - (y / horizonY)).coerceIn(0f, 1f).let { 0.35f + 0.65f * it }
            val twinkle = 0.65f + 0.35f * sin(seconds * (0.8f + noise.at(i, 2) * 2.4f) + noise.at(i, 3) * 6.28f)
            val size = (0.8f + noise.at(i, 4) * 1.6f) * w / 650f
            val alpha = (sky.stars * low * twinkle * (0.5f + 0.5f * noise.at(i, 5))).coerceIn(0f, 1f)
            val atlas = art?.stars
            if (atlas == null) drawCircle(Color.White.copy(alpha = alpha), size, Offset(x, y))
            else drawAtlasStar(atlas, (noise.at(i, 6) * atlas.count).toInt().coerceAtMost(atlas.count - 1), Offset(x, y), size * 4.2f, alpha, quality)
        }
    }

    val hide = (sky.weather.cloud * 0.8f + sky.weather.precipitation * 0.5f + sky.weather.fog * 0.6f).coerceIn(0f, 1f)
    // The sun: a soft halo and a disc, warm when low, dimmed by cloud; gone below the horizon.
    if (sky.sun.up && sky.sunElevation > -0.02f) {
        val c = Offset(sky.sun.x * w + shift, sky.sun.y * h * arc)
        val r = w * 0.045f
        val a = (1f - hide * 0.92f).coerceIn(0f, 1f) * (0.4f + 0.6f * sky.daylight.coerceAtLeast(sky.twilight))
        drawCircle(Brush.radialGradient(0f to sky.lightColor.copy(alpha = 0.55f * a), 1f to Color.Transparent, center = c, radius = r * 7f), r * 7f, c)
        val sunTint = lerp(sky.lightColor, Color.White, 0.5f).copy(alpha = a)
        val sunArt = art?.sun
        if (sunArt == null) drawCircle(sunTint, r, c)
        else drawImageSmooth(sunArt, c.x - r * 1.25f, c.y - r * 1.25f, (r * 2.5f).toInt(), (r * 2.5f).toInt(), colorFilter = ColorFilter.tint(sunTint, BlendMode.SrcIn), quality = quality)
    }
    // The moon: its lit part is the disc less a circle slid across it; a halo in the dark.
    if (sky.moon.up && sky.daylight < 0.85f) {
        val c = Offset(sky.moon.x * w + shift, sky.moon.y * h * arc)
        val r = w * 0.035f
        val a = (1f - hide * 0.9f).coerceIn(0f, 1f) * (1f - sky.daylight * 1.2f).coerceIn(0f, 1f)
        drawCircle(Brush.radialGradient(0f to Color(0xFFB4C4DA).copy(alpha = 0.22f * a), 1f to Color.Transparent, center = c, radius = r * 6f), r * 6f, c)
        // The lit part has a real terminator (see MoonShape), on the side the phase and the hemisphere say; the dark part shows faintly, as it does in the sky.
        val lit = MoonShape.litPath(c.x, c.y, r, sky.moonPhase, MoonShape.litOnRight(sky.moonWaxing, southern))
        drawCircle(Color(0xFFDDE5F0).copy(alpha = 0.1f * a), r, c)
        val moonArt = art?.moon
        if (moonArt == null) drawPath(lit, Color(0xFFDDE5F0).copy(alpha = a))
        // The picture shows only where the moon is lit, so its phase still works.
        else clipPath(lit) { drawImageSmooth(moonArt, c.x - r * 1.1f, c.y - r * 1.1f, (r * 2.2f).toInt(), (r * 2.2f).toInt(), colorFilter = ColorFilter.tint(Color(0xFFDDE5F0).copy(alpha = a), BlendMode.SrcIn), quality = quality) }
    }

    drawClouds(sky, seconds, shift, clouds, horizonY, quality, travelled)
}

/** Clouds painted as clusters of soft puffs drifting with the wind, or, with a picture, that strip scrolled across; lit from the sun's side. */
private fun DrawScope.drawClouds(sky: SkyState, seconds: Float, shift: Float, strip: ImageBitmap?, horizonY: Float, quality: Quality, travelled: Float) {
    val cover = sky.weather.cloud
    if (cover < 0.04f) return
    val w = size.width
    val dark = lerp(Color(0xFF2A2F38), Color(0xFF4A515B), sky.weather.storm)
    val light = lerp(Color(0xFF2C3340), Color(0xFFE2E6EA), sky.daylight)
    val body = lerp(light, dark, (sky.weather.storm * 0.8f + sky.weather.precipitation * 0.45f).coerceIn(0f, 1f))
    val rim = lerp(body, sky.horizon, 0.5f * sky.twilight + 0.1f)
    val wind = 0.004f + 0.012f * (sky.weather.storm + sky.weather.precipitation * 0.5f)
    if (strip != null) {
        val sw = w * 2f; val sh = sw * strip.height / strip.width
        // The weather's own slow drift to the left, plus wherever the wind has carried the clouds (the high ones and the low ones average out on a single strip).
        val gain = (WindResponse.HighCloud.driftGain + WindResponse.LowCloud.driftGain) / 2f
        val raw = -(seconds * wind * w) + travelled * gain * CLOUD_CARRY * w
        val offset = ((raw % sw) + sw) % sw - sw
        // Drifts a fraction of a pixel per frame: drawImageSmooth places it with a float translation (see drawLayer).
        val filter = ColorFilter.tint(body.copy(alpha = 0.55f), BlendMode.Modulate)
        for (k in 0..1) drawImageSmooth(strip, offset + k * sw + shift, horizonY * 0.08f, sw.toInt(), sh.toInt(), alpha = (cover * 1.1f).coerceIn(0f, 1f), colorFilter = filter, quality = quality)
        return
    }
    val noise = FastNoise(5L)
    val count = quality.scaled((3 + cover * 11).toInt())
    repeat(count) { i ->
        val speed = wind * (0.6f + noise.at(i, 0))
        val y = horizonY * (0.1f + 0.75f * noise.at(i, 3) * (1f - 0.3f * cover) + 0.05f)
        // High clouds are carried faster than low ones.
        val altitude = (y / horizonY).coerceIn(0f, 1f)
        val gain = WindResponse.HighCloud.driftGain + (WindResponse.LowCloud.driftGain - WindResponse.HighCloud.driftGain) * altitude
        val u = noise.at(i, 1) + seconds * speed + travelled * gain * CLOUD_CARRY
        val x = (((u % 1.3f) + 1.3f) % 1.3f - 0.15f) * w + shift * (0.5f + noise.at(i, 2))
        val scale = w * (0.07f + 0.09f * noise.at(i, 4)) * (0.8f + cover * 0.5f)
        val puffs = 5 + (noise.at(i, 5) * 4).toInt()
        for (p in 0 until puffs) {
            val a = noise.at(i * 17 + p, 6) * 2f * PI.toFloat()
            val px = x + cos(a) * scale * (0.2f + 0.8f * noise.at(i * 17 + p, 7))
            val py = y + sin(a) * scale * 0.28f * noise.at(i * 17 + p, 8) + scale * 0.06f
            val r = scale * (0.3f + 0.35f * noise.at(i * 17 + p, 9))
            val c = Offset(px, py)
            val colour = if (py < y) rim else body
            drawCircle(Brush.radialGradient(0f to colour.copy(alpha = 0.75f * cover.coerceAtLeast(0.35f)), 0.7f to colour.copy(alpha = 0.35f * cover), 1f to Color.Transparent, center = c, radius = r), r, c)
        }
    }
}

/** One star of [atlas], [pixels] across, centred on [at], white at [alpha]: for scenes that paint their own sky. */
@AdvancedBoitataApi
public fun DrawScope.drawAtlasStar(atlas: StarAtlas, index: Int, at: Offset, pixels: Float, alpha: Float, quality: Quality) {
    val size = atlas.sizes[(index / atlas.perSize).coerceAtMost(atlas.sizes.size - 1)]
    val k = pixels / size                                   // the star is `size` pixels across inside its cell
    val cx = (index % atlas.columns) * atlas.cell; val cy = (index / atlas.columns) * atlas.cell
    val drawn = (atlas.cell * k).toInt().coerceAtLeast(1)
    drawImageSmooth(
        atlas.image, at.x - drawn / 2f, at.y - drawn / 2f, drawn, drawn, colorFilter = ColorFilter.tint(Color.White.copy(alpha = alpha), BlendMode.SrcIn), quality = quality,
        srcOffset = androidx.compose.ui.unit.IntOffset(cx, cy), srcSize = androidx.compose.ui.unit.IntSize(atlas.cell, atlas.cell),
    )
}

/** How far clouds move (as a share of the screen) for one unit of the air's travelled distance, before their own gain. */
private const val CLOUD_CARRY = 0.012f
