package br.com.faru.boitata.weather

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.exp
import kotlin.math.max
import br.com.faru.boitata.core.FastNoise
import br.com.faru.boitata.core.sky.Strike
import br.com.faru.boitata.core.sky.StormModel

/** What a storm view shows now: the flash of the whole sky (0..1) and the bolt, with its strength. */
class StormView(val flash: () -> Float, val bolt: () -> Pair<Strike, Float>?)

/**
 * Runs a storm's clock while [active] and tells [onStrike] about each strike as its flash happens (the game plays the thunder [Strike.thunderDelay]
 * later). The flash and the bolt are read in the draw phase. With [frozen] (reduced motion) there is no flicker: only the thunder.
 */
@Composable
fun rememberStorm(active: Boolean, seed: Long = 17L, intensity: Float = 1f, frozen: Boolean = false, onStrike: (Strike) -> Unit = {}): StormView {
    val model = remember(seed, intensity) { StormModel(seed, intensity) }
    var seconds by remember { mutableFloatStateOf(0f) }
    val strikeHandler by rememberUpdatedState(onStrike)
    LaunchedEffect(active, model) {
        if (!active) return@LaunchedEffect
        var last = seconds
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                if (previous != 0L) seconds += ((now - previous) / 1_000_000_000f).coerceAtMost(0.1f)
                previous = now
                model.strikes(last, seconds).forEach { strikeHandler(it) }
                last = seconds
            }
        }
    }
    return remember(model, active, frozen) {
        StormView(
            flash = { if (active && !frozen) model.flash(seconds) else 0f },
            bolt = { if (active && !frozen) model.bolt(seconds) else null },
        )
    }
}

/** The bolt as a jagged line from the cloud to the roofs with a few branches, glowing; [strength] 0..1. */
internal fun DrawScope.drawBolt(strike: Strike, strength: Float, horizonY: Float) {
    val n = FastNoise(strike.boltSeed)
    val w = size.width
    var x = strike.boltX * w
    var y = -size.height * 0.02f
    val path = Path().apply { moveTo(x, y) }
    val branches = ArrayList<Path>()
    val steps = 14
    for (i in 1..steps) {
        x += (n.at(i, 0) - 0.5f) * w * 0.09f
        y = horizonY * 0.95f * i / steps
        path.lineTo(x, y)
        if (i in 3..11 && n.at(i, 1) > 0.72f) {
            val b = Path().apply { moveTo(x, y) }
            var bx = x; var by = y
            repeat(3 + (n.at(i, 2) * 3).toInt()) { k ->
                bx += (if (n.at(i, 3) > 0.5f) 1f else -1f) * w * (0.02f + 0.04f * n.at(i * 9 + k, 4))
                by += horizonY * (0.04f + 0.05f * n.at(i * 9 + k, 5))
                b.lineTo(bx, by)
            }
            branches.add(b)
        }
    }
    val core = Color(0xFFF2F6FF).copy(alpha = strength)
    val glow = Color(0xFFA9C2FF).copy(alpha = 0.35f * strength)
    for (p in listOf(path) + branches) {
        drawPath(p, glow, style = Stroke(w * 0.022f, cap = StrokeCap.Round))
        drawPath(p, core, style = Stroke(w * (if (p === path) 0.005f else 0.003f), cap = StrokeCap.Round))
    }
}

/** The whole sky brightening with the flash: a cold white over everything. */
internal fun DrawScope.drawFlash(flash: Float) {
    if (flash > 0.01f) drawRect(Color(0xFFDDE8FF).copy(alpha = (flash * 0.55f).coerceIn(0f, 0.7f)))
}
