package br.com.faru.boitata.core.burn

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.faru.boitata.core.wind.Wind
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Where a thing stands on the ground, in the scene's pixels: its feet, how wide and how tall it is drawn. Read live, so a thing dragged about carries its fire with it. */
public class BodyGeometry(public val feetX: Float, public val feetY: Float, public val width: Float, public val height: Float)

public enum class BurnStatus {
    /** Cool. */
    Cold,

    /** Warming up: heat is building but it has not caught. */
    Heating,

    /** On fire. */
    Burning,

    /** Burnt out: ash. */
    Spent,
}

/**
 * One thing in a [FireWorld]: what it is made of ([fuel]), where it stands ([geometry]) and how fire has treated it. The numbers are state, so a drawing that reads them in a draw block
 * redraws as the fire changes and nothing recomposes.
 */
public class FireBody(
    public val id: String,
    public val fuel: Fuel,
    public val geometry: () -> BodyGeometry,
) {
    /** How much heat it has taken: it catches when this reaches the fuel's ignition heat (raised by wetness). */
    public var heat: Float by mutableFloatStateOf(0f)
        internal set

    /** How fully it is alight, 0 to 1: it ramps up over a few seconds after catching and down when it burns out or is put out. */
    public var burn: Float by mutableFloatStateOf(0f)
        internal set

    /** How much of it is gone, 0 to 1: the more, the more charred. */
    public var consumed: Float by mutableFloatStateOf(0f)
        internal set

    /** How wet, 0 to 1: wet things are slow to catch and a wet fire dies. */
    public var wetness: Float by mutableFloatStateOf(0f)
        internal set

    /** Whose fire it is burning with (meaningful while it burns). */
    public var kind: FireKind by mutableStateOf(FireKind.Ordinary)
        internal set

    /** How much it still smoulders after burning out, 1 just after and falling to 0 over about twelve seconds: for smoke. */
    public var smoulder: Float by mutableFloatStateOf(0f)
        internal set

    internal var alight: Boolean = false
    internal var spent: Boolean = false
    internal var sparkSecond: Int = Int.MIN_VALUE

    public val status: BurnStatus
        get() = when {
            spent -> BurnStatus.Spent
            alight -> BurnStatus.Burning
            heat > 0.04f -> BurnStatus.Heating
            else -> BurnStatus.Cold
        }

    /** How big its flames are now, 0..1: how alight it is, times what it is made of. */
    public val flame: Float get() = if (alight || burn > 0f) burn * fuel.intensity else 0f

    /** How near it is to catching, 0 (cool) to 1 (about to). */
    public val nearIgnition: Float
        get() = if (!fuel.flammable) 0f else (heat / (fuel.ignitionHeat * (1f + 2f * wetness))).coerceIn(0f, 1f)
}

/**
 * Fire spreading among things. It is a small simulation (fire depends on what has already burnt, so it keeps state) that steps at a fixed rate, so the same wind, rain and seed
 * give the same fires every time and a test can pin them.
 *
 * - A burning thing gives off heat that falls off with distance to whatever stands near it (on the ground, so things farther up the picture are farther away).
 * - **The wind carries it**: downwind its reach grows with the wind's strength, upwind it shrinks, and in a strong wind sparks fly off and can light things farther away.
 * - Heat builds in a thing slowly, and cools again when the flame goes away; it catches when it reaches the fuel's ignition heat. So fire creeps to a thing instead of jumping.
 * - A burning thing burns for its fuel's time, chars as it does, and ends as ash that cannot burn again. Fire it lights spreads in turn.
 * - **Rain** wets things: they are harder to light, and a wet fire dies.
 * - **The Boitatá's fire** ([FireKind.Guardian]) never lights a living thing and puts out ordinary fires near it.
 */
public class FireWorld(private val seed: Long = 7L) {
    private val all = ArrayList<FireBody>()
    public val bodies: List<FireBody> get() = all

