package br.com.faru.boitata.light

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The shadow of anything you can draw, laid on the ground and thrown away from [light].
 *
 * [silhouette] draws the thing standing with its feet at (0, 0) and up being negative y, in the colour it is given (so it can be a tree, a post,
 * a figure: anything made of paths). The shadow is that drawing flipped onto the ground, leaning away from the light and longer the closer the light
 * is to the ground, softened with a few passes. [height] only sets how long the shadow can get.
 *
 * It is the vector sibling of [CastSilhouette], which does the same from sprites.
 */
@Composable
public fun CastShadow(
    light: LightSource?,
    feetX: Float,
    feetY: Float,
    modifier: Modifier = Modifier.fillMaxSize(),
    height: Float = 200f,
    maxAlpha: Float = 0.5f,
    silhouette: DrawScope.(Color) -> Unit,
) {
    Spacer(modifier.drawBehind { drawCastShadow(light, feetX, feetY, height, maxAlpha, silhouette) })
}

/** The shadow onto this scope; the function behind [CastShadow]. Coordinates are in pixels of the scope. */
public fun DrawScope.drawCastShadow(light: LightSource?, feetX: Float, feetY: Float, height: Float, maxAlpha: Float = 0.5f, silhouette: DrawScope.(Color) -> Unit) {
    if (light == null || light.strength <= 0f) return
    val dx = feetX - light.groundX
    val dy = feetY - light.groundY
    val dist = hypot(dx, dy).coerceAtLeast(1f)
    val ux = dx / dist
    val uy = dy / dist
    // Nearer the light, the longer the shadow; far off, it shortens to the thing's own width.
    val stretch = (1.6f - dist / light.reach).coerceIn(0.25f, 1.6f)
    val reachAlpha = (1f - dist / (light.reach * 1.3f)).coerceIn(0f, 1f)
    val alpha = maxAlpha * light.strength * (0.35f + 0.65f * reachAlpha)
    if (alpha <= 0.01f) return
    // x' = x + (-y) * lean_x ; y' = (-y) * lean_y: the silhouette flipped onto the ground and sheared along the light's direction.
    val leanX = ux * stretch
    val leanY = uy * stretch * 0.5f + SQUASH
    for (pass in 0..2) {
        val soften = 1f + pass * 0.07f
        val m = Matrix().apply {
            this[0, 0] = 1f
            // Compose stores the 4x4 row by row: [row, column]. Row 1 is how y feeds x and y; row 3 is the shift.
            this[1, 0] = -leanX * soften          // x gets a share of the (flipped) height: the lean
            this[1, 1] = -(leanY * soften)        // y is the height flipped down onto the ground and squashed
            this[3, 0] = feetX
            this[3, 1] = feetY
        }
        withTransform({ transform(m) }) {
            silhouette(Color.Black.copy(alpha = alpha * (if (pass == 0) 0.8f else 0.4f)))
        }
    }
}

private const val SQUASH = 0.34f

/** Whether a shadow thrown from [light] at ([feetX], [feetY]) would be visible at all (the light is on and near enough). */
public fun shadowVisible(light: LightSource?, feetX: Float, feetY: Float): Boolean =
    light != null && light.strength > 0f && abs(hypot(feetX - light.groundX, feetY - light.groundY)) < light.reach * 1.3f
