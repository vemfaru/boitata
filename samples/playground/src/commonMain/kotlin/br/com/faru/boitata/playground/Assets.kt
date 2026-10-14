package br.com.faru.boitata.playground

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import br.com.faru.boitata.core.FastNoise
import androidx.compose.ui.graphics.drawscope.clipPath
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.sky.MoonPhase
import br.com.faru.boitata.core.sky.MoonShape
import br.com.faru.boitata.playground.resources.Res
import br.com.faru.boitata.weather.SkyArt
import br.com.faru.boitata.weather.StarAtlas
import br.com.faru.boitata.weather.drawAtlasStar
import kotlin.math.sin
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * The woodcut art, loaded once. Every picture is solid Ink with its carved lines cut out as transparency, so the game tints it by the
 * hour, lights it from a fire, and casts shadows from its shape (see docs/design/playground-assets-prompts.md).
 */
class Sprites(
    val far: ImageBitmap, val mid: ImageBitmap, val ground: ImageBitmap, val near: ImageBitmap,
    val clouds: ImageBitmap,
    val head: ImageBitmap, val logs: ImageBitmap, val pit: ImageBitmap, val stones: ImageBitmap,
    val wall: ImageBitmap, val floor: ImageBitmap, val window: ImageBitmap,
    val sun: ImageBitmap, val moon: ImageBitmap, val stars: ImageBitmap,
    val props: Map<String, ImageBitmap>,
) {
    fun prop(name: String): ImageBitmap = props.getValue(name)
}

private val propNames = listOf(
    "araucaria", "palm", "sumauma", "fern", "bromeliad_log", "lantern", "floor_lamp", "stool", "plant", "crate", "figure", "pot",
)

private suspend fun bitmap(name: String): ImageBitmap = Res.readBytes("drawable/$name.png").decodeToImageBitmap()

suspend fun loadSprites(): Sprites = Sprites(
    far = bitmap("layer_far"), mid = bitmap("layer_mid"), ground = bitmap("layer_ground"), near = bitmap("layer_near"),
    clouds = bitmap("clouds_strip"),
    head = bitmap("serpent_head"), logs = bitmap("fire_logs"), pit = bitmap("fire_pit"), stones = bitmap("fire_stones"),
    wall = bitmap("room_wall"), floor = bitmap("room_floor"), window = bitmap("room_window"),
    sun = bitmap("sky_sun"), moon = bitmap("sky_moon"), stars = bitmap("sky_stars"),
    props = propNames.associateWith { bitmap("prop_$it") },
)

/** The art for the scenes below; null while it loads. */
val LocalSprites = compositionLocalOf<Sprites?> { null }

/** Loads the art, then shows [content] with it available as [LocalSprites]. */
@Composable
fun ProvideSprites(content: @Composable (Sprites) -> Unit) {
    val sprites by produceState<Sprites?>(null) { value = loadSprites() }
    sprites?.let { content(it) }
}

/** The sun, moon and stars you drew, as the library's sky wants them. */
fun Sprites.skyArt(): SkyArt = SkyArt(sun = sun, moon = moon, stars = StarAtlas(stars))

/** [count] twinkling stars from your star atlas over the top [maxY] pixels, at [level] 0..1 (a clear dark night is 1). They turn very slowly and each twinkles in its own time. */
fun DrawScope.drawNightStars(art: Sprites, t: Float, maxY: Float, level: Float, count: Int = 90, seed: Long = 2L, color: Color = Color(0xFFF1E6D0)) {
    if (level <= 0.01f) return
    val atlas = StarAtlas(art.stars)
    val noise = FastNoise(seed)
    for (i in 0 until count) {
        val alpha = (level * (0.35f + 0.65f * (0.5f + 0.5f * sin(t * (0.8f + noise.at(i, 2)) + i))) * 0.9f).coerceIn(0f, 1f)
        val pixels = (4.5f + 6f * noise.at(i, 3)) * size.width / 720f
        drawAtlasStar(atlas, (noise.at(i, 4) * atlas.count).toInt().coerceAtMost(atlas.count - 1), Offset(noise.at(i, 0) * size.width, noise.at(i, 1) * maxY * 0.8f), pixels, alpha, Quality.High)
    }
}

/**
 * Your moon disc centred on [center], [radius] pixels, in [color]; the cut-out detail shows the sky. Only the lit part of [phase] is drawn solid (on the side the hemisphere says),
 * and the dark part faintly, so a crescent or a quarter shows its whole round shape.
 */
fun DrawScope.drawMoonArt(art: Sprites, center: Offset, radius: Float, color: Color, phase: MoonPhase = MoonPhase.Full, southern: Boolean = false) {
    val at = Offset(center.x - radius, center.y - radius)
    val k = 2f * radius / art.moon.width
    if (phase.fraction < 0.98f) drawArt(art.moon, at, k, color.copy(alpha = color.alpha * 0.12f))
    clipPath(MoonShape.litPath(center.x, center.y, radius, phase.fraction, MoonShape.litOnRight(phase.waxing, southern))) { drawArt(art.moon, at, k, color) }
}

/** Your sun disc with its rays centred on [center], the disc [radius] pixels (the rays reach out past it). */
fun DrawScope.drawSunArt(art: Sprites, center: Offset, radius: Float, color: Color) {
    drawArt(art.sun, Offset(center.x - radius * 1.25f, center.y - radius * 1.25f), 2.5f * radius / art.sun.width, color)
}

/** A rectangle of a picture, in its own pixels. */
class Crop(val x: Int, val y: Int, val w: Int, val h: Int)

/** Draws [crop] of [image] with its top-left at [at], scaled by [scale], every visible pixel painted [tint] (null keeps the art's own ink). */
fun DrawScope.drawArt(image: ImageBitmap, at: Offset, scale: Float, tint: Color? = null, crop: Crop? = null, alpha: Float = 1f) {
    val c = crop ?: Crop(0, 0, image.width, image.height)
    // A float translation, so a picture that drifts a fraction of a pixel per frame moves smoothly instead of stepping.
    translate(at.x, at.y) {
        drawImage(
            image,
            srcOffset = IntOffset(c.x, c.y), srcSize = IntSize(c.w, c.h),
            dstSize = IntSize((c.w * scale).toInt().coerceAtLeast(1), (c.h * scale).toInt().coerceAtLeast(1)),
            alpha = alpha, colorFilter = tint?.let { ColorFilter.tint(it, BlendMode.SrcIn) }, filterQuality = FilterQuality.Medium,
        )
    }
}

/** Scales a 1920x1080 picture to cover this scope and centres it. */
class Cover(val scale: Float, val x: Float, val y: Float) {
    fun px(artX: Float) = x + artX * scale
    fun py(artY: Float) = y + artY * scale
}

fun DrawScope.cover(artW: Float = 1920f, artH: Float = 1080f): Cover {
    val s = maxOf(size.width / artW, size.height / artH)
    return Cover(s, (size.width - artW * s) / 2f, (size.height - artH * s) / 2f)
}

/** The scene's horizon (60% of the art's height). */
const val ART_HORIZON = 0.6f
