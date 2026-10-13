package br.com.faru.boitata.playground

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import br.com.faru.boitata.core.FastNoise
import br.com.faru.boitata.light.FlamePalette
import br.com.faru.boitata.light.drawCastShadow
import br.com.faru.boitata.light.drawFlame
import br.com.faru.boitata.light.fireLight
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class FireKind(val label: String, val palette: FlamePalette) { Campfire("Campfire", FlamePalette.Campfire), Boitata("Boitatá", FlamePalette.Boitata), Blue("Blue", FlamePalette.Blue) }

/** A fire in the dark: it lights the stones round it, they throw their shadows away from it, and the shadows breathe with the flames. */
@Composable
fun FireScene(seconds: () -> Float, intensity: Float, kind: FireKind, modifier: Modifier = Modifier.fillMaxSize()) {
    val art = LocalSprites.current ?: return
    Spacer(modifier.drawBehind { drawFireScene(art, seconds(), intensity, kind) })
}

/** The seven stones of `fire_stones`, as boxes in its pixels. */
private val Stones = listOf(
    Crop(0, 131, 132, 105), Crop(144, 145, 131, 91), Crop(296, 176, 132, 60), Crop(440, 158, 130, 78),
    Crop(581, 151, 132, 85), Crop(728, 123, 132, 113), Crop(873, 139, 134, 97),
)

fun DrawScope.drawFireScene(art: Sprites, t: Float, intensity: Float, kind: FireKind) {
    val w = size.width; val h = size.height
    val base = Offset(w * 0.5f, h * 0.8f)
    drawRect(Color(0xFF06080C), size = size)
    val stars = FastNoise(4L)
    for (i in 0 until 70) drawCircle(Color(0xFFF1E6D0).copy(alpha = 0.25f + 0.4f * stars.at(i, 2)), 1f + 1.4f * stars.at(i, 3), Offset(stars.at(i, 0) * w, stars.at(i, 1) * h * 0.5f))
    drawRect(Color(0xFF0B0E10), Offset(0f, h * 0.55f), Size(w, h * 0.45f))
    val light = fireLight(base.x, base.y - h * 0.12f, t, reach = w * 0.6f * (0.5f + 0.5f * intensity), strength = 0.5f + 0.5f * intensity, color = kind.palette.mid, groundX = base.x, groundY = base.y)
    drawCircle(Brush.radialGradient(0f to kind.palette.mid.copy(alpha = 0.3f * intensity), 1f to Color.Transparent, center = base, radius = light.reach), radius = light.reach, center = base, blendMode = BlendMode.Plus)
    for (k in 1..4) drawCircle(kind.palette.mid.copy(alpha = 0.32f / k * intensity), radius = light.reach * 0.22f * k, center = base, style = Stroke(2f))
    // The beaten earth under the fire, the logs on it, then the flame.
    val pk = w * 0.36f / art.pit.width
    drawArt(art.pit, Offset(base.x - art.pit.width * pk / 2f, base.y - art.pit.height * pk * 0.6f), pk, Color(0xFF1B1613))
    // The stones, ringed round the fire, each throwing a shadow of its own shape.
    for ((i, c) in Stones.withIndex()) {
        val a = i / Stones.size.toFloat() * 6.283f + 0.3f
        val x = base.x + cos(a) * w * 0.27f; val y = base.y + sin(a) * h * 0.075f + h * 0.01f
        val k = h * 0.1f / c.h * (0.8f + 0.3f * (i % 3) / 2f)
        drawCastShadow(light, x, y, c.h * k, 0.7f) { col -> drawArt(art.stones, Offset(-c.w * k / 2f, -c.h * k), k, col, c) }
        val lit = (1f - hypot(x - base.x, (y - base.y) * 2f) / light.reach).coerceIn(0f, 1f) * light.strength
        drawArt(art.stones, Offset(x - c.w * k / 2f, y - c.h * k), k, lerp(Color(0xFF14110F), kind.palette.mid, lit * 0.65f), c)
    }
    val lk = w * 0.18f / art.logs.width
    drawArt(art.logs, Offset(base.x - art.logs.width * lk / 2f, base.y - art.logs.height * lk * 0.92f), lk, lerp(Color(0xFF14110F), kind.palette.outer, 0.35f * intensity))
    drawFlame(base.x, base.y - h * 0.02f, w * 0.16f, h * 0.45f, t, intensity, kind.palette)
}
