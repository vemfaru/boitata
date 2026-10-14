package br.com.faru.boitata.light

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import br.com.faru.boitata.core.FastNoise
import br.com.faru.boitata.core.light.ShadowCast
import br.com.faru.boitata.core.light.ShadowMath
import br.com.faru.boitata.core.light.WallCast
import br.com.faru.boitata.core.sky.MoonPhase
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A source of light in a scene. Each kind lights and casts shadows in its own way, and they come with presets ([Lights]) so a fire, a lamp and the sun look different without tuning:
 * the fire is warm, low and unsteady with a soft shadow; a lamp is steady with a sharper one; the sun is far away so its shadows are parallel and long at dawn.
 *
 * A light is plain, mutable data (move it by setting [x] and [y]; the scene redraws every frame anyway), and everything it does is a pure function of where a thing stands and the time.
 * Put them in a [LightRig] to light and shadow things with several at once.
 */
public sealed class Light(
    /** The colour it casts. */
    public var color: Color,
    /** How hard it lights, 0 off to 1 full. */
    public var strength: Float,
    /** How big the source is, 0 a point and 1 huge: bigger gives a softer shadow edge. */
    public var size: Float,
    /** How much it flickers, 0 steady to 1 a candle in a draught. */
    public var flicker: Float,
) {
    public var enabled: Boolean = true

    /** How much this light lights a thing standing at ([feetX], [feetY]) at [seconds], 0 to 1: its strength, its flicker, and how far and how off-axis the thing is. */
    public abstract fun contribution(feetX: Float, feetY: Float, seconds: Float): Float

    /** Writes how this light casts the shadow of a thing at ([feetX], [feetY]) that is [objectHeight] tall into [into]. */
    public abstract fun cast(feetX: Float, feetY: Float, objectHeight: Float, into: ShadowCast)

    /** Writes how this light's shadow of a thing at ([feetX], [feetY]) lands on a wall standing along the screen line [wallFootY]; false if it does not reach that wall. */
    public abstract fun castOnWall(feetX: Float, feetY: Float, wallFootY: Float, into: WallCast): Boolean

    /** The flicker as a multiplier near 1, smooth, changing about twelve times a second: a pure function of time. */
    public fun flickerAt(seconds: Float): Float {
        if (flicker <= 0f) return 1f
        val u = seconds * 12f
        val i = floor(u).toInt()
        val a = NOISE.at(i, 0); val b = NOISE.at(i + 1, 0)
        val f = u - i
        val n = a + (b - a) * f * f * (3f - 2f * f)
        return 1f + (n * 2f - 1f) * (0.04f + 0.2f * flicker)
    }

    internal companion object {
        val NOISE = FastNoise(7L)
    }
}

/** A light at one place: a fire, a lamp, a candle. It lights things near it, less the farther, and its shadows radiate from it. */
public class PointLight(
    /** Where it is on the ground (the foot of it), in pixels. */
    public var x: Float,
    public var y: Float,
    /** How high above the ground it is, in pixels: low makes long shadows. */
    public var height: Float,
    color: Color,
    strength: Float,
    /** How far it reaches along the ground, in pixels. */
    public var reach: Float,
    size: Float = 0.2f,
    flicker: Float = 0f,
) : Light(color, strength, size, flicker) {
    override fun contribution(feetX: Float, feetY: Float, seconds: Float): Float {
        if (!enabled || strength <= 0f) return 0f
        val d = hypot(feetX - x, (feetY - y) / ShadowMath.GROUND_SQUASH)
        return strength * flickerAt(seconds) * falloff(d, reach)
    }

    override fun cast(feetX: Float, feetY: Float, objectHeight: Float, into: ShadowCast) = ShadowMath.point(x, y, height, feetX, feetY, objectHeight, size, into)

    override fun castOnWall(feetX: Float, feetY: Float, wallFootY: Float, into: WallCast): Boolean = ShadowMath.onWallFromPoint(x, y, height, feetX, feetY, wallFootY, size, into)
}

