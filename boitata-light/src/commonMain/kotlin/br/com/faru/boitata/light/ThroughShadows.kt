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
import androidx.compose.ui.graphics.Paint
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
import kotlin.math.cos
import kotlin.math.sin

/**
 * A lamp **behind** a translucent surface (a sheet of paper, a shadow theatre's screen), for [drawThroughShadows]: its flame where it is drawn ([flameX], [flameY]), the point on the
 * ground under it ([groundX], [groundY]: its depth, as everywhere in the engine), how strong it is (0..1), and its [size] (0 a point, 1 a lamp's chimney): a bigger light makes softer
 * shadows. All in the pixels of the scope that draws them.
 */
public class ThroughLamp(
    public val flameX: Float, public val flameY: Float, public val groundX: Float, public val groundY: Float,
    public val strength: Float = 1f, public val size: Float = 0.5f,
)

/**
 * A thing between a lamp and the surface behind it: its outline ([outline], drawn with its origin at ([x], [y]), in the colour and blend it is given), the line of its footprint
 * ([depth]: how far back it stands), and how far it reaches up and down on screen ([top], [bottom]).
 *
 * - [solid]: it stops all the light (a metal plate): where it stands no other shadow adds to its own.
 * - [contact], 0..1: it touches the surface (a plate the paper lies against): its shadow is fully dark where it touches, at its [bottom], and fades toward its [top] to `1 - contact`
 *   of that, with a soft edge there, as light creeps round through the surface where the two part. 0: it stands free.
 */
public class ThroughCaster(
    public val x: Float, public val y: Float, public val depth: Float, public val top: Float, public val bottom: Float,
    public val solid: Boolean = false, public val contact: Float = 0f,
    public val outline: DrawScope.(Color, BlendMode) -> Unit,
)

/**
 * How bright what comes through a surface of these [Optics] shows, against plain paper's (`Optics.Paper`): what passes (`1 - opacity`), the less the more it scatters
 * (`/ (1 + 0.8 scatter)`), the same rule the light on a translucent wall follows (`drawWallLight`). 1 for plain paper, more for thin paper, less for heavy.
 */
public fun Optics.throughShare(): Float = through(this) / through(Optics.Paper())

private fun through(o: Optics): Float = (1f - o.opacity) / (1f + 0.8f * o.scatter)

/**
 * The shadows of the shadow theatre, held between frames: they are drawn into a small picture ([scale] of the screen: a quarter keeps a leaf readable and soft) and that picture is
 * drawn again until something that decides them changes (a lamp, a thing, the surface), so a frame costs one scaled image. Make one per surface and keep it ([androidx.compose.runtime.remember]).
 */
@Stable
public class ThroughShadows(public val scale: Float = 0.25f) {
    internal var bitmap: ImageBitmap? = null
    private var canvas: Canvas? = null
    private val scope = CanvasDrawScope()
    internal var key = 0L
    internal var valid = false
    internal val cast = WallCast()
    internal val layerPaint = Paint()

    internal fun ensure(w: Int, h: Int) {
        val b = bitmap
        if (b == null || b.width != w || b.height != h) { val n = ImageBitmap(w, h); bitmap = n; canvas = Canvas(n); valid = false }
    }

    internal fun render(density: Float, w: Int, h: Int, draw: DrawScope.() -> Unit) {
        val c = canvas ?: return
        scope.draw(Density(density), LayoutDirection.Ltr, c, Size(w.toFloat(), h.toFloat())) {
            drawRect(Color.Transparent, blendMode = BlendMode.Clear)
            draw()
        }
    }
}

/**
 * The shadows that [casters] standing between [lamps] **behind** a translucent surface and that surface throw on it, as a shadow theatre's screen shows them from the front. The
 * surface stands at the depth [surfaceFootY] and fills this scope; its [optics] say how much light comes through and so how dark a shadow on it is ([darkness] for plain paper).
 *
 * - **Where and how big** (`ShadowMath.onWallFromPoint`): a thing's outline lands where it is, enlarged away from the lamp's flame by how much farther the surface is from the lamp
 *   than the thing (at most [maxScale]). A thing against the surface casts its own size; a thing near the lamp a big one.
 * - **How soft**: the penumbra grows with the thing's distance from the surface and with the lamp's size (several shifted copies of the outline, more at higher [quality]).
 * - **Solid things** erase every other shadow where they stand, then lay their own: no light gets through them, so nothing can be darker.
 * - **Contact**: a thing that touches the surface is darkest where it touches and fades up toward its far edge.
 *
 * Draw it over the surface; [blend] is how the shadows are laid on it ([BlendMode.SrcAtop] keeps them to what the surface has drawn, in its own layer).
 */
