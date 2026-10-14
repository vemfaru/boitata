package br.com.faru.boitata.playground

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import br.com.faru.boitata.core.FastNoise
import br.com.faru.boitata.core.draw.sway
import br.com.faru.boitata.core.wind.Wind
import br.com.faru.boitata.core.wind.WindResponse
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The wind's own art (section G of the cut sheet): the cordel [gusts] that stand for the moving air, the [leaves] and the [paina] seed it carries, and the grass [tufts] it bends.
 * The gusts are drawn blowing to the right, the leaves and the seed flat and centred on their middle, the tufts with their foot on the bottom edge.
 */
class WindArt(val gusts: List<ImageBitmap>, val leaves: List<ImageBitmap>, val paina: ImageBitmap, val tufts: List<ImageBitmap>)

private fun wrap(v: Float, span: Float): Float = ((v % span) + span) % span

/**
 * The wind itself, as the woodcut draws it: a few carved swirls between [top] and [bottom], carried along by the air. [travelled] is how far the air has gone
 * (`SceneWind.travelled`), so they speed up, slow down and turn round with the wind without ever jumping. Each one lives a few seconds (it fades in, rides, fades out and is
 * born again somewhere else) and shows only as much as the wind blows where it is: none in a calm, more of them and bolder in a gale, stretched in a gust.
 */
fun DrawScope.drawGusts(art: WindArt, wind: Wind, travelled: Float, t: Float, top: Float, bottom: Float, color: Color, count: Int = 5, seed: Long = 11L) {
    if (wind.speed <= 0f || wind.direction == 0f) return
    val w = size.width
    val noise = FastNoise(seed)
    val facing = if (wind.direction < 0f) -1f else 1f
    // A breeze has one or two, a gale all of them.
    val active = (count * (0.3f + wind.speed)).toInt().coerceIn(1, count)
    for (i in 0 until active) {
        val life = 4.5f + 3f * noise.at(i, 0)
        val age = t / life + noise.at(i, 1)
        val cycle = floor(age).toInt()
        val u = age - cycle
        val n = i * 131 + cycle
        val image = art.gusts[(noise.at(n, 2) * art.gusts.size).toInt().coerceAtMost(art.gusts.size - 1)]
        val gw = w * (0.14f + 0.12f * noise.at(n, 3))
        val k = gw / image.width
        val span = w + 2f * gw
        val x = wrap(noise.at(n, 4) * span + travelled * w * 0.12f * (0.8f + 0.4f * noise.at(i, 5)), span) - gw
        val y = top + (bottom - top) * noise.at(n, 6)
        val strength = abs(wind.at(t, ((x + gw / 2f) / w).coerceIn(0f, 1f)).x)
        val alpha = sin(PI.toFloat() * u) * ((strength - 0.05f) / 0.35f).coerceIn(0f, 1f)
        if (alpha <= 0.01f) continue
        // Turned to face the way the wind blows, and drawn out longer as it gusts.
        val pivot = Offset(x + gw / 2f, y + image.height * k / 2f)
        withTransform({ scale(facing * (0.85f + 0.4f * strength), 1f, pivot) }) { drawArt(image, Offset(x, y), k, color, alpha = alpha) }
    }
}

/**
 * Leaves and paina seeds blown through the picture: they fall from above [top] to below [bottom] at their own pace, swinging from side to side as a falling leaf does, and are
 * carried along by the air ([travelled], as for [drawGusts]) as much as [WindResponse.Leaf] says; the seed is lighter ([WindResponse.Ash]), falls slowly and goes farther.
 * Each leaf spins and turns over as it goes. [height] is how tall an average leaf is drawn; [color] gives the colour of a leaf at a place (to light it from a fire).
 * In a calm they only fall, and fewer of them.
 */
