package br.com.faru.boitata.core.light

import kotlin.math.sqrt

/**
 * A flat surface of a room a shadow can land on: a level one (a desk top, the floor, a shelf) [at] cm above the floor, or an upright one (a wall, a crate's face) [at] cm behind
 * the reference line. Its [outline] is a polygon in its own plane, as pairs: (x, d) on a level surface, (x, z) on an upright one. A ray of light stops at the first surface whose
 * outline it crosses, so a desk top catches what falls on it and lets past what falls beyond its edges, on to the floor or the wall.
 */
public class ShadowReceiver(public val level: Boolean, public val at: Float, public val outline: FloatArray) {
    init { require(outline.size >= 6 && outline.size % 2 == 0) { "an outline needs three corners or more" } }

    /** Whether the plane point (u, v) is inside the outline (even-odd). */
    public fun contains(u: Float, v: Float): Boolean {
        var inside = false
        val n = outline.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val ui = outline[2 * i]; val vi = outline[2 * i + 1]; val uj = outline[2 * j]; val vj = outline[2 * j + 1]
            if ((vi > v) != (vj > v) && u < (uj - ui) * (v - vi) / (vj - vi) + ui) inside = !inside
            j = i
        }
        return inside
    }

    public companion object {
        /** A level surface [z] cm above the floor bounded by [outline] ((x, d) pairs). */
        public fun level(z: Float, vararg outline: Float): ShadowReceiver = ShadowReceiver(true, z, outline)

        /** An upright surface [d] cm behind the reference line bounded by [outline] ((x, z) pairs). */
        public fun upright(d: Float, vararg outline: Float): ShadowReceiver = ShadowReceiver(false, d, outline)
    }
}

/** A light at one place in the room, in centimetres, and the size of what glows (a bulb, a flame): the bigger, the wider a shadow's soft edge grows with distance. */
public class RoomLight(public var x: Float, public var d: Float, public var z: Float, public var radius: Float = 1.5f) {
    /**
     * Puts the light where the picture shows it: its foot drawn at ([footX], [footY]) on a level surface [footZ] cm above the floor, and what glows drawn at ([glowX], [glowY]) straight
     * above the foot. False (and the light unmoved) when the picture points are not on the room's planes.
     */
    public fun place(camera: RoomCamera, glowX: Float, glowY: Float, footX: Float, footY: Float, footZ: Float, tmp: FloatArray = FloatArray(2)): Boolean {
        if (!camera.onLevel(footX, footY, footZ, tmp)) return false
        val fd = tmp[1]
        if (!camera.onUpright(glowX, glowY, fd, tmp)) return false
        x = tmp[0]; d = fd; z = maxOf(tmp[1], footZ + 1f)
        return true
    }

    /** Copies another light, moved by ([dx], [dd], [dz]) cm: a point across what glows, for a soft edge. */
    public fun setOffset(o: RoomLight, dx: Float, dd: Float, dz: Float) { x = o.x + dx; d = o.d + dd; z = o.z + dz; radius = o.radius }
}

/**
 * A thing standing in the room as a picture: an upright card [d] cm behind the reference line (the middle of its footprint), standing on a surface [baseZ] cm above the floor.
 * Its picture is drawn at [left], [top] to [right], [bottom] in world units; every point of it is a point of the card ([RoomCamera.onUpright]), so what the picture shows is what casts.
 */
public class RoomCaster(public var d: Float, public var baseZ: Float, public var left: Float, public var top: Float, public var right: Float, public var bottom: Float)

/**
 * A room's camera and the surfaces in it that catch shadows, with the exact projection of a thing's shadow onto each one: the ray from the light through every point of the thing's
 * picture runs on until it meets the first surface in its way ([firstHit]). The shadow on one surface is the picture carried along those rays onto its plane, which from one plane to
 * another through a point is a perspective, so it is right however the light and the thing stand: the shadow lengthens as `L = H / tan θ`, climbs a wall to `H − d tan θ`, slides
 * sideways with the light's angle and is cut where its surface ends. Its soft edge comes from the size of the light ([RoomLight.radius]): `W = D_shadow · size / D_light`.
 */
