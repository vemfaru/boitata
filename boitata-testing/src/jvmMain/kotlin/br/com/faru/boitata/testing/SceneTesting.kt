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
