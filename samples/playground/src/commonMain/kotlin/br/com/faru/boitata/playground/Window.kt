package br.com.faru.boitata.playground

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import br.com.faru.boitata.core.sky.SkyModel
import br.com.faru.boitata.core.sky.SkyWeather
import br.com.faru.boitata.light.LightSource
import br.com.faru.boitata.light.drawCastShadow
import br.com.faru.boitata.light.drawWindowLight

/**
 * A room with a window. The sky model for the [hour] says what colour the light is and how strong; the patch it throws on the floor is long and
 * golden when the sun is low, short and white at noon, bluish and faint under the moon, and the furniture throws its shadow away from the window.
 */
@Composable
fun WindowScene(seconds: () -> Float, hour: Float, cloud: Float, modifier: Modifier = Modifier.fillMaxSize()) {
    Spacer(modifier.drawBehind { drawRoom(seconds(), hour, cloud) })
}

fun DrawScope.drawRoom(t: Float, hour: Float, cloud: Float) {
    val w = size.width; val h = size.height
    val s = SkyModel.at(hour, SkyWeather(cloud = cloud))
    val floorY = h * 0.62f
    // Wall and floor, lit by the hour.
    val day = (0.25f + 0.75f * s.daylight)
    drawRect(Brush.verticalGradient(0f to lerp(Color(0xFF05070B), Color(0xFF8C7A66), day), 1f to lerp(Color(0xFF080A0F), Color(0xFF6E5E4E), day), startY = 0f, endY = floorY), size = Size(w, floorY))
    drawRect(Brush.verticalGradient(0f to lerp(Color(0xFF07090D), Color(0xFF5C4733), day), 1f to lerp(Color(0xFF030406), Color(0xFF3B2C20), day), startY = floorY, endY = h), Offset(0f, floorY), Size(w, h - floorY))
    // The window: the sky seen through it, a sun or moon, and the bars.
    val win = Rect(w * 0.12f, h * 0.1f, w * 0.38f, h * 0.5f)
    drawRect(Brush.verticalGradient(0f to s.zenith, 1f to s.horizon, startY = win.top, endY = win.bottom), win.topLeft, win.size)
    if (s.lightFrom.up) drawCircle(Color.White.copy(alpha = 0.85f * s.lightStrength + 0.1f), radius = h * 0.025f, center = Offset(win.left + s.lightFrom.x * win.width, win.top + s.lightFrom.y * win.height))
    val frame = Color(0xFF1B140E)
    drawRect(frame, win.topLeft, win.size, style = androidx.compose.ui.graphics.drawscope.Stroke(h * 0.014f))
    drawLine(frame, Offset(win.center.x, win.top), Offset(win.center.x, win.bottom), h * 0.01f)
    for (r in 1..2) drawLine(frame, Offset(win.left, win.top + win.height * r / 3f), Offset(win.right, win.top + win.height * r / 3f), h * 0.01f)
    // The light it lets in.
    val lean = ((0.5f - s.lightFrom.x) * 2.2f + 0.9f).coerceIn(-1f, 1.4f)
    drawWindowLight(win, floorY + h * 0.04f, h * 0.3f, lean, s.lightColor, s.lightStrength, s.sunElevation.coerceIn(0f, 1f), w * 0.5f, 2 to 3, t)
    // Furniture and its shadows, thrown away from the window.
    val lamp = LightSource(win.center.x, win.center.y, s.lightColor, s.lightStrength, w * 1.1f, groundX = win.center.x, groundY = floorY + h * 0.05f)
    for ((fx, fh, kind) in listOf(Triple(0.55f, 0.3f, 0), Triple(0.78f, 0.22f, 1), Triple(0.9f, 0.34f, 2))) {
        val x = w * fx; val y = h * (0.8f + 0.06f * kind)
        drawCastShadow(lamp, x, y, h * fh, 0.55f) { c -> drawFurniture(c, h * fh, kind) }
        withTransform({ translate(x, y) }) { drawFurniture(lerp(Color(0xFF16100B), Color(0xFF4A3A2C), day), h * fh, kind) }
    }
    // Night over everything.
    drawRect(Color(0xFF02040A).copy(alpha = (1f - s.daylight) * 0.35f), size = size)
}

/** Feet at (0, 0), up negative: 0 a tall plant in a pot, 1 a stool, 2 a standing lamp. */
fun DrawScope.drawFurniture(c: Color, hgt: Float, kind: Int) {
    when (kind) {
        0 -> { drawRect(c, Offset(-hgt * 0.09f, -hgt * 0.18f), Size(hgt * 0.18f, hgt * 0.18f)); for (i in -2..2) drawLine(c, Offset(0f, -hgt * 0.18f), Offset(i * hgt * 0.1f, -hgt * (0.8f + 0.06f * (2 - kotlin.math.abs(i)))), hgt * 0.035f) }
        1 -> { drawRect(c, Offset(-hgt * 0.22f, -hgt), Size(hgt * 0.44f, hgt * 0.1f)); drawLine(c, Offset(-hgt * 0.16f, -hgt * 0.9f), Offset(-hgt * 0.2f, 0f), hgt * 0.06f); drawLine(c, Offset(hgt * 0.16f, -hgt * 0.9f), Offset(hgt * 0.2f, 0f), hgt * 0.06f) }
        else -> { drawLine(c, Offset(0f, 0f), Offset(0f, -hgt * 0.85f), hgt * 0.035f); drawRect(c, Offset(-hgt * 0.14f, -hgt), Size(hgt * 0.28f, hgt * 0.2f)); drawRect(c, Offset(-hgt * 0.1f, -hgt * 0.03f), Size(hgt * 0.2f, hgt * 0.03f)) }
    }
}
