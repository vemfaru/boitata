package br.com.faru.boitata.playground

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import br.com.faru.boitata.core.light.Optics
import br.com.faru.boitata.core.FastNoise
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.sin
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.wind.Wind
import br.com.faru.boitata.core.wind.WindResponse
import br.com.faru.boitata.core.wind.rememberSceneWind
import br.com.faru.boitata.core.clock.rememberSceneQuality
import br.com.faru.boitata.core.sky.MoonPhase
import br.com.faru.boitata.core.sky.SkyModel
import br.com.faru.boitata.core.sky.SkyWeather
import br.com.faru.boitata.light.LightSource
import br.com.faru.boitata.light.drawWindowLight

/**
 * A room with a window. The sky model for the [hour] says what colour the light is and how strong; the patch it throws on the floor is long and
 * golden when the sun is low, short and pale at noon, bluish and faint under the moon, and the furniture throws its shadow away from the window.
 *
 * The [glass] says what the pane does: clear glass shows the sky and the light patch as they are; dirty or frosted glass ([Optics.Frosted]) blurs the sky and what is outside, veils it with haze, speckles the pane with dirt, and softens and dims the
 * patch of light it lets in; old glass bends the view; stained glass colours both. A clean pane costs nothing extra.
 *
 * The wind reaches the room only through the window: with [windowOpen] the plant leans in the draught and the dust in the light drifts with it; shut it and the air inside is still, whatever blows outside.
 */
@Composable
fun WindowScene(seconds: () -> Float, hour: Float, cloud: Float, modifier: Modifier = Modifier.fillMaxSize(), moon: MoonPhase = MoonPhase.Full, southern: Boolean = true, windowOpen: Boolean = true, glass: Optics = Optics.Glass()) {
    val art = LocalSprites.current ?: return
    val quality = rememberSceneQuality()
    val sceneWind = rememberSceneWind()
    // The window swings shut or open over most of a second; the draught it lets in follows it. Read in the draw phase, so it recomposes nothing.
    val open = animateFloatAsState(if (windowOpen) 1f else 0f, tween(700), label = "window")
    // Three offscreen layers for dirty and frosted glass: the outside as it is, the outside bent and smeared, and the light patch softened.
    val layers = GlassLayers(rememberGraphicsLayer(), rememberGraphicsLayer(), rememberGraphicsLayer())
    Spacer(modifier.drawBehind { drawRoom(art, seconds(), hour, cloud, quality(), moon, southern, sceneWind.wind, draught = open.value, glass = glass, layers = layers) })
}

/** Fills [area] with [image] repeated, each repeat [tile] pixels wide, painted [tint]. */
internal fun DrawScope.drawTiled(image: ImageBitmap, area: Rect, tile: Float, tint: Color) {
    val k = tile / image.width
    val th = image.height * k
    clipRect(area.left, area.top, area.right, area.bottom) {
        var y = area.top
        while (y < area.bottom) {
            var x = area.left
            while (x < area.right) { drawArt(image, Offset(x, y), k, tint); x += tile }
            y += th
        }
    }
}

