package br.com.faru.boitata.core.surface

import br.com.faru.boitata.core.FastNoise
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * The air on both sides of a pane of glass, and the weather on the outside of it. Temperatures are in degrees Celsius.
 */
public class SurfaceClimate(
    public var outsideTemp: Float = 10f,
    public var insideTemp: Float = 21f,
    /** The relative humidity of the air inside, 0 to 1. */
    public var humidity: Float = 0.45f,
    /** How hard it rains, 0 to 1. */
    public var rain: Float = 0f,
    /** How hard it snows, 0 to 1. */
    public var snow: Float = 0f,
    /** The wind along the pane, signed (-1 to 1): it blows the drops sideways. */
    public var wind: Float = 0f,
    /** How much of the outside air gets in, 0 (window shut) to 1 (wide open): an open window dries the inside air at the pane. */
    public var draught: Float = 0f,
) {
    public companion object {
        /** The dew point, in degrees: the temperature a surface must fall below for water to condense on it out of air at [temp] with relative humidity [humidity] (Magnus's formula). */
        public fun dewPoint(temp: Float, humidity: Float): Float {
            val a = 17.62f; val b = 243.12f
            val gamma = ln(humidity.coerceIn(0.01f, 1f)) + a * temp / (b + temp)
            return b * gamma / (a - gamma)
        }
    }
}

/**
 * What the weather does to a pane of glass, as a small simulation (a grid of [cols] by [rows] cells, stepped at a fixed rate so the same weather and seed always give the same pane):
 *
 * - **Fog.** Water condenses on the glass where it is colder than the dew point of the air inside (`SurfaceClimate.dewPoint`); the glass is nearer the outside's temperature than the inside's. It evaporates when the glass
 *   is warmer. The colder the glass, the faster it fogs.
 * - **Frost.** Below 0 degrees the condensation freezes: ice grows in from the edges, where the glass is coldest, in a pattern fixed by the seed, and melts above freezing.
 * - **Drops.** Rain lands as beads that stick until they are heavy enough, then run down, merging and **leaving a clear streak through the fog**, as real drops do. Heavy fog runs by itself.
 * - **Snow** piles on the sill and melts.
 * - **Heat** (a lamp or fire near a patch of glass) evaporates fog and melts frost there; **wiping** clears the fog under a hand.
 */
public class PaneWeather(public val cols: Int = 40, public val rows: Int = 56, seed: Long = 5L) {
    public val fog: FloatArray = FloatArray(cols * rows)
    public val frost: FloatArray = FloatArray(cols * rows)

    /** Where each cell's ice appears: the frost shows in a cell once its level passes this, so the ice grows in a fixed pattern (low values first). */
    public val pattern: FloatArray = FloatArray(cols * rows)

    /** How much snow is on the sill, 0 to 1. */
    public var sill: Float = 0f
        private set

    // The beads and runs of water.
    private val maxDrops = 64
    public val dropX: FloatArray = FloatArray(maxDrops)
    public val dropY: FloatArray = FloatArray(maxDrops)
    public val dropMass: FloatArray = FloatArray(maxDrops)
    private val dropSpeed = FloatArray(maxDrops)
    public var drops: Int = 0
        private set

    private val noise = FastNoise(seed)
    private var carry = 0f
    private var tick = 0

    init {
        for (r in 0 until rows) for (c in 0 until cols) {
            val edge = min(min(c, cols - 1 - c) / (cols * 0.5f), min(r, rows - 1 - r) / (rows * 0.5f))   // 0 at the border, 1 in the middle
            val n = noise.at(c, r)
            // Low near the edges and along streaks (crystals grow along lines), noisy elsewhere.
            val streak = 0.5f + 0.5f * kotlin.math.sin((c * 0.9f + r * 0.35f) + noise.at(c / 3, r / 3) * 6f)
            pattern[r * cols + c] = (0.55f * edge + 0.25f * n + 0.2f * streak).coerceIn(0f, 1f)
        }
    }

    /** The average fog over the pane, 0 to 1. */
    public fun averageFog(): Float = fog.average().toFloat()

    /** The average ice, 0 to 1. */
    public fun averageFrost(): Float = frost.average().toFloat()

    /** Whether cell ([c], [r]) shows ice. */
    public fun iced(c: Int, r: Int): Boolean = frost[r * cols + c] > pattern[r * cols + c]

    /** Dry, clear glass again: no fog, no ice, no drops, no snow. */
    public fun clear() { fog.fill(0f); frost.fill(0f); drops = 0; sill = 0f }

    /** Clears the fog within [radius] (a share of the pane's width) of ([x], [y]), both 0..1 across the pane: a hand wiping the glass. */
    public fun wipe(x: Float, y: Float, radius: Float) {
        val rc = radius * cols; val cx = x * cols; val cy = y * rows
        for (r in max(0, (cy - rc).toInt())..min(rows - 1, (cy + rc).toInt())) for (c in max(0, (cx - rc).toInt())..min(cols - 1, (cx + rc).toInt())) {
            val dx = c - cx; val dy = r - cy
            if (dx * dx + dy * dy <= rc * rc) fog[r * cols + c] = 0f
        }
    }