public fun DrawScope.drawThroughShadows(
    shadows: ThroughShadows, lamps: List<ThroughLamp>, casters: List<ThroughCaster>, surfaceFootY: Float, optics: Optics = Optics.Paper(),
    darkness: Float = 0.55f, quality: Quality = Quality.High, maxScale: Float = 1.6f, blend: BlendMode = BlendMode.SrcAtop,
) {
    val behind = lamps.filter { it.strength >= 0.1f && it.groundY < surfaceFootY - 8f }
    if (behind.isEmpty() || casters.isEmpty()) return
    val dark = (darkness * optics.throughShare()).coerceIn(0.05f, 0.9f)
    val k = shadows.scale
    val w = (size.width * k).toInt().coerceAtLeast(1); val h = (size.height * k).toInt().coerceAtLeast(1)
    var key = mix(quality.ordinal.toLong(), w.toLong() * 65_537L + h)
    key = mix(key, dark); key = mix(key, surfaceFootY); key = mix(key, maxScale)
    for (l in behind) { key = mix(key, l.flameX); key = mix(key, l.flameY); key = mix(key, l.groundX); key = mix(key, l.groundY); key = mix(key, l.strength); key = mix(key, l.size) }
    for (c in casters) { key = mix(key, c.x); key = mix(key, c.y); key = mix(key, c.depth); key = mix(key, c.top); key = mix(key, c.bottom); key = mix(key, c.contact) }
    shadows.ensure(w, h)
    if (!shadows.valid || shadows.key != key) {
        shadows.render(density, w, h) {
            withTransform({ scale(k, k, Offset.Zero) }) {
                for (lamp in behind) {
                    val alpha = dark * (lamp.strength / 0.75f).coerceIn(0.3f, 1f)
                    // Free things first, then the solid ones over them (each erasing what is under it first).
                    for (c in casters) if (!c.solid) castOne(shadows, lamp, c, surfaceFootY, maxScale, alpha, quality)
                    for (c in casters) if (c.solid) castOne(shadows, lamp, c, surfaceFootY, maxScale, alpha, quality)
                }
            }
        }
        shadows.key = key; shadows.valid = true
    }
    shadows.bitmap?.let { drawImage(it, dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)), filterQuality = FilterQuality.Medium, blendMode = blend) }
}

/** One caster's shadow from one lamp, in the full-size coordinates (the picture's own scale is applied around it). */
private fun DrawScope.castOne(shadows: ThroughShadows, lamp: ThroughLamp, c: ThroughCaster, surfaceFootY: Float, maxScale: Float, alpha: Float, quality: Quality) {
    if (!ShadowMath.onWallFromPoint(lamp.groundX, lamp.groundY, 1f, c.x, c.depth, surfaceFootY, 1f, shadows.cast, maxScale)) return
    val s = shadows.cast.scale
    val pivot = Offset(lamp.flameX, lamp.flameY)
    // The penumbra: wider the farther the thing is from the surface (s above 1) and the bigger the lamp.
    val spread = (c.bottom - c.top).coerceAtLeast(1f) * (0.012f + 0.05f * (s - 1f)) * (0.4f + lamp.size)
    val copies = when (quality) { Quality.High -> 6; Quality.Medium -> 3; Quality.Low -> 1 }
    fun outlineAt(dx: Float, dy: Float, colour: Color, mode: BlendMode) {
        withTransform({ scale(s, s, pivot) }) { translate(c.x + dx, c.y + dy) { c.outline(this, colour, mode) } }
    }
    fun soft(colour: Color, mode: BlendMode) {
        if (copies == 1 || spread < 0.5f) { outlineAt(0f, 0f, colour, mode); return }
        val each = colour.copy(alpha = (colour.alpha * 1.6f / copies).coerceAtMost(1f))
        for (i in 0 until copies) {
            val a = i * 6.2831855f / copies
            outlineAt(cos(a) * spread, sin(a) * spread, each, mode)
        }
    }
    if (c.solid) outlineAt(0f, 0f, Color.Black, BlendMode.DstOut)
    if (c.contact <= 0f) { soft(Color.Black.copy(alpha = alpha), BlendMode.SrcOver); return }
    // Touching the surface: dark where it touches (its bottom), fading toward its top, in a layer of its own so the fade is cut to its outline.
    val bounds = androidx.compose.ui.geometry.Rect(0f, 0f, size.width / shadows.scale, size.height / shadows.scale)
    drawContext.canvas.saveLayer(bounds, shadows.layerPaint)
    soft(Color.Black.copy(alpha = alpha), BlendMode.SrcOver)
    val top = lamp.flameY + s * (c.top - lamp.flameY)
    val bottom = lamp.flameY + s * (c.bottom - lamp.flameY)
    drawRect(
        Brush.verticalGradient(0f to Color.Black.copy(alpha = 1f - c.contact), 0.35f to Color.Black.copy(alpha = 1f - 0.6f * c.contact), 1f to Color.Black, startY = top, endY = bottom),
        size = Size(bounds.width, bounds.height), blendMode = BlendMode.DstIn,
    )
    drawContext.canvas.restore()
}

private fun mix(k: Long, f: Float): Long = k * 31L + f.toBits()
private fun mix(k: Long, l: Long): Long = k * 31L + l
