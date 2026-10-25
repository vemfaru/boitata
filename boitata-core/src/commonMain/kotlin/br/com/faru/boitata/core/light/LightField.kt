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
    public val corners: Int = (cols + 1) * (rows + 1)
    public val amount: FloatArray = FloatArray(corners)
    public val red: FloatArray = FloatArray(corners)
    public val green: FloatArray = FloatArray(corners)
    public val blue: FloatArray = FloatArray(corners)
    public var u0: Float = 0f; public var v0: Float = 0f; public var u1: Float = 1f; public var v1: Float = 1f

    /** The room point of corner ([c], [r]), into [out] (x, d, z). */
    public fun point(room: ShadowRoom, c: Int, r: Int, out: FloatArray) {
        val rec = room.receivers[receiver]
        val u = u0 + (u1 - u0) * c / cols; val v = v0 + (v1 - v0) * r / rows
        if (rec.level) { out[0] = u; out[1] = v; out[2] = rec.at } else { out[0] = u; out[1] = rec.at; out[2] = v }
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
    /** The points across a light its visibility is sampled at (shares of its radius: across, up). */
    private val SAMPLES = arrayOf(0f to 0f, 1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f)

    /**
     * Fills [out] with the light on receiver [out].receiver of [room] from [lights], over the part of the surface within [view] (its plane box: u0, v0, u1, v1, or null for the
     * whole outline). [ambient] is the light that arrives everywhere (the room's own glow, bounced; 0xRRGGBB colour [ambientColor]).
     */
    public fun compute(room: ShadowRoom, lights: List<FieldLight>, out: SurfaceLight, ambient: Float = 0f, ambientColor: Int = 0xFFFFFF, view: FloatArray? = null) {
        val rec = room.receivers[out.receiver]
        var minU = Float.MAX_VALUE; var maxU = -Float.MAX_VALUE; var minV = Float.MAX_VALUE; var maxV = -Float.MAX_VALUE
        var k = 0
        while (k < rec.outline.size) { minU = minOf(minU, rec.outline[k]); maxU = maxOf(maxU, rec.outline[k]); minV = minOf(minV, rec.outline[k + 1]); maxV = maxOf(maxV, rec.outline[k + 1]); k += 2 }
        if (view != null) { minU = maxOf(minU, view[0]); minV = maxOf(minV, view[1]); maxU = minOf(maxU, view[2]); maxV = minOf(maxV, view[3]) }
        if (maxU <= minU) maxU = minU + 1f
        if (maxV <= minV) maxV = minV + 1f
        out.u0 = minU; out.v0 = minV; out.u1 = maxU; out.v1 = maxV
        val p = FloatArray(3)
        val ar = ((ambientColor shr 16) and 0xFF) / 255f; val ag = ((ambientColor shr 8) and 0xFF) / 255f; val ab = (ambientColor and 0xFF) / 255f
        for (r in 0..out.rows) for (c in 0..out.cols) {
            val i = r * (out.cols + 1) + c
            out.point(room, c, r, p)
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
        // A level surface is a top (the desk, the floor, a board): lit only from above. An upright one faces the room (toward the viewer).
        val cos = if (rec.level) dz / dist else -dd / dist
        if (cos <= 0f) return 0f
        val seen = visibility(room, i, l, px, pd, pz)
        if (seen <= 0f) return 0f
        val fall = 1f / (1f + (dist / fl.reach) * (dist / fl.reach))
        return fl.strength * cos * fall * seen
    }

    /** The share of the light [l] the point ([px], [pd], [pz]) of receiver [self] sees: of a few points across it, those whose ray is not stopped. */
    public fun visibility(room: ShadowRoom, self: Int, l: RoomLight, px: Float, pd: Float, pz: Float): Float {
        var seen = 0
        for ((sx, sz) in SAMPLES) {
            val lx = l.x + sx * l.radius; val ld = l.d; val lz = l.z + sz * l.radius
            if (clear(room, self, l.opening, lx, ld, lz, px, pd, pz)) seen++
        }
        return seen / SAMPLES.size.toFloat()
    }

    /** Whether the ray from the light point to the surface point passes every surface of the room (and the light's opening, if it has one). */
    private fun clear(room: ShadowRoom, self: Int, opening: Opening?, lx: Float, ld: Float, lz: Float, px: Float, pd: Float, pz: Float): Boolean {
        if (opening != null) {
            // The ray must leave through the opening: where it crosses the opening's plane, inside its rectangle.
            val den = pd - ld
            if (den > -1e-4f && den < 1e-4f) return false
            val t = (opening.d - ld) / den
            if (t > 0f && t < 1f) {
                val x = lx + t * (px - lx); val z = lz + t * (pz - lz)
                if (x < opening.left || x > opening.right || z < opening.bottom || z > opening.top) return false
            }
        }
        for (j in room.receivers.indices) {
            if (j == self) continue
            val r = room.receivers[j]
            // The wall the opening is in: the opening test above already let the ray through it.
            if (opening != null && !r.level && kotlin.math.abs(r.at - opening.d) < 0.5f) continue
            val from = if (r.level) lz else ld
            val to = if (r.level) pz else pd
            val den = to - from
            if (den > -1e-4f && den < 1e-4f) continue
            val t = (r.at - from) / den
            if (t <= 0.001f || t >= 0.999f) continue
            val hx = lx + t * (px - lx); val hd = ld + t * (pd - ld); val hz = lz + t * (pz - lz)
            if (if (r.level) r.contains(hx, hd) else r.contains(hx, hz)) return false
        }
        return true
    }
}
