package br.com.faru.boitata.core.clock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.isActive

/** What a scene's clock is allowed to do. */
public class SceneConfig(
    /** Start here, or stay here if [adaptiveQuality] is off. */
    public val quality: Quality = Quality.High,
    /** Step [quality] down when frames run long and back up when there is room (see [QualityGovernor]). */
    public val adaptiveQuality: Boolean = true,
    /** The frame time to protect, in frames per second. 60 suits most screens; 120 on a high-refresh phone that can hold it. */
    public val targetFps: Int = 60,
    /** Follow the system's "reduce motion" setting, or force it on or off. With it on, time stands still: the scene is drawn but nothing drifts. */
    public val reducedMotion: ReducedMotionMode = ReducedMotionMode.Auto,
    /** Stop the clock while the screen is not in the foreground (a background tab, a locked phone, another app in front). */
    public val pauseWhenNotResumed: Boolean = true,
)

public enum class ReducedMotionMode { Auto, On, Off }

/**
 * One clock for a whole scene: every effect reads its time from here, so there is a single frame loop however many things move, they all agree on
 * "now", and pausing, reduced motion and the quality budget are decided in one place.
 *
 * Everything is read through lambdas: read them in a draw block (`drawBehind`, `Canvas`, `graphicsLayer {}`), never in the body of a composable, or the
 * scene recomposes on every frame.
 */
@Stable
public interface SceneClock {
    /** Seconds since the scene began; stands still while paused or under reduced motion. */
    public val seconds: () -> Float

    /** The quality to draw at now; it changes rarely, and only when [SceneConfig.adaptiveQuality] is on. */
    public val quality: () -> Quality

    /** Whether the scene should hold still (the system asks for less motion). Effects that move should draw their still frame. */
    public val reducedMotion: Boolean

    /**
     * Seconds that keep running under reduced motion, for things that are not motion: a sound still has to arrive on time when the flash is switched off.
     * The same as [seconds] otherwise; stands still only while the screen is not in the foreground.
     */
    public val realSeconds: () -> Float

    /** Whether the screen is in the foreground. When it is not, the clock is stopped and sound should be suspended. */
    public val inForeground: () -> Boolean
}

/** A clock that follows a time you give it, for recordings, screenshots and tests: nothing waits for a frame. */
public class FixedSceneClock(
    override val seconds: () -> Float,
    private val fixedQuality: Quality = Quality.High,
    override val reducedMotion: Boolean = false,
    private val foreground: () -> Boolean = { true },
    private val real: (() -> Float)? = null,
) : SceneClock {
    override val quality: () -> Quality = { fixedQuality }
    override val realSeconds: () -> Float = real ?: seconds
    override val inForeground: () -> Boolean = foreground
}

/** The clock of the nearest [BoitataScene]; null outside one, where each effect makes a clock of its own (correct, but not shared). */
public val LocalSceneClock: androidx.compose.runtime.ProvidableCompositionLocal<SceneClock?> = compositionLocalOf { null }

/** Time for an effect: the scene's clock, or one made for it if it stands outside a [BoitataScene]. */
@Composable
public fun rememberSceneSeconds(): () -> Float = (LocalSceneClock.current ?: rememberSceneClock()).seconds

/** The quality for an effect: the scene's, or the default if it stands outside a [BoitataScene]. */
@Composable
public fun rememberSceneQuality(): () -> Quality = (LocalSceneClock.current ?: rememberSceneClock()).quality

@Stable
private class FrameSceneClock(config: SceneConfig) : SceneClock {
    var time by mutableFloatStateOf(START)
    var real by mutableFloatStateOf(START)
    var current by mutableStateOf(config.quality)
    var reduced by mutableStateOf(false)
    var foreground by mutableStateOf(true)
    override val seconds: () -> Float = { time }
    override val realSeconds: () -> Float = { real }
    override val inForeground: () -> Boolean = { foreground }
    override val quality: () -> Quality = { current }
    override val reducedMotion: Boolean get() = reduced
}

private const val START = 3f

/**
 * A clock that runs a frame loop while the screen is in the foreground and motion is allowed, adapting [Quality] if the config says so.
 * Most scenes want [BoitataScene], which makes one and shares it with everything inside.
 */
@Composable
public fun rememberSceneClock(config: SceneConfig = SceneConfig()): SceneClock {
    val clock = remember { FrameSceneClock(config) }
    val systemReduced = rememberSystemReducedMotion()
    val reduced = when (config.reducedMotion) {
        ReducedMotionMode.Auto -> systemReduced
        ReducedMotionMode.On -> true
        ReducedMotionMode.Off -> false
    }
    clock.reduced = reduced
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateAsState()
    val running = !config.pauseWhenNotResumed || state.isAtLeast(Lifecycle.State.RESUMED)
    clock.foreground = running
    LaunchedEffect(running, reduced, config.adaptiveQuality, config.targetFps) {
        if (!running) return@LaunchedEffect
        val governor = QualityGovernor(1f / config.targetFps, config.quality)
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = (now - last) / 1_000_000_000f
                    // A long frame (a hitch, a resume) must not make time jump: clamp what the scene sees, keep the real value for the governor.
                    val step = dt.coerceAtMost(MAX_STEP)
                    clock.real += step
                    // Under reduced motion only the real time runs (for sound): nothing visible reads it, so nothing is redrawn.
                    if (!reduced) {
                        clock.time += step
                        if (config.adaptiveQuality) clock.current = governor.onFrame(dt)
                    }
                }
                last = now
            }
        }
    }
    return clock
}

private const val MAX_STEP = 0.1f

/**
 * The home of a scene. Everything inside shares one clock, one quality budget and one pause and reduced-motion decision:
 * `Rain`, `Snow`, `Fog`, `Fire`, `SkyWindow`, `WindowLight`, `rememberStorm` all read them from here.
 */
@Composable
public fun BoitataScene(
    modifier: Modifier = Modifier,
    config: SceneConfig = SceneConfig(),
    content: @Composable BoxScope.() -> Unit,
) {
    val clock = rememberSceneClock(config)
    CompositionLocalProvider(LocalSceneClock provides clock) {
        Box(modifier) { content() }
    }
}

/** Runs [content] on a clock you control (`FixedSceneClock`), for recordings and tests. */
@Composable
public fun WithSceneClock(clock: SceneClock, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSceneClock provides clock) { content() }
}

/** Whether the system asks for less motion (Android: animations turned off, iOS: Reduce Motion, web: `prefers-reduced-motion`). */
@Composable
public expect fun rememberSystemReducedMotion(): Boolean
