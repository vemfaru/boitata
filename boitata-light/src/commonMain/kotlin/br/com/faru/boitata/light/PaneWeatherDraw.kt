package br.com.faru.boitata.light

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import br.com.faru.boitata.core.FastNoise
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.surface.PaneWeather
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hand-drawn weather for a pane, in place of the drawn-by-code crystals, beads and snow: [ferns] (frost growing from a corner, an edge, the top and a free flake, in that order), [beads] of water (small to large), a fine
 * [veil] of crystals and the [sillSnow] heap. The pictures are solid ink with the lines cut out, and are painted in the weather's colour.
 */
public class PaneArt(public val ferns: List<ImageBitmap>, public val beads: List<ImageBitmap>, public val veil: ImageBitmap? = null, public val sillSnow: ImageBitmap? = null)

/** Draws [image] in the box ([x], [y], [w], [h]) painted [tint]. */
private fun DrawScope.drawTinted(image: ImageBitmap, x: Float, y: Float, w: Float, h: Float, tint: Color) {
    withTransform({ translate(x, y); scale(w / image.width, h / image.height, Offset.Zero) }) {
        drawImage(image, filterQuality = FilterQuality.Medium, colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn))
    }
}

/**
 * What the weather has done to a pane, drawn over the glass in [area]: the **fog** as a milky veil in a few shades (merged into runs, so a whole pane is a few hundred rectangles, not thousands), the **ice** as frosted cells
 * and a pattern of crystals that grow in along fern-like stems, and the **drops** as beads with a dark rim and a highlight. Where a bead ran, the fog is already gone, so its streak shows as clear glass.
 * The sill's snow is [drawSillSnow]. At Low quality the veil is drawn at half the rows and the crystals are left out.
 */
public fun DrawScope.drawPaneWeather(pane: PaneWeather, area: Rect, seconds: Float, quality: Quality = Quality.High, fog: Color = Color(0xFFE6EDF0), ice: Color = Color(0xFFDDEBF6), art: PaneArt? = null) {
    val cw = area.width / pane.cols; val ch = area.height / pane.rows
    val rowStep = if (quality == Quality.Low) 2 else 1
    clipRect(area.left, area.top, area.right, area.bottom) {
        // The veil: for each row, runs of cells with the same shade.
        var r = 0
        while (r < pane.rows) {
            var c = 0
            while (c < pane.cols) {
                val q = (pane.fog[r * pane.cols + c] * 5f).toInt().coerceAtMost(5)
                var e = c + 1
                while (e < pane.cols && (pane.fog[r * pane.cols + e] * 5f).toInt().coerceAtMost(5) == q) e++
                if (q > 0) drawRect(fog.copy(alpha = q / 5f * 0.62f), Offset(area.left + c * cw, area.top + r * ch), Size((e - c) * cw + 0.5f, ch * rowStep + 0.5f))
                c = e
            }
            r += rowStep
        }
        // The ice: frosted where the level has passed the pattern, whiter where it is thicker.
        r = 0
        while (r < pane.rows) {
            var c = 0
            while (c < pane.cols) {
                if (!pane.iced(c, r)) { c++; continue }
                var e = c + 1
                while (e < pane.cols && pane.iced(e, r)) e++
                val level = pane.frost[r * pane.cols + c]
                drawRect(ice.copy(alpha = (0.3f + 0.5f * level).coerceAtMost(0.8f)), Offset(area.left + c * cw, area.top + r * ch), Size((e - c) * cw + 0.5f, ch * rowStep + 0.5f))
                c = e
            }
            r += rowStep
        }
        if (quality != Quality.Low && pane.averageFrost() > 0.03f) { if (art != null && art.ferns.isNotEmpty()) drawFerns(pane, area, ice, art) else drawCrystals(pane, area, ice) }
        // Beads and runs of water.
        for (d in 0 until pane.drops) {
            val x = area.left + (pane.dropX[d] + 0.5f) * cw; val y = area.top + (pane.dropY[d] + 0.5f) * ch
            val m = pane.dropMass[d]
            val rad = cw * (0.5f + 2.2f * m)
            if (art != null && art.beads.isNotEmpty()) {
                val bead = art.beads[(m * art.beads.size).toInt().coerceIn(0, art.beads.size - 1)]
                drawTinted(bead, x - rad, y - rad * bead.height / bead.width, rad * 2f, rad * 2f * bead.height / bead.width, Color(0xFFCFE0EC).copy(alpha = 0.85f))
                continue
            }
            drawCircle(Color.White.copy(alpha = 0.16f), rad, Offset(x, y))
            drawCircle(Color(0xFF14202A).copy(alpha = 0.4f), rad, Offset(x, y), style = Stroke(1.2f))
            drawCircle(Color.White.copy(alpha = 0.75f), rad * 0.28f, Offset(x - rad * 0.3f, y - rad * 0.3f))
        }
    }
}