fun DrawScope.drawRoom(art: Sprites, t: Float, hour: Float, cloud: Float, quality: Quality = Quality.High, moon: MoonPhase = MoonPhase.Full, southern: Boolean = true, wind: Wind = Wind.Calm,
    /** How much of the outside wind gets in, 0 with the window shut to 1 wide open. */
    draught: Float = 1f,
    /** What the pane does to the view and the light. */
    glass: Optics = Optics.Glass(),
    layers: GlassLayers? = null,
) {
    val w = size.width; val h = size.height
    val s = SkyModel.at(hour, SkyWeather(cloud = cloud), moon)
    val day = 0.2f + 0.8f * s.daylight
    val floorY = h * 0.62f
    // Wall and floor: a lighter plaster behind, the carved ink tile over it, both by the hour.
    val wall = lerp(Color(0xFF0B0E12), Color(0xFF7C6B58), day); val floor = lerp(Color(0xFF080A0D), Color(0xFF4F3D2B), day)
    drawRect(lerp(wall, Color.White, 0.12f), Offset.Zero, Size(w, floorY))
    drawTiled(art.wall, Rect(0f, 0f, w, floorY), tile = w * 0.34f, tint = wall)
    drawRect(lerp(floor, Color.White, 0.1f), Offset(0f, floorY), Size(w, h - floorY))
    drawTiled(art.floor, Rect(0f, floorY, w, h), tile = w * 0.3f, tint = floor)
    // The window: the sky seen through its transparent panes, a sun or moon, and the carved frame over it.
    val wh = h * 0.5f; val ww = wh * art.window.width / art.window.height
    val win = Rect(w * 0.1f, h * 0.06f, w * 0.1f + ww, h * 0.06f + wh)
    val pane = Rect(win.left + ww * 0.08f, win.top + wh * 0.09f, win.right - ww * 0.08f, win.bottom - wh * 0.1f)
    // What is outside, in the pane's own place.
    fun DrawScope.outside() {
        val bands = 5
        for (i in 0 until bands) drawRect(lerp(s.zenith, s.horizon, (i + 0.5f) / bands), Offset(pane.left, pane.top + pane.height * i / bands), Size(pane.width, pane.height / bands + 1f))
        if (s.lightFrom.up) {
            val body = Offset(pane.left + s.lightFrom.x * pane.width, pane.top + s.lightFrom.y * pane.height)
            // The sun by day, the moon when it is the moon that shines.
            if (s.daylight > 0.3f) drawSunArt(art, body, h * 0.022f, Color(0xFFFFF3C4).copy(alpha = 0.5f + 0.4f * s.lightStrength))
            else drawMoonArt(art, body, h * 0.026f, Color(0xFFDDE5F0).copy(alpha = 0.6f + 0.4f * s.lightStrength), moon, southern)
        }
    }
    val smeared = layers != null && quality != Quality.Low && (glass.blur > 0.01f || glass.distortion > 0.01f)
    clipRect(pane.left, pane.top, pane.right, pane.bottom) {
        if (!smeared) outside() else drawThroughGlass(layers!!, pane, glass, t, quality) { outside() }
        // Dirt on the glass itself and a milky veil over what is seen through it; stained glass colours it.
        if (glass.haze > 0.01f) drawRect(Color(0xFFEFE8DA).copy(alpha = (0.45f * glass.haze).coerceAtMost(0.6f)), pane.topLeft, pane.size)
        if (glass.tint != Color.White) drawRect(glass.tint.copy(alpha = 0.32f), pane.topLeft, pane.size)
        if (glass.haze > 0.01f) drawDirt(pane, glass.haze)
    }
    drawArt(art.window, win.topLeft, wh / art.window.height, lerp(Color(0xFF0B0907), Color(0xFF2A1F15), day))
    // Shut, the panes are glass: a pale sheen and two streaks of reflection (gone when it is open).
    if (draught < 0.99f) clipRect(pane.left, pane.top, pane.right, pane.bottom) {
        val shut = 1f - draught
        drawRect(Color.White.copy(alpha = 0.07f * shut), pane.topLeft, pane.size)
        for (k in 0..1) drawLine(Color.White.copy(alpha = (0.16f - 0.07f * k) * shut), Offset(pane.left + pane.width * (0.25f + 0.3f * k), pane.top), Offset(pane.left + pane.width * (0.05f + 0.3f * k), pane.bottom), pane.width * (0.05f - 0.02f * k))
    }
    // The light it lets in, lying on the floor in panes divided by the bars.
    val lean = ((0.5f - s.lightFrom.x) * 2.2f + 0.9f).coerceIn(-1f, 1.4f)
    val patchColor = Color(s.lightColor.red * glass.tint.red, s.lightColor.green * glass.tint.green, s.lightColor.blue * glass.tint.blue)
    val patchStrength = s.lightStrength * (1f - glass.opacity * 0.8f)
    val dust = WindResponse.Dust.drift(wind, t, 0.2f).coerceIn(-3f, 3f) * draught
    if (layers != null && quality != Quality.Low && glass.scatter > 0.01f) {
        // Light scattered by the glass lands softer and wider: the patch is drawn into a layer that is blurred by how much the glass scatters, then added.
        layers.patch.record(size = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt())) { drawWindowLight(pane, floorY + h * 0.03f, h * 0.3f, lean, patchColor, patchStrength, s.sunElevation.coerceIn(0f, 1f), w * 0.5f, 2 to 3, t, quality, dust) }
        val r = glass.scatter * w * 0.02f
        layers.patch.renderEffect = androidx.compose.ui.graphics.BlurEffect(r, r, androidx.compose.ui.graphics.TileMode.Decal)
        layers.patch.blendMode = androidx.compose.ui.graphics.BlendMode.Plus
        drawLayer(layers.patch)
    } else drawWindowLight(pane, floorY + h * 0.03f, h * 0.3f, lean, patchColor, patchStrength, s.sunElevation.coerceIn(0f, 1f), w * 0.5f, 2 to 3, t, quality, dust)
    // Furniture and its shadows, thrown away from the window.
    val lamp = LightSource(win.center.x, win.center.y, s.lightColor, s.lightStrength, w * 1.1f, groundX = win.center.x, groundY = floorY + h * 0.05f)
    val dark = lerp(Color(0xFF0B0907), Color(0xFF2A1F15), day); val lit = lerp(Color(0xFF5B4A3A), Color(0xFFE2C48A), s.lightStrength)
    // A draught through the window: the plant by it leans.
    drawProp(art.prop("plant"), w * 0.56f, h * 0.84f, h * 0.36f, lamp, dark, lit, quality, WindResponse.Plant.bend(wind, t, 0.56f, 0.3f) * draught)
    drawProp(art.prop("stool"), w * 0.76f, h * 0.86f, h * 0.2f, lamp, dark, lit, quality)
    drawProp(art.prop("floor_lamp"), w * 0.9f, h * 0.88f, h * 0.42f, lamp, dark, lit, quality)
    drawRect(Color(0xFF02040A).copy(alpha = (1f - s.daylight) * 0.3f), size = size)
}

