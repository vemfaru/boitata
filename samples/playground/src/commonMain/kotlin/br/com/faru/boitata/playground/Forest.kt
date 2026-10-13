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
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The Boitatá in its forest: a night wood in carved woodcut, the fire serpent weaving between the trees. It lights the trunks near it and every
 * tree throws its shadow away from the serpent, so the shadows swing round as it passes. Rain and mist are optional layers.
 */
@Composable
fun ForestScene(
    seconds: () -> Float,
    modifier: Modifier = Modifier.fillMaxSize(),
    rain: Float = 0f,
    fog: Float = 0.35f,
    hour: Float = 23.5f,
) {
    val art = LocalSprites.current ?: return
    Spacer(modifier.drawBehind { drawForest(art, seconds(), rain, fog, hour) })
}

/** Where each tree of `layer_mid` stands, in the art's own pixels; its feet are at the bottom centre of the box. */
private val MidTrees = listOf(
    Crop(13, 344, 261, 428), Crop(292, 487, 195, 309), Crop(523, 423, 206, 337), Crop(745, 363, 266, 449),
    Crop(1034, 391, 241, 393), Crop(1309, 448, 137, 320), Crop(1490, 512, 204, 292), Crop(1706, 473, 193, 317),
)

fun DrawScope.drawForest(art: Sprites, t: Float, rain: Float, fog: Float, hour: Float) {
    val w = size.width; val h = size.height
    val v = cover()
    val sc = v.scale
    val state = SkyModel.at(hour, SkyWeather(cloud = 0.2f + 0.4f * rain, precipitation = rain))
    val day = state.daylight
    val horizon = v.py(1080f * ART_HORIZON)

    // Sky, in flat horizontal bands (a woodcut has no gradients), with stars and the moon.
    val bands = 5
    for (i in 0 until bands) {
        val y0 = horizon * i / bands; val y1 = horizon * (i + 1) / bands
        drawRect(lerp(state.zenith, state.horizon, (i + 0.5f) / bands), Offset(0f, y0), Size(w, y1 - y0 + 1f))
    }
    drawRect(lerp(state.horizon, Color.Black, 0.25f), Offset(0f, horizon), Size(w, h - horizon))
    val stars = FastNoise(2L)
    for (i in 0 until 90) {
        val a = state.stars * (0.35f + 0.65f * (0.5f + 0.5f * sin(t * (0.8f + stars.at(i, 2)) + i)))
        val c = Offset(stars.at(i, 0) * w, stars.at(i, 1) * horizon * 0.8f); val r = 1f + stars.at(i, 3) * 1.6f
        drawCircle(Color(0xFFF1E6D0).copy(alpha = a * 0.8f), radius = r, center = c)
    }
    if (state.moon.up) {
        val m = Offset(state.moon.x * w, state.moon.y * horizon)
        for (k in 1..3) drawCircle(Color(0xFFA9B8E8).copy(alpha = 0.22f / k), radius = h * (0.03f + 0.022f * k), center = m, style = Stroke(1.5f))
        drawCircle(Color(0xFFE9EEF8), radius = h * 0.028f, center = m)
    }

    fun tint(night: Long, dayColor: Long) = lerp(Color(night), Color(dayColor), day)
    drawArt(art.far, Offset(v.x, v.y), sc, tint(0xFF0C1A22, 0xFF6F8794))
    drawArt(art.ground, Offset(v.x, v.y), sc, tint(0xFF070D0B, 0xFF26302A))

    // The serpent: the head follows a slow loop, the body is where the head was a moment ago.
    val count = 72
    val pts = FloatArray(count * 2)
    for (n in 0 until count) {
        val tt = t * 1.5f - n * 0.07f
        val weave = sin(n * 0.32f - t * 3.5f) * h * 0.045f * (0.3f + n / count.toFloat())
        pts[n * 2] = w * (0.5f + 0.34f * sin(tt * 0.62f) + 0.04f * sin(tt * 1.9f))
        pts[n * 2 + 1] = h * (0.5f + 0.08f * sin(tt * 1.1f + 1f)) + weave
    }
    val head = Offset(pts[0], pts[1])
    val light = fireLight(head.x, head.y, t, reach = w * 0.55f, strength = 0.95f * (1f - day), groundX = head.x, groundY = v.py(1080f * 0.76f))

    for (tr in MidTrees) {
        val feetX = v.px(tr.x + tr.w / 2f); val feetY = v.py((tr.y + tr.h).toFloat())
        val near = (1f - hypot(feetX - head.x, feetY - h * 0.1f - head.y) / light.reach).coerceIn(0f, 1f) * light.strength
        drawCastShadow(light, feetX, feetY, tr.h * sc, 0.6f) { c -> drawArt(art.mid, Offset(-tr.w / 2f * sc, -tr.h * sc), sc, c, tr) }
        val base = tint(0xFF08130F, 0xFF33463C)
        drawArt(art.mid, Offset(v.px(tr.x.toFloat()), v.py(tr.y.toFloat())), sc, lerp(base, Color(0xFF8A4B1F), (near * 0.6f).coerceIn(0f, 0.65f)), tr)
    }

    // Rings of carved arcs around the serpent show its glow; a soft light on the ground below it.
    val glow = Offset(head.x, v.py(1080f * 0.78f))
    drawCircle(Brush.radialGradient(0f to Color(0x44FFA63C), 1f to Color.Transparent, center = glow, radius = w * 0.28f), radius = w * 0.28f, center = glow, blendMode = BlendMode.Plus)
    for (k in 1..3) drawCircle(Color(0xFFF2A83B).copy(alpha = 0.35f / k * (1f - day)), radius = h * 0.09f * k, center = head, style = Stroke(2f))
    drawFlameTrail(pts, count, h * 0.052f, t, FlamePalette.Boitata)
    drawSerpentHead(art, pts, h * 0.052f)

    val nearTint = tint(0xFF040A08, 0xFF16201A)
    drawArt(art.near, Offset(v.x, v.y), sc, lerp(nearTint, Color(0xFF5A3216), (0.25f * light.strength).coerceIn(0f, 0.3f)))

    if (fog > 0f) drawFog(t, fog, Color(0xFF8DA2B5))
    if (rain > 0f) drawRain(t, rain, 0.3f, Color(0xFFB7C8DC))
}

