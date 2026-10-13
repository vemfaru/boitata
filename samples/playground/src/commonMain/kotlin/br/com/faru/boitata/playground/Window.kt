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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import br.com.faru.boitata.core.sky.SkyModel
import br.com.faru.boitata.core.sky.SkyWeather
import br.com.faru.boitata.light.LightSource
import br.com.faru.boitata.light.drawWindowLight

/**
 * A room with a window. The sky model for the [hour] says what colour the light is and how strong; the patch it throws on the floor is long and
 * golden when the sun is low, short and pale at noon, bluish and faint under the moon, and the furniture throws its shadow away from the window.
 */
@Composable
fun WindowScene(seconds: () -> Float, hour: Float, cloud: Float, modifier: Modifier = Modifier.fillMaxSize()) {
    val art = LocalSprites.current ?: return
    Spacer(modifier.drawBehind { drawRoom(art, seconds(), hour, cloud) })
}

/** Fills [area] with [image] repeated, each repeat [tile] pixels wide, painted [tint]. */
private fun DrawScope.drawTiled(image: ImageBitmap, area: Rect, tile: Float, tint: Color) {
    val k = tile / image.width
    val th = image.height * k
    clipRect(area.left, area.top, area.right, area.bottom) {
        var y = area.top
        while (y < area.bottom) {
            var x = area.left
            while (x < area.right) { drawArt(image, Offset(x, y), k, tint); x += tile }
            y += th
        }
    }
}

fun DrawScope.drawRoom(art: Sprites, t: Float, hour: Float, cloud: Float) {
    val w = size.width; val h = size.height
    val s = SkyModel.at(hour, SkyWeather(cloud = cloud))
    val day = 0.2f + 0.8f * s.daylight
    val floorY = h * 0.62f
    // Wall and floor: a lighter plaster behind, the carved ink tile over it, both by the hour.
    val wall = lerp(Color(0xFF0B0E12), Color(0xFF7C6B58), day); val floor = lerp(Color(0xFF080A0D), Color(0xFF4F3D2B), day)
    drawRect(lerp(wall, Color.White, 0.12f), Offset.Zero, Size(w, floorY))
    drawTiled(art.wall, Rect(0f, 0f, w, floorY), tile = w * 0.34f, tint = wall)
    drawRect(lerp(floor, Color.White, 0.1f), Offset(0f, floorY), Size(w, h - floorY))
    drawTiled(art.floor, Rect(0f, floorY, w, h), tile = w * 0.3f, tint = floor)
    // The window: the sky seen through its transparent panes, a sun or moon, and the carved frame over it.
    val wh = h * 0.5f; val ww = wh * art.window.width / art.window.height
    val win = Rect(w * 0.1f, h * 0.06f, w * 0.1f + ww, h * 0.06f + wh)
    val pane = Rect(win.left + ww * 0.08f, win.top + wh * 0.09f, win.right - ww * 0.08f, win.bottom - wh * 0.1f)
    clipRect(pane.left, pane.top, pane.right, pane.bottom) {
        val bands = 5
        for (i in 0 until bands) drawRect(lerp(s.zenith, s.horizon, (i + 0.5f) / bands), Offset(pane.left, pane.top + pane.height * i / bands), Size(pane.width, pane.height / bands + 1f))
        if (s.lightFrom.up) drawCircle(Color(0xFFFFF3C4).copy(alpha = (0.5f + 0.4f * s.lightStrength)), radius = h * 0.022f, center = Offset(pane.left + s.lightFrom.x * pane.width, pane.top + s.lightFrom.y * pane.height))
    }
    drawArt(art.window, win.topLeft, wh / art.window.height, lerp(Color(0xFF0B0907), Color(0xFF2A1F15), day))
    // The light it lets in, lying on the floor in panes divided by the bars.
    val lean = ((0.5f - s.lightFrom.x) * 2.2f + 0.9f).coerceIn(-1f, 1.4f)
    drawWindowLight(pane, floorY + h * 0.03f, h * 0.3f, lean, s.lightColor, s.lightStrength, s.sunElevation.coerceIn(0f, 1f), w * 0.5f, 2 to 3, t)
    // Furniture and its shadows, thrown away from the window.
    val lamp = LightSource(win.center.x, win.center.y, s.lightColor, s.lightStrength, w * 1.1f, groundX = win.center.x, groundY = floorY + h * 0.05f)
    val dark = lerp(Color(0xFF0B0907), Color(0xFF2A1F15), day); val lit = lerp(Color(0xFF5B4A3A), Color(0xFFE2C48A), s.lightStrength)
    drawProp(art.prop("plant"), w * 0.56f, h * 0.84f, h * 0.36f, lamp, dark, lit)
    drawProp(art.prop("stool"), w * 0.76f, h * 0.86f, h * 0.2f, lamp, dark, lit)
    drawProp(art.prop("floor_lamp"), w * 0.9f, h * 0.88f, h * 0.42f, lamp, dark, lit)
    drawRect(Color(0xFF02040A).copy(alpha = (1f - s.daylight) * 0.3f), size = size)
}
