package br.com.faru.boitata.light

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import br.com.faru.boitata.core.light.RoomCaster
import br.com.faru.boitata.core.light.RoomLight
import br.com.faru.boitata.core.light.ShadowMesh
import br.com.faru.boitata.core.light.ShadowRoom

/**
 * One thing's shadow in a [ShadowRoom], ready to draw on any of the room's surfaces: its picture laid on each surface along the light's rays ([ShadowMesh]), from a few points across
 * what glows, so the edge is sharp where the thing touches its surface and widens with distance as `W = D_shadow · size / D_light`. Each surface's part is cut to the surface's outline
 * and to where another surface takes the light away (the desk top's own shade on the floor).
 *
 * [build] works the geometry out (when the light or the thing moves); [draw] costs one mesh per point of the light, in picture units drawn at [unit] pixels each from [originX], [originY].
 */
class RoomShadow(private val room: ShadowRoom, private val cols: Int = 10, private val rows: Int = 14) {
    private val meshes = Array(SAMPLES) { ShadowMesh(cols, rows, room.receivers.size) }
    private val sample = RoomLight(0f, 0f, 0f)
    private var built: Array<Array<Vertices?>> = Array(SAMPLES) { arrayOfNulls(room.receivers.size) }
    private var clips: Array<Path?> = arrayOfNulls(room.receivers.size)
    private var drawnClips: Array<Path?> = arrayOfNulls(room.receivers.size)
    private var builtUnit = Float.NaN
    private var builtX = Float.NaN
    private var builtY = Float.NaN
    private var imageW = 1f
    private var imageH = 1f
    private var casterW = 1f
    private var casterH = 1f
    private val paint = Paint().apply { filterQuality = FilterQuality.Low }

    /** Whether the shadow reaches surface [i] at all, as last built. */
    fun reaches(i: Int): Boolean = meshes[0].owner.any { it == i }

    /**
     * Works out the shadow of [caster] (drawn from [image]) from [light]: [strength] at [reach] cm and [ambient] decide how dark it is on each surface. Call again when anything of it moves.
     */
    fun build(light: RoomLight, caster: RoomCaster, image: ImageBitmap, strength: Float = 1f, reach: Float = 150f, ambient: Float = 0.1f) {
        val r = light.radius
        for (k in 0 until SAMPLES) {
            val (dx, dz) = OFFSETS[k]
            sample.setOffset(light, dx * r, 0f, dz * r)
            meshes[k].build(room, sample, caster, strength, reach, ambient)
        }
        imageW = image.width.toFloat(); imageH = image.height.toFloat()
        casterW = (caster.right - caster.left).coerceAtLeast(1e-3f); casterH = (caster.bottom - caster.top).coerceAtLeast(1e-3f)
        paint.shader = ImageShader(image)
        for (i in room.receivers.indices) {
            val outline = polygon(room.outlineOnPicture(i), 1f, 0f, 0f)
            for (j in room.receivers.indices) if (j != i) room.shadeOf(j, i, light)?.let { shade -> outline.op(outline, polygon(shade, 1f, 0f, 0f), PathOperation.Difference) }
            clips[i] = outline
        }
        builtUnit = Float.NaN
    }

    /** Draws the shadow's part on surface [receiver], [alpha] dark at most, the picture's world units drawn at [unit] pixels each, from the world point ([originX], [originY]). */
    fun draw(scope: DrawScope, receiver: Int, alpha: Float, unit: Float, originX: Float, originY: Float) {
        for (k in 0 until SAMPLES) drawSample(scope, receiver, k, alpha / SAMPLES, unit, originX, originY)
    }

    /**
     * Draws the part on [receiver] seen from one point [sample] of the light ([SAMPLES] of them): what [draw] adds up. A thing made of several pictures draws each one's sample into one
     * layer and lays the layer down at a share of the darkness, so where its pictures overlap the shadow is not darker.
     */
    fun drawSample(scope: DrawScope, receiver: Int, sample: Int, alpha: Float, unit: Float, originX: Float, originY: Float) {
        if (alpha <= 0f) return
        if (unit != builtUnit || originX != builtX || originY != builtY) {
            for (k in 0 until SAMPLES) for (i in room.receivers.indices) built[k][i] = vertices(meshes[k], i, unit, originX, originY)
            for (i in room.receivers.indices) drawnClips[i] = clips[i]?.let { c ->
                Path().apply { addPath(c, Offset(-originX, -originY)); transform(androidx.compose.ui.graphics.Matrix().apply { scale(unit, unit) }) }
            }
            builtUnit = unit; builtX = originX; builtY = originY
        }
        val scaled = drawnClips[receiver] ?: return
        val v = built[sample][receiver] ?: return
        paint.alpha = alpha
        scope.clipPath(scaled) { drawIntoCanvas { canvas -> canvas.drawVertices(v, BlendMode.Modulate, paint) } }
    }

    private fun vertices(mesh: ShadowMesh, i: Int, unit: Float, ox: Float, oy: Float): Vertices? {
        val indices = ArrayList<Int>()
        for (r in 0 until rows) for (c in 0 until cols) if (mesh.cellOn(i, c, r)) {
            val a = r * (cols + 1) + c; val b = a + 1; val d = a + cols + 1; val e = d + 1
            indices += a; indices += b; indices += e; indices += a; indices += e; indices += d
        }
        if (indices.isEmpty()) return null
        val pos = mesh.positions[i]
        val positions = List(mesh.corners) { k -> Offset((pos[2 * k] - ox) * unit, (pos[2 * k + 1] - oy) * unit) }
        val sx = imageW / casterW; val sy = imageH / casterH
        val tex = List(mesh.corners) { k -> Offset(mesh.texture[2 * k] * sx, mesh.texture[2 * k + 1] * sy) }
        val colors = List(mesh.corners) { k -> Color.Black.copy(alpha = mesh.share[k]) }
        return Vertices(VertexMode.Triangles, positions, tex, colors, indices)
    }

    private fun polygon(pts: FloatArray, unit: Float, ox: Float, oy: Float): Path = Path().apply {
        fillType = PathFillType.NonZero
        if (pts.size < 6) return@apply
        moveTo((pts[0] - ox) * unit, (pts[1] - oy) * unit)
        var k = 2
        while (k < pts.size) { lineTo((pts[k] - ox) * unit, (pts[k + 1] - oy) * unit); k += 2 }
        close()
    }

    companion object {
        /** The points across what glows the shadow is drawn from: the middle and four round it (as shares of its radius, across and up). */
        private val OFFSETS = arrayOf(0f to 0f, 1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f)
        /** How many points of the light a shadow is drawn from. */
        val SAMPLES: Int = OFFSETS.size
    }
}