/** The head sprite on the front of the flame, turned the way the serpent is going, its eyes lit from behind through the sockets cut in it. */
private fun DrawScope.drawSerpentHead(art: Sprites, pts: FloatArray, width: Float) {
    val head = Offset(pts[0], pts[1])
    val angle = (atan2((pts[1] - pts[3]).toDouble(), (pts[0] - pts[2]).toDouble()) * 180.0 / PI).toFloat()
    val k = 2f * width / 80f * 0.6f
    // Only the head of the sprite (from its back at x = 250): the body is the flame.
    val headCrop = Crop(250, 184, 226, 156)
    withTransform({ translate(head.x, head.y); rotate(angle, Offset.Zero) }) {
        for (sy in floatArrayOf(219f, 291f)) {
            val e = Offset((347f - 255f) * k, (sy - 256f) * k)
            drawCircle(Brush.radialGradient(0f to Color(0xCCFFF1C2), 1f to Color.Transparent, center = e, radius = 70f * k), radius = 70f * k, center = e, blendMode = BlendMode.Plus)
            drawCircle(Color(0xFFFFF1C2), radius = 30f * k, center = e)
        }
        // An Ember rim first, so the dark head stands out of the dark wood; then the carved head itself.
        val rim = 1.12f
        drawArt(art.head, Offset((250f - 255f) * k * rim - 0.5f * (rim - 1f) * 226f * k, (184f - 256f) * k * rim - 0.5f * (rim - 1f) * 156f * k), k * rim, Color(0xFFC2410C), headCrop)
        drawArt(art.head, Offset((250f - 255f) * k, (184f - 256f) * k), k, Color(0xFF14110F), headCrop)
    }
}
