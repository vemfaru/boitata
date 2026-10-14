package br.com.faru.boitata.core.fluid

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sin

/**
 * A container that can be knocked over: a rectangle [width] by [height] (in meters) standing on the floor, tipping about its bottom corner, falling to lie on its side and bouncing a little.
 *
 * Its centre of mass is above the corner it pivots on until it has tipped to the angle `atan(width / height)` (the *tipping angle*); push it past that and gravity pulls it over, push it less and it settles back upright.
 * With `I = m (w^2 + h^2) / 3` about the corner and the torque of the weight about it, the angle follows `angle'' = g (y sin(angle) - (w / 2) cos(angle)) / (I / m)`, y the height of the centre of mass. Load it with a liquid ([setLoad]) and the centre of mass drops and the inertia changes.
 * [angle] is positive tipping one way and negative the other; the floor holds it between lying on one side and the other.
 */
public class TippingBody(public val width: Float, public val height: Float) {
    /** Radians from upright: positive tips one way, negative the other, a quarter turn is lying on its side. */
    public var angle: Float = 0f

    /** Radians per second. */
    public var omega: Float = 0f

    /** How much of the speed it keeps on hitting the floor lying down. */
    public var restitution: Float = 0.28f

    // What it is loaded with: the container's mass and the liquid's (a block of the liquid's depth sitting in it), which together set where the centre of mass is and how hard it is to turn.
    private var comY = height / 2f
    private var inertiaPerMass = (width * width + height * height) / 3f

    /**
     * Tells it what is in it: the container weighs [containerMass] kg, the liquid [liquidMass] kg and stands [liquidDepth] m deep. The centre of mass is the average of the two (so a heavy liquid lowers it and the container
     * is harder to tip), and the resistance to turning is the sum of theirs about the corner (a block `m (w^2 + h^2) / 3` each).
     */
    public fun setLoad(containerMass: Float, liquidMass: Float, liquidDepth: Float) {
        val total = (containerMass + liquidMass).coerceAtLeast(1e-6f)
        comY = (containerMass * height / 2f + liquidMass * liquidDepth / 2f) / total
        inertiaPerMass = (containerMass * (width * width + height * height) + liquidMass * (width * width + liquidDepth * liquidDepth)) / (3f * total)
    }

    /** The angle at which it falls over of itself: where the centre of mass is straight above the corner it tips on. */
    public val tippingAngle: Float get() = atan2(width / 2f, comY)

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
            // The torque about the corner it tips on, per unit mass: restoring below the tipping angle, pulling it over above it. (The centre of mass is half a width in and comY up.)
            val acc = 9.81f * (comY * sin(a) - width / 2f * kotlin.math.cos(a)) / inertiaPerMass
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
