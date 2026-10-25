package br.com.faru.boitata.core.light

import kotlin.math.sqrt

/**
 * A light for the [LightField]: where it is ([light], in the room, with its size and its opening if it stands inside something), its colour (0xRRGGBB), how hard it lights
 * what is [reach] cm away ([strength], falling off as 1 / (1 + (r / reach)²)).
 */
public class FieldLight(public val light: RoomLight, public val color: Int, public val strength: Float, public val reach: Float)

/**
 * How much light arrives at each point of one surface of a [ShadowRoom]: a grid of [cols] × [rows] cells over the part of the surface in view, its corners in the surface's own
 * plane ((x, d) on a level surface, (x, z) on an upright one) from ([u0], [v0]) to ([u1], [v1]). [amount] is the light arriving at each corner (the sum over the lights), and
 * [red], [green], [blue] its colour, each corner's light weighted by how much of it comes from which lamp. Row by row, corners `(cols + 1) × (rows + 1)`. Built in place.
 */
public class SurfaceLight(public val receiver: Int, public val cols: Int, public val rows: Int) {
    /**
     * Whether the grid is laid over the picture instead of the surface's plane: its box ([u0] .. [u1], [v0] .. [v1]) is then in picture units, and each corner is found on the
     * surface's plane through the camera. A picture drawn with its own perspective (a room's backdrop) puts its surfaces where it draws them; the grid follows the picture.
     */
    public var onPicture: Boolean = false

    /**
     * How a picture point lands on the surface's plane, when the picture's own drawing of the surface does not follow the camera (a room's side walls drawn with a wider
     * perspective): writes the plane point ((x, d) on a level surface, (x, z) on a back wall, (d, z) on a side wall) into the array and says whether it lands. Null: through
     * the camera.
     */
    public var toPlane: ((x: Float, y: Float, out: FloatArray) -> Boolean)? = null
    public val corners: Int = (cols + 1) * (rows + 1)
    public val amount: FloatArray = FloatArray(corners)
    public val red: FloatArray = FloatArray(corners)
    public val green: FloatArray = FloatArray(corners)
    public val blue: FloatArray = FloatArray(corners)
    public var u0: Float = 0f; public var v0: Float = 0f; public var u1: Float = 1f; public var v1: Float = 1f

    /** The room point of corner ([c], [r]), into [out] (x, d, z). False when a picture corner does not land on the surface's plane. */
    public fun point(room: ShadowRoom, c: Int, r: Int, out: FloatArray): Boolean {
        val rec = room.receivers[receiver]
        val u = u0 + (u1 - u0) * c / cols; val v = v0 + (v1 - v0) * r / rows
        if (!onPicture) { rec.toRoom(u, v, out); return true }
        val cam = room.camera
        val custom = toPlane
        val ok = if (custom != null) custom(u, v, tmp) else when (rec.axis) { 2 -> cam.onLevel(u, v, rec.at, tmp); 1 -> cam.onUpright(u, v, rec.at, tmp); else -> cam.onSide(u, v, rec.at, tmp) }
        if (!ok) return false
        // Kept on the surface as far as it really goes: a picture drawn with its own perspective can put a point of a side wall behind the back wall, where the back wall would
        // hide every light from it.
        val o = rec.outline
        var minU = Float.MAX_VALUE; var maxU = -Float.MAX_VALUE; var minV = Float.MAX_VALUE; var maxV = -Float.MAX_VALUE
        var k = 0
        while (k < o.size) { minU = minOf(minU, o[k]); maxU = maxOf(maxU, o[k]); minV = minOf(minV, o[k + 1]); maxV = maxOf(maxV, o[k + 1]); k += 2 }
        val pu = tmp[0].coerceIn(minU + EDGE_IN, maxU - EDGE_IN); val pv = tmp[1].coerceIn(minV + EDGE_IN, maxV - EDGE_IN)
        rec.toRoom(pu, pv, out)
        return true
    }

    /** Where corner ([c], [r]) is on the picture, for a grid laid over the picture ([onPicture]). */
    public fun pictureX(c: Int): Float = u0 + (u1 - u0) * c / cols
    public fun pictureY(r: Int): Float = v0 + (v1 - v0) * r / rows

    private val tmp = FloatArray(2)

    private companion object {
        /** How far inside its outline a point found through the picture is kept, in cm (on the edge, the next surface would hide it). */
        const val EDGE_IN = 0.5f
    }
}

