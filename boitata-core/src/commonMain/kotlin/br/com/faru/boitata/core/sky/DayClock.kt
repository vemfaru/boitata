package br.com.faru.boitata.core.sky

import kotlin.jvm.JvmInline
import kotlin.math.floor

/**
 * The calendar side of the clock: a day number and an hour. [SkyModel] only knows the hour (it wraps), so anything that lives for more than a day (soil that dries, a plant that grows)
 * needs this. It is one number, the hours since day 0, so it adds up without drift and costs nothing to pass around.
 */
@JvmInline
public value class DayClock(public val totalHours: Double) {
    /** The whole days since the start (0 on the first day). */
    public val day: Int get() = floor(totalHours / 24.0).toInt()

    /** The hour on the clock, 0 until 24: what [SkyModel.at] takes. */
    public val hour: Float get() = (totalHours - floor(totalHours / 24.0) * 24.0).toFloat()

    /** This clock moved on by [hours] (negative goes back). */
    public fun advanced(hours: Float): DayClock = DayClock(totalHours + hours)

    public companion object {
        /** A clock at [hour] on day [day]. */
        public fun at(hour: Float, day: Int = 0): DayClock = DayClock(day * 24.0 + hour)
    }
}
