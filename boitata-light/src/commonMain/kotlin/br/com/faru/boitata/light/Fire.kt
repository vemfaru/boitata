package br.com.faru.boitata.light

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import br.com.faru.boitata.core.AdvancedBoitataApi
import br.com.faru.boitata.core.FastNoise
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.clock.rememberSceneQuality
import br.com.faru.boitata.core.clock.rememberSceneSeconds
import br.com.faru.boitata.core.fx.bump
import br.com.faru.boitata.core.fx.cycle
import kotlin.math.cos
import kotlin.math.sin

/** The three colours of a flame, from its cool outer edge to its hot core. */
@androidx.compose.runtime.Immutable
public data class FlamePalette(val outer: Color, val mid: Color, val core: Color, val ember: Color) {
    public companion object {
        /** An ordinary wood fire. */
        public val Campfire: FlamePalette = FlamePalette(Color(0xFFC2410C), Color(0xFFF2A83B), Color(0xFFFFF1C2), Color(0xFFFFB347))
        /** The Boitatá's fire: it burns blue-white at the heart and amber at the edge, cold and watchful. */
        public val Boitata: FlamePalette = FlamePalette(Color(0xFFB4350F), Color(0xFFF2A83B), Color(0xFFFFF6D6), Color(0xFFFFC46B))
        public val Blue: FlamePalette = FlamePalette(Color(0xFF1D4ED8), Color(0xFF60A5FA), Color(0xFFE0F2FE), Color(0xFF93C5FD))
    }
}

/**
 * A fire: tongues of flame that sway and flicker, three layers deep (edge, body, core), with embers rising off it.
 * Drawn on a plain canvas, additive, as a pure function of time (see the weather module's rain for why); time and quality come from the scene's clock,
 * so a heavy scene gets a lighter fire on its own. The fire stands on the bottom
 * centre of the area; [intensity] 0..1 sets its height and how many embers fly.
 */
@Composable
public fun Fire(
    modifier: Modifier = Modifier.fillMaxSize(),
    intensity: Float = 1f,
    palette: FlamePalette = FlamePalette.Campfire,
    seed: Long = 11L,
    seconds: (() -> Float)? = null,
    quality: (() -> Quality)? = null,
) {
    val time = seconds ?: rememberSceneSeconds()
    val level = quality ?: rememberSceneQuality()
    Spacer(
        modifier.drawBehind {
            drawFlame(size.width * 0.5f, size.height, size.width * 0.8f, size.height * 0.9f, time(), intensity, palette, seed, level())
        },
    )
}

/** A flame with its foot at ([baseX], [baseY]), [width] wide at the foot and [height] tall at most. */
@AdvancedBoitataApi
public fun DrawScope.drawFlame(
    baseX: Float, baseY: Float, width: Float, height: Float,
    seconds: Float, intensity: Float = 1f, palette: FlamePalette = FlamePalette.Campfire, seed: Long = 11L, quality: Quality = Quality.High,
) {
    val noise = FastNoise(seed)
    val k = intensity.coerceIn(0f, 1f)
    val tongues = if (quality == Quality.Low) 5 else 7
    val path = Path()
    // Glow first, so the flame stands in its own light (the costliest part of a fire: skipped at Low).
    if (quality.softGlow) drawCircle(
        Brush.radialGradient(0f to palette.mid.copy(alpha = 0.38f * k), 1f to Color.Transparent, center = Offset(baseX, baseY - height * 0.3f), radius = height * 1.1f),
        radius = height * 1.1f, center = Offset(baseX, baseY - height * 0.3f), blendMode = BlendMode.Plus,
    )
    for (layer in 0..2) {
        val scale = when (layer) { 0 -> 1f; 1 -> 0.7f; else -> 0.42f }
        val color = when (layer) { 0 -> palette.outer; 1 -> palette.mid; else -> palette.core }
        for (i in 0 until tongues) {
            val u = (i + 0.5f) / tongues                       // 0..1 across the foot
            val centre = (u - 0.5f) * 2f                       // -1..1
            val flicker = 0.78f + 0.22f * sin(seconds * (5f + 4f * noise.at(i, 0)) + noise.at(i, 1) * 6.28f)
            val tall = height * k * scale * flicker * (1f - 0.55f * centre * centre) * (0.75f + 0.25f * noise.at(i, 2))
            val sway = sin(seconds * (2.2f + noise.at(i, 3)) + noise.at(i, 4) * 6.28f) * width * 0.05f * (0.4f + scale)
            val fx = baseX + centre * width * 0.5f * scale
            val half = width / tongues * (0.95f * scale + 0.15f)
            path.reset()
            path.moveTo(fx - half, baseY)
            path.quadraticTo(fx - half * 0.9f + sway * 0.4f, baseY - tall * 0.55f, fx + sway, baseY - tall)
            path.quadraticTo(fx + half * 0.9f + sway * 0.4f, baseY - tall * 0.55f, fx + half, baseY)
            path.close()
            drawPath(path, color.copy(alpha = 0.55f), blendMode = BlendMode.Plus)
        }
    }
    drawEmbers(baseX, baseY - height * 0.25f, width, height * 1.6f, seconds, k, palette.ember, seed + 1, quality)
}