/** A light that shines in a cone, like a lantern held up or a torch: lights and shadows only inside the cone, soft at its edge. */
public class SpotLight(
    public var x: Float,
    public var y: Float,
    public var height: Float,
    color: Color,
    strength: Float,
    public var reach: Float,
    /** The way it points along the ground (radians; 0 to the right, a quarter turn toward the viewer). */
    public var aim: Float,
    /** Half the width of the beam (radians). */
    public var cone: Float = 0.6f,
    /** How soft the edge of the beam is, as a share of the cone. */
    public var penumbra: Float = 0.4f,
    size: Float = 0.15f,
    flicker: Float = 0f,
) : Light(color, strength, size, flicker) {
    override fun contribution(feetX: Float, feetY: Float, seconds: Float): Float {
        if (!enabled || strength <= 0f) return 0f
        val gx = feetX - x; val gy = (feetY - y) / ShadowMath.GROUND_SQUASH
        val d = hypot(gx, gy)
        // Straight under the lamp is always lit; off to the side only inside the beam.
        val off = if (d < 1f) 0f else abs(angleBetween(atan2(gy, gx), aim))
        val inner = cone * (1f - penumbra)
        val beam = if (off <= inner) 1f else if (off >= cone) 0f else 1f - smooth((off - inner) / (cone - inner))
        return strength * flickerAt(seconds) * falloff(d, reach) * beam
    }

    override fun cast(feetX: Float, feetY: Float, objectHeight: Float, into: ShadowCast) = ShadowMath.point(x, y, height, feetX, feetY, objectHeight, size, into)

    override fun castOnWall(feetX: Float, feetY: Float, wallFootY: Float, into: WallCast): Boolean = ShadowMath.onWallFromPoint(x, y, height, feetX, feetY, wallFootY, size, into)
}

/**
 * A light so far away that its rays are parallel and it lights everything the same: the sun and the moon. It is defined by where it is in the sky: [azimuth] is the way the shadows fall
 * along the ground (radians; 0 to the right, a quarter turn toward the viewer) and [elevation] how high it is (radians; low gives long shadows).
 */
public class DirectionalLight(
    public var azimuth: Float,
    public var elevation: Float,
    color: Color,
    strength: Float,
    size: Float = 0.35f,
) : Light(color, strength, size, 0f) {
    /** How far along its path across the sky it is: 0 just rising on the left, 0.5 highest, 1 setting on the right; -1 while it is below the horizon. Set by [followSun] and [followMoon], for drawing its disc. */
    public var arc: Float = -1f
        private set

    override fun contribution(feetX: Float, feetY: Float, seconds: Float): Float = if (enabled) strength.coerceIn(0f, 1f) else 0f

    override fun cast(feetX: Float, feetY: Float, objectHeight: Float, into: ShadowCast) = ShadowMath.directional(azimuth, elevation, size, into)

    override fun castOnWall(feetX: Float, feetY: Float, wallFootY: Float, into: WallCast): Boolean = ShadowMath.onWallFromDirection(azimuth, elevation, feetX, feetY, wallFootY, size, into)

    /** Puts this light where the sun is at [hour] (0 to 24): it rises on the left, crosses high at noon and sets on the right, orange at the edges and pale at noon, off at night. */
    public fun followSun(hour: Float) {
        val t = (hour - 6f) / 12f
        if (t <= 0f || t >= 1f) { strength = 0f; arc = -1f; return }
        arc = t
        val angle = PI.toFloat() * t
        val high = sin(angle)
        arc(angle)
        elevation = max(ShadowMath.MIN_ELEVATION, high * 1.15f)
        color = lerp(SUNRISE, NOON, sqrt(high))
        strength = (smooth(high * 3f)).coerceIn(0f, 1f) * (0.55f + 0.45f * high)
    }

    /**
     * Puts this light where the moon is at [hour] for [phase]: a full moon is up all night, a new moon is up by day and gives no light, a first quarter sets around midnight.
     * Its light is pale blue and weaker the less of it is lit.
     */
    public fun followMoon(hour: Float, phase: MoonPhase) {
        val age = if (phase.waxing) phase.fraction / 2f else 1f - phase.fraction / 2f          // 0 new, 0.5 full, back to 1
        val transit = 12f + 24f * age                                                         // the hour it stands highest
        var ha = (hour - transit) % 24f
        if (ha > 12f) ha -= 24f else if (ha < -12f) ha += 24f
        if (abs(ha) >= 6f) { strength = 0f; arc = -1f; return }
        arc = (ha + 6f) / 12f
        val angle = PI.toFloat() * (ha + 6f) / 12f
        val high = sin(angle)
        arc(angle)
        elevation = max(ShadowMath.MIN_ELEVATION, high * 1.1f)
        color = MOONLIGHT
        strength = smooth(high * 3f) * 0.4f * sqrt(phase.fraction)
    }

    /** The shadows run along the way across the sky, a little toward the viewer (the sun is a little behind the scene). */
    private fun arc(angle: Float) { azimuth = atan2(TOWARD_VIEWER, cos(angle)) }

    private companion object {
        val SUNRISE = Color(0xFFFFA24D); val NOON = Color(0xFFFFF3DC); val MOONLIGHT = Color(0xFFB8CCFF)
        const val TOWARD_VIEWER = 0.45f
    }
}

