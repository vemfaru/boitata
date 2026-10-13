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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import br.com.faru.boitata.light.LightSource
import br.com.faru.boitata.light.drawCastShadow
import kotlin.math.hypot

/** A bare floor with a few carved things on it and one light you can move: each throws a shadow of its own shape away from the light, longer when the light is near. */
@Composable
fun ShadowScene(light: () -> Offset, reach: Float, modifier: Modifier = Modifier.fillMaxSize()) {
    val art = LocalSprites.current ?: return
    Spacer(modifier.drawBehind { drawShadows(art, light(), reach) })
}

/** A prop stood on the ground at ([feetX], [feetY]), [height] pixels tall: its shadow first, then the prop, warm where the light reaches it. */
fun DrawScope.drawProp(image: ImageBitmap, feetX: Float, feetY: Float, height: Float, light: LightSource?, dark: Color, lit: Color) {
    val k = height / image.height
    drawCastShadow(light, feetX, feetY, height, 0.7f) { c -> drawArt(image, Offset(-image.width * k / 2f, -height), k, c) }
    val near = if (light == null) 0f else (1f - hypot(feetX - light.x, feetY - light.y) / light.reach).coerceIn(0f, 1f) * light.strength
    drawArt(image, Offset(feetX - image.width * k / 2f, feetY - height), k, lerp(dark, lit, near * 0.85f))
}

fun DrawScope.drawShadows(art: Sprites, lightFraction: Offset, reach: Float) {
    val w = size.width; val h = size.height
    val lp = Offset(lightFraction.x * w, lightFraction.y * h)
    val source = LightSource(lp.x, lp.y, Color(0xFFFFD08A), 0.95f, w * reach, groundX = lp.x, groundY = lp.y + h * 0.04f)
    drawRect(Color(0xFF0A0C10), size = size)
    drawRect(Brush.radialGradient(0f to Color(0xFF4A3F31), 0.55f to Color(0xFF241F18), 1f to Color(0xFF0A0C10), center = lp, radius = source.reach), size = size)
    for (k in 1..4) drawCircle(Color(0xFFF2A83B).copy(alpha = 0.3f / k), radius = h * 0.07f * k, center = lp, style = Stroke(2f))
    val dark = Color(0xFF14110F); val lit = Color(0xFFC79B5E)
    drawProp(art.prop("araucaria"), w * 0.2f, h * 0.8f, h * 0.5f, source, dark, lit)
    drawProp(art.prop("crate"), w * 0.45f, h * 0.88f, h * 0.2f, source, dark, lit)
    drawProp(art.prop("figure"), w * 0.7f, h * 0.8f, h * 0.38f, source, dark, lit)
    drawProp(art.prop("lantern"), w * 0.88f, h * 0.9f, h * 0.45f, source, dark, lit)
    drawCircle(Brush.radialGradient(0f to Color(0xFFFFF1C2), 0.25f to Color(0x88FFD08A), 1f to Color.Transparent, center = lp, radius = h * 0.09f), radius = h * 0.09f, center = lp, blendMode = BlendMode.Plus)
}
