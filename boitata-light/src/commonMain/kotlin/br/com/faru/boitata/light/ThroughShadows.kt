package br.com.faru.boitata.light

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.light.Optics
import br.com.faru.boitata.core.light.ShadowMath
import br.com.faru.boitata.core.light.WallCast
import kotlin.math.sqrt

/**
 * A lamp **behind** a translucent surface (a sheet of paper, a shadow theatre's screen), for [drawThroughShadows]: its flame where it is drawn ([flameX], [flameY]), the point on the
 * ground under it ([groundX], [groundY]: its depth, as everywhere in the engine), how strong it is (0..1), and its [size]: how wide the glowing part is (a chimney, a bulb, a flame), in
 * the scope's pixels. A wider light makes wider, softer shadow edges. Positions in the pixels of the scope that draws them; [groundY] in the same units as the casters' depths.
 */
public class ThroughLamp(
    public val flameX: Float, public val flameY: Float, public val groundX: Float, public val groundY: Float,
    public val strength: Float = 1f, public val size: Float = 24f,
)

/**
 * A thing between a lamp and the surface behind it: its outline ([outline], drawn with its origin at ([x], [y]), in the colour and blend it is given), the line of its footprint
 * ([depth]: how far back it stands), and how far it reaches up and down on screen ([top], [bottom]).
 *
 * - [solid]: it stops all the light (a metal plate): where it stands no other shadow adds to its own.
 * - [contact], 0..1: it touches the surface (a plate the paper lies against): its shadow is fully dark where it touches, at its [bottom], and fades slowly toward its [top] to
 *   `1 - contact` of that, its far edge spread wide, as light creeps round through the surface where the two part. 0: it stands free.
 */
public class ThroughCaster(
    public val x: Float, public val y: Float, public val depth: Float, public val top: Float, public val bottom: Float,
    public val solid: Boolean = false, public val contact: Float = 0f,
    public val outline: DrawScope.(Color, BlendMode) -> Unit,
)

/**
 * How dark a shadow on a lit translucent surface shows, 0..1: the share of the surface's brightness there that came through from behind and the thing now stops. What comes
 * through is the lamp's [back] light times what the surface lets pass (`1 - opacity`, see `paperTransmittance`); what lights its face is [front] (the room's light on it, 0..1) times
 * what the surface sends back. So a shadow is strong when the room is dark and the lamp behind is the main light, and faint on heavy paper in a lit room.
 */
public fun Optics.shadowShare(back: Float, front: Float): Float {
    val through = (1f - opacity) * back
    val face = front * (opacity * 0.9f).coerceAtLeast(0.05f)
    return if (through <= 0f) 0f else (through / (through + face)).coerceIn(0f, 0.92f)
}

/**
 * The shadows of the shadow theatre, held between frames: they are drawn into a small picture ([scale] of the screen) and that picture is drawn again until something that decides
 * them changes (a lamp, a thing, the surface), so a frame costs one scaled image. Make one per surface and keep it ([androidx.compose.runtime.remember]).
 */
@Stable
public class ThroughShadows(public val scale: Float = 0.25f) {
    internal val main = Layer()
    // Smaller copies of the picture, halving each time, and the way back up: a shadow is shrunk as far as its blur needs and grown again 2× at a time, which blurs it
    // smoothly (a single big stretch would show its texels as steps).
    internal val levels = Array(LEVELS) { Layer() }
    internal val ups = Array(LEVELS) { Layer() }
    internal var key = 0L
    internal var valid = false
    internal val cast = WallCast()

    internal class Layer {
        var bitmap: ImageBitmap? = null
        private var canvas: Canvas? = null
        private val scope = CanvasDrawScope()

        fun ensure(w: Int, h: Int): Boolean {
            val b = bitmap
            if (b != null && b.width == w && b.height == h) return false
            val n = ImageBitmap(w, h); bitmap = n; canvas = Canvas(n)
            return true
        }

        fun render(density: Float, draw: DrawScope.() -> Unit) {
            val c = canvas ?: return; val b = bitmap ?: return
            scope.draw(Density(density), LayoutDirection.Ltr, c, Size(b.width.toFloat(), b.height.toFloat())) {
                drawRect(Color.Transparent, blendMode = BlendMode.Clear)
                draw()
            }
        }
    }

