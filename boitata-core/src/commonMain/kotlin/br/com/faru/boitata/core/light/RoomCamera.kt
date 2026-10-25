package br.com.faru.boitata.core.light

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The one viewpoint a 2D room is drawn from, as a pinhole camera, so a point of the room (in centimetres) and a point of the picture (in world units) can be turned into each
 * other. Room coordinates: `x` to the right of the camera's axis, `d` behind a reference line on the floor (a desk's front edge), `z` above the floor. The eye is [eyeHeight]
 * above the floor and [eyeBack] in front of the reference line, looking down at [pitchDegrees]; a point `s = d + eyeBack` ahead of the eye and `b = eyeHeight − z` below it is
 *
 *     along = s cos(pitch) + b sin(pitch)        down = b cos(pitch) − s sin(pitch)
 *
 * along the axis and below it, and is drawn at `x' = centerX + focalX · x / along`, `y' = horizonY + focalY · down / along`. The two focal lengths may differ: a painted room is
 * often drawn with its depth squashed against its widths, and a separate [focalY] keeps the camera true to the picture while every plane still maps to it by a perspective.
 *
 * Plain numbers, no allocation: results go into the array passed in.
 */
public class RoomCamera(
    public val eyeHeight: Float,
    public val eyeBack: Float,
    public val pitchDegrees: Float,
    public val focalX: Float,
    public val focalY: Float,
    public val centerX: Float,
    public val horizonY: Float,
) {
    private val c = cos(pitchDegrees * PI / 180.0).toFloat()
    private val s = sin(pitchDegrees * PI / 180.0).toFloat()

    /** How far along the camera's axis the point is: what its size on the picture goes as one over. */
    public fun along(d: Float, z: Float): Float = (d + eyeBack) * c + (eyeHeight - z) * s

    /** Where the room point ([x], [d], [z]) is drawn, into [out] (x', y'). False (and [out] untouched) when it is behind the eye. */
    public fun project(x: Float, d: Float, z: Float, out: FloatArray): Boolean {
        val a = along(d, z)
        if (a <= MIN_ALONG) return false
        val down = (eyeHeight - z) * c - (d + eyeBack) * s
        out[0] = centerX + focalX * x / a
        out[1] = horizonY + focalY * down / a
        return true
    }

    /** The point of the horizontal plane [z] cm above the floor that is drawn at ([px], [py]), into [out] (x, d). False when that picture point is above the plane's horizon. */
    public fun onLevel(px: Float, py: Float, z: Float, out: FloatArray): Boolean {
        val b = eyeHeight - z
        val q = (py - horizonY) / focalY
        val den = q * c + s
        if (b <= 0f || den <= 1e-5f) return false
        val ahead = b * (c - q * s) / den
        val a = ahead * c + b * s
        out[0] = (px - centerX) * a / focalX
        out[1] = ahead - eyeBack
        return true
    }

    /** The point of the upright plane [d] cm behind the reference line (a wall, the face of a thing standing there) that is drawn at ([px], [py]), into [out] (x, z). */
    public fun onUpright(px: Float, py: Float, d: Float, out: FloatArray): Boolean {
        val ahead = d + eyeBack
        val q = (py - horizonY) / focalY
        val den = c - q * s
        if (den <= 1e-5f) return false
        val b = ahead * (q * c + s) / den
        val a = ahead * c + b * s
        if (a <= MIN_ALONG) return false
        out[0] = (px - centerX) * a / focalX
        out[1] = eyeHeight - b
        return true
    }

    /** The point of the side wall [x] cm across (it runs from the front to the back) that is drawn at ([px], [py]), into [out] (d, z). False on the camera's own axis or behind the eye. */
    public fun onSide(px: Float, py: Float, x: Float, out: FloatArray): Boolean {
        val dx = px - centerX
        if (dx > -1e-3f && dx < 1e-3f) return false
        val a = focalX * x / dx
        if (a <= MIN_ALONG) return false
        val down = (py - horizonY) / focalY * a
        val ahead = a * c - down * s
        val below = a * s + down * c
        out[0] = ahead - eyeBack
        out[1] = eyeHeight - below
        return true
    }

    /** How many world units a centimetre across is, drawn at ([d], [z]). */
    public fun unitsPerCm(d: Float, z: Float): Float = focalX / along(d, z)

    public companion object {
        private const val MIN_ALONG = 1f

        /**
         * The camera of a room whose desk top the picture shows: the eye [eyeHeight] above the floor, [eyeBack] in front of the desk's front edge, pitched down [pitchDegrees];
         * the desk top [deskTop] cm above the floor and [deskDepth] cm deep, drawn from world y [frontY] (its front edge) back to [backY]. The two edges fix the focal length
         * and the horizon along y; across, a thing standing at world y [referenceY] on the desk is drawn [unitsPerCmAtReference] units to the centimetre, centred on [centerX].
         */
        public fun fromDesk(
            eyeHeight: Float, eyeBack: Float, pitchDegrees: Float, deskTop: Float, deskDepth: Float,
            frontY: Float, backY: Float, referenceY: Float, unitsPerCmAtReference: Float, centerX: Float,
        ): RoomCamera {
            val c = cos(pitchDegrees * PI / 180.0).toFloat(); val s = sin(pitchDegrees * PI / 180.0).toFloat()
            val b = eyeHeight - deskTop
            fun q(ahead: Float) = (b * c - ahead * s) / (ahead * c + b * s)
            val qFront = q(eyeBack); val qBack = q(eyeBack + deskDepth)
            val focalY = (frontY - backY) / (qFront - qBack)
            val horizon = frontY - focalY * qFront
            // The reference line's depth on the desk, from its y (the inverse of q, at the desk top).
            val qRef = (referenceY - horizon) / focalY
            val aheadRef = b * (c - qRef * s) / (qRef * c + s)
            val alongRef = aheadRef * c + b * s
            return RoomCamera(eyeHeight, eyeBack, pitchDegrees, unitsPerCmAtReference * alongRef, focalY, centerX, horizon)
        }
    }
}
