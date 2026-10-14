package br.com.faru.boitata.playground

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * How a creature moves about, as a pure function of time (so a clip is the same every time and nothing is kept between frames).
 *
 * [Patrol] walks to and fro along the ground between two places, stands a moment at each end, and turns round; [Fly] crosses the picture at some height, over and over.
 */
sealed class Gait {
    class Patrol(val from: Float, val to: Float, val y: Float, val speed: Float, val pause: Float = 2f, val phase: Float = 0f) : Gait()
    class Fly(val y: Float, val altitude: Float, val period: Float, val rest: Float = 0f, val phase: Float = 0f) : Gait()
}

/** One animal: its picture (a [strip] of poses if it has an animation, else the still [sprite], which then bobs as it walks), how tall it is, and how it moves. None of them can burn. */
class Creature(val id: String, val sprite: String, val strip: String?, val heightShare: Float, val gait: Gait)

/** Where a creature is now, reused so nothing is allocated while a scene draws. */
class Pose {
    /** Feet, in pixels. */
    var x = 0f
    var y = 0f

    /** 1 facing right (the way the art faces), -1 facing left. */
    var facing = 1f

    /** Which pose of the strip, and how far the feet are lifted (a bird in the air, a still picture's hop). */
    var frame = 0
    var lift = 0f
    var moving = false
}

/**
 * Where [c] is at [t], in a ground that spans [left]..[right] across and [top]..[bottom] from back to front, written to [into]. [bodyLength] is how long the animal is drawn, in pixels:
 * a walker takes a pose of its strip for every fifth of its body length it covers, so its feet keep up with the ground instead of skating.
 */
fun Creature.poseAt(t: Float, left: Float, right: Float, top: Float, bottom: Float, bodyLength: Float, into: Pose) {
    when (val g = gait) {
        is Gait.Patrol -> {
            val length = abs(g.to - g.from) * (right - left)
            val walk = length / (g.speed * (right - left))                       // seconds one way
            val cycle = 2f * (walk + g.pause)
            val p = ((t + g.phase * cycle) % cycle + cycle) % cycle
            val outbound = p < walk + g.pause
            val q = if (outbound) p else p - (walk + g.pause)
            val moving = q < walk
            val travelled = (if (moving) q else walk) * g.speed * (right - left)
            val share = if (moving) q / walk else 1f
            val a = if (outbound) g.from else g.to; val b = if (outbound) g.to else g.from
            into.x = left + (right - left) * (a + (b - a) * share)
            into.y = top + (bottom - top) * g.y
            into.facing = if (b >= a) 1f else -1f
            into.moving = moving
            val stride = bodyLength * 0.2f
            val step = if (moving) travelled / stride else 0f
            into.frame = if (moving) floor(step).toInt() else 0
            into.lift = if (moving && strip == null) abs(sin(step * PI.toFloat())) * 0.03f * bodyLength else 0f
        }
        is Gait.Fly -> {
            val cycle = g.period + g.rest
            val p = ((t + g.phase * cycle) % cycle + cycle) % cycle
            val share = (p / g.period).coerceAtMost(1f)
            val flying = p < g.period
            into.x = left - bodyLength + (right - left + 2f * bodyLength) * share
            into.y = top + (bottom - top) * g.y
            into.facing = 1f
            into.moving = flying
            into.frame = floor(t * 9f).toInt()
            into.lift = (g.altitude + sin(share * PI.toFloat()) * g.altitude * 0.4f + sin(t * 3f) * 0.02f) * bodyLength
        }
    }
}

/** Draws [c]'s current pose with its feet at (0, 0) and up negative, in [colour] (the same convention as a silhouette for a shadow): flipped to face the way it goes. */
fun DrawScope.drawCreature(art: Sprites, c: Creature, pose: Pose, height: Float, colour: Color) {
    val strip = c.strip?.let { art.strips.getValue(it) }
    val image = strip?.image ?: art.prop(c.sprite)
    val aspect = strip?.aspect ?: (image.width.toFloat() / image.height)
    val width = height * aspect
    val crop = strip?.let { Crop((pose.frame % it.frames) * it.frameWidth, 0, it.frameWidth, it.image.height) }
    val k = height / (crop?.h ?: image.height)
    withTransform({ scale(pose.facing, 1f, Offset.Zero) }) {
        drawArt(image, Offset(-width / 2f, -height - pose.lift), k, colour, crop)
    }
}
