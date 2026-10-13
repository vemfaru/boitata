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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import br.com.faru.boitata.core.FastNoise
import br.com.faru.boitata.light.FlamePalette
import br.com.faru.boitata.light.drawCastShadow
import br.com.faru.boitata.light.drawFlame
import br.com.faru.boitata.light.fireLight
import kotlin.math.hypot

enum class FireKind(val label: String, val palette: FlamePalette) { Campfire("Campfire", FlamePalette.Campfire), Boitata("Boitatá", FlamePalette.Boitata), Blue("Blue", FlamePalette.Blue) }

/** A fire in the dark: it lights the stones round it, they throw their shadows away from it, and the shadows breathe with the flames. */
@Composable
fun FireScene(seconds: () -> Float, intensity: Float, kind: FireKind, modifier: Modifier = Modifier.fillMaxSize()) {
    Spacer(modifier.drawBehind { drawFireScene(seconds(), intensity, kind) })
}

fun DrawScope.drawFireScene(t: Float, intensity: Float, kind: FireKind) {
    val w = size.width; val h = size.height
    val base = Offset(w * 0.5f, h * 0.8f)
    drawRect(Color(0xFF06080C), size = size)
    val stars = FastNoise(4L)
    for (i in 0 until 70) drawCircle(Color.White.copy(alpha = 0.25f + 0.4f * stars.at(i, 2)), 0.6f + stars.at(i, 3), Offset(stars.at(i, 0) * w, stars.at(i, 1) * h * 0.5f))
    drawRect(Brush.verticalGradient(0f to Color(0xFF0C0F10), 1f to Color(0xFF040506), startY = h * 0.55f, endY = h), Offset(0f, h * 0.55f), Size(w, h * 0.45f))
    val light = fireLight(base.x, base.y - h * 0.12f, t, reach = w * 0.6f * (0.5f + 0.5f * intensity), strength = 0.5f + 0.5f * intensity, color = kind.palette.mid, groundX = base.x, groundY = base.y)
    drawCircle(Brush.radialGradient(0f to kind.palette.mid.copy(alpha = 0.35f * intensity), 1f to Color.Transparent, center = Offset(base.x, base.y), radius = light.reach), radius = light.reach, center = Offset(base.x, base.y), blendMode = BlendMode.Plus)
    val stones = FastNoise(8L)
    for (i in 0 until 7) {
        val a = i / 7f * 6.283f + 0.3f
        val x = base.x + kotlin.math.cos(a) * w * (0.2f + 0.14f * stones.at(i, 0)); val y = base.y + kotlin.math.sin(a) * h * 0.07f + h * 0.04f
        val sh = h * (0.05f + 0.05f * stones.at(i, 1))
        drawCastShadow(light, x, y, sh, 0.6f) { c -> drawStone(c, sh) }
        val lit = (1f - hypot(x - base.x, (y - base.y) * 2f) / light.reach).coerceIn(0f, 1f) * light.strength
        withTransform({ translate(x, y) }) { drawStone(lerp(Color(0xFF14171A), kind.palette.mid, lit * 0.65f), sh) }
    }
    drawRect(Color(0xFF1A0F08), Offset(base.x - w * 0.09f, base.y - h * 0.015f), Size(w * 0.18f, h * 0.03f))
    drawFlame(base.x, base.y, w * 0.16f, h * 0.45f, t, intensity, kind.palette)
}

private fun DrawScope.drawStone(c: Color, h: Float) {
    drawOval(c, Offset(-h * 0.9f, -h), Size(h * 1.8f, h))
}