/** Presets for common lights; each is a starting point, change what you like on the result. [scale] is roughly how many pixels tall a person is in your scene. */
public object Lights {
    /** A campfire or torch: warm, low, big soft reach, unsteady. */
    public fun fire(x: Float, y: Float, scale: Float = 100f): PointLight =
        PointLight(x, y, scale * 0.35f, Color(0xFFFFA63C), 0.95f, scale * 6f, size = 0.4f, flicker = 0.55f)

    /** A candle: tiny, warm, close, a hard small shadow. */
    public fun candle(x: Float, y: Float, scale: Float = 100f): PointLight =
        PointLight(x, y, scale * 0.3f, Color(0xFFFFC46B), 0.6f, scale * 2.2f, size = 0.05f, flicker = 0.8f)

    /** A hanging lamp or kerosene lamp: steady warm light, higher than a fire, a fairly crisp shadow. */
    public fun lamp(x: Float, y: Float, scale: Float = 100f): PointLight =
        PointLight(x, y, scale * 1.2f, Color(0xFFFFD08A), 0.9f, scale * 4.5f, size = 0.15f, flicker = 0.08f)

    /** A hurricane lamp carried by hand: like a lamp but at the height of a hand. */
    public fun hurricaneLamp(x: Float, y: Float, scale: Float = 100f): PointLight =
        PointLight(x, y, scale * 0.6f, Color(0xFFFFC978), 0.85f, scale * 3.6f, size = 0.12f, flicker = 0.25f)

    /** A lamp aimed along the ground like a lantern held up or a torch: lights only inside its cone. */
    public fun lantern(x: Float, y: Float, aim: Float, scale: Float = 100f): SpotLight =
        SpotLight(x, y, scale * 0.9f, Color(0xFFFFE0A8), 0.95f, scale * 6f, aim, cone = 0.55f, size = 0.1f, flicker = 0.05f)

    /** The sun at [hour]. */
    public fun sun(hour: Float = 12f): DirectionalLight = DirectionalLight(0f, 1f, Color.White, 0f, size = 0.3f).also { it.followSun(hour) }

    /** The moon at [hour] in [phase]: pale, a fairly soft shadow. */
    public fun moon(hour: Float, phase: MoonPhase = MoonPhase.Full): DirectionalLight = DirectionalLight(0f, 1f, Color.White, 0f, size = 0.45f).also { it.followMoon(hour, phase) }

    /** A flash of lightning at one spot overhead: white-blue, high and hard, and gone in an instant (set [Light.strength] to the flash). */
    public fun lightning(x: Float, y: Float, scale: Float = 100f): PointLight =
        PointLight(x, y, scale * 6f, Color(0xFFD7E3FF), 0f, scale * 30f, size = 0.05f)

