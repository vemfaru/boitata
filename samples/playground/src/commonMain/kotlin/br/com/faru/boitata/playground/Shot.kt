package br.com.faru.boitata.playground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.unit.dp
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.clock.rememberSceneQuality
import br.com.faru.boitata.core.light.Billboard
import br.com.faru.boitata.core.wind.Wind
import br.com.faru.boitata.core.wind.rememberSceneWind
import br.com.faru.boitata.light.FlamePalette
import br.com.faru.boitata.light.LightRig
import br.com.faru.boitata.light.PointLight
import br.com.faru.boitata.light.drawCastShadows
import br.com.faru.boitata.light.drawFlame
import br.com.faru.boitata.light.drawLightPools
import br.com.faru.boitata.light.drawWallLight
import kotlinx.coroutines.flow.first
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val ShotArt = listOf(
    "pose_lampiao_aim", "pose_lampiao_fire", "pose_cacador_aim", "pose_cacador_fall", "pose_cacador_ash", "back_house_wall", "back_house_floor",
    "shot_trail", "shell", "shot_shell", "shot_bullet", "burn_ash_1", "burn_ash_2", "burn_ash_3", "burn_ember_1", "burn_ember_2", "burn_ember_3",
) + (1..3).flatMap { listOf("shot_flash_$it", "shot_smoke_$it", "shot_spark_$it", "shot_dust_$it") }

/** When things happen in the shot, in seconds. */
private object Beat {
    const val Stops = 6.0f      // the hunter stops and raises his rifle
    const val Aims = 7.0f       // Lampião aims
    const val Fires = 8.0f      // the shot
    const val Falls = 8.15f     // the hunter goes down
    const val Serpent = 10.0f   // the guardian's fire passes and puts out the rest
    const val Calm = 13.0f
}

private class ShotSetup {
    val rig = LightRig(ambient = 0.07f)
    val hunterFire = rig.add(PointLight(0f, 0f, 0f, Color(0xFFFF8A2E), 0f, 500f, size = 0.35f, flicker = 0.5f)) as PointLight
    val flash = rig.add(PointLight(0f, 0f, 0f, Color(0xFFFFF0C4), 0f, 900f, size = 0.15f)) as PointLight
    val serpent = rig.add(PointLight(0f, 0f, 0f, Color(0xFFFF9A3A), 0f, 700f, size = 0.5f, flicker = 0.4f)) as PointLight
    val wall = Billboard(0f, 0f, 0f, 0f).also { it.blocksLight = false }
}

/**
 * **O Tiro.** The hunter, burning, staggers out of the dark toward the couple with his rifle; Lampião steps in front of Maria and fires. The muzzle flash is a light of its own: for a tenth of a second it floods the yard and
 * throws every shadow flat against the wall of the house; smoke climbs from the barrel and drifts the way the wind goes, the shell spins away. The hunter falls, and what the fire leaves of him goes up as ash on the wind until
 * the guardian's fire runs through the yard and puts the rest of it out. [onShot] is called the moment the shot is fired (that is where the sound goes). The scene is a pure function of [seconds], so it can be replayed or slowed.
 */
@Composable
fun ShotScene(seconds: () -> Float, modifier: Modifier = Modifier.fillMaxSize(), onShot: (() -> Unit)? = null) {
    val art = LocalSprites.current ?: return
    val loaded by produceState(false, art) { art.ensure(ShotArt.filter { it != "shell" }); value = true }
    if (!loaded) return
    val quality = rememberSceneQuality()
    val sceneWind = rememberSceneWind()
    val setup = remember { ShotSetup() }
    if (onShot != null) LaunchedEffect(Unit) {
        var fired = false
        snapshotFlow { seconds() >= Beat.Fires }.collect { now -> if (now && !fired) { fired = true; onShot() } else if (!now) fired = false }
    }
    Spacer(modifier.drawBehind { drawShot(art, setup, seconds(), quality(), sceneWind.wind) })
}

private fun smooth(x: Float) = x.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

private fun DrawScope.drawPose(image: ImageBitmap, feetX: Float, feetY: Float, height: Float, colour: Color, facing: Float = 1f) {
    val k = height / image.height
    withTransform({ translate(feetX, feetY); scale(facing, 1f, Offset.Zero) }) { drawArt(image, Offset(-image.width * k / 2f, -height), k, colour) }
}

