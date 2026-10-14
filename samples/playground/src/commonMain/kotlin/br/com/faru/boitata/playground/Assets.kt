package br.com.faru.boitata.playground

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import br.com.faru.boitata.light.VesselArt
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
import br.com.faru.boitata.core.light.AlphaMask
import androidx.compose.ui.graphics.toPixelMap
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
    /** The animations: a row of poses side by side (see [SpriteStrip]). */
    val strips: Map<String, SpriteStrip> = emptyMap(),
    /** The wind's own art: gusts, flying leaves, grass (see [WindArt]). */
    val wind: WindArt? = null,
    /** The hand-drawn containers of the liquids bench, by name (tumbler, bottle, jug, kerosene, mug). */
    val vessels: Map<String, VesselArt> = emptyMap(),
    /** The bench's own pictures (table parts, things to stand on it), by name without the bench_ prefix. */
    val bench: Map<String, ImageBitmap> = emptyMap(),
) {
    fun prop(name: String): ImageBitmap = props.getValue(name)

    private val masks = HashMap<String, AlphaMask>()

    /** How solid a prop is where, as a coarse grid of its picture's transparency (made once, the first time it is asked for): where a ray of light passes between its parts. */
    fun mask(name: String): AlphaMask = masks.getOrPut(name) { prop(name).toAlphaMask() }
}

/** An animation as one picture: [frames] poses of the same size side by side, in the order they play. */
class SpriteStrip(val image: ImageBitmap, val frames: Int) {
    val frameWidth: Int get() = image.width / frames

    /** Width over height of one pose. */
    val aspect: Float get() = frameWidth.toFloat() / image.height
}

/** The animations that exist, by name, and how many poses each has (the strips exported by docs/design/anim_strip.py). */
private val stripNames = mapOf(
    "tamandua_walk" to 4, "arara_fly" to 4, "capivara_walk" to 4, "jacare_walk" to 4,
    // The jaguar, armadillo and maned wolf have three poses (a stride, a pause and the other stride).
    "onca_walk" to 3, "tatu_walk" to 3, "lobo_guara_walk" to 3,
    // The story's cycles, four poses each (the people walk, run and burn; the animals flee and swim).
    "lampiao_walk" to 4, "maria_walk" to 4, "cacador_walk" to 4, "cacador_run" to 4, "cacador_panic" to 4, "cacador_burning" to 4,
    "couple_xote" to 4, "benz_pull" to 4, "arara_takeoff" to 4, "tucano_fly" to 4, "dragonfly" to 4,
    "capivara_swim" to 4, "jacare_swim" to 4, "lobo_run" to 4, "onca_run" to 4, "tamandua_flee" to 4, "tatu_curl" to 4,
)

/** Where the liquid sits in each container's pictures (their pixels): the part stretched onto the physical box. */
private val vesselBodies = mapOf(
    "tumbler" to Rect(29f, 60f, 391f, 552f), "bottle" to Rect(5f, 245f, 326f, 846f), "jug" to Rect(28f, 156f, 394f, 662f),
    "kerosene" to Rect(29f, 60f, 371f, 551f), "mug" to Rect(30f, 62f, 560f, 496f),
)
private val benchNames = listOf("table_top", "table_apron", "table_leg", "table_end", "clay_pot", "kettle", "rapadura", "spoon", "sugar_cube", "tin_cup")

private val propNames = listOf(
    "araucaria", "palm", "sumauma", "fern", "bromeliad_log", "lantern", "floor_lamp", "stool", "plant", "crate", "pot",
    // The cordel people, the lamp Lampião carries, and the animals of the woods and the sertão (side on, facing right).
    "lampiao", "maria_bonita", "cacador", "lampiao_lamp",
    "onca", "tamandua", "capivara", "lobo_guara", "jacare", "tatu", "arara", "tucano",
    // Plants that sway in the wind: a banana plant standing, a cipó hanging from the top.
    "bananeira", "cipo",
)

/**
 * Where each person's free hand is: the closed fist that grips a lamp's handle, as shares of their sprite (x from the left, y from the top).
 * Turn it into pixels with the sprite's drawn width and height, and hang [Lamp] from it.
 */
