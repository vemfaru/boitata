package br.com.faru.boitata.core.light

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cosh
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * What light does when it meets a thing: how much it stops, what colour it lets through, and how much it blurs and bends what is seen through it.
 *
 * Every picture is a silhouette; its own transparency says where the thing is, and `Optics` says how solid it is *there*. A crate stops all the light that hits it ([Opaque]); a pane of glass stops a
 * little and tints the rest; a sheet of paper stops half and scatters the half that passes ([Paper]), which is how a shadow theatre works.
 */
public class Optics(
    /** How much of the light that hits it is stopped: 1 stops all of it (a crate), 0 none (clear glass). */
    public val opacity: Float = 1f,
    /** The colour of the light that gets through (red paper lights the room red). Only matters when [opacity] is below 1. */
    public val tint: Color = Color.White,
    /** How much the light that passes is spread: 0 keeps a sharp patch, 1 makes it a soft glow (frosted glass, paper). */
    public val scatter: Float = 0f,
    /** How much what is seen through it is blurred, 0 to 1 (dirty or frosted glass). */
    public val blur: Float = 0f,
    /** How much what is seen through it is bent, 0 to 1 (old hand-made glass, water). */
    public val distortion: Float = 0f,
    /** A milky veil over what is seen through it, 0 to 1 (dirt, steam). */
    public val haze: Float = 0f,
) {
    /** Whether any light gets through. */
    public val translucent: Boolean get() = opacity < 0.999f

    public companion object {
        /** Stops all light: a crate, a person, a wall. */
        public val Opaque: Optics = Optics(1f)

        /** Clear glass: stops a little and tints a little. */
        public fun Glass(tint: Color = Color.White, opacity: Float = 0.12f): Optics = Optics(opacity, tint)

        /** Frosted or dirty glass: lets light through but scatters it and blurs the view. */
        public fun Frosted(dirt: Float = 0.6f, tint: Color = Color.White): Optics = Optics(0.2f + 0.2f * dirt, tint, scatter = dirt, blur = dirt, haze = 0.5f * dirt)

        /** A sheet of paper or cloth, as in a shadow theatre: stops about half the light and scatters what passes. */
        public fun Paper(tint: Color = Color.White): Optics = Optics(0.45f, tint, scatter = 0.7f, blur = 0.15f)

        /**
         * A sheet of real paper of this [grammage] (g/m²: onionskin about 34, typing paper 60, 20 lb bond 75), stopping what [paperTransmittance] says it stops: thin paper glows,
         * heavy paper hardly. [scattering] and [absorption] are the paper's own (m²/kg), as there.
         */
        public fun paper(grammage: Float, tint: Color = Color.White, scattering: Float = PAPER_SCATTERING, absorption: Float = PAPER_ABSORPTION): Optics =
            Optics(1f - paperTransmittance(grammage, scattering, absorption), tint, scatter = 0.7f, blur = 0.15f)

        /** What white printing paper scatters, per kilogram of it in a square metre (m²/kg): 40 to 60. */
        public const val PAPER_SCATTERING: Float = 50f

        /** What white paper absorbs (m²/kg): very little; most of what does not pass goes back toward the light. */
        public const val PAPER_ABSORPTION: Float = 0.8f
    }
}

/**
 * How much of the light that falls on one side of a sheet of paper comes out of the other, by the Kubelka–Munk theory of a layer that scatters and absorbs: with `a = 1 + K/S`,
 * `b = √(a² − 1)` and `SX` the scattering of the whole sheet (`S` times its weight per square metre), `T = b / (a·sinh(b·SX) + b·cosh(b·SX))`. About 0.39 for onionskin (34 g/m²),
 * 0.23 for typing paper (60) and 0.19 for 20 lb bond (75), with [scattering] `S` and [absorption] `K` in m²/kg.
 */
public fun paperTransmittance(grammage: Float, scattering: Float = Optics.PAPER_SCATTERING, absorption: Float = Optics.PAPER_ABSORPTION): Float {
    val sx = scattering * grammage / 1000f
    if (sx <= 0f) return 1f
    val a = 1f + absorption / scattering
    val b = sqrt(a * a - 1f)
    if (b < 1e-4f) return 1f / (1f + sx)                       // nothing absorbed: what is not sent back passes
    return b / (a * sinh(b * sx) + b * cosh(b * sx))
}

/**
 * Where a thing is, as a grid of how solid it is: a coarse copy of a picture's transparency, so a light ray that passes through a gap in a thing (between a person's legs, through a lattice) is not stopped.
 * [coverage] takes a point of the thing's box as shares: across from 0 at the left to 1 at the right, and up from 0 at its foot to 1 at its top.
 */
public class AlphaMask(public val width: Int, public val height: Int, private val data: ByteArray) {
    public fun coverage(across: Float, up: Float): Float {
        val x = (across * width).toInt().coerceIn(0, width - 1)
        val y = ((1f - up) * height).toInt().coerceIn(0, height - 1)
        return (data[y * width + x].toInt() and 0xFF) / 255f
    }
}

/**
 * A vertical surface that faces the viewer and stands on the ground along the screen line [footY], from [left] to [right] and [height] pixels tall: the back wall of a room, a crate, a person.
 * It does three jobs for the lights that know about it: it **blocks light** (what is behind it, as seen from a light, is in its shadow, by how much its [optics] say), it **receives shadows** (a shadow
 * thrown toward it lands on it, see `drawCastShadows`), and, for a thing with a [silhouette], it does so only where the thing actually is (the shadow is cut to its outline).
 *
 * A thing that is itself a surface names itself as [owner], so it never shadows or blocks its own light. Plain data: move it by setting its fields.
 */
public class Billboard(
    public var footY: Float,
    public var left: Float,
    public var right: Float,
    public var height: Float,
    public val owner: Any? = null,
    public var optics: Optics = Optics.Opaque,
    /** Its transparency, where light passes between the parts of the thing; null means the whole box is solid. */
    public var mask: AlphaMask? = null,
) {
    /** Whether shadows of other things land on its face (cut to its outline when it has a [silhouette]). */
    public var receivesShadows: Boolean = false

    /** The thing drawn standing with its feet at (0, 0) and up negative, in the colour given, at its current size: what a shadow on its face is cut to. */
    public var silhouette: (DrawScope.(Color) -> Unit)? = null

    /** Whether it blocks light at all. A back wall that nothing stands behind does not need to. */
    public var blocksLight: Boolean = true

    /** The most a shadow on its face may be enlarged: 3.2 keeps a lamp level with a thing from putting a gigantic shadow on a back wall; a shadow-theatre screen wants far more (a hand near the lamp is huge). */
    public var maxShadowScale: Float = ShadowMath.MAX_WALL_SCALE
}
