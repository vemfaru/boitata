package br.com.faru.boitata.core.sky

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import kotlin.math.abs

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
    public companion object {
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