public class ShadowRoom(public val camera: RoomCamera, public val receivers: List<ShadowReceiver>) {
    private val tmp = FloatArray(2)

    /**
     * How far along the ray from the light ([lx], [ld], [lz]) through the point ([px], [pd], [pz]) receiver [i]'s plane is, in units of the way to the point: above 1 is beyond the point.
     * NaN when the ray runs along the plane.
     */
    public fun along(i: Int, lx: Float, ld: Float, lz: Float, px: Float, pd: Float, pz: Float): Float {
        val r = receivers[i]
        val from = if (r.level) lz else ld
        val to = if (r.level) pz else pd
        val den = to - from
        return if (den > -1e-5f && den < 1e-5f) Float.NaN else (r.at - from) / den
    }

    /** The index of the first receiver the ray from the light through the point meets beyond the point, inside its outline; -1 when it meets none. */
    public fun firstHit(lx: Float, ld: Float, lz: Float, px: Float, pd: Float, pz: Float): Int {
        var best = -1; var bestT = Float.POSITIVE_INFINITY
        for (i in receivers.indices) {
            val t = along(i, lx, ld, lz, px, pd, pz)
            if (t.isNaN() || t <= 1f || t >= bestT) continue
            val hx = lx + t * (px - lx); val hd = ld + t * (pd - ld); val hz = lz + t * (pz - lz)
            val r = receivers[i]
            if (if (r.level) r.contains(hx, hd) else r.contains(hx, hz)) { best = i; bestT = t }
        }
        return best
    }

    /**
     * Where the ray from the light through the point meets receiver [i]'s **plane**, outline or not, drawn on the picture, into [out]. A ray that would reach it only far away is stopped
     * [maxReach] times the way to the point, so a shadow from a light almost level with a thing stays finite. False when the plane is not beyond the point.
     */
    public fun onPlane(i: Int, lx: Float, ld: Float, lz: Float, px: Float, pd: Float, pz: Float, out: FloatArray, maxReach: Float = MAX_REACH): Boolean {
        var t = along(i, lx, ld, lz, px, pd, pz)
        if (t.isNaN() || t <= 1f) return false
        if (t > maxReach) t = maxReach
        return camera.project(lx + t * (px - lx), ld + t * (pd - ld), lz + t * (pz - lz), out)
    }

    /** Receiver [i]'s outline drawn on the picture, as (x, y) pairs (corners behind the eye are left out). */
    public fun outlineOnPicture(i: Int): FloatArray {
        val r = receivers[i]
        val n = r.outline.size / 2
        val pts = FloatArray(n * 2); var k = 0
        for (j in 0 until n) {
            val u = r.outline[2 * j]; val v = r.outline[2 * j + 1]
            val ok = if (r.level) camera.project(u, v, r.at, tmp) else camera.project(u, r.at, v, tmp)
            if (ok) { pts[k++] = tmp[0]; pts[k++] = tmp[1] }
        }
        return pts.copyOf(k)
    }

    /**
     * The shadow receiver [blocker] throws on receiver [onto] from the light, drawn on the picture, as (x, y) pairs: the part of [onto] the light cannot reach because [blocker] is in the way
     * (the desk top's shadow on the floor and on the wall below it). Null when [blocker] does not stand between the light and [onto].
     */
    public fun shadeOf(blocker: Int, onto: Int, light: RoomLight): FloatArray? {
        val b = receivers[blocker]
        val n = b.outline.size / 2
        val pts = FloatArray(n * 2); var any = false
        for (j in 0 until n) {
            val u = b.outline[2 * j]; val v = b.outline[2 * j + 1]
            val px = u; val pd = if (b.level) v else b.at; val pz = if (b.level) b.at else v
            if (!onPlane(onto, light.x, light.d, light.z, px, pd, pz, tmp, BLOCKER_REACH)) {
                // The corner is not between the light and the plane: its ray runs away from it. Its edge of the shade is as far as it goes.
                camera.project(px, pd, pz, tmp)
            } else any = true
            pts[2 * j] = tmp[0]; pts[2 * j + 1] = tmp[1]
        }
        return if (any) pts else null
    }

