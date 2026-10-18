package br.com.faru.boitata.core.pour

import br.com.faru.boitata.core.fluid.LiquidVessel
import br.com.faru.boitata.core.fluid.PourSide
import br.com.faru.boitata.core.fluid.Tilt
import br.com.faru.boitata.core.fluid.Spill
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Something a container can be told to do, as a menu offers it: [id] says which, [label] is what to show, [enabled] false greys it out (nothing to pour, nowhere to pour it). A [hold] action goes on for as long as it is held down (sipping, pouring) and stops when let go; any other is done by a tap. */
public class VesselAction(public val id: String, public val label: String, public val enabled: Boolean = true, public val hold: Boolean = false)

/**
 * A hand pouring one container into another: it slides [source] beside [target], lifts it over the rim, tips it (see `TippingBody.hold`) until the liquid runs over the lip at a steady rate, keeps the stream on the
 * mouth of [target], then, when [source] is empty or [target] full, sets it upright and carries it home. All positions are the **centres** of the containers, in meters along the surface.
 *
 * Call [step] after [source] has stepped. What runs over the lip is followed to where it lands (`Spill.reach`, from the height it falls): into the mouth of [target] it is poured in (`LiquidVessel.receive`, the volume
 * kept: what [source] loses [target] gains, scaled for their depths), and anywhere else it is [missed] and the caller makes a puddle of it at [missX].
 */
public class PourJob(public val source: LiquidVessel, public val target: LiquidVessel, public val homeX: Float, public val targetX: Float, public val level: PourLevel = PourLevel.TopUp) {
    public enum class Phase { Approach, Pour, Return, Done }

    public var phase: Phase = Phase.Approach
        private set

    /** Where the centre of [source] is now. */
    public var x: Float = homeX
        private set

    /** The volume (m^3) that ran over the lip in the last [step] and did not land in the target, and the x it landed at. */
    public var missed: Float = 0f
        private set
    public var missX: Float = 0f
        private set

    /** The volume (m^3) that has gone into the target so far. */
    public var delivered: Float = 0f
        private set

    public val done: Boolean get() = phase == Phase.Done

    /** Which side of the target the source stands on, and so which way it tips: the way its spout points if it has one (it hops over the target to get there if it has to), else the nearer way. */
    private val dir = when (source.pourSide) {
        PourSide.Right -> 1f
        PourSide.Left -> -1f
        PourSide.Either -> if (targetX >= homeX) 1f else -1f
    }
    private var stopRequested = false

    /** Asks it to stop pouring now: it sets the source upright and carries it home. For [PourLevel.UntilStopped], but works on any pour. */
    public fun stop() { stopRequested = true }

    /** How far the source is tipped, radians (always on the side it can pour from). */
    public val tiltAngle: Float get() = tilt
    private var lastSpilled = source.spilled
    private var tilt = 0f
    private var extra = 0f
    private var elapsed = 0f

    /** Where the corner [source] tips on is: half a width toward the way it leans from its centre (the same rule [drawVessel] places it by). */
    public val pivotX: Float get() = x + (if (source.body.angle >= 0f) 1f else -1f) * source.width / 2f

    public fun step(dt: Float) {
        if (phase == Phase.Done || dt <= 0f) return
        elapsed += dt
        missed = 0f
        val s = if (source.body.angle >= 0f) 1f else -1f

        // What ran over the lip this step: where does it land?
        val ran = source.spilled - lastSpilled
        lastSpilled = source.spilled
        if (ran > 0f) {
            val reach = Spill.reach(source.fallHeight, source.jetSpeed)
            val land = (x + s * source.width / 2f) + s * (source.lipX() + reach)
            val half = target.width / 2f * 0.9f
            if (abs(land - targetX) <= half && abs(target.body.angle) < 0.3f) {
                val amount = ran * source.width
                target.receive(source.fluid, amount / target.width, source.temperature, at = (0.5f + (land - targetX) / target.width).coerceIn(0.05f, 0.95f))
                delivered += amount
            } else { missed = ran * source.width; missX = land }
        }

        when (phase) {
            Phase.Approach -> {
                if (stopRequested) { phase = Phase.Return; return }
                val beside = targetX - dir * (target.width / 2f + source.width / 2f + 0.025f)
                // To get to the far side it has to go over the target: lift first, then cross.
                val crossing = (x - targetX) * (beside - targetX) < 0f
                if (!crossing || source.lift > target.height + 0.005f) x = moveToward(x, beside, dt)
                liftTo(target.height + 0.03f, dt)
                if (abs(x - beside) < 0.004f && source.lift > target.height) phase = Phase.Pour
            }
            Phase.Pour -> {
                // Turn it more while too little runs, a little less while too much.
                // The tilt that just brings the liquid to the lip depends on how much is left (a full one needs little, an almost empty one a lot); on top of that, turn it more while too little runs and a little less while too much.
                val wanted = max(0.0005f, source.width * source.height * 0.15f)
                extra = (extra + (if (source.spillRate < wanted) 0.7f else -0.25f) * dt).coerceIn(0f, 0.9f)
                tilt = (Tilt.angleToReachLip(source.width, source.height, source.volume) + extra).coerceIn(0f, 1.5f)
                source.body.hold(dir * tilt)
                // Keep the stream on the middle of the mouth, and the lip over the rim.
                source.landHeight = target.height * target.fill
                val lip = source.lipX() + Spill.reach(source.fallHeight, source.jetSpeed)
                x = moveToward(x, targetX - dir * (lip + source.width / 2f), dt, 0.5f)
                liftTo(max(target.height + 0.03f - source.lipHeight(), target.height + 0.015f), dt)
                // Turned as far as it goes with only a trickle left: that is as empty as it gets.
                val drained = tilt >= 1.45f && source.spillRate < wanted * 0.02f
                if (source.empty || drained || stopRequested || elapsed > 90f || reached()) phase = Phase.Return
            }
            Phase.Return -> {
                tilt = max(0f, tilt - 1.1f * dt)
                source.body.hold(dir * tilt)
                if (tilt < 0.4f) { x = moveToward(x, homeX, dt); liftTo(0f, dt); source.landHeight = 0f }
                if (tilt <= 0f && abs(source.body.angle) < 0.01f && abs(x - homeX) < 0.003f && source.lift < 0.002f) {
                    source.body.hold(0f); source.body.release(); source.body.angle = 0f; source.body.omega = 0f
                    source.lift = 0f; source.landHeight = 0f; x = homeX
                    phase = Phase.Done
                }
            }
            Phase.Done -> Unit
        }
    }

    /** Whether this level of pouring has been poured. */
    private fun reached(): Boolean = when (level) {
        PourLevel.TopUp -> target.fill >= 0.95f
        is PourLevel.Amount -> target.fill >= 0.95f || delivered >= level.share.coerceIn(0f, 1f) * target.width * target.height * target.width
        PourLevel.UntilStopped -> false
    }

    private fun liftTo(goal: Float, dt: Float) { source.lift += (goal - source.lift) * (1f - exp(-6f * dt)); if (goal == 0f && source.lift < 0.0015f) source.lift = 0f }

    private fun moveToward(from: Float, to: Float, dt: Float, speed: Float = 0.35f): Float {
        val d = (to - from) * (1f - exp(-6f * dt))
        val limit = speed * dt
        return from + min(max(d, -limit), limit)
    }
}
