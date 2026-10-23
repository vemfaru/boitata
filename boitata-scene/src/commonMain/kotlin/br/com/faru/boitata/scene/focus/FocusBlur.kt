package br.com.faru.boitata.scene.focus

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.faru.boitata.core.clock.LocalSceneClock
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.clock.rememberSceneQuality
import br.com.faru.boitata.core.draw.blurSupported
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * How strongly what is not in focus is pushed back: blurred by [radius] and darkened by [darken] (0..1 of the wash colour). An app keeps its own strengths (a light one while a
 * wheel is open, a full one while one thing is inspected); the engine only eases between them.
 */
@Immutable
public data class FocusStrength(val radius: Dp, val darken: Float)

/**
 * How a focus moves: the blur comes in over [inMillis] and goes over [outMillis] (out is quicker than in), a change of strength (light to full) eases over [changeMillis], and
 * when the focus moves from one thing to another the one leaving and the one arriving cross-fade over [crossfadeMillis]. [reduced] makes every change land at once; by
 * default it follows the scene clock (the system's reduce motion).
 */
@Immutable
public data class FocusMotion(
    val inMillis: Int = 200,
    val outMillis: Int = 160,
    val changeMillis: Int = 250,
    val crossfadeMillis: Int = 120,
    val reduced: Boolean? = null,
)

/**
 * The focus blur on a layer: everything this layer draws is blurred by [strength]'s radius and washed with [wash] at its darkening (only where it draws something), eased in and out by [motion]; `null` is no
 * focus. Put it on the layers that are **not** in focus (a scene's background, the things behind and in front of the one in focus), never on the one in focus.
 *
 * Everything animated is read in the draw phase, so a change of focus recomposes nothing below this. Where a blur cannot be drawn (Android before 12, see [blurSupported]), or the
 * scene's quality governor has stepped down to [Quality.Low] because frames run long, the blur is dropped and only the darkening stays; [blurEnabled] false does the same by hand. [edges] is how the blur treats the layer's edges: [TileMode.Decal] for a thing on its own (it fades out softly), [TileMode.Clamp] for a layer that fills the view (so its edges do not fade to nothing).
 */
public fun Modifier.focusBlur(
    strength: FocusStrength?, motion: FocusMotion = FocusMotion(), wash: Color = Color.Black, blurEnabled: Boolean = true, edges: TileMode = TileMode.Decal,
): Modifier = focusBlurLayer(strength, motion, wash, blurEnabled, startFocused = false, edges = edges)

internal fun Modifier.focusBlurLayer(strength: FocusStrength?, motion: FocusMotion, wash: Color, blurEnabled: Boolean, startFocused: Boolean, edges: TileMode = TileMode.Decal): Modifier = composed {
    val clock = LocalSceneClock.current
    val reduced = motion.reduced ?: (clock?.reducedMotion ?: false)
    val quality = rememberSceneQuality()
    val supported = remember { blurSupported() }
    val amount = remember { Animatable(if (startFocused && strength != null) 1f else 0f) }
    val radius = remember { Animatable(strength?.radius?.value ?: 0f) }
    val darken = remember { Animatable(strength?.darken ?: 0f) }
    LaunchedEffect(strength, reduced) {
        if (strength == null) {
            if (reduced) amount.snapTo(0f) else amount.animateTo(0f, tween(motion.outMillis, easing = FastOutLinearInEasing))
            return@LaunchedEffect
        }
        coroutineScope {
            // From no focus the strength is simply taken (nothing shows it yet); from another strength it eases there.
            if (reduced || amount.value <= 0f) { radius.snapTo(strength.radius.value); darken.snapTo(strength.darken) }
            else {
                launch { radius.animateTo(strength.radius.value, tween(motion.changeMillis)) }
                launch { darken.animateTo(strength.darken, tween(motion.changeMillis)) }
            }
            if (reduced) amount.snapTo(1f) else amount.animateTo(1f, tween(motion.inMillis, easing = LinearOutSlowInEasing))
        }
    }
    this
        .graphicsLayer {
            val a = amount.value
            val r = radius.value.dp.toPx() * a
            renderEffect = if (blurEnabled && supported && quality() != Quality.Low && r > 0.5f) BlurEffect(r, r, edges) else null
            compositingStrategy = if (a > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        }
        .drawWithContent {
            drawContent()
            val a = amount.value
            if (a > 0f && darken.value > 0f) drawRect(wash.copy(alpha = (darken.value * a).coerceIn(0f, 1f)), blendMode = BlendMode.SrcAtop)
        }
}
