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

/** Where a thing is in its life with fire. */
public enum class BurnStatus {
    /** Cool. */
    Cold,

    /** Warming up: heat is building but it has not caught (or, for metal and wax, it is heating and glowing or softening). */
    Heating,

    /** On fire. */
    Burning,

    /** Done: burnt out to ash or char, or melted into a puddle. It will not burn or melt again. */
    Spent,
}

/**
 * One thing in a [FireWorld]: what it is made of ([material]), where it stands ([geometry]) and how fire has treated it. The numbers are state, so a drawing that reads them in a draw block
 * redraws as the fire changes and nothing recomposes.
 *
 * A thing made of several materials is several bodies that touch ([FireWorld.addComposite]): a candle is a wick that burns and wax that melts. A body can be [fedBy] another: it burns as long
 * as that one has anything left to give it (a wick burns while the wax lasts) and it wears down as that one does.
 */
public class FireBody(
    public val id: String,
    public val material: Material,
    /** The body whose substance keeps this one burning, if any. */
    public val fedBy: FireBody? = null,
    public val geometry: () -> BodyGeometry,
) {
    /** How much heat it has taken. It catches when this reaches the material's ignition heat (raised by wetness), melts above its melting heat and glows above its glow heat. */
    public var heat: Float by mutableFloatStateOf(0f)
        internal set

    /** How fully it is alight, 0 to 1: it ramps up over a few seconds after catching and down when it burns out or is put out. */
    public var burn: Float by mutableFloatStateOf(0f)
        internal set

    /** How much of it has burnt away, 0 to 1. */
    public var consumed: Float by mutableFloatStateOf(0f)
        internal set

    /** How much of it has melted, 0 (whole) to 1 (a puddle). Melting is permanent: it does not undo as the heat goes. */
    public var melt: Float by mutableFloatStateOf(0f)
        internal set

    /** How wet, 0 to 1: wet things are slow to catch and a wet fire dies. */
    public var wetness: Float by mutableFloatStateOf(0f)
        internal set

    /** Whose fire it is burning with (meaningful while it burns). */
    public var kind: FireKind by mutableStateOf(FireKind.Ordinary)
        internal set

    /** How much it still smoulders after it is spent, 1 just after and falling to 0 over about twelve seconds: for smoke. */
    public var smoulder: Float by mutableFloatStateOf(0f)
        internal set

    internal var alight: Boolean = false
    internal var spent: Boolean = false
    internal var sparkSecond: Int = Int.MIN_VALUE
    internal val touching: MutableList<FireBody> = ArrayList()

    public val status: BurnStatus
        get() = when {
            spent -> BurnStatus.Spent
            alight -> BurnStatus.Burning
            heat > 0.04f || melt > 0f -> BurnStatus.Heating
            else -> BurnStatus.Cold
        }

    /** How big its flames are now, 0..1: how alight it is, times what it is made of. */
    public val flame: Float get() = if (alight || burn > 0f) burn * material.intensity else 0f

    /** How near it is to catching, 0 (cool) to 1 (about to). */
    public val nearIgnition: Float
        get() = if (!material.flammable) 0f else (heat / (material.ignitionHeat * (1f + 2f * wetness))).coerceIn(0f, 1f)

    /** How much it glows from heat, 0..1, for a material that glows: it follows the heat, so it fades as the thing cools. */
    public val glow: Float
        get() = if (material.glowHeat == Float.POSITIVE_INFINITY) 0f else ((heat - material.glowHeat) / GLOW_SPAN).coerceIn(0f, 1f)

    /** How much of what it is made of is left to burn or to give: the wax's melting counts as using it up. */
    public val remaining: Float get() = if (material.style == BurnStyle.Melt) 1f - melt else 1f - consumed

    private companion object {
        const val GLOW_SPAN = 1.4f
    }
}

/** A rectangle inside a body, as fractions of its box: (0, 0) is its top left, (1, 1) its bottom right. */
public class PartRegion(public val left: Float, public val top: Float, public val right: Float, public val bottom: Float) {
    public companion object {
        public val Whole: PartRegion = PartRegion(0f, 0f, 1f, 1f)
    }
}

/**
 * One material of a thing made of several, as configuration: what it is, which part of the picture it occupies, and what feeds it.
 * A candle is `PartSpec("wax", Material.Wax, PartRegion(0f, 0.1f, 1f, 1f))` and `PartSpec("wick", Material.Wick, PartRegion(0.45f, 0f, 0.55f, 0.12f), fedBy = "wax")`.
 */
