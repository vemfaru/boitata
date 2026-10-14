package br.com.faru.boitata.playground

import androidx.compose.runtime.CompositionLocalProvider
import br.com.faru.boitata.core.clock.FixedSceneClock
import br.com.faru.boitata.core.clock.WithSceneClock
import br.com.faru.boitata.core.wind.Wind
import br.com.faru.boitata.core.wind.WithSceneWind
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

private const val WIDTH = 720
private const val HEIGHT = 405
private const val FPS = 20

/**
 * Draws every [Clips] entry frame by frame into `<out>/<name>/0000.png ...`. Arguments: the output folder, then optionally a clip name (only that
 * one), then optionally `preview` (three frames at a quarter, a half and three quarters of the clip, for a quick look).
 */
fun main(args: Array<String>) {
    val out = File(args[0])
    val only = args.getOrNull(1)?.takeIf { it.isNotBlank() }
    val preview = args.getOrNull(2) == "preview"
    val sprites = runBlocking { loadSprites() }
    for (clip in Clips) {
        if (only != null && clip.name != only) continue
        val dir = File(out, clip.name).apply { deleteRecursively(); mkdirs() }
        var t by mutableFloatStateOf(0f)
        val scene = ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
            CompositionLocalProvider(LocalSprites provides sprites) { WithSceneClock(FixedSceneClock({ t })) { WithSceneWind(clip.wind) { clip.content { t } } } }
        }
        val frames = (clip.length * FPS).toInt()
        scene.render(0L)
        val indices = if (preview) listOf(frames / 4, frames / 2, frames * 3 / 4) else (0 until frames).toList()
        for (i in indices) {
            t = i / FPS.toFloat()
            // Compose learns of the new time asynchronously: render, let it settle, and keep the second picture (as boitata-testing does).
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            scene.render((i * 1_000_000_000L) / FPS)
            val image = scene.render((i * 1_000_000_000L) / FPS + 1)
            File(dir, "%04d.png".format(i)).writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
        scene.close()
        println("${clip.name}: ${indices.size} frames")
    }
}