/** The offscreen layers dirty glass needs: [outside] (the view as it is), [view] (bent and smeared) and [patch] (the light patch, softened). */
class GlassLayers(val outside: GraphicsLayer, val view: GraphicsLayer, val patch: GraphicsLayer)

/**
 * Draws what [outside] draws (in the pane's place) as seen through glass that bends and blurs: it is recorded once, then drawn again in strips each shifted a little sideways by a slow wave (old glass: the more [Optics.distortion],
 * the more the straight lines wobble), and the result is blurred by [Optics.blur]. Where the platform cannot blur (older Android) the view is simply sharp.
 */
private fun DrawScope.drawThroughGlass(layers: GlassLayers, pane: Rect, glass: Optics, t: Float, quality: Quality, outside: DrawScope.() -> Unit) {
    val size = IntSize(pane.width.toInt().coerceAtLeast(1), pane.height.toInt().coerceAtLeast(1))
    layers.outside.record(size = size) { translate(-pane.left, -pane.top) { outside() } }
    layers.view.record(size = size) {
        val strips = if (glass.distortion > 0.01f) (if (quality == Quality.High) 24 else 10) else 1
        val stripH = size.height.toFloat() / strips
        for (i in 0 until strips) {
            val shift = if (strips == 1) 0f else glass.distortion * pane.width * 0.02f * (sin(i * 0.8f + t * 0.35f) + 0.6f * sin(i * 2.1f - t * 0.2f))
            clipRect(0f, i * stripH, size.width.toFloat(), (i + 1) * stripH + 1f) { translate(shift, 0f) { drawLayer(layers.outside) } }
        }
    }
    layers.view.topLeft = IntOffset(pane.left.toInt(), pane.top.toInt())
    val r = glass.blur * pane.width * 0.025f
    layers.view.renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Clamp) else null
    drawLayer(layers.view)
}

/** Speckle and streaks on a dirty pane: a fixed pattern, as much of it as [amount] (0 to 1) says. */
private fun DrawScope.drawDirt(pane: Rect, amount: Float) {
    val noise = FastNoise(31L)
    val n = (60 * amount).toInt()
    for (i in 0 until n) {
        val x = pane.left + pane.width * noise.at(i, 0); val y = pane.top + pane.height * noise.at(i, 1)
        drawCircle(Color(0xFF2B2620).copy(alpha = 0.05f + 0.1f * noise.at(i, 2) * amount), pane.width * (0.004f + 0.012f * noise.at(i, 3)), Offset(x, y))
    }
    for (i in 0 until (6 * amount).toInt() + 1) {
        val x = pane.left + pane.width * noise.at(i, 4); val top = pane.top + pane.height * 0.2f * noise.at(i, 5)
        drawRect(Color(0xFF3A342B).copy(alpha = 0.07f * amount), Offset(x, top), Size(pane.width * 0.006f, pane.height * (0.3f + 0.5f * noise.at(i, 6))))
    }
}

/** The panes the window demo can have. */
enum class WindowGlass(val label: String, val optics: Optics) {
    Clean("Clean glass", Optics.Glass()),
    Dirty("Dirty glass", Optics.Frosted(0.5f)),
    Frosted("Frosted glass", Optics.Frosted(1f)),
    Old("Old glass", Optics(0.12f, Color.White, scatter = 0.1f, blur = 0.08f, distortion = 0.9f, haze = 0.1f)),
    Stained("Stained glass", Optics(0.2f, Color(0xFFFF9A5B), scatter = 0.2f, blur = 0.1f, distortion = 0.25f)),
}