/** The hand-drawn frost: ferns that grow in from the corner, the edges and the top as the ice thickens there (each reaches farther the thicker the ice at its root), over a fine veil. */
private fun DrawScope.drawFerns(pane: PaneWeather, area: Rect, ice: Color, art: PaneArt) {
    val cw = area.width / pane.cols; val ch = area.height / pane.rows
    fun levelAt(x: Float, y: Float): Float = pane.frost[((y - area.top) / ch).toInt().coerceIn(0, pane.rows - 1) * pane.cols + ((x - area.left) / cw).toInt().coerceIn(0, pane.cols - 1)]
    art.veil?.let { v ->
        val tile = area.width * 0.6f
        var yy = area.top
        while (yy < area.bottom) { var xx = area.left; while (xx < area.right) { drawTinted(v, xx, yy, tile, tile, ice.copy(alpha = (pane.averageFrost() * 0.7f).coerceAtMost(0.5f))); xx += tile }; yy += tile }
    }
    val f = art.ferns
    fun fern(i: Int, rootX: Float, rootY: Float, ax: Float, ay: Float, size: Float, flip: Boolean = false) {
        val image = f[i % f.size]
        val level = levelAt(rootX, rootY)
        val grow = ((level - 0.08f) * 2.2f).coerceIn(0f, 1f)
        if (grow <= 0.01f) return
        val sz = size * (0.35f + 0.65f * grow)
        val x = rootX - ax * sz; val y = rootY - ay * sz
        val a = (0.4f + 0.55f * level).coerceAtMost(0.95f)
        // A cool shadow just under each stem, so the white crystals read against the milky pane.
        withTransform({ if (flip) scale(-1f, 1f, Offset(rootX, rootY)) }) { drawTinted(image, x + 2f, y + 2.5f, sz, sz, Color(0xFF4F6A82).copy(alpha = a * 0.7f)); drawTinted(image, x, y, sz, sz, Color.White.copy(alpha = a)) }
    }
    val big = area.width * 0.8f
    fern(0, area.left, area.bottom, 0f, 1f, big)                                 // from the bottom-left corner
    fern(1, area.right, area.top + area.height * 0.5f, 1f, 0.5f, big * 0.8f)    // from the right edge
    fern(2, area.left + area.width * 0.35f, area.top, 0.5f, 0f, big * 0.7f)       // from the top
    fern(2, area.left + area.width * 0.8f, area.top, 0.5f, 0f, big * 0.55f, flip = true)
    fern(3, area.left + area.width * 0.55f, area.top + area.height * 0.55f, 0.5f, 0.5f, big * 0.45f)   // a free flake
    fern(1, area.left, area.top + area.height * 0.25f, 0f, 0.5f, big * 0.5f, flip = true)
}

/** Crystals: stems that grow in from the edges of the pane toward its middle, with twigs at an angle, drawn as far as the ice has got along them. */
private fun DrawScope.drawCrystals(pane: PaneWeather, area: Rect, ice: Color) {
    val noise = FastNoise(17L)
    val stems = 34
    val cw = area.width / pane.cols; val ch = area.height / pane.rows
    for (i in 0 until stems) {
        // Start on one of the four edges, pointing in toward the middle with a wobble.
        val side = i % 4; val u = noise.at(i, 0)
        val start = when (side) { 0 -> Offset(area.left + area.width * u, area.top); 1 -> Offset(area.left + area.width * u, area.bottom); 2 -> Offset(area.left, area.top + area.height * u); else -> Offset(area.right, area.top + area.height * u) }
        val toMiddle = Offset(area.center.x - start.x, area.center.y - start.y)
        val len = kotlin.math.hypot(toMiddle.x, toMiddle.y).coerceAtLeast(1f)
        val base = kotlin.math.atan2(toMiddle.y, toMiddle.x) + (noise.at(i, 1) - 0.5f) * 0.9f
        var p = start
        val seg = area.width * (0.045f + 0.03f * noise.at(i, 2))
        for (k in 0 until 9) {
            val a = base + 0.18f * sin(k * 1.3f + i)
            val next = Offset(p.x + cos(a) * seg, p.y + sin(a) * seg)
            val cx = ((next.x - area.left) / cw).toInt().coerceIn(0, pane.cols - 1); val cy = ((next.y - area.top) / ch).toInt().coerceIn(0, pane.rows - 1)
            val level = pane.frost[cy * pane.cols + cx]
            // The ice reaches this far along the stem only if it is thick enough here.
            if (level < 0.12f + 0.06f * k) break
            val alpha = (0.35f + 0.6f * level).coerceAtMost(0.95f)
            drawLine(ice.copy(alpha = alpha), p, next, 1.6f)
            // Twigs, shorter toward the tip.
            val tw = seg * (0.65f - 0.05f * k)
            val twig = ice.copy(alpha = alpha * 0.8f)
            drawLine(twig, next, Offset(next.x + cos(a - 0.95f) * tw, next.y + sin(a - 0.95f) * tw), 1.2f)
            drawLine(twig, next, Offset(next.x + cos(a + 0.95f) * tw, next.y + sin(a + 0.95f) * tw), 1.2f)
            p = next
        }
    }
}

/** Snow piled on the sill under a window, [amount] (0..1) deep, in soft bumps along [sill] (the strip of the sill's top edge, left to right). */
public fun DrawScope.drawSillSnow(sill: Rect, amount: Float, seconds: Float, art: PaneArt? = null) {
    if (amount < 0.03f) return
    if (art?.sillSnow != null) {
        val heap = art.sillSnow
        val depth = sill.height * (0.6f + 4f * amount)
        val tile = depth * heap.width / heap.height
        var x = sill.left
        clipRect(sill.left, sill.top - depth, sill.right, sill.top + 3f) { while (x < sill.right) { drawTinted(heap, x, sill.top + 3f - depth, tile, depth, Color(0xFFF2F7FB)); x += tile } }
        return
    }
    val bumps = 9
    val depth = sill.height * (0.2f + 3.2f * amount)
    val path = Path().apply {
        moveTo(sill.left, sill.top + 2f)
        for (i in 0..bumps * 4) {
            val u = i / (bumps * 4f)
            val bump = (0.6f + 0.4f * sin(u * bumps * PI.toFloat() * 2f + 1f)) * (0.7f + 0.3f * sin(u * 5f))
            lineTo(sill.left + u * sill.width, sill.top - depth * bump)
        }
        lineTo(sill.right, sill.top + 2f); close()
    }
    drawPath(path, Color(0xFFF2F7FB))
    drawPath(path, Color(0xFF9FB4C4).copy(alpha = 0.5f), style = Stroke(1.3f))
}