    internal companion object { const val LEVELS = 6 }
}

/**
 * The shadows that [casters] standing between [lamps] **behind** a translucent surface and that surface throw on it, as a shadow theatre's screen shows them from the front. The
 * surface stands at the depth [surfaceFootY] and fills this scope; its [optics] say how much light comes through, and [front] how lit its face is from the room (0 a dark room),
 * which together say how dark a shadow is ([shadowShare]).
 *
 * - **Where and how big** (`ShadowMath.onWallFromPoint`): a thing's outline lands where it is, enlarged away from the lamp's flame by how much farther the surface is from the lamp
 *   than the thing (at most [maxScale]). A thing against the surface casts its own size; a thing near the lamp a big one.
 * - **How soft**: the penumbra is the lamp's [ThroughLamp.size] times how much farther the surface is than the thing (`t - 1`), as a real light's is; on top of that the surface
 *   itself spreads the light that crosses it by [diffusion] pixels. The edge is a smooth fade that wide, not a step.
 * - **Solid things** erase every other shadow where they stand, then lay their own: no light gets through them, so nothing can be darker.
 * - **Contact**: a thing that touches the surface is darkest where it touches and fades slowly up toward its far edge.
 *
 * Draw it over the surface; [blend] is how the shadows are laid on it ([BlendMode.SrcAtop] keeps them to what the surface has drawn, in its own layer).
 */
public fun DrawScope.drawThroughShadows(
    shadows: ThroughShadows, lamps: List<ThroughLamp>, casters: List<ThroughCaster>, surfaceFootY: Float, optics: Optics = Optics.Paper(),
    front: Float = 0.3f, diffusion: Float = 4f, quality: Quality = Quality.High, maxScale: Float = 1.6f, blend: BlendMode = BlendMode.SrcAtop,
) {
    var any = false
    for (l in lamps) if (castsThrough(l, surfaceFootY)) any = true
    if (!any || casters.isEmpty()) return
    val k = shadows.scale
    val w = (size.width * k).toInt().coerceAtLeast(1); val h = (size.height * k).toInt().coerceAtLeast(1)
    var key = mix(quality.ordinal.toLong(), w.toLong() * 65_537L + h)
    key = mix(key, optics.opacity); key = mix(key, front); key = mix(key, diffusion); key = mix(key, surfaceFootY); key = mix(key, maxScale)
    for (l in lamps) { key = mix(key, l.flameX); key = mix(key, l.flameY); key = mix(key, l.groundX); key = mix(key, l.groundY); key = mix(key, l.strength); key = mix(key, l.size) }
    for (c in casters) { key = mix(key, c.x); key = mix(key, c.y); key = mix(key, c.depth); key = mix(key, c.top); key = mix(key, c.bottom); key = mix(key, c.contact) }
    if (shadows.main.ensure(w, h)) shadows.valid = false
    for (i in 0 until ThroughShadows.LEVELS) {
        val f = 1 shl i
        shadows.levels[i].ensure((w + f - 1) / f, (h + f - 1) / f)
        shadows.ups[i].ensure((w + f - 1) / f, (h + f - 1) / f)
    }
    if (!shadows.valid || shadows.key != key) {
        val d = density
        shadows.main.render(d) {
            for (lamp in lamps) {
                if (!castsThrough(lamp, surfaceFootY)) continue
                val alpha = optics.shadowShare(lamp.strength, front)
                // Free things first, then the solid ones over them (each erasing what is under it first).
                for (c in casters) if (!c.solid) castOne(shadows, lamp, c, surfaceFootY, maxScale, alpha, diffusion, quality, k, d)
                for (c in casters) if (c.solid) castOne(shadows, lamp, c, surfaceFootY, maxScale, alpha, diffusion, quality, k, d)
            }
        }
        shadows.key = key; shadows.valid = true
    }
    shadows.main.bitmap?.let { drawImage(it, dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)), filterQuality = FilterQuality.Medium, blendMode = blend) }
}

private fun castsThrough(l: ThroughLamp, surfaceFootY: Float) = l.strength >= 0.05f && l.groundY < surfaceFootY - 8f