private fun DrawScope.drawShot(art: Sprites, s: ShotSetup, t: Float, quality: Quality, wind: Wind) {
    val w = size.width; val h = size.height
    val rig = s.rig
    val floorY = h * 0.66f
    // The yard: the wall of the house behind, the boards of its porch, and the dark beyond.
    drawRect(Brush.verticalGradient(0f to Color(0xFF080B14), 1f to Color(0xFF151A26)), Offset.Zero, Size(w, floorY))
    drawNightStars(art, t, floorY - h * 0.38f, 0.8f, count = 40, seed = 9L)
    val wall = art.art("back_house_wall"); drawArt(wall, Offset(0f, floorY - h * 0.4f), 1f, Color(0xFF3B3732), scaleX = w / wall.width, scaleY = h * 0.4f / wall.height)
    val floor = art.art("back_house_floor"); drawArt(floor, Offset(0f, floorY), 1f, Color(0xFF14100D), scaleX = w / floor.width, scaleY = (h - floorY) / floor.height)

    // Who is where. The hunter walks in from the right, burning, then stops, then falls; Lampião steps in front of Maria.
    val aim = art.art("pose_lampiao_aim"); val fire = art.art("pose_lampiao_fire"); val hunterAim = art.art("pose_cacador_aim"); val fall = art.art("pose_cacador_fall"); val ash = art.art("pose_cacador_ash")
    val walkIn = smooth(t / Beat.Stops)
    val hunterX = w * (1.08f - 0.44f * walkIn)
    val hunterY = h * 0.9f
    val lampiaoX = w * (0.22f + 0.1f * smooth((t - 4.5f) / 1.6f)); val lampiaoY = h * 0.92f
    val mariaX = w * 0.12f; val mariaY = h * 0.86f
    val down = t >= Beat.Falls
    val burning = t < Beat.Serpent + 1.2f
    // Fire on the hunter (and the heat light it gives), dying as the guardian passes.
    val heat = if (t < Beat.Serpent) 1f else (1f - smooth((t - Beat.Serpent) / 1.6f))
    s.hunterFire.x = hunterX; s.hunterFire.y = hunterY; s.hunterFire.height = h * 0.12f; s.hunterFire.strength = 0.9f * heat
    val sinceShot = t - Beat.Fires
    val muzzle = Offset(lampiaoX + h * 0.33f, lampiaoY - h * 0.255f)
    s.flash.x = muzzle.x; s.flash.y = lampiaoY; s.flash.height = h * 0.25f
    s.flash.strength = if (sinceShot in 0f..0.6f) 0.98f * exp(-sinceShot * 11f) else 0f
    val serpentP = ((t - Beat.Serpent) / 2.6f)
    s.serpent.x = w * (-0.1f + 1.3f * serpentP.coerceIn(0f, 1f)); s.serpent.y = h * 0.93f; s.serpent.height = h * 0.1f
    s.serpent.strength = if (serpentP in 0f..1.05f) 0.85f else 0f
    rig.surfaces.clear()
    s.wall.footY = floorY; s.wall.left = 0f; s.wall.right = w; s.wall.height = h * 0.4f; rig.add(s.wall)
    drawWallLight(rig, s.wall, t)
    drawLightPools(rig, t, bulbs = false)

    val dark = Color(0xFF6A5A66); val lit = Color(0xFFFFE6B8)
    class Body(val x: Float, val y: Float, val height: Float, val draw: DrawScope.(Color) -> Unit)
    val bodies = ArrayList<Body>()
    bodies += Body(mariaX, mariaY, h * 0.34f) { c -> drawPose(art.prop("maria_bonita"), 0f, 0f, h * 0.34f, c) }
    val lampiaoPose = when { t < Beat.Aims - 0.6f -> null; sinceShot in 0f..1.4f -> fire; t >= Beat.Aims -> aim; else -> aim }
    val lampiaoH = h * 0.4f
    bodies += Body(lampiaoX, lampiaoY, lampiaoH) { c ->
        if (lampiaoPose != null && t < Beat.Calm) drawPose(lampiaoPose, 0f, 0f, lampiaoH, c)
        else drawStripOrProp(art, "lampiao_walk", "lampiao", t, 0f, 0f, h * 0.34f, c, walking = t > 4.5f && t < 6.1f)
    }
    if (!down) {
        val burnStrip = art.strips.getValue("cacador_burning")
        bodies += Body(hunterX, hunterY, h * 0.36f) { c ->
            if (t >= Beat.Stops) drawPose(hunterAim, 0f, 0f, h * 0.4f, c, facing = -1f)
            else drawStripFrame(burnStrip, floor(t * 6f).toInt(), 0f, 0f, h * 0.34f, c, facing = -1f)
        }
    } else {
        val remains = if (t < Beat.Falls + 1.6f) fall else ash
        bodies += Body(hunterX - w * 0.03f, hunterY, h * 0.2f) { c -> drawPose(remains, 0f, 0f, if (remains === fall) h * 0.22f else h * 0.12f, c, facing = -1f) }
    }
    for (b in bodies) drawCastShadows(rig, b.x, b.y, b.height, t, 0.85f, quality) { c -> b.draw(this, c) }
    for (b in bodies.sortedBy { it.y }) {
        val colour = rig.shade(dark, lit, b.x, b.y - b.height * 0.3f, t, b.height)
        withTransform({ translate(b.x, b.y) }) { b.draw(this, colour) }
    }
    // The flames on the hunter's body: a torso and a head, until the guardian's fire takes what is left.
    if (heat > 0.02f && t > 0.3f) {
        val fx = if (down) hunterX - w * 0.03f else hunterX
        val bodyH = if (down) h * 0.12f else h * 0.3f
        drawFlame(fx + h * 0.02f, hunterY - (if (down) h * 0.02f else h * 0.1f), h * 0.07f * heat, bodyH * (0.45f + 0.25f * heat), t, heat * 0.85f, FlamePalette.Campfire, 21L, quality)
        if (!down) drawFlame(fx - h * 0.02f, hunterY - h * 0.26f, h * 0.04f * heat, h * 0.09f * heat, t, heat * 0.85f, FlamePalette.Campfire, 22L, quality)
    }
    // The guardian's fire, running through the yard: a low wave of flame that leaves the ground cold.
    if (serpentP in 0f..1.1f) {
        val head = w * (-0.1f + 1.3f * serpentP)
        for (i in 0 until 9) {
            val x = head - i * w * 0.045f
            if (x < -w * 0.05f || x > w * 1.05f) continue
            val sz = h * (0.09f - 0.006f * i)
            drawFlame(x, h * 0.95f + sin(t * 5f + i) * h * 0.005f, sz * 1.4f, sz * 2.4f, t + i * 0.1f, 0.9f, FlamePalette.Campfire, 40L + i, quality)
        }
    }

    // The shot itself: flash, bullet, shell, spark, dust, smoke.
    if (sinceShot in 0f..0.18f) {
        val a = 1f - sinceShot / 0.18f
        val flash = art.art("shot_flash_${(floor(sinceShot * 60f).toInt() % 3) + 1}"); val fs = h * 0.32f
        drawArt(flash, Offset(muzzle.x - fs * 0.2f, muzzle.y - fs / 2f), fs / flash.width, Color(0xFFFFF0C4), alpha = a)
        drawCircle(Brush.radialGradient(0f to Color(0xFFFFE9B0).copy(alpha = 0.7f * a), 1f to Color.Transparent, center = muzzle, radius = h * 0.35f), radius = h * 0.35f, center = muzzle)
    }
    if (sinceShot in 0f..0.12f) {
        val u = sinceShot / 0.12f
        val bullet = art.art("shot_bullet"); val bl = h * 0.16f
        val x = muzzle.x + (hunterX - muzzle.x) * u
        drawArt(bullet, Offset(x - bl, muzzle.y - bl * bullet.height / bullet.width / 2f), bl / bullet.width, Color(0xFFFFE9B0))
        val trail = art.art("shot_trail"); val tl = (x - muzzle.x).coerceAtLeast(1f)
        drawArt(trail, Offset(muzzle.x, muzzle.y - h * 0.02f), 1f, Color(0xFFFFE9B0), alpha = 0.6f * (1f - u), scaleX = tl / trail.width, scaleY = h * 0.04f / trail.height)
    }
    if (sinceShot in 0f..0.5f) {
        val sp = art.art("shot_spark_${(floor(sinceShot * 30f).toInt() % 3) + 1}"); val ss = h * (0.12f + 0.2f * sinceShot)
        drawArt(sp, Offset(muzzle.x - ss / 2f, muzzle.y - ss / 2f), ss / sp.width, Color(0xFFFFC14A), alpha = 1f - sinceShot / 0.5f)
    }
    if (sinceShot in 0f..1.2f) {
        val shell = art.art("shot_shell"); val u = sinceShot / 1.2f
        val sx = lampiaoX + h * 0.1f - u * h * 0.1f; val sy = lampiaoY - h * 0.24f - sin(u * 3.14f) * h * 0.05f + u * u * h * 0.2f
        withTransform({ rotate(u * 900f, Offset(sx, sy)) }) { drawArt(shell, Offset(sx, sy), h * 0.02f / shell.width * 3f, Color(0xFFD8A24A)) }
    }
    if (sinceShot in 0f..0.9f) for (i in 0 until 3) {
        val dust = art.art("shot_dust_${i + 1}"); val ds = h * (0.1f + 0.14f * sinceShot)
        drawArt(dust, Offset(hunterX - ds / 2f + (i - 1) * h * 0.05f, hunterY - ds * 0.4f), ds / dust.width, Color(0xFF8A7A66), alpha = 0.5f * (1f - sinceShot / 0.9f))
    }
    // Smoke from the barrel and the hunter's fire, climbing and leaning with the wind.
    if (sinceShot > 0f) for (i in 0 until 6) {
        val born = i * 0.12f; val age = sinceShot - born
        if (age < 0f || age > 4.5f) continue
        val smoke = art.art("shot_smoke_${i % 3 + 1}"); val u = age / 4.5f; val sz = h * (0.1f + 0.22f * u)
        val x = muzzle.x + age * (0.012f + wind.direction * wind.speed * 0.06f) * w + sin(age + i) * h * 0.01f; val y = muzzle.y - age * h * 0.045f
        drawArt(smoke, Offset(x - sz / 2f, y - sz / 2f), sz / smoke.width, Color(0xFFB5AEA4), alpha = 0.6f * (1f - u))
    }
    // Ash lifting off what the fire left, carried off by the wind.
    if (t > Beat.Falls + 0.4f) for (i in 0 until 36) {
        val born = Beat.Falls + 0.4f + i * 0.14f; val age = t - born
        if (age < 0f || age > 5f) continue
        val flake = art.art("burn_ash_${i % 3 + 1}"); val u = age / 5f; val fs = h * (0.025f + 0.02f * ((i * 7) % 5) / 5f)
        val x = hunterX + (((i * 37) % 100) / 100f - 0.5f) * h * 0.12f + age * (0.03f + wind.direction * wind.speed * 0.11f + 0.02f) * w + sin(age * 1.7f + i) * h * 0.012f
        val y = hunterY - h * 0.04f - age * h * 0.05f - u * u * h * 0.02f
        withTransform({ rotate(age * 60f + i * 40f, Offset(x, y)) }) { drawArt(flake, Offset(x - fs / 2f, y - fs / 4f), fs / flake.width, Color(0xFFBFB6A6), alpha = (1f - u).coerceIn(0f, 1f) * 0.85f) }
    }
    // Embers rising from the fire while it lasts.
    if (heat > 0.1f && t > 1f) for (i in 0 until 10) {
        val life = ((t * 0.9f + i * 0.31f) % 1f)
        val ember = art.art("burn_ember_${i % 3 + 1}"); val es = h * 0.015f
        val ex = (if (down) hunterX - w * 0.03f else hunterX) + sin(i * 2f + t) * h * 0.04f + wind.direction * wind.speed * life * h * 0.1f
        drawArt(ember, Offset(ex, hunterY - h * 0.1f - life * h * 0.28f), es / ember.width, Color(0xFFFF9A3A), alpha = (1f - life) * heat)
    }
    // The flash over everything, for the instant it lasts.
    if (s.flash.strength > 0.05f) drawRect(Color(0xFFFFF4D6).copy(alpha = s.flash.strength * 0.18f), Offset.Zero, size)
}

