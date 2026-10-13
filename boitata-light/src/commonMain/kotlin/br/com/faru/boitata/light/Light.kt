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
fun LightShade(source: LightSource?, night: Boolean, modifier: Modifier = Modifier.fillMaxSize(), origin: Offset = Offset.Zero) {
    val ambient = LocalSceneAmbient.current
    val flash = LocalSceneFlash.current
    Spacer(
        modifier.drawBehind {
            val d = density
            // A flash of lightning lights everything for an instant.
            flash().let { f -> if (f > 0.01f) drawRect(Color(0xFFCFDCFF).copy(alpha = (f * 0.45f).coerceAtMost(0.5f)), blendMode = BlendMode.SrcAtop) }
            // The hour's veil first, then the source's own light is laid over it.
            if (ambient.alpha > 0f) drawRect(ambient, blendMode = BlendMode.SrcAtop)
            if (source == null) return@drawBehind
            if (source.strength <= 0f) {
                drawRect(Color(0x66000000), blendMode = BlendMode.SrcAtop)
                return@drawBehind
            }
            val c = Offset((source.x - origin.x) * d, (source.y - origin.y) * d)
            val r = source.reach * d
            // Away from the light: shade. The darker the night, the deeper.
            drawRect(
                Brush.radialGradient(0f to Color.Transparent, 0.55f to Color(0x00000000), 1f to Color(if (night) 0x99000000 else 0x55000000), center = c, radius = r),
                blendMode = BlendMode.SrcAtop,
            )
            // Near the light: lit, in its colour.
            drawRect(
                Brush.radialGradient(
                    0f to source.color.copy(alpha = 0.34f * source.strength),
                    0.45f to source.color.copy(alpha = 0.10f * source.strength),
                    1f to Color.Transparent,
                    center = c, radius = r * 0.7f,
                ),
                blendMode = BlendMode.SrcAtop,
            )
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
 */
@Composable
fun rememberFlicker(amount: Float, reduced: Boolean = false): Float {
    var value by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    androidx.compose.runtime.LaunchedEffect(amount, reduced) {
        if (reduced || amount <= 0f) { value = 1f; return@LaunchedEffect }
        val random = kotlin.random.Random(7)
        while (true) {
            value = 1f + (random.nextFloat() * 2f - 1f) * (0.05f + 0.1f * amount)
            kotlinx.coroutines.delay(80)
        }
    }
    return value
}

/** The flash of the lightning over the room (0..1), read by [LightShade] and the room's own layers in the draw phase. */
val LocalSceneFlash = compositionLocalOf<() -> Float> { { 0f } }
