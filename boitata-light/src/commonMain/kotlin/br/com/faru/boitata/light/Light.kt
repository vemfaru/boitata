package br.com.faru.boitata.light

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp

/** Where the room's light comes from, in world units, and how it looks. */
@Immutable
data class LightSource(
    val x: Float,
    val y: Float,
    val color: Color,
    /** How hard it lights what is near it (0 to 1). */
    val strength: Float,
    /** How far it reaches, in world units. */
    val reach: Float,
    val flicker: Float = 0f,
    /** Where the light is straight above the desk top, for casting shadows (the foot of the lamp). */
    val groundX: Float = x,
    val groundY: Float = y,
)


/** The ambient light of the hour and the room, as a veil over everything standing in it; provide it over the scene, read by [LightShade]. */
val LocalSceneAmbient = compositionLocalOf { Color.Transparent }

/**
 * Shades what is drawn under it (the layers of the machine, in an offscreen layer) for the light: the side towards
 * the light is lit in its colour, the side away from it falls into shade. Draw last inside the layer, [BlendMode.SrcAtop].
 */
@Composable
fun LightShade(source: LightSource?, night: Boolean, modifier: Modifier = Modifier.fillMaxSize(), origin: Offset = Offset.Zero) =
    LightShade({ source }, night, modifier, origin)

/**
 * [LightShade] with the light read in the draw phase (from [rememberEasedLightState], say): a moving light redraws the shading without
 * recomposing, and its gradients are built again only when the light has changed, not on every frame of the scene.
 */
@Composable
fun LightShade(source: () -> LightSource?, night: Boolean, modifier: Modifier = Modifier.fillMaxSize(), origin: Offset = Offset.Zero) {
    val ambient = LocalSceneAmbient.current
    val flash = LocalSceneFlash.current
    Spacer(
        modifier.drawWithCache {
            val d = density
            // Read here, the light invalidates only this cache: the brushes below are made once per change of the light.
            val light = source()
            val c = light?.let { Offset((it.x - origin.x) * d, (it.y - origin.y) * d) }
            val r = (light?.reach ?: 0f) * d
            val shade = if (light == null || light.strength <= 0f || r <= 0f) null
            else Brush.radialGradient(0f to Color.Transparent, 0.55f to Color(0x00000000), 1f to Color(if (night) 0x99000000 else 0x55000000), center = c!!, radius = r)
            val lit = if (light == null || light.strength <= 0f || r <= 0f) null
            else Brush.radialGradient(
                0f to light.color.copy(alpha = 0.34f * light.strength),
                0.45f to light.color.copy(alpha = 0.10f * light.strength),
                1f to Color.Transparent,
                center = c!!, radius = r * 0.7f,
            )
            onDrawBehind {
                // A flash of lightning lights everything for an instant.
                flash().let { f -> if (f > 0.01f) drawRect(Color(0xFFCFDCFF).copy(alpha = (f * 0.45f).coerceAtMost(0.5f)), blendMode = BlendMode.SrcAtop) }
                // The hour's veil first, then the source's own light is laid over it.
                if (ambient.alpha > 0f) drawRect(ambient, blendMode = BlendMode.SrcAtop)
                if (light == null) return@onDrawBehind
                if (light.strength <= 0f) {
                    drawRect(Color(0x66000000), blendMode = BlendMode.SrcAtop)
                    return@onDrawBehind
                }
                // Away from the light: shade, the darker the night, the deeper; near the light: lit, in its colour.
                shade?.let { drawRect(it, blendMode = BlendMode.SrcAtop) }
                lit?.let { drawRect(it, blendMode = BlendMode.SrcAtop) }
            }
        },
    )
}


/**
 * A soft shadow under a flat thing lying on another (a sheet on the platen): its rectangle repeated a few times, offset away from
 * the light and each a little bigger and fainter, so the edge is soft and the shadow falls the way the light does.
 */
fun Modifier.softDropShadow(light: LightSource?, groundX: Float, groundY: Float, lift: Float = 6f): Modifier = drawBehind {
    val d = density
    var ux = 0f; var uy = 1f; var a = 0.2f
    if (light != null && light.strength > 0f) {
        val dx = groundX - light.groundX
        val dy = (groundY - light.groundY) * 0.5f
        val dist = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
        ux = dx / dist; uy = dy / dist
        a = 0.14f + 0.14f * light.strength
    }
    val ox = ux * lift * d; val oy = (0.4f + 0.6f * uy).coerceIn(0.2f, 1f) * lift * d
    for (i in 0..3) {
        val grow = i * 2.2f * d
        drawRect(Color.Black.copy(alpha = a * (1f - i * 0.22f) * 0.55f), topLeft = Offset(ox - grow, oy - grow), size = androidx.compose.ui.geometry.Size(size.width + 2 * grow, size.height + 2 * grow))
    }
}


/**
 * A flicker multiplier near 1 (about 0.95 to 1.05 for a steady lamp, more for a dying bulb), new every ~80 ms; exactly 1 when the
 * light does not flicker or under reduced motion.
 *
 * It follows the scene's clock (no loop of its own) and changes only every 80 ms, so reading it recomposes about twelve times a second, not every
 * frame. Where the flicker only feeds drawing, use [rememberFlickerState] and read it in the draw block: no recomposition at all.
 */
@Composable
fun rememberFlicker(amount: Float, reduced: Boolean = false): Float = rememberFlickerState(amount, reduced).value

/** [rememberFlicker] as a [State]: read `.value` in a draw block, so the flicker costs a redraw twelve times a second and no recomposition. */
@Composable
fun rememberFlickerState(amount: Float, reduced: Boolean = false): androidx.compose.runtime.State<Float> {
    val clock = br.com.faru.boitata.core.clock.LocalSceneClock.current ?: br.com.faru.boitata.core.clock.rememberSceneClock()
    return androidx.compose.runtime.remember(amount, reduced, clock) {
        val noise = br.com.faru.boitata.core.FastNoise(7L)
        androidx.compose.runtime.derivedStateOf {
            if (reduced || clock.reducedMotion || amount <= 0f) 1f
            else {
                val step = kotlin.math.floor(clock.seconds() / FLICKER_STEP).toInt()
                1f + (noise.at(step, 0) * 2f - 1f) * (0.05f + 0.1f * amount)
            }
        }
    }
}

private const val FLICKER_STEP = 0.08f

/** The flash of the lightning over the room (0..1), read by [LightShade] and the room's own layers in the draw phase. */
val LocalSceneFlash = compositionLocalOf<() -> Float> { { 0f } }