/**
 * The amount of light on the room's surfaces, as light adds up: at every point, for every light, `strength · cos θ / (1 + (r / reach)²) · V`, where θ is the angle between the
 * surface's normal and the way to the light and V how much of the light the point sees: the share of a few points across the flame or the bulb whose ray reaches the point past
 * the room's surfaces and, for a light standing inside something, through its opening. So a surface turned away from a light, or hidden from it by the edge of a ledge, gets
 * none of it, and the edge of what it does get is soft, wider the farther it is from what hides it.
 *
 * Pure numbers, no drawing (the light module draws it). Things standing in the room (props) are not in it: their sharp shadows are drawn on top.
 */
public object LightField {
    /** The points across a light its visibility is sampled at (shares of its radius: across, up): its middle, and two rings of six, so an edge's shadow fades in thirteen steps. */
    private val SAMPLES: Array<Pair<Float, Float>> = buildList {
        add(0f to 0f)
        for (ring in listOf(0.5f, 1f)) for (k in 0 until 6) {
            val a = (k + if (ring < 1f) 0.5f else 0f) * kotlin.math.PI.toFloat() / 3f
            add(ring * kotlin.math.cos(a) to ring * kotlin.math.sin(a))
        }
    }.toTypedArray()

    /** What glows of a light is never smaller than this, in cm, for the field: a flame's glow is wider than its core, and shadows in a room are never razor-edged. */
    public const val MIN_GLOW_CM: Float = 3f

    /**
     * Fills [out] with the light on receiver [out].receiver of [room] from [lights], over the part of the surface within [view] (its plane box: u0, v0, u1, v1, or null for the
     * whole outline). [ambient] is the light that arrives everywhere (the room's own glow, bounced; 0xRRGGBB colour [ambientColor]).
     */
    public fun compute(room: ShadowRoom, lights: List<FieldLight>, out: SurfaceLight, ambient: Float = 0f, ambientColor: Int = 0xFFFFFF, view: FloatArray? = null) {
        val rec = room.receivers[out.receiver]
        var minU = Float.MAX_VALUE; var maxU = -Float.MAX_VALUE; var minV = Float.MAX_VALUE; var maxV = -Float.MAX_VALUE
        var k = 0
        while (k < rec.outline.size) { minU = minOf(minU, rec.outline[k]); maxU = maxOf(maxU, rec.outline[k]); minV = minOf(minV, rec.outline[k + 1]); maxV = maxOf(maxV, rec.outline[k + 1]); k += 2 }
        if (out.onPicture) {
            // A grid over the picture: [view] is its box there.
            requireNotNull(view) { "a grid over the picture needs its box" }
            minU = view[0]; minV = view[1]; maxU = view[2]; maxV = view[3]
        } else if (view != null) { minU = maxOf(minU, view[0]); minV = maxOf(minV, view[1]); maxU = minOf(maxU, view[2]); maxV = minOf(maxV, view[3]) }
        if (maxU <= minU) maxU = minU + 1f
        if (maxV <= minV) maxV = minV + 1f
        out.u0 = minU; out.v0 = minV; out.u1 = maxU; out.v1 = maxV
        val p = FloatArray(3)
        val ar = ((ambientColor shr 16) and 0xFF) / 255f; val ag = ((ambientColor shr 8) and 0xFF) / 255f; val ab = (ambientColor and 0xFF) / 255f
        for (r in 0..out.rows) for (c in 0..out.cols) {
            val i = r * (out.cols + 1) + c
            if (!out.point(room, c, r, p)) { out.amount[i] = ambient; out.red[i] = ar; out.green[i] = ag; out.blue[i] = ab; continue }
            var sum = ambient; var sr = ambient * ar; var sg = ambient * ag; var sb = ambient * ab
            for (fl in lights) {
                val e = arriving(room, out.receiver, fl, p[0], p[1], p[2])
                if (e <= 0f) continue
                sum += e
                sr += e * ((fl.color shr 16) and 0xFF) / 255f; sg += e * ((fl.color shr 8) and 0xFF) / 255f; sb += e * (fl.color and 0xFF) / 255f
            }
            out.amount[i] = sum
            if (sum > 1e-6f) { out.red[i] = sr / sum; out.green[i] = sg / sum; out.blue[i] = sb / sum } else { out.red[i] = 0f; out.green[i] = 0f; out.blue[i] = 0f }
        }
    }