    /**
     * Moves the pane on by [dt] seconds in [climate]. [heat] says how hot the air is against the glass at a place (x and y 0..1 across the pane): 0 for none, 1 for a flame close by; it evaporates fog and melts ice there.
     * It works in steps of 1/20 s whatever [dt] is.
     */
    public fun step(dt: Float, climate: SurfaceClimate, heat: ((Float, Float) -> Float)? = null) {
        carry += dt.coerceIn(0f, 0.5f)
        while (carry >= STEP) { carry -= STEP; advance(STEP, climate, heat) }
    }

    private fun advance(dt: Float, climate: SurfaceClimate, heat: ((Float, Float) -> Float)?) {
        tick++
        val glassTemp = climate.outsideTemp + (climate.insideTemp - climate.outsideTemp) * 0.3f
        // An open window mixes the outside air in, so the air at the pane is drier and nearer the outside's temperature.
        val air = climate.humidity * (1f - 0.7f * climate.draught)
        val dew = SurfaceClimate.dewPoint(climate.insideTemp, air)
        val condensing = if (glassTemp < dew) ((dew - glassTemp) * 0.035f).coerceAtMost(0.5f) else -0.04f
        val freezing = glassTemp < 0f
        val growth = if (freezing) 0.03f * min(1f, -glassTemp / 8f) * (0.3f + air) else 0f
        val thaw = if (glassTemp > 0f) 0.05f * (1f + glassTemp * 0.25f) else 0f
        for (r in 0 until rows) for (c in 0 until cols) {
            val i = r * cols + c
            val h = heat?.invoke((c + 0.5f) / cols, (r + 0.5f) / rows) ?: 0f
            // Fog forms on bare glass (less where there is ice, which is the water already) and evaporates with heat.
            val room = 1f - fogCoverBy(frost[i])
            fog[i] = (fog[i] + (condensing * (if (condensing > 0f) room else 1f) - h * 0.7f) * dt).coerceIn(0f, 1f)
            // Ice grows from the edges in, and melts with warmth and heat.
            val edgeBias = 1.3f - pattern[i]
            frost[i] = (frost[i] + (growth * edgeBias * (if (fog[i] > 0.05f || air > 0.3f) 1f else 0.4f) - thaw - h * 0.9f) * dt).coerceIn(0f, 1f)
        }
        // Rain lands as beads on the glass.
        if (climate.rain > 0.02f && !freezing) {
            val n = climate.rain * 14f * dt + (if (noise.at(tick, 77) < (climate.rain * 14f * dt) % 1f) 1f else 0f)
            for (k in 0 until n.toInt() + (if (noise.at(tick, 78) < n % 1f) 1 else 0)) spawn(noise.at(tick, 10 + k) * (cols - 1), noise.at(tick, 30 + k) * rows * 0.85f, 0.06f + 0.12f * noise.at(tick, 50 + k))
        }
        // Heavy fog gathers into beads that run.
        if (!freezing && tick % 6 == 0) for (c in 0 until cols step 3) if (fog[(rows / 3) * cols + c] > 0.92f && noise.at(tick, c) > 0.97f) spawn(c + 0.5f, rows / 3f, 0.34f)
        moveDrops(dt, climate)
        // Snow on the sill piles up in the cold and melts in the warm.
        sill = (sill + (climate.snow * 0.02f - (if (glassTemp > 0.5f) 0.015f * glassTemp * 0.2f else 0f)) * dt).coerceIn(0f, 1f)
    }

    private fun fogCoverBy(frostLevel: Float): Float = frostLevel * 0.6f

    private fun spawn(x: Float, y: Float, mass: Float) {
        // A bead landing on one already there joins it.
        for (d in 0 until drops) if (abs(dropX[d] - x) < 1.2f && abs(dropY[d] - y) < 1.5f) { dropMass[d] += mass; return }
        if (drops >= maxDrops) return
        dropX[drops] = x; dropY[drops] = y; dropMass[drops] = mass; dropSpeed[drops] = 0f; drops++
    }

    private fun moveDrops(dt: Float, climate: SurfaceClimate) {
        var d = 0
        while (d < drops) {
            val m = dropMass[d]
            // A bead sticks until it is heavy enough, then runs: the heavier the faster. It gets heavier by taking up the fog it passes through.
            if (m > STICK) {
                val target = 2.5f + 14f * (m - STICK)
                dropSpeed[d] += (target - dropSpeed[d]) * min(1f, 4f * dt)
                dropY[d] += dropSpeed[d] * dt
                dropX[d] = (dropX[d] + climate.wind * 1.2f * dt).coerceIn(0f, cols - 1f)
                val r = dropY[d].toInt(); val c = dropX[d].toInt()
                if (r in 0 until rows) {
                    val i = r * cols + c
                    // The run clears a streak of fog (and a little to each side) and the bead loses a little of itself laying it down.
                    for (cc in max(0, c - 1)..min(cols - 1, c + 1)) fog[r * cols + cc] = 0f
                    dropMass[d] = max(STICK * 0.9f, dropMass[d] - 0.004f + fog[i] * 0.0f)
                    if (dropMass[d] <= STICK * 0.95f) dropSpeed[d] = 0f
                }
            }
            if (dropY[d] >= rows) { drops--; dropX[d] = dropX[drops]; dropY[d] = dropY[drops]; dropMass[d] = dropMass[drops]; dropSpeed[d] = dropSpeed[drops]; continue }
            d++
        }
    }

    private companion object {
        const val STEP = 1f / 20f
        const val STICK = 0.3f
    }
}