    public companion object {
        /** The farthest a shadow is carried along its ray, as a multiple of the way from the light to the thing. */
        public const val MAX_REACH: Float = 6f

        /** A surface's own shade reaches farther (it is a big flat thing, its far edge must not fall short of the shadows inside it). */
        public const val BLOCKER_REACH: Float = 60f
    }
}

/**
 * A thing's shadow in a [ShadowRoom], as a mesh of its picture laid on each surface: a grid of [cols] × [rows] cells over the picture, each corner carried along its ray onto every
 * receiver's plane. For receiver `i`, [positions]`[i]` holds the corners on the picture (x, y pairs, the picture's grid row by row) and [valid]`[i]` whether each reached the plane;
 * [owner] says which receiver each corner's ray really ends on (or -1), and [share] how dark the shadow is there: the light's share of all the light at that spot.
 * Built in place, so a room draws its shadows without allocating.
 */
public class ShadowMesh(public val cols: Int, public val rows: Int, receivers: Int) {
    public val corners: Int = (cols + 1) * (rows + 1)
    public val positions: Array<FloatArray> = Array(receivers) { FloatArray(corners * 2) }
    public val valid: Array<BooleanArray> = Array(receivers) { BooleanArray(corners) }
    public val owner: IntArray = IntArray(corners)
    public val share: FloatArray = FloatArray(corners)

    /** The picture coordinates of each corner (x, y pairs), the same for every receiver. */
    public val texture: FloatArray = FloatArray(corners * 2)

    private val tmp = FloatArray(2)

    /**
     * Lays [caster]'s picture on every receiver of [room] from [light]. [strength] is how hard the light lights what is [reach] cm away (it falls off as 1 / (1 + (r / reach)²)),
     * [ambient] how much other light there is everywhere: a shadow is only as dark as the light it takes away.
     */
    public fun build(room: ShadowRoom, light: RoomLight, caster: RoomCaster, strength: Float = 1f, reach: Float = 150f, ambient: Float = 0.1f) {
        val cam = room.camera
        val w = caster.right - caster.left; val h = caster.bottom - caster.top
        for (r in 0..rows) for (c in 0..cols) {
            val k = r * (cols + 1) + c
            val sx = caster.left + w * c / cols; val sy = caster.top + h * r / rows
            texture[2 * k] = sx - caster.left; texture[2 * k + 1] = sy - caster.top
            // The point of the thing this corner of the picture shows: on its upright card, and never below the surface it stands on (the base's front half is drawn below the card's line).
            var px = 0f; var pz = caster.baseZ
            if (cam.onUpright(sx, sy, caster.d, tmp)) { px = tmp[0]; pz = maxOf(tmp[1], caster.baseZ + 0.01f) }
            val pd = caster.d
            for (i in room.receivers.indices) {
                val ok = room.onPlane(i, light.x, light.d, light.z, px, pd, pz, tmp)
                valid[i][k] = ok
                if (ok) { positions[i][2 * k] = tmp[0]; positions[i][2 * k + 1] = tmp[1] }
            }
            val hit = room.firstHit(light.x, light.d, light.z, px, pd, pz)
            owner[k] = hit
            share[k] = if (hit < 0) 0f else {
                val t = room.along(hit, light.x, light.d, light.z, px, pd, pz)
                val dx = t * (px - light.x); val dd = t * (pd - light.d); val dz = t * (pz - light.z)
                val dist = sqrt(dx * dx + dd * dd + dz * dz)
                val lit = strength / (1f + (dist / reach) * (dist / reach))
                lit / (lit + ambient)
            }
        }
    }

    /**
     * Whether the cell at ([c], [r]) is drawn on receiver [i]: all four corners reached its plane and at least one corner's ray ends on it (the cells along an edge are drawn on both
     * sides and cut there by each surface's outline).
     */
    public fun cellOn(i: Int, c: Int, r: Int): Boolean {
        val a = r * (cols + 1) + c; val b = a + 1; val d = a + cols + 1; val e = d + 1
        val v = valid[i]
        if (!v[a] || !v[b] || !v[d] || !v[e]) return false
        return owner[a] == i || owner[b] == i || owner[d] == i || owner[e] == i
    }
}
