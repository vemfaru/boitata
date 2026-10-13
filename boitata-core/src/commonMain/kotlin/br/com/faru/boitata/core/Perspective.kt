package br.com.faru.boitata.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Perspective on a flat top (a desk, a stage floor) seen from a fixed viewpoint, so things standing on it are drawn smaller the
 * farther back they stand. The surface runs from [frontEdgeY] (nearest the viewer) to [backEdgeY] in world y, and is [depth] real
 * units deep (any unit, as long as the eye's numbers use the same one). The eye is [eyeBack] behind the front edge and
 * [eyeHeight] above the surface, looking down at [pitchDegrees]. A point d behind the front edge is at distance
 *
 *     z(d) = (eyeBack + d) cos(pitch) + eyeHeight sin(pitch)
 *
 * along the camera's axis, and an object's size goes as 1 / z; [referenceY] is the foot position drawn at scale 1.
 */
class Perspective(
    val frontEdgeY: Float,
    val backEdgeY: Float,
    val depth: Float,
    val eyeBack: Float,
    val eyeHeight: Float,
    val pitchDegrees: Float,
    val referenceY: Float,
) {
    private val cosPitch = cos(pitchDegrees * PI / 180.0).toFloat()
    private val sinPitch = sin(pitchDegrees * PI / 180.0).toFloat()

    /** Distance from the eye along the camera's axis to the point whose foot is at world y [feetY]. */
    fun distance(feetY: Float): Float {
        val d = ((frontEdgeY - feetY) / (frontEdgeY - backEdgeY) * depth).coerceIn(-10f, depth + 10f)
        return (eyeBack + d) * cosPitch + eyeHeight * sinPitch
    }

    /** How big a thing standing with its feet at world y [feetY] is drawn, against one standing at [referenceY]. */
    fun scale(feetY: Float): Float = distance(referenceY) / distance(feetY)
}