fun DrawScope.drawFlyingLeaves(art: WindArt, wind: Wind, travelled: Float, t: Float, top: Float, bottom: Float, height: Float, count: Int, seed: Long = 23L, color: (Float, Float) -> Color) {
    val w = size.width
    val noise = FastNoise(seed)
    val margin = height * 2f
    val spanX = w + 2f * margin
    val spanY = bottom - top
    val active = (count * (0.35f + 0.65f * (wind.speed * 1.5f).coerceAtMost(1f))).toInt()
    for (i in 0 until active) {
        val seedLike = i % 5 == 4
        val response = if (seedLike) WindResponse.Ash else WindResponse.Leaf
        val image = if (seedLike) art.paina else art.leaves[i % art.leaves.size]
        val n0 = noise.at(i, 0); val n1 = noise.at(i, 1); val n2 = noise.at(i, 2)
        val fall = spanY * (if (seedLike) 0.015f + 0.015f * n1 else 0.04f + 0.035f * n1)
        val swing = sin(t * (1.1f + 0.9f * n2) + 6.28f * n0)
        // Carried by the air, and lifted a little by the gusts.
        val gust = abs(wind.at(t, n0).x)
        val x = wrap(n0 * spanX + travelled * response.driftGain * w * 0.05f * (0.7f + 0.6f * n2) + swing * height * 1.4f, spanX) - margin
        val y = top + wrap(n1 * spanY + t * fall - gust * height * 2f * sin(t * 0.7f + i), spanY)
        val size = height * (if (seedLike) 0.9f else 0.6f + 0.8f * noise.at(i, 3))
        val k = size / image.height
        // A leaf spins and turns over (seen edge on when it is halfway); the seed only turns slowly.
        val turn = if (seedLike) 1f else cos(t * (1.5f + 2f * n2) + 6.28f * n1).let { if (abs(it) < 0.12f) 0.12f * (if (it < 0f) -1f else 1f) else it }
        val angle = 360f * n2 + t * (if (seedLike) 12f else 60f + 120f * n0) * (if (i % 2 == 0) 1f else -1f) + swing * 25f
        withTransform({ translate(x, y); rotate(angle, Offset.Zero); scale(turn, 1f, Offset.Zero) }) {
            drawArt(image, Offset(-image.width * k / 2f, -image.height * k / 2f), k, color(x, y))
        }
    }
}

/** Something rooted that the wind bends: which picture, where it stands (a share of the width, and a depth: 0 near, 1 at the horizon), how tall (a share of the height at depth 0.5) and how stiff. */
class Rooted(val image: (Sprites) -> ImageBitmap?, val x: Float, val z: Float, val height: Float, val response: WindResponse, val maxLean: Float, val castsShadow: Boolean = false)

/** The grass and the banana plants of the forest floor, far to near. */
val ForestPlants: List<Rooted> = listOf(
    Rooted({ it.wind?.tufts?.get(1) }, 0.33f, 0.72f, 0.11f, WindResponse.Grass, 0.4f),
    Rooted({ it.prop("bananeira") }, 0.15f, 0.62f, 0.3f, WindResponse.Palm, 0.12f, castsShadow = true),
    Rooted({ it.wind?.tufts?.get(0) }, 0.56f, 0.62f, 0.07f, WindResponse.Grass, 0.4f),
    Rooted({ it.wind?.tufts?.get(2) }, 0.78f, 0.55f, 0.1f, WindResponse.Grass, 0.4f),
    Rooted({ it.wind?.tufts?.get(1) }, 0.44f, 0.4f, 0.11f, WindResponse.Grass, 0.4f),
    Rooted({ it.prop("bananeira") }, 0.88f, 0.38f, 0.34f, WindResponse.Palm, 0.12f, castsShadow = true),
    Rooted({ it.wind?.tufts?.get(2) }, 0.27f, 0.3f, 0.1f, WindResponse.Grass, 0.4f),
    Rooted({ it.wind?.tufts?.get(0) }, 0.66f, 0.28f, 0.07f, WindResponse.Grass, 0.4f),
)

/** [image] standing with its foot at ([feetX], [feetY]), [height] tall, bent by [bend] from its foot. */
fun DrawScope.drawRooted(image: ImageBitmap, feetX: Float, feetY: Float, height: Float, bend: Float, maxLean: Float, color: Color) {
    val k = height / image.height
    sway(feetX, feetY, bend, maxLean) { drawArt(image, Offset(feetX - image.width * k / 2f, feetY - height), k, color) }
}

/** A cipó hanging from ([anchorX], [anchorY]), [length] long: it swings about its top, its bottom going the way the wind blows (so the bend is turned round). */
fun DrawScope.drawHangingVine(image: ImageBitmap, anchorX: Float, anchorY: Float, length: Float, bend: Float, color: Color) {
    val k = length / image.height
    sway(anchorX, anchorY, -bend, maxLean = 0.12f) { drawArt(image, Offset(anchorX - image.width * k / 2f, anchorY), k, color) }
}
