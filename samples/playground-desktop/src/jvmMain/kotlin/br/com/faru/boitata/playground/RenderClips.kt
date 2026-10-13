package br.com.faru.boitata.playground

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

private const val WIDTH = 720
private const val HEIGHT = 405
private const val FPS = 20

/** Draws every [Clips] entry frame by frame into `<out>/<name>/0000.png ...`; pass a clip name as the second argument to draw only that one. */
fun main(args: Array<String>) {
    val out = File(args[0])
    val only = args.getOrNull(1)
    for (clip in Clips) {
        if (only != null && clip.name != only) continue
        val dir = File(out, clip.name).apply { deleteRecursively(); mkdirs() }
        var t by mutableFloatStateOf(0f)
        val scene = ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) { clip.content { t } }
        val frames = (clip.length * FPS).toInt()
        // A first frame to let the composition settle, then the clip.
        scene.render(0L)
        for (i in 0 until frames) {
            t = i / FPS.toFloat()
            val image = scene.render((i * 1_000_000_000L) / FPS)
            File(dir, "%04d.png".format(i)).writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
        scene.close()
        println("${clip.name}: $frames frames")
    }
}
