package br.com.faru.boitata.scene.focus

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import br.com.faru.boitata.core.clock.LocalSceneClock
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Which things are drawn sharp over a focus, and how sharp each is now: the one in focus (1), and the one that just lost it, fading back into the blur. Read [alpha] in the draw
 * phase. Made by [rememberFocusFade].
 */
@Stable
public class FocusFade<K> internal constructor() {
    internal class Entry<K>(val key: K, val alpha: Animatable<Float, *>)

    internal val entries = mutableStateListOf<Entry<K>>()

    /** The things to draw sharp now, the one in focus last (so it is on top). */
    public val keys: List<K> get() = entries.map { it.key }

    /** How sharp [key] is drawn now, 0..1. */
    public fun alpha(key: K): Float = entries.firstOrNull { it.key == key }?.alpha?.value ?: 0f
}

/**
 * Follows [focus] and cross-fades the sharp layer when it moves: the thing leaving fades into the blur and the one arriving out of it ([FocusMotion.crossfadeMillis]), so the blur
 * itself never flickers. When the focus is first taken, the thing is sharp at once (the blur is what eases in); when the focus is let go, it stays sharp until the blur has gone.
 */
@Composable
public fun <K> rememberFocusFade(focus: K?, motion: FocusMotion = FocusMotion()): FocusFade<K> {
    val fade = remember { FocusFade<K>() }
    val reduced = motion.reduced ?: (LocalSceneClock.current?.reducedMotion ?: false)
    LaunchedEffect(focus, reduced) {
        val had = fade.entries.any { it.alpha.value > 0f }
        if (focus != null && fade.entries.none { it.key == focus }) {
            fade.entries += FocusFade.Entry(focus, Animatable(if (had && !reduced) 0f else 1f))
        } else if (focus != null) {
            // Back to one that was leaving: put it on top again.
            val e = fade.entries.first { it.key == focus }
            fade.entries.remove(e); fade.entries += e
        }
        coroutineScope {
            for (e in fade.entries.toList()) launch {
                when {
                    e.key == focus -> if (reduced) e.alpha.snapTo(1f) else e.alpha.animateTo(1f, tween(motion.crossfadeMillis))
                    focus == null -> if (reduced) e.alpha.snapTo(0f) else e.alpha.animateTo(0f, tween(1, delayMillis = motion.outMillis))
                    else -> if (reduced) e.alpha.snapTo(0f) else e.alpha.animateTo(0f, tween(motion.crossfadeMillis))
                }
            }
        }
        fade.entries.removeAll { it.key != focus && it.alpha.value <= 0f }
    }
    return fade
}

/**
 * A scene with one thing in focus, drawn in passes: the whole [scene] blurred and darkened by [strength]; then the thing in [focus] drawn [sharp] again at its own place; then
 * what stands [front] of it, blurred again, so things in front of the one in focus stay in front of it. When the focus moves from one thing to another, the blur stays and the two
 * cross-fade (see [rememberFocusFade]). With no focus the scene is drawn once, sharp.
 *
 * [scene], [sharp] and [front] are drawn in the same box and coordinates; [sharp] and [front] should draw only that thing and what is in front of it.
 */
@Composable
public fun <K> FocusPasses(
    focus: K?,
    strength: FocusStrength?,
    modifier: Modifier = Modifier,
    motion: FocusMotion = FocusMotion(),
    wash: Color = Color.Black,
    scene: @Composable BoxScope.() -> Unit,
    sharp: @Composable BoxScope.(K) -> Unit,
    front: @Composable BoxScope.(K) -> Unit = {},
) {
    val fade = rememberFocusFade(focus, motion)
    val shown = lastStrength(strength)
    Box(modifier) {
        Box(Modifier.matchParentSize().focusBlur(if (focus != null) strength else null, motion, wash, edges = TileMode.Clamp)) { scene() }
        for (k in fade.keys) key(k) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = fade.alpha(k) }) { sharp(k) }
            Box(Modifier.matchParentSize().focusBlurLayer(shown, motion, wash, true, startFocused = true).graphicsLayer { alpha = fade.alpha(k) }) { front(k) }
        }
    }
}

/** The last strength that was not null, so the passes keep their look while the focus fades out. */
@Composable
private fun lastStrength(strength: FocusStrength?): FocusStrength? {
    val held = remember { arrayOfNulls<FocusStrength>(1) }
    if (strength != null) held[0] = strength
    return held[0]
}
