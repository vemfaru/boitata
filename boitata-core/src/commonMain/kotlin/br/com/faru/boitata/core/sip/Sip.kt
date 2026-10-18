package br.com.faru.boitata.core.sip

import br.com.faru.boitata.core.fluid.LiquidVessel
import br.com.faru.boitata.core.fluid.Sipper
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * Where a container is drawn while someone sips from it: [x] and [y] are the middle of its base in pixels, [scale] how much bigger than on the table (1 is as it stands there; closer to the eye is bigger) and [tilt]
 * how far it is tipped **toward the viewer**, radians (0 upright, side on; larger and the top leans in toward the eye, so it is seen foreshortened and its opening is seen from above). It does not turn in the picture.
 */
public class SipPose(public val x: Float, public val y: Float, public val scale: Float, public val tilt: Float) {
    /** The pose [t] (0..1) of the way from this to [other]. */
    public fun lerp(other: SipPose, t: Float): SipPose =
        SipPose(x + (other.x - x) * t, y + (other.y - y) * t, scale + (other.scale - scale) * t, tilt + (other.tilt - tilt) * t)
}

/**
 * How a sip is seen: the camera, and so where the container ends up. [drinkPose] is where it is held to drink, for a container on a canvas [canvasWidth] by [canvasHeight] pixels drawn [pixelsPerMeter] big, leaning
 * [pitch] radians toward the viewer; [dim] is how much the rest of the scene is darkened while it is (0..1), and [vignette] how dark the edges go, which is what makes the view first person.
 */
public interface SipViewpoint {
    public val name: String
    public val dim: Float
    public val vignette: Float
    public fun drinkPose(vessel: LiquidVessel, pixelsPerMeter: Float, canvasWidth: Float, canvasHeight: Float, pitch: Float): SipPose
}

/**
 * Seen through the eyes of whoever drinks: the container comes **in toward the camera**, growing until its rim is [rimShare] of the width of the view across, low in the middle of it, its top leaning in to the eye so the
 * opening is seen from above and the base drops out of the bottom of the view.
 */
public class PovViewpoint(public val rimShare: Float = 0.55f, override val dim: Float = 0.45f, override val vignette: Float = 0.65f) : SipViewpoint {
    override val name: String get() = "POV"

    override fun drinkPose(vessel: LiquidVessel, pixelsPerMeter: Float, canvasWidth: Float, canvasHeight: Float, pitch: Float): SipPose {
        val scale = max(1f, rimShare * canvasWidth / (vessel.width * pixelsPerMeter))
        // The middle of the rim is held still low in the view; the base is below it, foreshortened by the lean.
        val rimY = canvasHeight * 0.74f
        return SipPose(canvasWidth * 0.5f, rimY + vessel.height * pixelsPerMeter * scale * cos(pitch), scale, pitch)
    }
}

/** Who is sipping, and from where it is seen. [You] drinks in first person; another character in another camera is another [SipViewpoint]. */
public class Drinker(public val name: String, public val viewpoint: SipViewpoint) {
    public companion object {
        public val You: Drinker = Drinker("You", PovViewpoint())

        public val All: List<Drinker> = listOf(You)
    }
}

/**
 * Someone sipping from [vessel]: it is brought up from where it stands ([from]) to the mouth ([Phase.Raise]), then, while the sip is held, a gulp is taken at once and another every half second ([Phase.Drink]; each
 * gulp lowers the level and the container leans in a touch more as there is less in it), and when it is let go, or there is nothing left to drink, it is put back ([Phase.Lower]). Call [step] once a step, after the
 * vessel has stepped; its position, size and lean are [pose]. The vessel itself stays upright and the liquid in it still: nothing can spill.
 */
public class SipJob(
    public val vessel: LiquidVessel, public val drinker: Drinker, public val from: SipPose,
    private val pixelsPerMeter: Float, private val canvasWidth: Float, private val canvasHeight: Float,
) {
    public enum class Phase { Raise, Drink, Lower, Done }

    public var phase: Phase = Phase.Raise
        private set

    /** How far up it is, 0 on the table to 1 at the mouth: what the dimming follows. */
    public var progress: Float = 0f
        private set

    /** 1 just after a gulp, dying away: the container dips a little with each. */
    public var gulp: Float = 0f
        private set

    /** How many gulps have been taken, and how much they took in all (m^2). */
    public var gulps: Int = 0
        private set
    public var taken: Float = 0f
        private set

    public val done: Boolean get() = phase == Phase.Done

    private val sipper = Sipper()
    private var held = true

    /** Where the container is drawn now. */
    public var pose: SipPose = from
        private set

    /** The sip is let go: it is put back. */
    public fun release() { held = false }

    /** How far the top leans in toward the eye: more the less there is, as you tip a glass up to get the last of it. */
    private fun drinkPitch(): Float = (0.7f + 0.55f * (1f - vessel.fill)).coerceAtMost(1.3f)

    public fun step(dt: Float) {
        if (phase == Phase.Done || dt <= 0f) return
        gulp = max(0f, gulp - dt / 0.35f)
        when (phase) {
            Phase.Raise -> {
                progress = min(1f, progress + dt / RAISE)
                if (!held) phase = Phase.Lower
                else if (progress >= 1f) phase = Phase.Drink
            }
            Phase.Drink -> {
                val took = sipper.step(vessel, dt)
                if (took > 0f) { gulps++; taken += took; gulp = 1f; vessel.emit(br.com.faru.boitata.core.fluid.LiquidCueKind.Gulp) }
                if (!held || !vessel.sippable) phase = Phase.Lower
            }
            Phase.Lower -> {
                progress = max(0f, progress - dt / LOWER)
                if (progress <= 0f) {
                    pose = from; phase = Phase.Done
                    return
                }
            }
            Phase.Done -> return
        }
        val e = progress * progress * (3f - 2f * progress)
        // Each gulp leans it in a touch more.
        val target = drinker.viewpoint.drinkPose(vessel, pixelsPerMeter, canvasWidth, canvasHeight, drinkPitch() + 0.05f * gulp)
        pose = from.lerp(target, e)
    }

    private companion object { const val RAISE = 0.75f; const val LOWER = 0.65f }
}
