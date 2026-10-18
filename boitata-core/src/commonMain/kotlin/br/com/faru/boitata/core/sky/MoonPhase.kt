package br.com.faru.boitata.core.sky

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor

/**
 * A phase of the moon: how much of its face is lit ([fraction], 0 new to 1 full) and whether it is growing ([waxing]) or shrinking.
 * The eight named phases are the ones people recognise; any in-between value works too.
 */
public class MoonPhase(
    public val label: String,
    /** The lit part of the face, 0 (new, dark) to 1 (full). */
    public val fraction: Float,
    /** Growing towards full (true) or shrinking away from it (false). */
    public val waxing: Boolean = true,
) {
    /**
     * Where in the month this phase is, 0 new, 0.25 first quarter, 0.5 full, 0.75 last quarter, back to 1 (new again). The phases are points on this circle, which is what lets one phase
     * move smoothly into another (see [stepAge]) and lets a calendar say which phase a day has (see [of]).
     */
    public val age: Float get() {
        val rise = (acos((1f - 2f * fraction.coerceIn(0f, 1f)).coerceIn(-1f, 1f)) / (2.0 * PI)).toFloat()
        return if (waxing) rise else 1f - rise
    }

    public companion object {
        /** The days from one new moon to the next. */
        public const val SYNODIC_DAYS: Double = 29.530588

        /** The phase at [age] (0..1, wraps): the lit part is how far round the circle it is, and it is growing in the first half. The label is the nearest named phase. */
        public fun ofAge(age: Float): MoonPhase {
            val a = age - floor(age)
            val k = ((1.0 - cos(2.0 * PI * a)) / 2.0).toFloat()
            val nearest = All.minBy { circularDistance(it.age, a) }
            return MoonPhase(nearest.label, k, waxing = a < 0.5f)
        }

        /**
         * The moon on the day [clock] says. The first moon of day 0 is [ageAtStartDays] days old (about 4 is a thin waxing crescent, 14.8 a full moon), and from there a month is [SYNODIC_DAYS]
         * long, so the phase changes a little every hour and the same one comes back each month.
         */
        public fun of(clock: DayClock, ageAtStartDays: Double = 4.0): MoonPhase {
            val days = clock.totalHours / 24.0 + ageAtStartDays
            val age = ((days / SYNODIC_DAYS) % 1.0 + 1.0) % 1.0
            return ofAge(age.toFloat())
        }

        private fun circularDistance(a: Float, b: Float): Float = abs(((a - b + 1.5f) % 1f) - 0.5f)

        /**
         * One step of a smooth change from the moon at age [current] to the one at [target]: it goes the short way round the month (a waning crescent into a waxing one passes through the new moon,
         * never through the full), easing out so it settles in about [settleSeconds]. Returns the new age; at [target] once close enough.
         */
        public fun stepAge(current: Float, target: Float, dt: Float, settleSeconds: Float = 1.2f): Float {
            val delta = ((target - current + 1.5f) % 1f) - 0.5f                     // -0.5..0.5, the short way
            if (abs(delta) < 0.0005f) return target
            val next = current + delta * (1f - exp(-dt * 4f / settleSeconds))
            return (next % 1f + 1f) % 1f
        }

        public val New: MoonPhase = MoonPhase("New", 0f)
        public val WaxingCrescent: MoonPhase = MoonPhase("Waxing crescent", 0.2f, waxing = true)
        public val FirstQuarter: MoonPhase = MoonPhase("First quarter", 0.5f, waxing = true)
        public val WaxingGibbous: MoonPhase = MoonPhase("Waxing gibbous", 0.8f, waxing = true)
        public val Full: MoonPhase = MoonPhase("Full", 1f)
        public val WaningGibbous: MoonPhase = MoonPhase("Waning gibbous", 0.8f, waxing = false)
        public val LastQuarter: MoonPhase = MoonPhase("Last quarter", 0.5f, waxing = false)
        public val WaningCrescent: MoonPhase = MoonPhase("Waning crescent", 0.2f, waxing = false)

        /** The eight phases in the order of a month. */
        public val All: List<MoonPhase> = listOf(New, WaxingCrescent, FirstQuarter, WaxingGibbous, Full, WaningGibbous, LastQuarter, WaningCrescent)
    }
}

/** The shape of the lit part of the moon. */
public object MoonShape {
    /**
     * The lit part of a moon centred on ([cx], [cy]) with radius [r], as a path.
     *
     * One edge is the moon's own limb (half a circle); the other is the terminator, the line between day and night on the moon, which is half an ellipse as wide as the moon is tall and
     * as thick as the phase says: a thin crescent near new, flat at a quarter, bulging past the middle towards full.
     * [litOnRight] picks the side. From the northern hemisphere a waxing moon is lit on its right; from the southern it is lit on its left (see [litOnRight]).
     */
    public fun litPath(cx: Float, cy: Float, r: Float, fraction: Float, litOnRight: Boolean): Path {
        val k = fraction.coerceIn(0f, 1f)
        val path = Path()
        if (k <= 0.002f) return path
        // The terminator's reach from the centre towards the lit side: all the way (thin crescent) at 0, none at a quarter, back across the face (gibbous) towards 1.
        val reach = 1f - 2f * k
        path.moveTo(cx, cy - r)
        path.arcTo(Rect(cx - r, cy - r, cx + r, cy + r), -90f, 180f, false)               // the lit limb: down the right side
        val t = abs(reach) * r
        if (t < 0.5f) path.lineTo(cx, cy - r)
        else path.arcTo(Rect(cx - t, cy - r, cx + t, cy + r), 90f, if (reach > 0f) -180f else 180f, false)   // back up along the terminator
        path.close()
        if (!litOnRight) {
            // Mirror about the vertical through the centre.
            val m = Matrix().apply { translate(cx, 0f); scale(-1f, 1f); translate(-cx, 0f) }
            path.transform(m)
        }
        return path
    }

    /** Which side is lit: from the northern hemisphere a waxing moon is lit on its right, from the southern on its left; waning is the other way round. */
    public fun litOnRight(waxing: Boolean, southernHemisphere: Boolean): Boolean = waxing != southernHemisphere
}
