package br.com.faru.boitata.scene.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import br.com.faru.boitata.core.clock.LocalSceneClock
import br.com.faru.boitata.core.clock.rememberSceneClock

/** What a [HoverIntent] has decided: the pointer has been over [target] long enough to preview it ([Preview], `null` when it left), or has rested on it ([Dwell]). */
public sealed interface HoverEvent<out K> {
    public data class Preview<K>(val target: K?) : HoverEvent<K>
    public data class Dwell<K>(val target: K) : HoverEvent<K>
}

/**
 * What a mouse means by where it is, as plain arithmetic (no clock, no input system), so it can be tested with made-up moves: over a thing for [enterDelay] seconds is a
 * **preview** of it (sweeping across a room does not flicker through everything it passes), and resting still on it (moves under [slop] pixels) for [dwell] seconds is a
 * **dwell**, as good as a click. Leaving to nothing ends the preview at once; moving onto another thing keeps the old preview until the new one has been held [enterDelay].
 * A dwell fires once per visit.
 */
public class HoverIntent<K>(public val enterDelay: Float = 0.08f, public val dwell: Float = 0.65f, public val slop: Float = 6f) {
    /** What is being previewed now, or `null`. */
    public var preview: K? = null
        private set

    private var over: K? = null
    private var overFor = 0f
    private var stillFor = 0f
    private var anchor = Offset.Zero
    private var dwelt = false

    /** The pointer is at [position], over [target] (`null`: over nothing, or gone). Returns what that decides at once, if anything. */
    public fun move(target: K?, position: Offset): HoverEvent<K>? {
        if (target != over) {
            over = target; overFor = 0f; stillFor = 0f; anchor = position; dwelt = false
            if (target == null && preview != null) { preview = null; return HoverEvent.Preview(null) }
            return null
        }
        if ((position - anchor).getDistance() > slop) { anchor = position; stillFor = 0f }
        return null
    }

    /** Time passes ([dt] seconds). Returns what that decides, if anything. */
    public fun step(dt: Float): HoverEvent<K>? {
        val target = over ?: return null
        overFor += dt; stillFor += dt
        if (preview != target && overFor >= enterDelay) { preview = target; return HoverEvent.Preview(target) }
        if (!dwelt && preview == target && stillFor >= dwell) { dwelt = true; return HoverEvent.Dwell(target) }
        return null
    }

    /** Forgets the visit (a click already chose, a wheel opened by hand): a dwell will not fire until the pointer comes back. */
    public fun consume() { dwelt = true }
}

/** A [HoverIntent] running on real time and telling [onPreview] and [onDwell]. Feed it with [move] or with [Modifier.hoverIntent]. */
@Stable
public class HoverIntentState<K> internal constructor(internal val intent: HoverIntent<K>, private val emit: (HoverEvent<K>) -> Unit) {
    public fun move(target: K?, position: Offset) { intent.move(target, position)?.let(emit) }
    public fun consume() { intent.consume() }
    internal fun step(dt: Float) { intent.step(dt)?.let(emit) }
}

/**
 * Remembers a [HoverIntent] (preview after [enterDelayMs], dwell after [dwellMs]) stepped on the scene's **real** time, so it works under reduced motion and while the scene's
 * own time is slowed.
 */
@Composable
public fun <K> rememberHoverIntent(onPreview: (K?) -> Unit, onDwell: (K) -> Unit, enterDelayMs: Int = 80, dwellMs: Int = 650): HoverIntentState<K> {
    val preview by rememberUpdatedState(onPreview)
    val dwell by rememberUpdatedState(onDwell)
    val state = remember(enterDelayMs, dwellMs) {
        HoverIntentState(HoverIntent<K>(enterDelayMs / 1000f, dwellMs / 1000f)) { e ->
            when (e) {
                is HoverEvent.Preview -> preview(e.target)
                is HoverEvent.Dwell -> dwell(e.target)
            }
        }
    }
    val clock = LocalSceneClock.current ?: rememberSceneClock()
    LaunchedEffect(state, clock) {
        var last = clock.realSeconds()
        snapshotFlow { clock.realSeconds() }.collect { now ->
            val dt = (now - last).coerceIn(0f, 0.25f)
            last = now
            if (dt > 0f) state.step(dt)
        }
    }
    return state
}

/** Feeds [state] from the mouse (and pen) over this element: [hitTest] says what is under a point. Touch is left alone: a tap is the only way to focus there. */
public fun <K> Modifier.hoverIntent(state: HoverIntentState<K>, hitTest: (Offset) -> K?): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: continue
            if (change.type != PointerType.Mouse && change.type != PointerType.Stylus) continue
            when (event.type) {
                PointerEventType.Enter, PointerEventType.Move -> state.move(hitTest(change.position), change.position)
                PointerEventType.Exit -> state.move(null, change.position)
                else -> Unit
            }
        }
    }
}
