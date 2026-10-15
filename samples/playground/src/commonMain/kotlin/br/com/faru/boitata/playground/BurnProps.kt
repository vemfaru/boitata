package br.com.faru.boitata.playground

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.clock.rememberSceneQuality
import br.com.faru.boitata.core.wind.Wind
import br.com.faru.boitata.core.wind.rememberSceneWind
import br.com.faru.boitata.light.FlamePalette
import br.com.faru.boitata.light.LightRig
import br.com.faru.boitata.light.PointLight
import br.com.faru.boitata.light.drawCastShadows
import br.com.faru.boitata.light.drawFlame
import br.com.faru.boitata.light.drawLightPools
import kotlin.math.PI
import kotlin.math.sin

private val PropsArt = listOf(
    "burn_candle_whole", "burn_candle_half", "burn_candle_stub", "burn_candle_holder", "burn_paper_flat", "burn_paper_curled", "burn_cloth_line", "burn_cloth_bundle",
    "burn_matchbox", "burn_match", "burn_lamp", "burn_ash_1", "burn_ash_2", "burn_ash_3", "burn_char_1", "burn_char_2", "burn_scorch_1", "burn_soot_1", "burn_ember_1", "burn_ember_2", "burn_ember_3",
)

private class PropsSetup {
    val rig = LightRig(ambient = 0.06f)
    val candle = rig.add(PointLight(0f, 0f, 0f, Color(0xFFFFC46B), 0f, 400f, size = 0.06f, flicker = 0.35f)) as PointLight
    val paper = rig.add(PointLight(0f, 0f, 0f, Color(0xFFFF8A2E), 0f, 600f, size = 0.3f, flicker = 0.6f)) as PointLight
    val cloth = rig.add(PointLight(0f, 0f, 0f, Color(0xFFFF7A2E), 0f, 500f, size = 0.25f, flicker = 0.6f)) as PointLight
    val lamp = rig.add(PointLight(0f, 0f, 0f, Color(0xFFFFD08A), 0.55f, 450f, size = 0.1f, flicker = 0.12f)) as PointLight
}

private fun smooth(x: Float) = x.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

/** How long the things on the table take, in seconds, and when each starts: the match, the candle, the paper, the cloth. */
private const val Cycle = 16f

/**
 * The little things that burn, one after another on a table: a match is struck and lights a candle that burns down (whole, half, a stub in its wax); the same match lights a paper that curls, chars and goes up as ash
 * on the draught; the heat reaches a rag on a line that scorches and catches; a kerosene lamp burns steadily through all of it. Each flame is a light of its own, so every thing on the table throws its shadow away
 * from each of them. The scene is a pure function of [seconds] (it repeats every sixteen).
 */
@Composable
fun BurnPropsScene(seconds: () -> Float, modifier: Modifier = Modifier.fillMaxSize()) {
    val art = LocalSprites.current ?: return
    val loaded by produceState(false, art) { art.ensure(PropsArt); value = true }
    if (!loaded) return
    val quality = rememberSceneQuality()
    val sceneWind = rememberSceneWind()
    val setup = remember { PropsSetup() }
    Spacer(modifier.drawBehind { drawProps(art, setup, seconds() % Cycle, quality(), sceneWind.wind) })
}

private fun DrawScope.drawImg(image: ImageBitmap, feetX: Float, feetY: Float, height: Float, colour: Color, alpha: Float = 1f) {
    val k = height / image.height
    drawArt(image, Offset(feetX - image.width * k / 2f, feetY - height), k, colour, alpha = alpha)
}

