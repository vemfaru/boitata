package br.com.faru.boitata.playground

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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import br.com.faru.boitata.core.FastNoise
import br.com.faru.boitata.core.sky.SkyModel
import br.com.faru.boitata.core.sky.SkyWeather
import br.com.faru.boitata.light.FlamePalette
import br.com.faru.boitata.light.drawCastShadow
import br.com.faru.boitata.light.drawFlameTrail
import br.com.faru.boitata.light.fireLight
import br.com.faru.boitata.weather.drawFog
import br.com.faru.boitata.weather.drawRain
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The Boitatá in its forest: a night wood, the fire serpent weaving between the trees. It lights the trunks near it and every tree throws its
 * shadow away from the serpent, so the shadows swing round as it passes. Rain and mist are optional layers.
 */
@Composable
fun ForestScene(
    seconds: () -> Float,
    modifier: Modifier = Modifier.fillMaxSize(),
    rain: Float = 0f,
    fog: Float = 0.35f,
    hour: Float = 23.5f,
) {
    Spacer(modifier.drawBehind { drawForest(seconds(), rain, fog, hour) })
}

private class Tree(val x: Float, val ground: Float, val height: Float, val shade: Float)

fun DrawScope.drawForest(t: Float, rain: Float, fog: Float, hour: Float) {
    val w = size.width; val h = size.height
    val state = SkyModel.at(hour, SkyWeather(cloud = 0.2f + 0.4f * rain, precipitation = rain))
    val horizon = h * 0.6f
    // Sky.
    drawRect(Brush.verticalGradient(0f to state.zenith, 1f to state.horizon, startY = 0f, endY = horizon), size = androidx.compose.ui.geometry.Size(w, horizon))
    val stars = FastNoise(2L)
    for (i in 0 until 90) {
        val a = state.stars * (0.35f + 0.65f * (0.5f + 0.5f * sin(t * (0.8f + stars.at(i, 2)) + i)))
        drawCircle(Color.White.copy(alpha = a * 0.8f), radius = 0.6f + stars.at(i, 3) * 1.1f, center = Offset(stars.at(i, 0) * w, stars.at(i, 1) * horizon * 0.8f))
    }
    if (state.moon.up) {
        val m = Offset(state.moon.x * w, state.moon.y * horizon)
        drawCircle(Brush.radialGradient(0f to Color(0x55DDE6FF), 1f to Color.Transparent, center = m, radius = h * 0.12f), radius = h * 0.12f, center = m)
        drawCircle(Color(0xFFE9EEF8), radius = h * 0.028f, center = m)
    }
    // Ground.
    drawRect(Brush.verticalGradient(0f to Color(0xFF0B1410), 1f to Color(0xFF040806), startY = horizon, endY = h), topLeft = Offset(0f, horizon), size = androidx.compose.ui.geometry.Size(w, h - horizon))

    // The serpent: the head follows a slow loop, the body is where the head was a moment ago.
    val count = 72
    val pts = FloatArray(count * 2)
    for (n in 0 until count) {
        val tt = t * 1.5f - n * 0.07f
        val weave = sin(n * 0.32f - t * 3.5f) * h * 0.045f * (0.3f + n / count.toFloat())
        val px = w * (0.5f + 0.34f * sin(tt * 0.62f) + 0.04f * sin(tt * 1.9f))
        val py = h * (0.5f + 0.08f * sin(tt * 1.1f + 1f)) + weave
        pts[n * 2] = px; pts[n * 2 + 1] = py
    }
    val head = Offset(pts[0], pts[1])
    val light = fireLight(head.x, head.y, t, reach = w * 0.55f, groundX = head.x, groundY = horizon + (h - horizon) * 0.2f)

    fun tree(base: Color, tr: Tree, lit: Boolean) {
        val d = hypot(tr.x - head.x, (tr.ground - h * 0.1f) - head.y)
        val near = (1f - d / light.reach).coerceIn(0f, 1f) * light.strength
        val c = lerp(base, Color(0xFF8A4B1F), (near * 0.75f).coerceIn(0f, 0.8f))
        withTransform({ translate(tr.x, tr.ground) }) { drawPine(c, tr.height) }
        if (lit) drawCastShadow(light, tr.x, tr.ground, tr.height, 0.6f) { color -> drawPine(color, tr.height) }
    }
    val far = FastNoise(5L); val mid = FastNoise(6L); val nearN = FastNoise(7L)
    for (i in 0 until 16) tree(Color(0xFF0C1A22), Tree(far.at(i, 0) * w, horizon + h * 0.01f, h * (0.16f + 0.08f * far.at(i, 1)), 0f), false)
    drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color(0x66506878), startY = horizon - h * 0.12f, endY = horizon + h * 0.02f), topLeft = Offset(0f, horizon - h * 0.12f), size = androidx.compose.ui.geometry.Size(w, h * 0.14f))
    for (i in 0 until 8) tree(Color(0xFF08130F), Tree((i + 0.3f + 0.5f * mid.at(i, 0)) / 8f * w, horizon + h * (0.05f + 0.06f * mid.at(i, 1)), h * (0.3f + 0.12f * mid.at(i, 2)), 0f), true)

    // Glow on the ground under the serpent, then the serpent itself.
    drawCircle(Brush.radialGradient(0f to Color(0x55FFA63C), 1f to Color.Transparent, center = Offset(head.x, horizon + (h - horizon) * 0.3f), radius = w * 0.3f), radius = w * 0.3f, center = Offset(head.x, horizon + (h - horizon) * 0.3f), blendMode = BlendMode.Plus)
    drawFlameTrail(pts, count, h * 0.052f, t, FlamePalette.Boitata)
    drawHead(pts, h)

    for (i in 0 until 4) tree(Color(0xFF040A08), Tree((i * 0.3f + 0.08f + 0.1f * nearN.at(i, 0)) * w * 1.1f - w * 0.02f, h * (1.0f + 0.04f * nearN.at(i, 1)), h * (0.75f + 0.2f * nearN.at(i, 2)), 0f), false)

    if (fog > 0f) drawFog(t, fog, Color(0xFF8DA2B5))
    if (rain > 0f) drawRain(t, rain, 0.3f, Color(0xFFB7C8DC))
}