val HandAnchors: Map<String, Offset> = mapOf(
    "lampiao" to Offset(0.048f, 0.668f),
    "maria_bonita" to Offset(0.949f, 0.625f),
    "cacador" to Offset(0.046f, 0.581f),
)

/** The lampião, the hand-held kerosene lamp (prop "lampiao_lamp"), as shares of its sprite: it hangs by [handle] and its light comes from [flame]. */
object Lamp {
    /** The top of the handle, where the fist holds it. */
    val handle = Offset(0.5f, 0f)

    /** The flame behind the glass. */
    val flame = Offset(0.5f, 0.58f)

    /** How tall it is drawn against whoever carries it. */
    const val OF_HOLDER = 0.22f
}

private suspend fun bitmap(name: String): ImageBitmap = Res.readBytes("drawable/$name.png").decodeToImageBitmap()

suspend fun loadSprites(): Sprites = Sprites(
    far = bitmap("layer_far"), mid = bitmap("layer_mid"), ground = bitmap("layer_ground"), near = bitmap("layer_near"),
    clouds = bitmap("clouds_strip"),
    head = bitmap("serpent_head"), logs = bitmap("fire_logs"), pit = bitmap("fire_pit"), stones = bitmap("fire_stones"),
    wall = bitmap("room_wall"), floor = bitmap("room_floor"), window = bitmap("room_window"),
    sun = bitmap("sky_sun"), moon = bitmap("sky_moon"), stars = bitmap("sky_stars"),
    props = propNames.associateWith { bitmap("prop_$it") },
    strips = stripNames.mapValues { (name, frames) -> SpriteStrip(bitmap("anim_$name"), frames) },
    vessels = vesselBodies.mapValues { (name, body) ->
        if (name == "mug") VesselArt(bitmap("vessel_mug_body"), body, shine = bitmap("vessel_mug_shine"), opening = bitmap("vessel_mug_opening"), tint = Color(0xFFC9B38A))
        else VesselArt(bitmap("vessel_${name}_front"), body, back = bitmap("vessel_${name}_back"), shine = bitmap("vessel_${name}_shine"))
    },
    bench = benchNames.associateWith { bitmap("bench_$it") },
    wind = WindArt(
        gusts = listOf("wind_gust_1", "wind_gust_2", "wind_gust_3").map { bitmap(it) },
        leaves = listOf("leaf_broad", "leaf_embauba", "leaf_dry", "leaf_grimpa").map { bitmap(it) },
        paina = bitmap("seed_paina"),
        tufts = listOf("grass_tuft_low", "grass_tuft_tall", "grass_tuft_flower").map { bitmap(it) },
    ),
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
fun DrawScope.drawArt(image: ImageBitmap, at: Offset, scale: Float, tint: Color? = null, crop: Crop? = null, alpha: Float = 1f, scaleX: Float = scale, scaleY: Float = scale) {
    val c = crop ?: Crop(0, 0, image.width, image.height)
    // A float translation, so a picture that drifts a fraction of a pixel per frame moves smoothly instead of stepping.
    translate(at.x, at.y) {
        drawImage(
            image,
            srcOffset = IntOffset(c.x, c.y), srcSize = IntSize(c.w, c.h),
            dstSize = IntSize((c.w * scaleX).toInt().coerceAtLeast(1), (c.h * scaleY).toInt().coerceAtLeast(1)),
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

/** A coarse grid ([cols] by [rows]) of how opaque this picture is, each cell the average of a few samples of its alpha, for the lights to ask where a thing is solid. */
fun ImageBitmap.toAlphaMask(cols: Int = 24, rows: Int = 48): AlphaMask {
    val pixels = toPixelMap()
    val data = ByteArray(cols * rows)
    for (r in 0 until rows) for (c in 0 until cols) {
        var sum = 0f; var n = 0
        for (sy in 0..2) for (sx in 0..2) {
            val x = ((c + (sx + 0.5f) / 3f) / cols * width).toInt().coerceIn(0, width - 1)
            val y = ((r + (sy + 0.5f) / 3f) / rows * height).toInt().coerceIn(0, height - 1)
            sum += pixels[x, y].alpha; n++
        }
        data[r * cols + c] = (sum / n * 255f).toInt().toByte()
    }
    return AlphaMask(cols, rows, data)
}