private fun DrawScope.drawProps(art: Sprites, s: PropsSetup, t: Float, quality: Quality, wind: Wind) {
    val w = size.width; val h = size.height
    val rig = s.rig
    // The wall and the table.
    drawRect(Color(0xFF181310), Offset.Zero, size)
    drawTiled(art.wall, Rect(0f, 0f, w, h * 0.7f), tile = w * 0.34f, tint = Color(0xFF3A2F27))
    val left = w * 0.04f; val right = w * 0.96f; val topBack = h * 0.62f; val topFront = h * 0.8f
    drawRect(Color(0xFF0F0B09), Offset(0f, h * 0.7f), Size(w, h * 0.3f))
    val tableTint = Color(0xFF5A3F28)
    val leg = art.bench.getValue("table_leg"); val legH = h * 0.2f; val legW = legH * leg.width / leg.height
    drawArt(leg, Offset(left + w * 0.01f, h * 0.88f), legH / leg.height, tableTint.copy(alpha = 0.9f)); drawArt(leg, Offset(right - w * 0.01f - legW, h * 0.88f), legH / leg.height, tableTint.copy(alpha = 0.9f))
    val apron = art.bench.getValue("table_apron"); drawArt(apron, Offset(left, topFront), 1f, Color(0xFF3F2C1C), scaleX = (right - left) / apron.width, scaleY = (h * 0.9f - topFront) / apron.height)
    val top = art.bench.getValue("table_top"); drawArt(top, Offset(left, topBack), 1f, tableTint, scaleX = (right - left) / top.width, scaleY = (topFront - topBack) / top.height)

    val feetY = h * 0.76f
    val candleX = w * 0.3f; val paperX = w * 0.5f; val clothX = w * 0.7f; val lampX = w * 0.88f; val boxX = w * 0.12f
    // The story of each thing, as 0..1 progress.
    val matchLit = smooth((t - 0.9f) / 0.4f) * (1f - smooth((t - 5.2f) / 0.4f))
    val candleOn = t >= 2.0f
    val paperP = ((t - 4.8f) / 5f).coerceIn(0f, 1f).takeIf { t >= 4.8f } ?: 0f
    val clothP = ((t - 8.6f) / 4.5f).coerceIn(0f, 1f).takeIf { t >= 8.6f } ?: 0f
    val candleH = h * 0.3f
    // The lights.
    s.candle.x = candleX; s.candle.y = feetY; s.candle.height = candleH * (if (t < 6f) 1f else if (t < 11f) 0.85f else 0.45f); s.candle.strength = if (candleOn) 0.55f else 0f
    s.paper.x = paperX; s.paper.y = feetY; s.paper.height = h * 0.25f; s.paper.strength = if (paperP in 0.02f..0.95f) 0.9f * sin(paperP * PI.toFloat()).coerceAtLeast(0.25f) else 0f
    s.cloth.x = clothX; s.cloth.y = feetY; s.cloth.height = h * 0.25f; s.cloth.strength = if (clothP in 0.3f..0.95f) 0.8f else 0f
    s.lamp.x = lampX; s.lamp.y = feetY; s.lamp.height = h * 0.22f
    rig.surfaces.clear()
    drawLightPools(rig, t, bulbs = false)

    val dark = Color(0xFF3A2E36); val lit = Color(0xFFFFE6B8)
    fun shade(x: Float, height: Float) = rig.shade(dark, lit, x, feetY - height * 0.3f, t, height)
    // The things, with their shadows first.
    val half = art.art("burn_candle_half"); val whole = art.art("burn_candle_whole"); val stub = art.art("burn_candle_stub"); val holder = art.art("burn_candle_holder")
    val candleImg = when { t < 6f -> whole; t < 11f -> half; else -> stub }
    val paperImg = if (paperP < 0.3f) art.art("burn_paper_flat") else if (paperP < 0.85f) art.art("burn_paper_curled") else null
    val clothImg = if (clothP < 0.8f) art.art("burn_cloth_line") else art.art("burn_cloth_bundle")
    drawCastShadows(rig, candleX, feetY, candleH, t, 0.8f, quality) { c -> drawImg(candleImg, 0f, 0f, candleH, c) }
    if (paperImg != null) drawCastShadows(rig, paperX, feetY, h * 0.36f, t, 0.8f, quality) { c -> drawImg(paperImg, 0f, 0f, h * 0.36f, c) }
    drawCastShadows(rig, clothX, feetY, h * 0.36f, t, 0.8f, quality) { c -> drawImg(clothImg, 0f, 0f, h * 0.36f, c) }
    drawCastShadows(rig, lampX, feetY, h * 0.34f, t, 0.8f, quality) { c -> drawImg(art.art("burn_lamp"), 0f, 0f, h * 0.34f, c) }

    // Scorch and ash on the table under what burnt.
    if (paperP > 0.3f) drawImg(art.art("burn_char_1"), paperX, feetY + h * 0.02f, h * 0.1f, Color(0xFF0A0706), alpha = smooth((paperP - 0.3f) / 0.4f) * 0.9f)
    if (clothP > 0.3f) drawImg(art.art("burn_scorch_1"), clothX, feetY + h * 0.03f, h * 0.11f, Color(0xFF0A0706), alpha = smooth((clothP - 0.3f) / 0.5f) * 0.85f)
    drawImg(holder, candleX, feetY, h * 0.09f, shade(candleX, h * 0.09f))
    drawImg(candleImg, candleX, feetY - h * 0.01f, candleH, shade(candleX, candleH))
    if (paperImg != null) drawImg(paperImg, paperX, feetY, h * 0.36f, shade(paperX, h * 0.36f))
    else drawImg(art.art("burn_ash_1"), paperX, feetY + h * 0.01f, h * 0.1f, shade(paperX, h * 0.1f))
    drawImg(clothImg, clothX, feetY, h * 0.36f, shade(clothX, h * 0.36f).let { if (clothP > 0.3f) lerpColour(it, Color(0xFF1A1210), clothP) else it })
    drawImg(art.art("burn_lamp"), lampX, feetY, h * 0.34f, shade(lampX, h * 0.34f))
    drawImg(art.art("burn_matchbox"), boxX, feetY, h * 0.07f, shade(boxX, h * 0.07f))
    // Soot on the wall above the paper.
    if (paperP > 0.2f) drawImg(art.art("burn_soot_1"), paperX, h * 0.62f, h * 0.45f, Color(0xFF0B0807), alpha = smooth((paperP - 0.2f) / 0.6f) * 0.7f)

    // The match: out of its box, struck, to the candle, then to the paper, and out.
    if (t in 0.5f..5.6f) {
        val m = art.art("burn_match")
        val p: Offset = when {
            t < 1.3f -> Offset(boxX + (w * 0.015f) * smooth((t - 0.5f) / 0.8f), feetY - h * 0.08f - h * 0.04f * smooth((t - 0.5f) / 0.8f))
            t < 2.0f -> Offset(boxX + w * 0.015f + (candleX - boxX - w * 0.015f) * smooth((t - 1.3f) / 0.7f), feetY - h * 0.12f - h * 0.17f * smooth((t - 1.3f) / 0.7f))
            t < 3.5f -> Offset(candleX, feetY - candleH * 1.0f)
            else -> Offset(candleX + (paperX - candleX) * smooth((t - 3.5f) / 1.3f), feetY - candleH + (candleH - h * 0.1f) * smooth((t - 3.5f) / 1.3f))
        }
        withTransform({ translate(p.x, p.y) ; rotate(-30f, Offset.Zero) }) { drawArt(m, Offset(-m.width * h * 0.1f / m.height / 2f, 0f), h * 0.1f / m.height, Color(0xFFD9B27A)) }
        if (matchLit > 0.05f) drawFlame(p.x, p.y, h * 0.02f, h * 0.05f, t, matchLit, FlamePalette.Campfire, 5L, quality)
    }
    // Flames: the candle, the paper (it climbs down its face as it burns), the cloth, the lamp.
    if (candleOn) {
        val topOfCandle = feetY - candleH * (if (t < 6f) 1f else if (t < 11f) 0.85f else 0.45f)
        drawFlame(candleX, topOfCandle, h * 0.025f, h * 0.07f * (if (t > 11f) 0.7f else 1f), t, 0.8f, FlamePalette.Campfire, 6L, quality)
    }
    if (paperP in 0.02f..0.95f) {
        val level = feetY - h * 0.36f * (1f - 0.7f * paperP)
        drawFlame(paperX, level + h * 0.2f * paperP, h * 0.12f, h * 0.24f * sin(paperP * PI.toFloat()).coerceAtLeast(0.3f), t, 0.9f, FlamePalette.Campfire, 7L, quality)
    }
    if (clothP in 0.3f..0.95f) drawFlame(clothX, feetY - h * 0.1f - h * 0.12f * clothP, h * 0.1f, h * 0.22f, t, 0.85f, FlamePalette.Campfire, 8L, quality)
    drawFlame(lampX, feetY - h * 0.22f, h * 0.03f, h * 0.09f, t, 0.7f, FlamePalette.Campfire, 9L, quality)
    // Ash off the paper on the draught, embers off the cloth.
    if (paperP > 0.5f) for (i in 0 until 22) {
        val age = (t - 7.3f - i * 0.18f).let { if (it < 0f) -1f else it % 4.5f }
        if (age < 0f) continue
        val flake = art.art("burn_ash_${i % 3 + 1}"); val fs = h * 0.025f
        val x = paperX + age * w * (0.02f + wind.direction * wind.speed * 0.06f) + sin(age * 1.8f + i) * h * 0.012f; val y = feetY - h * 0.2f - age * h * 0.06f
        drawArt(flake, Offset(x, y), fs / flake.width, Color(0xFFC9C0B0), alpha = (1f - age / 4.5f).coerceIn(0f, 1f) * 0.8f)
    }
    if (clothP in 0.3f..1f) for (i in 0 until 8) {
        val life = (t * 0.8f + i * 0.37f) % 1f; val e = art.art("burn_ember_${i % 3 + 1}")
        drawArt(e, Offset(clothX + sin(i * 2f + t) * h * 0.03f, feetY - h * 0.2f - life * h * 0.25f), h * 0.014f / e.width, Color(0xFFFF9A3A), alpha = 1f - life)
    }
}

private fun lerpColour(a: Color, b: Color, f: Float) = androidx.compose.ui.graphics.lerp(a, b, f.coerceIn(0f, 1f))
