package br.com.faru.boitata.light

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import br.com.faru.boitata.core.light.ShadowRoom
import br.com.faru.boitata.core.light.SurfaceLight
import kotlin.math.exp

/**
 * How a surface's light ([SurfaceLight]) is laid over its picture: where little light arrives the picture goes into the room's [darkness] (0 to 1 of black), where a lamp lights
 * it the darkness lifts (by up to [lift] of it) and the lamp's colour warms it by up to [tint]. How lit a point looks is `1 − e^(−amount · gain)`: a little light lifts the
 * dark a lot, more light adds less and less, as an eye sees it.
 */
class LightLook(val darkness: Float, val gain: Float, val tint: Float, val lift: Float = 1f)

/**
 * Lays the light of [grid] over its surface of [room] as two smooth meshes (the darkness, then the lamps' colour added), cut to [clip] (the part of the surface in view, world
 * units drawn at [unit] pixels each from the world point ([originX], [originY])). The meshes are built once per grid ([LightMesh]) and drawn as they are: a frame costs two
 * mesh draws per surface.
 */
class LightMesh(private val room: ShadowRoom) {
    private var dark: Vertices? = null
    private var warm: Vertices? = null
    private val darkPaint = Paint()
    private val warmPaint = Paint().apply { blendMode = BlendMode.Plus }

    /** Builds the meshes of [grid] seen as [look], for drawing at [unit] pixels per world unit from ([originX], [originY]). */
    fun build(grid: SurfaceLight, look: LightLook, unit: Float, originX: Float, originY: Float) {
        val p = FloatArray(3); val s = FloatArray(2)
        val positions = ArrayList<Offset>(grid.corners); val darks = ArrayList<Color>(grid.corners); val warms = ArrayList<Color>(grid.corners)
        for (r in 0..grid.rows) for (c in 0..grid.cols) {
            val i = r * (grid.cols + 1) + c
            grid.point(room, c, r, p)
            if (!room.camera.project(p[0], p[1], p[2], s)) { s[0] = 0f; s[1] = 0f }
            positions += Offset((s[0] - originX) * unit, (s[1] - originY) * unit)
            val lit = 1f - exp(-grid.amount[i] * look.gain)
            darks += Color.Black.copy(alpha = (look.darkness * (1f - look.lift * lit)).coerceIn(0f, 1f))
            warms += Color(grid.red[i], grid.green[i], grid.blue[i], (look.tint * lit).coerceIn(0f, 1f))
        }
        val indices = ArrayList<Int>(grid.cols * grid.rows * 6)
        for (r in 0 until grid.rows) for (c in 0 until grid.cols) {
            val a = r * (grid.cols + 1) + c; val b = a + 1; val d = a + grid.cols + 1; val e = d + 1
            indices += a; indices += b; indices += e; indices += a; indices += e; indices += d
        }
        dark = Vertices(VertexMode.Triangles, positions, positions, darks, indices)
        warm = Vertices(VertexMode.Triangles, positions, positions, warms, indices)
    }

    /** Draws the meshes last built, cut to [clip] (already in this scope's pixels). */
    fun draw(scope: DrawScope, clip: Path) {
        val d = dark ?: return; val w = warm ?: return
        scope.clipPath(clip) {
            // Dst: the vertices' own colours, alpha and all (with no picture to blend them with, Src would draw them opaque).
            drawIntoCanvas { canvas ->
                canvas.drawVertices(d, BlendMode.Dst, darkPaint)
                canvas.drawVertices(w, BlendMode.Dst, warmPaint)
            }
        }
    }
}
