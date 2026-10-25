package br.com.faru.boitata.core.light

/** A real thing's size in centimetres: across, front to back, and up. Any of them may be unknown (NaN); what is known wins over what a picture suggests. */
public class ObjectSize(public val width: Float = Float.NaN, public val depth: Float = Float.NaN, public val height: Float = Float.NaN)

/** How a thing meets the room, which decides what its shadow is. */
public enum class CasterKind {
    /** It stands on a surface (a mug, a lamp, a radio): an upright card on its footprint, its whole height casting. */
    Standing,

    /** It lies flat on a surface (a sheet, a notepad, letters): only its thickness casts, a thin rim round it. */
    Lying,

    /** It hangs on a wall (a calendar, a mirror): a card a few centimetres in front of the wall, casting on the wall close behind it. */
    Hung,
}

/**
 * What is known about a thing besides its picture, written next to its art (docs/art-pipeline.md): its real [size], its [kind], and [base], the share of the picture's height
 * where it touches what it stands on (NaN: read from the picture, [SilhouetteMetrics]). For a hung thing, [gap] is how far in front of the wall it hangs, in centimetres.
 */
public class ObjectFacts(
    public val size: ObjectSize = ObjectSize(),
    public val kind: CasterKind = CasterKind.Standing,
    public val base: Float = Float.NaN,
    public val gap: Float = 1.5f,
)

/**
 * What a thing's picture says about its shape, read from its transparency: where it starts at the top, where its base touches what it stands on (the lowest solid row), and how wide
 * the base is there. All as shares of the picture (0 at the left or the top, 1 at the right or the bottom). [solid] is how opaque a cell has to be to count.
 */
public class SilhouetteMetrics(
    public val top: Float,
    public val base: Float,
    public val baseLeft: Float,
    public val baseRight: Float,
    public val left: Float,
    public val right: Float,
) {
    /** The middle of the base, across. */
    public val baseCenter: Float get() = (baseLeft + baseRight) / 2f

    /**
     * The best guess of where the thing whose picture is drawn at [left] .. [right], [top] .. [bottom] (world units) stands, as a [RoomCaster] in [camera]'s room: its base on a level
     * surface [baseZ] cm above the floor. The lowest solid row is the front of its footprint; the footprint is [size]'s depth deep when it is known, and otherwise as deep as the base is
     * wide (a round or square base), and the card that casts stands in its middle.
     */
    public fun caster(camera: RoomCamera, left: Float, top: Float, right: Float, bottom: Float, baseZ: Float, size: ObjectSize? = null): RoomCaster =
        caster(camera, left, top, right, bottom, baseZ, ObjectFacts(size ?: ObjectSize()))

    /**
     * As above, with everything known about the thing: a known [ObjectFacts.base] wins over the picture's lowest row, a lying thing casts only its thickness (its size's height,
     * or [LYING_THICKNESS]). A hung thing is placed with [hung] instead.
     */
    public fun caster(camera: RoomCamera, left: Float, top: Float, right: Float, bottom: Float, baseZ: Float, facts: ObjectFacts): RoomCaster {
        val w = right - left; val h = bottom - top
        val baseY = top + (facts.base.takeIf { !it.isNaN() } ?: this.base) * h
        val p = FloatArray(2)
        var dFront = 0f; var baseWidthCm = 0f
        if (camera.onLevel(left + baseLeft * w, baseY, baseZ, p)) { val x0 = p[0]; dFront = p[1]; if (camera.onLevel(left + baseRight * w, baseY, baseZ, p)) baseWidthCm = p[0] - x0 }
        val depth = facts.size.depth.takeIf { !it.isNaN() } ?: baseWidthCm
        val maxHeight = if (facts.kind == CasterKind.Lying) facts.size.height.takeIf { !it.isNaN() } ?: LYING_THICKNESS else Float.POSITIVE_INFINITY
        return RoomCaster(dFront + depth / 2f, baseZ, left, top, right, bottom, maxHeight)
    }

    /** How tall the picture says the thing is, in centimetres, standing as [caster] in [camera]'s room: from its base to its top row. */
    public fun heightCm(camera: RoomCamera, caster: RoomCaster): Float {
        val p = FloatArray(2)
        val y = caster.top + this.top * (caster.bottom - caster.top)
        return if (camera.onUpright(caster.left + baseCenter * (caster.right - caster.left), y, caster.d, p)) p[1] - caster.baseZ else Float.NaN
    }

    public companion object {
        /** How thick a thing lying flat is taken to be when its size does not say (a few sheets, a notepad), in centimetres. */
        public const val LYING_THICKNESS: Float = 0.6f

        /** A thing hung on a wall [wallD] cm behind the reference line, its picture drawn at [left] .. [right], [top] .. [bottom]: a card [ObjectFacts.gap] in front of the wall. */
        public fun hung(wallD: Float, left: Float, top: Float, right: Float, bottom: Float, facts: ObjectFacts = ObjectFacts(kind = CasterKind.Hung)): RoomCaster =
            RoomCaster(wallD - facts.gap, Float.NEGATIVE_INFINITY, left, top, right, bottom)

        /** Reads a picture's [mask]: the rows and columns with a cell at least [solid] opaque. The base is the lowest of them, and its span the solid cells in the bottom [baseBand] of the thing. */
        public fun of(mask: AlphaMask, solid: Float = 0.125f, baseBand: Float = 0.06f): SilhouetteMetrics {
            var top = -1; var bottom = -1; var minX = mask.width; var maxX = -1
            for (y in 0 until mask.height) for (x in 0 until mask.width) if (mask.alpha(x, y) >= solid) {
                if (top < 0) top = y
                bottom = y
                if (x < minX) minX = x
                if (x > maxX) maxX = x
            }
            if (top < 0) return SilhouetteMetrics(0f, 1f, 0f, 1f, 0f, 1f)
            val band = maxOf(1, ((bottom - top + 1) * baseBand).toInt())
            var bl = mask.width; var br = -1
            for (y in (bottom - band + 1)..bottom) for (x in 0 until mask.width) if (mask.alpha(x, y) >= solid) { if (x < bl) bl = x; if (x > br) br = x }
            val w = mask.width.toFloat(); val h = mask.height.toFloat()
            return SilhouetteMetrics(top / h, (bottom + 1) / h, bl / w, (br + 1) / w, minX / w, (maxX + 1) / w)
        }
    }
}
