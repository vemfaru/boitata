package br.com.faru.boitata.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import br.com.faru.boitata.core.clock.FixedSceneClock
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.clock.WithSceneClock
import org.jetbrains.skia.Bitmap

/** What drawing a scene frame by frame showed. */
public class SceneStats(
    public val frames: Int,
    public val meanMillis: Double,
    public val p95Millis: Double,
    /** How many frames came out pixel-for-pixel the same as the frame before. */
    public val identicalFrames: Int,
    /** The longest run of such frames in a row. */
    public val longestStill: Int,
    /** Which frames (by index) repeated the one before. */
    public val identicalAt: List<Int> = emptyList(),
)

/**
 * Draws [content] for [frames] frames at [fps] on a clock you step, and reports the frame cost and whether the picture changed each frame.
 *
 * The cost is CPU raster on whatever machine runs it: compare one run with another (this against that, High against Low, before against after), never
 * against a frame budget in milliseconds. A scene that is meant to be moving and shows [SceneStats.identicalFrames] near [frames] is stuttering: it is
 * being drawn at whole-pixel positions, or its time is read in composition and not in the draw phase.
 */
public fun measureScene(
    width: Int = 640,
    height: Int = 360,
    frames: Int = 120,
    warmup: Int = 20,
    fps: Int = 60,
    quality: Quality = Quality.High,
    startSeconds: Float = 2f,
    content: @Composable () -> Unit,
): SceneStats {
    var t by mutableFloatStateOf(startSeconds)
    val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
        WithSceneClock(FixedSceneClock({ t }, quality)) { content() }
    }
    val nanosPerFrame = 1_000_000_000L / fps
    repeat(warmup) { scene.render(it * nanosPerFrame) }
    val millis = DoubleArray(frames)
    var previous: Int? = null
    var identical = 0; var run = 0; var longest = 0
    val at = mutableListOf<Int>()
    for (i in 0 until frames) {
        t = startSeconds + i / fps.toFloat()
        // Compose learns of the new time asynchronously. Settle it, time the render that draws the frame, then render again so the picture we
        // compare is certainly the new one (a render that came too early would show the previous state).
        Snapshot.sendApplyNotifications()
        val start = System.nanoTime()
        scene.render((warmup + i) * nanosPerFrame)
        millis[i] = (System.nanoTime() - start) / 1e6
        val image = scene.render((warmup + i) * nanosPerFrame + 1)
        val hash = Bitmap.makeFromImage(image).readPixels()?.contentHashCode()
        if (hash != null && hash == previous) { identical++; at += i; run++; if (run > longest) longest = run } else run = 0
        previous = hash
    }
    scene.close()
    val sorted = millis.sorted()
    return SceneStats(frames, millis.average(), sorted[(frames * 0.95).toInt().coerceAtMost(frames - 1)], identical, longest, at)
}

/** One drawn frame, readable by pixel. */
public class Still(public val width: Int, public val height: Int, private val bytes: ByteArray) {
    /** How opaque the pixel at ([x], [y]) is, 0..255. */
    public fun alpha(x: Int, y: Int): Int = bytes[(y * width + x) * 4 + 3].toInt() and 0xFF

    /** How light the pixel at ([x], [y]) is, 0 black to 255 white (the mean of its colour channels). */
    public fun luma(x: Int, y: Int): Int { val i = (y * width + x) * 4; return ((bytes[i].toInt() and 0xFF) + (bytes[i + 1].toInt() and 0xFF) + (bytes[i + 2].toInt() and 0xFF)) / 3 }

    /** The mean x of the opaque pixels in rows [fromRow] until [toRow], or null if there are none. */
    public fun meanX(fromRow: Int = 0, toRow: Int = height, threshold: Int = 40): Double? {
        var sum = 0.0; var n = 0
        for (y in fromRow until toRow) for (x in 0 until width) if (alpha(x, y) > threshold) { sum += x; n++ }
        return if (n == 0) null else sum / n
    }

    /** How many pixels are opaque. */
    public fun count(threshold: Int = 40): Int = (0 until height).sumOf { y -> (0 until width).count { x -> alpha(x, y) > threshold } }
}

/** Draws [content] once at [seconds] on a clock that stands there, and returns the picture. */
public fun renderStill(width: Int = 400, height: Int = 300, seconds: Float = 5f, content: @Composable () -> Unit): Still {
    var t by mutableFloatStateOf(seconds)
    val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) { WithSceneClock(FixedSceneClock({ t })) { content() } }
    repeat(3) { scene.render(it * 16_666_667L) }
    Snapshot.sendApplyNotifications()
    scene.render(4 * 16_666_667L)
    val image = scene.render(5 * 16_666_667L)
    val bytes = Bitmap.makeFromImage(image).readPixels() ?: ByteArray(width * height * 4)
    scene.close()
    return Still(width, height, bytes)
}