/** A pine with its feet at (0, 0): a trunk and four layers of boughs, up being negative y. */
fun DrawScope.drawPine(color: Color, height: Float) {
    val w = height * 0.32f
    drawRect(color, topLeft = Offset(-w * 0.07f, -height * 0.18f), size = androidx.compose.ui.geometry.Size(w * 0.14f, height * 0.18f))
    val p = Path()
    for (i in 0 until 4) {
        val top = -height * (0.98f - i * 0.2f)
        val bottom = -height * (0.62f - i * 0.15f)
        val half = w * (0.35f + i * 0.16f)
        p.reset(); p.moveTo(0f, top); p.lineTo(half, bottom); p.lineTo(-half, bottom); p.close()
        drawPath(p, color)
    }
}

private fun DrawScope.drawHead(pts: FloatArray, h: Float) {
    val head = Offset(pts[0], pts[1])
    var dx = pts[0] - pts[2]; var dy = pts[1] - pts[3]
    val len = hypot(dx, dy).coerceAtLeast(0.001f); dx /= len; dy /= len
    val px = -dy; val py = dx
    val s = h * 0.04f
    val p = Path()
    p.moveTo(head.x + dx * s * 1.9f, head.y + dy * s * 1.9f)
    p.lineTo(head.x - dx * s * 0.3f + px * s, head.y - dy * s * 0.3f + py * s)
    p.lineTo(head.x - dx * s * 0.3f - px * s, head.y - dy * s * 0.3f - py * s)
    p.close()
    drawPath(p, Color(0xFF2B0D05).copy(alpha = 0.92f))
    for (side in intArrayOf(-1, 1)) {
        val e = Offset(head.x + dx * s * 0.45f + px * s * 0.5f * side, head.y + dy * s * 0.45f + py * s * 0.5f * side)
        drawCircle(Brush.radialGradient(0f to Color(0xAAFFF1C2), 1f to Color.Transparent, center = e, radius = s * 1.1f), radius = s * 1.1f, center = e, blendMode = BlendMode.Plus)
        drawCircle(Color(0xFFFFF6D6), radius = s * 0.4f, center = e)
    }
}