public class PartSpec(
    public val id: String,
    public val material: Material,
    public val region: PartRegion = PartRegion.Whole,
    /** The id of the part whose substance keeps this one burning. */
    public val fedBy: String? = null,
)

/** How much a melting thing sags (its height is lost) and spreads (its width is gained), at fully melted. The drawing uses the same numbers, so a part that sits on top follows it down. */
public object Melting {
    public const val SAG: Float = 0.8f
    public const val SPREAD: Float = 0.45f
}

/**
 * Fire spreading among things. It is a small simulation (fire depends on what has already happened, so it keeps state) that steps at a fixed rate, so the same wind, rain and seed
 * give the same fires every time and a test can pin them.
 *
 * - A burning thing gives off heat that falls off with distance to whatever stands near it (on the ground, so things farther up the picture are farther away) and heats the parts it touches.
 * - **The wind carries it**: downwind its reach grows with the wind's strength, upwind it shrinks, and in a strong wind sparks fly off and can light things farther away.
 * - Heat builds in a thing slowly and drains away when the flame goes; what it does at each level is up to its [Material]: it catches, melts, glows, or nothing.
 * - A burning thing burns for its time, chars as it does, and ends spent. Fire it lights spreads in turn. A melted thing is a puddle.
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

    /** Makes [a] and [b] touch: a flame on one heats the other directly, whatever the distance between their feet. */
    public fun join(a: FireBody, b: FireBody) { if (b !in a.touching) a.touching += b; if (a !in b.touching) b.touching += a }

    /**
     * Adds a thing made of several materials, laid out by [parts] (configuration, not code). The parts share [geometry] (the whole thing's box), each takes its region of it, and they all touch.
     * As the part that melts sags, the others sit on it and follow it down. Returns the bodies, first part first; the first part's id is also [id].
     */
    public fun addComposite(id: String, geometry: () -> BodyGeometry, parts: List<PartSpec>): List<FireBody> {
        val made = ArrayList<FireBody>()
        val byPart = HashMap<String, FireBody>()          // part id -> its body (the first part's body is called [id] itself, the others "$id.part")
        val melting = parts.firstOrNull { it.material.style == BurnStyle.Melt }?.id
        for (spec in parts) {
            val fed = spec.fedBy?.let { byPart[it] }
            val bodyId = if (spec === parts.first()) id else "$id.${spec.id}"
            val body = FireBody(bodyId, spec.material, fed) {
                val g = geometry()
                // The shape sags as its melting part melts: the box is lower and wider, and every part's region is measured on that.
                val m = melting?.let { byPart[it]?.melt } ?: 0f
                val h = g.height * (1f - Melting.SAG * m); val w = g.width * (1f + Melting.SPREAD * m)
                val r = spec.region
                BodyGeometry(g.feetX + (((r.left + r.right) / 2f) - 0.5f) * w, g.feetY - (1f - r.bottom) * h, (r.right - r.left) * w, (r.bottom - r.top) * h)
            }
            made += add(body)
            byPart[spec.id] = body
        }
        for (i in made.indices) for (j in i + 1 until made.size) join(made[i], made[j])
        return made
    }

    /** Sets [id] alight now, with [kind] fire. */
    public fun ignite(id: String, kind: FireKind = FireKind.Ordinary) {
        val b = body(id) ?: return
        if (b.spent || !b.material.flammable) return
        if (b.fedBy != null && b.fedBy.remaining <= 0.05f) return
        b.alight = true; b.kind = kind
        b.heat = max(b.heat, b.material.ignitionHeat)
    }

    /** Puts [id] out (it keeps what it has burnt). */
    public fun extinguish(id: String) { body(id)?.let { it.alight = false; it.burn = 0f } }

    /** Back to the start: everything cool, whole and dry, nothing alight. */
    public fun reset() {
        for (b in all) {
            b.heat = 0f; b.burn = 0f; b.consumed = 0f; b.melt = 0f; b.wetness = 0f; b.smoulder = 0f
            b.alight = false; b.spent = false; b.sparkSecond = Int.MIN_VALUE; b.kind = FireKind.Ordinary
        }
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

    /** Whether heat does anything to [b]: it can catch, melt or glow, and is not already done. */
    private fun reactive(b: FireBody): Boolean = !b.spent && (b.material.flammable || b.material.meltable || b.material.glowHeat < Float.POSITIVE_INFINITY)

    private fun advance(dt: Float, t: Float, wind: Wind, rain: Float) {
        val geo = all.map { it.geometry() }
        val gain = FloatArray(all.size)

        // Weather first: rain wets everything, a dry spell dries it.
        for (b in all) b.wetness = (b.wetness + (rain * WET_RATE - (1f - rain) * DRY_RATE) * dt).coerceIn(0f, 1f)

        for ((i, source) in all.withIndex()) {
            if (!source.alight || source.burn <= 0.02f) continue
            val flame = source.flame
            val g = geo[i]

            // What it touches takes the heat directly: the wax under a wick.
            for (target in source.touching) {
                val j = all.indexOf(target)
                if (!reactive(target) || target.alight) continue
                if (source.kind == FireKind.Guardian && target.material.living) continue
                gain[j] += flame * TOUCH * HEAT_RATE * target.material.heatGain * (1f - 0.9f * target.wetness) * dt
            }

            val sample = wind.at(t, (g.feetX / WORLD_WIDTH).coerceIn(0f, 1f))
            val dir = if (sample.x >= 0f) 1f else -1f
            val strength = min(sample.strength, 1.4f)
            val reachBase = g.height * BASE_REACH
            for ((j, target) in all.withIndex()) {
                if (j == i || target in source.touching) continue
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
                if (!reactive(target) || target.alight) continue
                if (source.kind == FireKind.Guardian && target.material.living) continue
                gain[j] += flame * closeness * closeness * HEAT_RATE * target.material.heatGain * (1f - 0.9f * target.wetness) * dt
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
                            if (j == i || !target.material.flammable || target.spent || target.alight) continue
                            if (source.kind == FireKind.Guardian && target.material.living) continue
                            val tg = geo[j]
                            if (hypot(tg.feetX - landX, (tg.feetY - landY) * GROUND_SQUASH) < max(tg.width, tg.height * 0.5f) * 0.8f) gain[j] += SPARK_HEAT * (1f - 0.9f * target.wetness)
                        }
                    }
                }
            }
        }

        // Each thing: take the heat it was given, lose some, then do what its material does at that heat.
        var total = 0f
        for ((i, b) in all.withIndex()) {
            val m = b.material
            b.heat = (b.heat + gain[i] - (if (b.alight) 0f else m.coolRate) * dt).coerceIn(0f, MAX_HEAT)

            if (!b.alight && !b.spent && m.flammable && b.heat >= m.ignitionHeat * (1f + 2f * b.wetness) && (b.fedBy == null || b.fedBy.remaining > 0.05f)) {
                b.alight = true
                // It burns with the kind of fire that lit it: the nearest burning thing's.
                b.kind = all.indices.filter { it != i && all[it].alight }.minByOrNull { hypot(geo[it].feetX - geo[i].feetX, geo[it].feetY - geo[i].feetY) }?.let { all[it].kind } ?: FireKind.Ordinary
            }
            if (m.meltable && b.melt < 1f && !b.spent) {
                b.melt = min(1f, b.melt + max(0f, b.heat - m.meltHeat) * m.meltRate * dt)
                // A thing that melts and cannot burn is done when it is a puddle; one that can burn goes on to burn.
                if (b.melt >= 0.98f && !m.flammable) { b.melt = 1f; b.spent = true; b.smoulder = 0f }
            }
            if (b.alight) {
                // A wet fire dies; a dry one ramps up and eats its substance.
                b.burn = (b.burn + (RAMP_UP * (1f - b.wetness) - b.wetness * WET_DAMP) * dt).coerceIn(0f, 1f)
                val feeder = b.fedBy
                if (feeder != null) {
                    // Burns as long as what feeds it lasts, and wears down as that does.
                    b.consumed = max(b.consumed, 1f - feeder.remaining)
                    if (feeder.remaining <= 0.02f) { b.alight = false; b.spent = true; b.heat = 0f; b.smoulder = 1f }
                } else if (m.burnSeconds.isFinite()) {
                    b.consumed = min(1f, b.consumed + dt / m.burnSeconds * (0.4f + 0.6f * b.burn))
                }
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
        const val TOUCH = 0.7f
        const val MAX_HEAT = 3.5f
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
