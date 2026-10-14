package br.com.faru.boitata.light

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
 * What the weather has done to a pane, drawn over the glass in [area]: the **fog** as a milky veil in a few shades (merged into runs, so a whole pane is a few hundred rectangles, not thousands), the **ice** as frosted cells
 * and a pattern of crystals that grow in along fern-like stems, and the **drops** as beads with a dark rim and a highlight. Where a bead ran, the fog is already gone, so its streak shows as clear glass.
 * The sill's snow is [drawSillSnow]. At Low quality the veil is drawn at half the rows and the crystals are left out.
 */
public fun DrawScope.drawPaneWeather(pane: PaneWeather, area: Rect, seconds: Float, quality: Quality = Quality.High, fog: Color = Color(0xFFE6EDF0), ice: Color = Color(0xFFDDEBF6)) {
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
        if (quality != Quality.Low && pane.averageFrost() > 0.03f) drawCrystals(pane, area, ice)
        // Beads and runs of water.
        for (d in 0 until pane.drops) {
            val x = area.left + (pane.dropX[d] + 0.5f) * cw; val y = area.top + (pane.dropY[d] + 0.5f) * ch
            val m = pane.dropMass[d]
            val rad = cw * (0.5f + 2.2f * m)
            drawCircle(Color.White.copy(alpha = 0.16f), rad, Offset(x, y))
            drawCircle(Color(0xFF14202A).copy(alpha = 0.4f), rad, Offset(x, y), style = Stroke(1.2f))
            drawCircle(Color.White.copy(alpha = 0.75f), rad * 0.28f, Offset(x - rad * 0.3f, y - rad * 0.3f))
        }
    }
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
            for (sgn in floatArrayOf(-1f, 1f)) drawLine(ice.copy(alpha = alpha * 0.8f), next, Offset(next.x + cos(a + sgn * 0.95f) * tw, next.y + sin(a + sgn * 0.95f) * tw), 1.2f)
            p = next
        }
    }
}

/** Snow piled on the sill under a window, [amount] (0..1) deep, in soft bumps along [sill] (the strip of the sill's top edge, left to right). */
public fun DrawScope.drawSillSnow(sill: Rect, amount: Float, seconds: Float) {
    if (amount < 0.03f) return
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
