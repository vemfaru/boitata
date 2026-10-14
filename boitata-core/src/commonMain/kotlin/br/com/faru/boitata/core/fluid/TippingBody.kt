package br.com.faru.boitata.core.fluid

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A container that can be knocked over: a rectangle [width] by [height] (in meters) standing on the floor, tipping about its bottom corner, falling to lie on its side and bouncing a little.
 *
 * Its centre of mass is above the corner it pivots on until it has tipped to the angle `atan(width / height)` (the *tipping angle*); push it past that and gravity pulls it over, push it less and it settles back upright.
 * With `I = m (w^2 + h^2) / 3` about the corner and the torque `m g R sin(angle - tippingAngle)` (R the distance from the corner to the centre), the angle follows `angle'' = 3 g / (4 R) sin(angle - tippingAngle)`.
 * [angle] is positive tipping one way and negative the other; the floor holds it between lying on one side and the other.
 */
public class TippingBody(public val width: Float, public val height: Float) {
    /** Radians from upright: positive tips one way, negative the other, a quarter turn is lying on its side. */
    public var angle: Float = 0f

    /** Radians per second. */
    public var omega: Float = 0f

    /** How much of the speed it keeps on hitting the floor lying down. */
    public var restitution: Float = 0.28f

    /** The angle at which it falls over of itself. */
    public val tippingAngle: Float get() = atan2(width, height)

    private val radius: Float get() = sqrt(width * width + height * height) / 2f

    /** Whether it has come to rest lying on its side. */
    public val lying: Boolean get() = abs(angle) >= HALF_TURN - 0.002f && abs(omega) < 0.05f

    /** Whether it is standing. */
    public val upright: Boolean get() = angle == 0f && omega == 0f

    /** Knocks it: gives it [speed] radians per second toward the side [direction] (+1 or -1). */
    public fun knock(speed: Float, direction: Float = 1f) { omega += speed * (if (direction >= 0f) 1f else -1f) }

    /** Moves it on by [dt] seconds. */
    public fun step(dt: Float) {
        var left = dt
        while (left > 0f) {
            val h = minOf(left, 1f / 240f); left -= h
            val a = abs(angle); val s = if (angle != 0f) (if (angle > 0f) 1f else -1f) else (if (omega >= 0f) 1f else -1f)
            // The torque about the corner it tips on: restoring below the tipping angle, pulling it over above it.
            val acc = 3f * 9.81f / (4f * radius) * sin(a - tippingAngle)
            val resting = angle == 0f && omega == 0f && acc < 0f
            if (!resting) omega += s * acc * h * (if (a == 0f && omega != 0f) 1f else 1f)
            omega *= 1f - 0.25f * h
            angle += omega * h
            // The floor: it cannot go below upright (it sits back down) or past lying on its side.
            if (angle * s < 0f && abs(angle) > 0f && a < 0.02f && omega * s <= 0f) { angle = 0f; omega = 0f }
            if (abs(angle) > HALF_TURN) { angle = HALF_TURN * s; omega = -omega * restitution; if (abs(omega) < 0.4f) omega = 0f }
        }
        if (abs(angle) < 0.0005f && abs(omega) < 0.02f) { angle = 0f; omega = 0f }
    }

    private companion object { const val HALF_TURN = (PI / 2.0).toFloat() }
}