    /** A light as old code described it: a [LightSource] is a point light. */
    public fun from(source: LightSource): PointLight =
        PointLight(source.groundX, source.groundY, max(source.groundY - source.y, source.reach * 0.25f), source.color, source.strength, source.reach, flicker = source.flicker)
}

/** 1 at the light, falling smoothly to 0 at [reach]. */
private fun falloff(distance: Float, reach: Float): Float {
    val x = (1f - distance / reach).coerceIn(0f, 1f)
    val s = x * x * (3f - 2f * x)
    return s * s            // squared again: the light gathers near its source and is nearly gone well before its reach
}

private fun smooth(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

/** The difference between two angles in radians, wrapped to -PI..PI. */
private fun angleBetween(a: Float, b: Float): Float {
    var d = (a - b) % (2f * PI.toFloat())
    if (d > PI.toFloat()) d -= 2f * PI.toFloat() else if (d < -PI.toFloat()) d += 2f * PI.toFloat()
    return d
}

/**
 * The lights of a scene and the ambient light they stand in, and what they do to a thing standing at a spot: light it (colour and amount, added up) and throw shadows
 * (one per light, each as dark as its light's share of all the light there).
 *
 * Nothing here allocates while a scene draws: shadows are written into a reusable [ShadowCasts].
 */
public class LightRig(
    /** How much light there is with no source at all, 0 to 1: the sky at that hour. A shadow's darkness is its light's share of this and every light together, so by day (high ambient) a lamp's shadow barely shows. */
    public var ambient: Float = 0.12f,
) {
    public val lights: MutableList<Light> = ArrayList()

    /** The vertical surfaces that shadows can land on, besides the floor. */
    public val walls: MutableList<Wall> = ArrayList()

    public fun add(wall: Wall): Wall { walls += wall; return wall }

    private val scratchWall = WallCast()

    public fun add(light: Light): Light { lights += light; return light }

    private val scratchCast = ShadowCast()

    /** How much light reaches a thing at ([feetX], [feetY]): the lights added up and held to 1, plus the ambient. */
    public fun level(feetX: Float, feetY: Float, seconds: Float): Float {
        var sum = ambient
        for (l in lights) sum += l.contribution(feetX, feetY, seconds)
        return min(1f, sum)
    }

    /**
     * The colour of a thing standing at ([feetX], [feetY]): [dark] with no light on it up to [lit] fully lit, where [lit] is first tinted by the colours of the lights that reach it, weighted by how
     * much each does. A thing between a campfire and the moon comes out warm and cold at once, in proportion.
     */
    public fun shade(dark: Color, lit: Color, feetX: Float, feetY: Float, seconds: Float): Color {
        var sum = 0f; var r = 0f; var g = 0f; var b = 0f
        for (l in lights) {
            val c = l.contribution(feetX, feetY, seconds)
            if (c <= 0f) continue
            sum += c
            val m = max(l.color.red, max(l.color.green, l.color.blue)).coerceAtLeast(0.01f)
            r += l.color.red / m * c; g += l.color.green / m * c; b += l.color.blue / m * c
        }
        if (sum <= 0f) return lerp(dark, lit, (ambient * 0.5f).coerceIn(0f, 1f) * 0.25f)
        val tinted = Color((lit.red * r / sum).coerceIn(0f, 1f), (lit.green * g / sum).coerceIn(0f, 1f), (lit.blue * b / sum).coerceIn(0f, 1f), lit.alpha)
        // Squared: half the light looks a quarter as bright, so what is far from every light stays dark and what is beside one glows.
        val level = min(1f, sum + ambient * 0.5f)
        return lerp(dark, tinted, level * level)
    }

    /**
     * The shadows of a thing at ([feetX], [feetY]) on [wall], one per light that lights it and shines on the wall, the [max] strongest, in [into]; each one's alpha is [maxAlpha] times its light's share of
     * all the light there. Returns how many it wrote (0 if the thing is behind the wall or no light is in front of it).
     */
    public fun wallShadows(feetX: Float, feetY: Float, wall: Wall, seconds: Float, maxAlpha: Float, max: Int, into: WallCasts): Int {
        into.count = 0
        var total = ambient
        for (l in lights) total += l.contribution(feetX, feetY, seconds)
        val limit = min(max, into.capacity)
        var taken = 0
        while (taken < limit) {
            var best: Light? = null; var bestC = 0.02f
            for (l in lights) {
                val c = l.contribution(feetX, feetY, seconds)
                if (c <= bestC) continue
                var already = false
                for (i in 0 until into.count) if (into.owner[i] === l) already = true
                if (!already && l.castOnWall(feetX, feetY, wall.footY, scratchWall)) { best = l; bestC = c }
            }
            if (best == null) break
            best.castOnWall(feetX, feetY, wall.footY, scratchWall)
            val to = into.casts[into.count]
            to.set(scratchWall.x, scratchWall.footLift, scratchWall.scale, scratchWall.spread); to.alpha = maxAlpha * (bestC / total).coerceIn(0f, 1f)
            into.owner[into.count] = best
            into.count++
            taken++
        }
        return into.count
    }

    /**
     * The shadows of a thing at ([feetX], [feetY]) that is [objectHeight] tall, one per light that lights it, the [max] strongest, written to [into] with each one's [ShadowCast.alpha] set to
     * [maxAlpha] times its light's share of all the light there. Returns how many it wrote.
     */
    public fun shadows(feetX: Float, feetY: Float, objectHeight: Float, seconds: Float, maxAlpha: Float, max: Int, into: ShadowCasts): Int {
        into.count = 0
        var total = ambient
        for (l in lights) total += l.contribution(feetX, feetY, seconds)
        val limit = min(max, into.capacity)
        // Take the strongest few (a small selection, no sorting and nothing allocated).
        var taken = 0
        while (taken < limit) {
            var best: Light? = null; var bestC = 0.02f
            for (l in lights) {
                val c = l.contribution(feetX, feetY, seconds)
                if (c > bestC && !into.has(l)) { best = l; bestC = c }
            }
            if (best == null) break
            best.cast(feetX, feetY, objectHeight, scratchCast)
            into.put(best, scratchCast, maxAlpha * (bestC / total).coerceIn(0f, 1f))
            taken++
        }
        return into.count
    }
}

/**
 * A vertical surface that faces the viewer and stands on the ground along the screen line [footY], from [left] to [right] and [height] pixels tall: the back wall of a room, or the face of a crate.
 * Shadows of things standing in front of it land on it in the shape the light gives them (see [ShadowMath.onWallFromPoint]), instead of lying flat on the floor.
 * A thing that is itself the wall (a crate) names itself as [owner] so it does not shadow its own face. Plain data: move it by setting its fields.
 */
public class Wall(public var footY: Float, public var left: Float, public var right: Float, public var height: Float, public val owner: Any? = null)

/** A few shadows on walls' worth of room, reused by [LightRig.wallShadows]. */
public class WallCasts(public val capacity: Int = 4) {
    internal val owner = arrayOfNulls<Light>(capacity)
    public val casts: Array<WallCast> = Array(capacity) { WallCast() }
    public var count: Int = 0
        internal set
}

/** A few shadows' worth of room, reused by [LightRig.shadows]. */
public class ShadowCasts(public val capacity: Int = 4) {
    internal val owner = arrayOfNulls<Light>(capacity)
    public val casts: Array<ShadowCast> = Array(capacity) { ShadowCast() }
    public var count: Int = 0
        internal set

    internal fun has(light: Light): Boolean { for (i in 0 until count) if (owner[i] === light) return true; return false }

    internal fun put(light: Light, from: ShadowCast, alpha: Float) {
        val to = casts[count]
        to.set(from.leanX, from.leanY, from.spread); to.alpha = alpha
        owner[count] = light
        count++
    }
}
