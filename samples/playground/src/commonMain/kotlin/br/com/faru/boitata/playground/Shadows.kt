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
import br.com.faru.boitata.light.LightSource
import br.com.faru.boitata.light.drawCastShadow
import kotlin.math.hypot

/** A bare floor with a few things on it and one light you can move: each thing throws its shadow away from the light, longer when the light is near. */
@Composable
fun ShadowScene(light: () -> Offset, reach: Float, modifier: Modifier = Modifier.fillMaxSize()) {
    Spacer(modifier.drawBehind { drawShadows(light(), reach) })
}

fun DrawScope.drawShadows(lightFraction: Offset, reach: Float) {
    val w = size.width; val h = size.height
    val lp = Offset(lightFraction.x * w, lightFraction.y * h)
    val source = LightSource(lp.x, lp.y, Color(0xFFFFD08A), 0.95f, w * reach, groundX = lp.x, groundY = lp.y + h * 0.04f)
    drawRect(Color(0xFF0A0C10), size = size)
    drawRect(Brush.radialGradient(0f to Color(0xFF6B5A44), 0.55f to Color(0xFF2A241C), 1f to Color(0xFF0A0C10), center = lp, radius = source.reach), size = size)
    val things = listOf(Offset(0.2f, 0.78f) to 0, Offset(0.45f, 0.86f) to 1, Offset(0.7f, 0.76f) to 2, Offset(0.88f, 0.88f) to 3)
    for ((pos, kind) in things) {
        val x = w * pos.x; val y = h * pos.y; val th = h * (0.22f + 0.04f * kind)
        drawCastShadow(source, x, y, th, 0.6f) { c -> drawThing(c, th, kind) }
        val d = hypot(x - lp.x, y - lp.y)
        val lit = (1f - d / source.reach).coerceIn(0f, 1f)
        withTransform({ translate(x, y) }) { drawThing(lerp(Color(0xFF111418), Color(0xFFC79B5E), lit * 0.8f), th, kind) }
    }
    drawCircle(Brush.radialGradient(0f to Color(0xFFFFF1C2), 0.25f to Color(0x88FFD08A), 1f to Color.Transparent, center = lp, radius = h * 0.09f), radius = h * 0.09f, center = lp, blendMode = BlendMode.Plus)
}

private fun DrawScope.drawThing(c: Color, th: Float, kind: Int) {
    when (kind) {
        0 -> drawPine(c, th * 1.3f)
        1 -> { drawRect(c, Offset(-th * 0.3f, -th * 0.4f), Size(th * 0.6f, th * 0.4f)); drawRect(c, Offset(-th * 0.3f, -th * 0.4f), Size(th * 0.6f, th * 0.06f)) }
        2 -> { drawCircle(c, th * 0.11f, Offset(0f, -th * 0.88f)); drawRect(c, Offset(-th * 0.13f, -th * 0.76f), Size(th * 0.26f, th * 0.5f)); drawRect(c, Offset(-th * 0.12f, -th * 0.28f), Size(th * 0.09f, th * 0.28f)); drawRect(c, Offset(th * 0.03f, -th * 0.28f), Size(th * 0.09f, th * 0.28f)) }
        else -> { drawRect(c, Offset(-th * 0.03f, -th), Size(th * 0.06f, th)); drawRect(c, Offset(-th * 0.14f, -th * 1.04f), Size(th * 0.28f, th * 0.1f)); drawRect(c, Offset(-th * 0.12f, -th * 0.04f), Size(th * 0.24f, th * 0.04f)) }
    }
}