private fun DrawScope.drawStripFrame(strip: SpriteStrip, frame: Int, feetX: Float, feetY: Float, height: Float, colour: Color, facing: Float = 1f) {
    val width = height * strip.aspect; val k = height / strip.image.height
    withTransform({ translate(feetX, feetY); scale(facing, 1f, Offset.Zero) }) {
        drawArt(strip.image, Offset(-width / 2f, -height), k, colour, Crop((frame % strip.frames) * strip.frameWidth, 0, strip.frameWidth, strip.image.height))
    }
}

/** A walking figure from its strip while [walking], else the still picture of the same person. */
private fun DrawScope.drawStripOrProp(art: Sprites, stripName: String, prop: String, t: Float, x: Float, y: Float, height: Float, colour: Color, walking: Boolean) {
    if (walking) drawStripFrame(art.strips.getValue(stripName), floor(t * 6f).toInt(), x, y, height, colour)
    else drawPose(art.prop(prop), x, y, height, colour)
}

/** The shot with its controls: it plays over and over from just before the hunter stops, at the speed you pick. */
@Composable
internal fun ShotDemo(seconds: () -> Float, onShot: (() -> Unit)? = null) {
    var speed by remember { mutableFloatStateOf(1f) }
    var origin by remember { mutableFloatStateOf(0f) }
    var anchor by remember { mutableFloatStateOf(0f) }
    val timeline: () -> Float = { 5.4f + (anchor + (seconds() - origin) * speed) % 10.6f }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { ShotScene(timeline, onShot = onShot) }
        Controls {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, v) in listOf("Slow motion 0.15x" to 0.15f, "Slow 0.5x" to 0.5f, "Real time" to 1f)) FilterChip(selected = speed == v, onClick = { anchor = (anchor + (seconds() - origin) * speed); origin = seconds(); speed = v }, label = { Text(label) })
                FilterChip(selected = false, onClick = { origin = seconds(); anchor = 0f }, label = { Text("Fire again") })
            }
            Text("The muzzle flash is a light: for a tenth of a second it throws every shadow flat against the wall. The smoke and the ash follow the wind (set it above). Slow it down to watch the flash, the shell, the smoke and the ash separately.", color = controlText())
        }
    }
}
