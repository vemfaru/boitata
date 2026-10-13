package br.com.faru.boitata.light

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import br.com.faru.boitata.core.AdvancedBoitataApi
import br.com.faru.boitata.core.FastNoise
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.clock.rememberSceneQuality
import br.com.faru.boitata.core.clock.rememberSceneSeconds
import br.com.faru.boitata.core.fx.bump
import br.com.faru.boitata.core.fx.cycle
import br.com.faru.boitata.core.fx.LightShaft
import kotlin.math.sin

/**
 * The light that comes in at a window and lies on the floor: one band per row of panes (so the frame's bars show as dark lines across it),
 * tinted by [color], with dust turning in the beam.
 *
 * The patch starts at the foot of the wall ([floorY]) below the [window] and runs [depth] pixels down the floor; [lean] says where the light is
 * travelling (-1 left to 1 right) and [elevation] how high the sun is (1 overhead, 0 on the horizon: a low sun leans out far). [length] is how
 * far a high noon patch leans, in pixels. [panes] is columns by rows. Feed [color] and [strength] from the sky (`SkyState.lightColor` and
 * `lightStrength`) and the room follows the hour.
 */
@Composable
public fun WindowLight(
    window: Rect,
    floorY: Float,
    depth: Float,
    lean: Float,
    color: Color,
    strength: Float,
    modifier: Modifier = Modifier.fillMaxSize(),
    elevation: Float = 0.5f,
    length: Float = 260f,
    panes: Pair<Int, Int> = 2 to 3,
    seconds: (() -> Float)? = null,
    quality: (() -> Quality)? = null,
) {
    val time = seconds ?: rememberSceneSeconds()
    val level = quality ?: rememberSceneQuality()
    Spacer(modifier.drawBehind { drawWindowLight(window, floorY, depth, lean, color, strength, elevation, length, panes, time(), level()) })
}

@AdvancedBoitataApi
public fun DrawScope.drawWindowLight(
    window: Rect, floorY: Float, depth: Float, lean: Float, color: Color, strength: Float, elevation: Float, length: Float, panes: Pair<Int, Int>, seconds: Float,
    quality: Quality = Quality.High,
) {
    if (strength <= 0.01f) return
    val reach = LightShaft.reachFor(elevation, length)
    val (cols, rows) = panes
    val bar = 0.07f                                              // the width of a bar, as a share of a pane
    val pw = window.width / cols
    val path = Path()
    for (c in 0 until cols) for (r in 0 until rows) {
        val l = window.left + c * pw + pw * bar; val rr = window.left + (c + 1) * pw - pw * bar
        // The window's top row lights the far end of the floor, its bottom row the near end.
        val far = 1f - r / rows.toFloat(); val near = 1f - (r + 1) / rows.toFloat()
        val q = LightShaft.band(l, rr, floorY, depth, lean, reach, near + (far - near) * bar, far - (far - near) * bar)
        path.reset()
        path.moveTo(q[0], q[1]); path.lineTo(q[2], q[3]); path.lineTo(q[4], q[5]); path.lineTo(q[6], q[7]); path.close()
        drawPath(
            path,
            Brush.linearGradient(
                0f to color.copy(alpha = 0.5f * strength), 1f to color.copy(alpha = 0.16f * strength),
                start = Offset(q[0], q[1]), end = Offset(q[6], q[7]),
            ),
            blendMode = BlendMode.Plus,
        )
    }
    // Dust in the beam, between the window and the patch.
    val noise = FastNoise(31L)
    for (i in 0 until quality.scaled(26)) {
        val u = noise.at(i, 0); val v = noise.at(i, 1)
        val life = cycle(seconds, 5f + 5f * noise.at(i, 3), noise.at(i, 2))
        val px = window.left + window.width * u + lean * reach * v * 0.8f + sin(seconds * 0.5f + i) * 6f
        val py = window.bottom + (floorY - window.bottom) * v - life * 20f
        drawCircle(color.copy(alpha = bump(life) * 0.6f * strength), radius = 1f + noise.at(i, 4) * 1.2f, center = Offset(px, py), blendMode = BlendMode.Plus)
    }
}