/**
 * One caster's shadow from one lamp, drawn into the main picture (this scope, in cache pixels). The outline is drawn at the picture's own size, shrunk by halves as far as its blur
 * needs and grown back by doubles, each step sampled four times half a texel apart (a dual Kawase blur): the edge fades over the blur's width, smoothly, with no texels showing.
 */
private fun DrawScope.castOne(
    shadows: ThroughShadows, lamp: ThroughLamp, c: ThroughCaster, surfaceFootY: Float, maxScale: Float, alpha: Float, diffusion: Float, quality: Quality, k: Float, d: Float,
) {
    if (!ShadowMath.onWallFromPoint(lamp.groundX, lamp.groundY, 1f, c.x, c.depth, surfaceFootY, 1f, shadows.cast, maxScale)) return
    val s = shadows.cast.scale
    // The penumbra of a light of this size: the surface's distance from the thing over the thing's from the light, times the light's width (unclamped, as the light really is).
    val t = (surfaceFootY - lamp.groundY) / (c.depth - lamp.groundY)
    val penumbra = lamp.size * (t - 1f).coerceAtLeast(0f)
    // A thing touching the surface: its far edge leans away from it, so that edge spreads too.
    val lean = if (c.contact > 0f) 0.18f * (c.bottom - c.top) * s * c.contact else 0f
    val radius = sqrt(diffusion * diffusion + (0.5f * penumbra) * (0.5f * penumbra) + lean * lean)
    // How many halvings the blur's radius (in cache pixels) asks for: each down-and-up pair blurs by about its texel. Low quality stops one sooner.
    var level = 0
    while (level < ThroughShadows.LEVELS - 1 && (1 shl (level + 1)) <= radius * k / 1.1f) level++
    if (quality == Quality.Low && level > 0) level--
    val pivot = Offset(lamp.flameX, lamp.flameY)
    val base = shadows.levels[0]
    base.render(d) {
        withTransform({ scale(k, k, Offset.Zero); scale(s, s, pivot) }) {
            translate(c.x, c.y) { c.outline(this, Color.Black, BlendMode.SrcOver) }
            if (c.contact > 0f) {
                // Darkest where it touches (its bottom), fading slowly toward its top.
                val e = c.contact
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 1f - e), 0.45f to Color.Black.copy(alpha = 1f - 0.6f * e), 0.8f to Color.Black.copy(alpha = 1f - 0.2f * e), 1f to Color.Black,
                        startY = c.top, endY = c.bottom,
                    ),
                    topLeft = Offset(-1e5f, c.top - 1e4f), size = Size(2e5f, 2e4f + c.bottom - c.top), blendMode = BlendMode.DstIn,
                )
            }
        }
    }
    var img = base.bitmap ?: return
    for (i in 1..level) {
        val src = img
        shadows.levels[i].render(d) { taps(src, (src.width + 1) / 2, (src.height + 1) / 2, 0.5f) }
        img = shadows.levels[i].bitmap ?: return
    }
    for (i in level - 1 downTo 0) {
        val src = img
        shadows.ups[i].render(d) { taps(src, src.width * 2, src.height * 2, 1f) }
        img = shadows.ups[i].bitmap ?: return
    }
    val full = IntSize(img.width, img.height)
    if (c.solid) drawImage(img, dstSize = full, filterQuality = FilterQuality.Medium, blendMode = BlendMode.DstOut)
    drawImage(img, dstSize = full, alpha = alpha, filterQuality = FilterQuality.Medium)
}

/** [src] drawn at [w] × [h], four times a quarter each, shifted ±[o] pixels: a 2× step of the blur, as dark inside as one copy. */
private fun DrawScope.taps(src: ImageBitmap, w: Int, h: Int, o: Float) {
    val to = IntSize(w, h)
    for (j in 0 until 4) {
        val ox = if (j % 2 == 0) -o else o; val oy = if (j < 2) -o else o
        translate(ox, oy) { drawImage(src, dstSize = to, alpha = 0.25f, filterQuality = FilterQuality.Medium, blendMode = BlendMode.Plus) }
    }
}

private fun mix(k: Long, f: Float): Long = k * 31L + f.toBits()
private fun mix(k: Long, l: Long): Long = k * 31L + l