/** Embers lifting off a fire: [count] scales with [intensity]; each rises over about two seconds, drifting, and fades. */
@AdvancedBoitataApi
public fun DrawScope.drawEmbers(x: Float, y: Float, spread: Float, rise: Float, seconds: Float, intensity: Float, color: Color, seed: Long = 12L, quality: Quality = Quality.High) {
    val noise = FastNoise(seed)
    val count = quality.scaled((22 * intensity.coerceIn(0f, 1f)).toInt())
    for (i in 0 until count) {
        val life = cycle(seconds, 1.6f + 1.2f * noise.at(i, 0), noise.at(i, 1))
        val px = x + (noise.at(i, 2) - 0.5f) * spread * 0.6f + sin(seconds * 1.7f + i) * spread * 0.06f * life + (life * life) * spread * 0.25f * (noise.at(i, 3) - 0.3f)
        val py = y - life * rise * (0.5f + 0.5f * noise.at(i, 4))
        val a = bump(life) * 0.9f
        drawCircle(color.copy(alpha = a), radius = (1.2f + 1.6f * noise.at(i, 5)) * (1f - 0.5f * life), center = Offset(px, py), blendMode = BlendMode.Plus)
    }
}

/**
 * Fire along a path, like a serpent made of flame. [points] are x,y pairs from the head to the tail ([count] of them); the body is a chain of
 * glowing blobs that thin out and cool from [head] to the tail, with a flicker running down it and embers shed from it.
 */
@AdvancedBoitataApi
public fun DrawScope.drawFlameTrail(
    points: FloatArray, count: Int, width: Float, seconds: Float,
    palette: FlamePalette = FlamePalette.Boitata, seed: Long = 21L, quality: Quality = Quality.High,
) {
    if (count < 2) return
    val noise = FastNoise(seed)
    // Tail first, so the hot head is drawn over the cooler body. Between every two points three more blobs run together into one ribbon.
    val steps = when (quality) { Quality.High -> 3; Quality.Medium -> 2; Quality.Low -> 1 }
    for (n in count - 1 downTo 0) {
        for (k in steps - 1 downTo 0) {
            if (n == count - 1 && k > 0) continue
            val f = k / steps.toFloat()
            val nx = if (n + 1 < count) n + 1 else n
            val t = (n + f) / (count - 1f)                    // 0 at the head, 1 at the tail
            val flicker = 0.88f + 0.12f * sin(seconds * 9f - (n + f) * 0.7f + noise.at(n, 0) * 6.28f)
            val r = width * (0.3f + 0.7f * (1f - t) * (1f - t * 0.5f)) * flicker
            val bx = points[n * 2] + (points[nx * 2] - points[n * 2]) * f
            val by = points[n * 2 + 1] + (points[nx * 2 + 1] - points[n * 2 + 1]) * f
            val c = Offset(bx + (noise.at(n, 1) - 0.5f) * width * 0.18f * t, by + (noise.at(n, 2) - 0.5f) * width * 0.18f * t)
            val fade = 1f - t * t
            if (quality.softGlow) drawCircle(Brush.radialGradient(0f to palette.outer.copy(alpha = 0.3f * fade), 1f to Color.Transparent, center = c, radius = r * 2.2f), radius = r * 2.2f, center = c, blendMode = BlendMode.Plus)
            drawCircle(lerp(palette.mid, palette.outer, t).copy(alpha = 0.34f * fade), radius = r, center = c, blendMode = BlendMode.Plus)
            drawCircle(palette.core.copy(alpha = 0.12f * fade * (1f - t) * (1f - t)), radius = r * 0.5f, center = c, blendMode = BlendMode.Plus)
        }
    }
    // Sparks shed along the body.
    for (i in 0 until quality.scaled(count.coerceAtMost(40))) {
        val at = ((noise.at(i, 6) * (count - 1)).toInt()).coerceIn(0, count - 1)
        val life = cycle(seconds, 1.1f + noise.at(i, 7), noise.at(i, 8))
        drawCircle(
            palette.ember.copy(alpha = bump(life) * 0.8f), radius = 1.2f + noise.at(i, 9) * 1.6f,
            center = Offset(points[at * 2] + (noise.at(i, 10) - 0.5f) * width, points[at * 2 + 1] - life * width * 2.2f), blendMode = BlendMode.Plus,
        )
    }
}

/**
 * The light a fire gives: warm, reaching [reach] world units, and unsteady, flickering in step with the flames at [seconds]. Feed it to [LightShade]
 * or [CastShadow] so what stands near the fire is lit by it and throws its shadow away from it.
 */
public fun fireLight(x: Float, y: Float, seconds: Float, reach: Float = 520f, strength: Float = 0.95f, color: Color = Color(0xFFFFA63C), groundX: Float = x, groundY: Float = y): LightSource {
    val f = 0.9f + 0.1f * sin(seconds * 11f) + 0.05f * cos(seconds * 17.3f)
    return LightSource(x, y, color, (strength * f).coerceIn(0f, 1f), reach * (0.96f + 0.04f * f), flicker = 0.3f, groundX = groundX, groundY = groundY)
}
