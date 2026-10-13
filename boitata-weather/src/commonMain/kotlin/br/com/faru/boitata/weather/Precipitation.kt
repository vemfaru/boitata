package br.com.faru.boitata.weather

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import br.com.faru.boitata.core.AdvancedBoitataApi
import br.com.faru.boitata.core.FastNoise
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.clock.rememberSceneQuality
import br.com.faru.boitata.core.clock.rememberSceneSeconds
import br.com.faru.boitata.core.fx.cycle
import kotlin.math.sin

/**
 * Rain, snow and fog, drawn on a plain canvas, so they look the same on every platform and need no shaders.
 *
 * Every one is a function of time alone (each drop or flake is a repeating cycle, see [cycle]), so nothing is kept between frames: the same time gives
 * the same picture, and you can scrub or record them. Time and quality come from the scene's clock ([br.com.faru.boitata.core.clock.BoitataScene]); they are read in the draw phase, so a
 * moving clock redraws and never recomposes, and a heavy scene draws fewer drops on its own. Pass [seconds] or [quality] only to override the clock.
 */

/** Falling rain: [intensity] 0..1 sets how many drops, [wind] leans them (-1 to 1), [color] is the drop. */
@Composable
public fun Rain(
    modifier: Modifier = Modifier.fillMaxSize(),
    intensity: Float = 0.6f,
    wind: Float = 0.25f,
    color: Color = Color(0xFFBFD0E6),
    seed: Long = 3L,
    seconds: (() -> Float)? = null,
    quality: (() -> Quality)? = null,
) {
    val time = seconds ?: rememberSceneSeconds()
    val level = quality ?: rememberSceneQuality()
    Spacer(modifier.drawBehind { drawRain(time(), intensity, wind, color, seed, level()) })
}

/** Rain onto this scope; the function behind [Rain]. Read [seconds] and [quality] inside the draw block that calls it. */
@AdvancedBoitataApi
public fun DrawScope.drawRain(seconds: Float, intensity: Float, wind: Float, color: Color, seed: Long = 3L, quality: Quality = Quality.High) {
    val noise = FastNoise(seed)
    val count = quality.scaled((MAX_DROPS * intensity.coerceIn(0f, 1f)).toInt())
    // Fewer drops must not look thinner: the ones that remain are drawn a little stronger.
    val boost = 1f + (1f - quality.particles) * 0.5f
    val w = size.width; val h = size.height
    val lean = wind * 0.35f
    for (i in 0 until count) {
        val depth = noise.at(i, 0)                       // far drops are slower, shorter and fainter
        val life = cycle(seconds, 0.55f + 0.45f * (1f - depth), noise.at(i, 1))
        val y = (life * 1.2f - 0.1f) * h
        val x = noise.at(i, 2) * (w + h * 0.5f) - h * 0.25f + lean * (y - h * 0.5f) * -1f
        val len = h * (0.025f + 0.04f * depth)
        drawLine(
            color.copy(alpha = ((0.15f + 0.4f * depth) * boost).coerceAtMost(0.9f)), Offset(x, y), Offset(x + lean * len, y + len),
            strokeWidth = (1f + 1.2f * depth) * boost, cap = StrokeCap.Round,
        )
    }
}

/** Drifting snow: [intensity] 0..1 sets how many flakes. */
@Composable
public fun Snow(
    modifier: Modifier = Modifier.fillMaxSize(),
    intensity: Float = 0.6f,
    wind: Float = 0.1f,
    color: Color = Color.White,
    seed: Long = 5L,
    seconds: (() -> Float)? = null,
    quality: (() -> Quality)? = null,
) {
    val time = seconds ?: rememberSceneSeconds()
    val level = quality ?: rememberSceneQuality()
    Spacer(modifier.drawBehind { drawSnow(time(), intensity, wind, color, seed, level()) })
}

@AdvancedBoitataApi
public fun DrawScope.drawSnow(seconds: Float, intensity: Float, wind: Float, color: Color, seed: Long = 5L, quality: Quality = Quality.High) {
    val noise = FastNoise(seed)
    val count = quality.scaled((MAX_FLAKES * intensity.coerceIn(0f, 1f)).toInt())
    val boost = 1f + (1f - quality.particles) * 0.5f
    val w = size.width; val h = size.height
    for (i in 0 until count) {
        val depth = noise.at(i, 0)
        val life = cycle(seconds, 7f + 7f * (1f - depth), noise.at(i, 1))
        val y = (life * 1.1f - 0.05f) * h
        val sway = sin((seconds * (0.6f + noise.at(i, 3)) + noise.at(i, 4) * 6.28f)) * w * 0.012f
        val x = noise.at(i, 2) * w + sway + wind * life * w * 0.3f
        drawCircle(color.copy(alpha = (0.25f + 0.55f * depth).coerceAtMost(1f)), radius = (1f + 2.4f * depth) * boost, center = Offset(x, y))
    }
}

/** Mist drifting across the scene: [density] 0..1; layered soft banks that slide slowly. */
@Composable
public fun Fog(
    modifier: Modifier = Modifier.fillMaxSize(),
    density: Float = 0.5f,
    color: Color = Color(0xFFB9C4D0),
    seed: Long = 9L,
    seconds: (() -> Float)? = null,
    quality: (() -> Quality)? = null,
) {
    val time = seconds ?: rememberSceneSeconds()
    val level = quality ?: rememberSceneQuality()
    Spacer(modifier.drawBehind { drawFog(time(), density, color, seed, level()) })
}

@AdvancedBoitataApi
public fun DrawScope.drawFog(seconds: Float, density: Float, color: Color, seed: Long = 9L, quality: Quality = Quality.High) {
    val noise = FastNoise(seed)
    val w = size.width; val h = size.height
    val banks = quality.scaled(BANKS)
    // Each bank is a soft gradient, the costliest thing here: fewer banks, each a little denser, keeps the same amount of mist.
    val denser = BANKS.toFloat() / banks
    for (i in 0 until banks) {
        val speed = 0.01f + 0.02f * noise.at(i, 0)
        val x = ((noise.at(i, 1) + seconds * speed) % 1f) * (w * 1.6f) - w * 0.3f
        val y = h * (0.35f + 0.6f * noise.at(i, 2))
        val r = w * (0.25f + 0.3f * noise.at(i, 3))
        val a = ((0.05f + 0.16f * noise.at(i, 4)) * density.coerceIn(0f, 1f) * (0.8f + 0.2f * sin(seconds * 0.3f + i)) * denser).coerceAtMost(0.5f)
        drawCircle(
            Brush.radialGradient(0f to color.copy(alpha = a), 1f to Color.Transparent, center = Offset(x, y), radius = r),
            radius = r, center = Offset(x, y),
        )
    }
}

private const val MAX_DROPS = 420
private const val MAX_FLAKES = 260
private const val BANKS = 14