    /** How much of all the fire there is, for sound and light: the sum of the flames, 0 and up. */
    public var totalFlame: Float by mutableFloatStateOf(0f)
        private set

    private var carry = 0f

    public fun add(body: FireBody): FireBody = body.also { all += it }

    public fun body(id: String): FireBody? = all.firstOrNull { it.id == id }

    /** Sets [id] alight now, with [kind] fire. */
    public fun ignite(id: String, kind: FireKind = FireKind.Ordinary) {
        val b = body(id) ?: return
        if (b.spent || !b.fuel.flammable) return
        b.alight = true; b.kind = kind
        b.heat = max(b.heat, b.fuel.ignitionHeat)
    }

    /** Puts [id] out (it keeps what it has burnt). */
    public fun extinguish(id: String) { body(id)?.let { it.alight = false; it.burn = 0f } }

    /** Back to the start: everything cool, whole and dry, nothing alight. */
    public fun reset() {
        for (b in all) { b.heat = 0f; b.burn = 0f; b.consumed = 0f; b.wetness = 0f; b.alight = false; b.spent = false; b.sparkSecond = Int.MIN_VALUE; b.kind = FireKind.Ordinary; b.smoulder = 0f }
        totalFlame = 0f; carry = 0f
    }

    /**
     * Moves the fires on by [dt] seconds. [seconds] is the scene's time (the wind is read at it), [wind] the wind and [rain] 0..1 how hard it rains.
     * It works in steps of at most 1/20 s however long [dt] is, so a long frame does not change the outcome.
     */
    public fun step(dt: Float, seconds: Float, wind: Wind, rain: Float = 0f) {
        carry += dt.coerceIn(0f, 0.5f)
        var t = seconds - carry
        while (carry >= STEP) {
            carry -= STEP; t += STEP
            advance(STEP, t, wind, rain.coerceIn(0f, 1f))
        }
    }

