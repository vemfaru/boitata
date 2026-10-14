package br.com.faru.boitata.core.light

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The physics of looking at water, as numbers.
 *
 * - **Reflection** ([reflectance]): water mirrors the sky and what stands at its edge, a little at a steep look and more at a grazing one (Schlick's approximation of Fresnel: `R0 + (1 - R0) * (1 - cos)^5`, with
 *   `R0 = ((n - 1) / (n + 1))^2`, about 2% for water).
 * - **Refraction** ([apparentDepth]): what is under the surface looks nearer to it than it is, by the index `n` (Snell): a thing 1 m under looks 0.75 m under.
 */
public object LiquidMath {
    /** The index of refraction of water. */
    public const val WATER_INDEX: Float = 1.33f

    /** How much of the light is reflected when the surface is seen at [cosIncidence] (the cosine of the angle between the line of sight and the surface's normal; 1 looking straight down, 0 grazing). */
    public fun reflectance(cosIncidence: Float, index: Float = WATER_INDEX): Float {
        val r0 = ((index - 1f) / (index + 1f)).let { it * it }
        return r0 + (1f - r0) * (1f - cosIncidence.coerceIn(0f, 1f)).pow(5)
    }

    /** How deep something [depth] under the surface looks, seen from above: `depth / index`. */
    public fun apparentDepth(depth: Float, index: Float = WATER_INDEX): Float = depth / index
}

/**
 * A pool of water lying on the ground: an ellipse centred on ([cx], [cy]) with half-width [rx] and half-height [ry] (on screen: a round pond seen at a slant is a flattened ellipse), whose water is [maxDepth] pixels
 * deep at the middle and nothing at the shore. Things whose feet stand in it are partly under water (see [depthAt]), and it mirrors what stands at its edge and what is above it.
 */
public class Liquid(
    public var cx: Float,
    public var cy: Float,
    public var rx: Float,
    public var ry: Float,
    /** How deep the water is at the middle, in pixels of a thing of reference size (the drawing scales it with the thing). */
    public var maxDepth: Float,
    /** What the surface does to what is seen through it and how it looks (tint, blur, distortion). */
    public val optics: Optics = Optics.Water(),
    public val index: Float = LiquidMath.WATER_INDEX,
    /** How strongly it mirrors, 0 to 1. Physically about 6% at our camera angle; a stylised value shows the reflection. */
    public var reflectance: Float = 0.42f,
    /** The colour of the water seen straight down. */
    public val color: Color = Color(0xFF123238),
) {
    private fun r(x: Float, y: Float): Float {
        val dx = (x - cx) / rx; val dy = (y - cy) / ry
        return sqrt(dx * dx + dy * dy)
    }

    /** Whether the ground point ([x], [y]) is under water. */
    public fun contains(x: Float, y: Float): Boolean = r(x, y) < 1f

    /** How deep the water is at ([x], [y]) as a share of [maxDepth]: 0 at the shore, 1 at the middle, rounded like a bowl (and 0 outside). */
    public fun depthAt(x: Float, y: Float): Float {
        val d = r(x, y)
        return if (d >= 1f) 0f else 1f - d * d
    }
}

/** Water with the optics of a pond: it stops a little light, tints it blue-green, scatters, blurs and bends what is seen through it. */
public fun Optics.Companion.Water(tint: Color = Color(0xFF7FC4C0)): Optics = Optics(0.35f, tint, scatter = 0.3f, blur = 0.25f, distortion = 0.6f, haze = 0.15f)