    /** The light of [fl] arriving at the room point ([px], [pd], [pz]) of receiver [i]: its falloff, the cosine with the surface's normal, and how much of it the point sees. */
    public fun arriving(room: ShadowRoom, i: Int, fl: FieldLight, px: Float, pd: Float, pz: Float): Float {
        val l = fl.light
        val rec = room.receivers[i]
        val dx = l.x - px; val dd = l.d - pd; val dz = l.z - pz
        val dist = sqrt(dx * dx + dd * dd + dz * dz).coerceAtLeast(0.1f)
        // A level surface is a top (the desk, the floor, a board): lit only from above. A back wall faces the viewer, a side wall the middle of the room.
        val toward = when (rec.axis) { 2 -> dz; 1 -> dd; else -> dx }
        val cos = rec.facing * toward / dist
        if (cos <= 0f) return 0f
        val seen = visibility(room, i, l, px, pd, pz)
        if (seen <= 0f) return 0f
        val fall = 1f / (1f + (dist / fl.reach) * (dist / fl.reach))
        return fl.strength * cos * fall * seen
    }

    /**
     * The light arriving at a point of a thing standing in the room ([px], [pd], [pz]), from [lights]: as on a surface, but a thing is rounded, so it takes a light from any side
     * (no cosine), and nothing of the room's surfaces is its own. How bright a prop shows.
     */
    public fun at(room: ShadowRoom, lights: List<FieldLight>, px: Float, pd: Float, pz: Float): Float {
        var sum = 0f
        for (fl in lights) {
            val l = fl.light
            val dx = l.x - px; val dd = l.d - pd; val dz = l.z - pz
            val dist = sqrt(dx * dx + dd * dd + dz * dz).coerceAtLeast(0.1f)
            val seen = visibility(room, -1, l, px, pd, pz)
            if (seen <= 0f) continue
            sum += fl.strength * seen / (1f + (dist / fl.reach) * (dist / fl.reach))
        }
        return sum
    }

    /** The share of the light [l] the point ([px], [pd], [pz]) of receiver [self] sees: of a few points across it, those whose ray is not stopped. */
    public fun visibility(room: ShadowRoom, self: Int, l: RoomLight, px: Float, pd: Float, pz: Float): Float {
        var seen = 0
        val radius = maxOf(l.radius, MIN_GLOW_CM)
        for ((sx, sz) in SAMPLES) {
            val lx = l.x + sx * radius; val ld = l.d; val lz = l.z + sz * radius
            if (clear(room, self, l.opening, lx, ld, lz, px, pd, pz)) seen++
        }
        return seen / SAMPLES.size.toFloat()
    }

    /** Whether the ray from the light point to the surface point passes every surface of the room (and the light's opening, if it has one). */
    private fun clear(room: ShadowRoom, self: Int, opening: Opening?, lx: Float, ld: Float, lz: Float, px: Float, pd: Float, pz: Float): Boolean {
        if (opening != null) {
            // The ray must leave through the opening: the point is beyond the opening's plane (a point on the light's own side of it is inside what holds the light, or behind
            // it, and in its shade), and where the ray crosses that plane is inside the opening's rectangle.
            val den = pd - ld
            if (den > -1e-4f && den < 1e-4f) return false
            val t = (opening.d - ld) / den
            if (t <= 0f || t >= 1f) return false
            val x = lx + t * (px - lx); val z = lz + t * (pz - lz)
            if (x < opening.left || x > opening.right || z < opening.bottom || z > opening.top) return false
        }
        for (j in room.receivers.indices) {
            if (j == self) continue
            val r = room.receivers[j]
            // The wall the opening is in: the opening test above already let the ray through it.
            if (opening != null && r.axis == 1 && kotlin.math.abs(r.at - opening.d) < 0.5f) continue
            val from = r.along(lx, ld, lz)
            val to = r.along(px, pd, pz)
            val den = to - from
            if (den > -1e-4f && den < 1e-4f) continue
            val t = (r.at - from) / den
            if (t <= 0.001f || t >= 0.999f) continue
            val hx = lx + t * (px - lx); val hd = ld + t * (pd - ld); val hz = lz + t * (pz - lz)
            if (r.holds(hx, hd, hz)) return false
        }
        return true
    }
}