    private fun advance(dt: Float, t: Float, wind: Wind, rain: Float) {
        val geo = all.map { it.geometry() }
        val heated = BooleanArray(all.size)

        // Weather first: rain wets everything, a dry spell dries it.
        for (b in all) b.wetness = (b.wetness + (rain * WET_RATE - (1f - rain) * DRY_RATE) * dt).coerceIn(0f, 1f)

        // Each flame heats what is near it, and the Boitatá's puts out ordinary fires.
        for ((i, source) in all.withIndex()) {
            if (!source.alight || source.burn <= 0.02f) continue
            val flame = source.flame
            val g = geo[i]
            val sample = wind.at(t, (g.feetX / WORLD_WIDTH).coerceIn(0f, 1f))
            val dir = if (sample.x >= 0f) 1f else -1f
            val strength = min(sample.strength, 1.4f)
            val reachBase = g.height * BASE_REACH
            for ((j, target) in all.withIndex()) {
                if (j == i) continue
                val tg = geo[j]
                val dx = tg.feetX - g.feetX
                val dy = (tg.feetY - g.feetY) * GROUND_SQUASH
                val d = hypot(dx, dy)
                // Along the wind (+1 straight downwind, -1 straight upwind) decides whether the wind carries the heat to it or holds it back.
                val along = if (d < 1e-3f) 0f else (dx * dir) / d
                val reach = reachBase * (if (along > 0f) 1f + WIND_REACH * strength * along else 1f - 0.6f * min(1f, strength * -along))
                if (d >= reach) continue
                val closeness = 1f - d / reach
                if (source.kind == FireKind.Guardian && target.alight && target.kind == FireKind.Ordinary) {
                    // The guardian's fire consumes the fires of people.
                    target.burn = max(0f, target.burn - GUARD_DAMP * flame * closeness * dt * 4f)
                    if (target.burn <= 0f) target.alight = false
                    continue
                }
                if (!target.fuel.flammable || target.spent || target.alight) continue
                if (source.kind == FireKind.Guardian && target.fuel.living) continue
                target.heat += flame * closeness * closeness * HEAT_RATE * (1f - 0.9f * target.wetness) * dt
                heated[j] = true
            }
            // Sparks: in a strong wind fire throws embers downwind, and one that lands on something flammable heats it hard.
            val second = floor(t).toInt()
            if (source.sparkSecond != second) {
                source.sparkSecond = second
                if (strength > 0.25f) {
                    val r = Random(seed xor (source.id.hashCode().toLong() * 31L) xor second.toLong())
                    if (r.nextFloat() < flame * strength * SPARK_CHANCE) {
                        val fly = g.height * (0.8f + SPARK_RANGE * strength * (0.5f + r.nextFloat()))
                        val landX = g.feetX + dir * fly
                        val landY = g.feetY + (r.nextFloat() - 0.5f) * g.height * 0.5f
                        for ((j, target) in all.withIndex()) {
                            if (j == i || !target.fuel.flammable || target.spent || target.alight) continue
                            if (source.kind == FireKind.Guardian && target.fuel.living) continue
                            val tg = geo[j]
                            if (hypot(tg.feetX - landX, (tg.feetY - landY) * GROUND_SQUASH) < max(tg.width, tg.height * 0.5f) * 0.8f) {
                                target.heat += SPARK_HEAT * (1f - 0.9f * target.wetness); heated[j] = true
                            }
                        }
                    }
                }
            }
        }

        // Each thing: cool if nothing heats it, catch if hot enough, burn.
        var total = 0f
        for ((i, b) in all.withIndex()) {
            if (!heated[i] && !b.alight) b.heat = max(0f, b.heat - COOL_RATE * dt)
            if (!b.alight && !b.spent && b.fuel.flammable && b.heat >= b.fuel.ignitionHeat * (1f + 2f * b.wetness)) {
                b.alight = true
                // It burns with the kind of fire that lit it: the nearest burning thing's.
                b.kind = all.indices.filter { it != i && all[it].alight }.minByOrNull { hypot(geo[it].feetX - geo[i].feetX, geo[it].feetY - geo[i].feetY) }?.let { all[it].kind } ?: FireKind.Ordinary
            }
            if (b.alight) {
                // A wet fire dies; a dry one ramps up and eats its fuel.
                b.burn = (b.burn + (RAMP_UP * (1f - b.wetness) - b.wetness * WET_DAMP) * dt).coerceIn(0f, 1f)
                if (b.fuel.burnSeconds.isFinite()) b.consumed = min(1f, b.consumed + dt / b.fuel.burnSeconds * (0.4f + 0.6f * b.burn))
                if (b.burn <= 0f && b.wetness > 0.5f) b.alight = false
                if (b.consumed >= 1f) { b.alight = false; b.spent = true; b.heat = 0f; b.smoulder = 1f }
            } else {
                b.burn = max(0f, b.burn - RAMP_DOWN * dt)
                if (b.spent) b.smoulder = max(0f, b.smoulder - dt / 12f)
            }
            total += b.flame
        }
        totalFlame = total
    }

    private companion object {
        const val STEP = 1f / 20f
        const val BASE_REACH = 1.3f
        const val WIND_REACH = 2.4f
        const val GROUND_SQUASH = 2.0f
        const val HEAT_RATE = 1.15f
        const val COOL_RATE = 0.12f
        const val RAMP_UP = 0.35f
        const val RAMP_DOWN = 0.5f
        const val WET_RATE = 0.28f
        const val DRY_RATE = 0.04f
        const val WET_DAMP = 0.9f
        const val GUARD_DAMP = 0.35f
        const val SPARK_CHANCE = 0.55f
        const val SPARK_RANGE = 5.5f
        const val SPARK_HEAT = 0.9f
        /** The width the wind's travelling gusts are spread over (a scene is about this many pixels across). */
        const val WORLD_WIDTH = 800f
    }
}
