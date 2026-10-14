package br.com.faru.boitata.scene

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import br.com.faru.boitata.core.depth.DepthItem
import br.com.faru.boitata.core.depth.DepthStage
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Makes the things on a [DepthStage] draggable: press on one and it lifts, drag it about the ground (kept on the ground, so dragging up sends it back and smaller, dragging down
 * brings it forward and bigger) and let go to set it down. Where things overlap, the nearest is the one picked.
 *
 * The positions are state read in the draw phase, so a drag redraws and recomposes nothing. Apply it to the same box the stage is drawn in, so the pointer and the picture share pixels.
 * [onGrab] and [onRelease] tell you which item was picked up and put down (to play a sound, say); [onTap] tells you which one was tapped without being moved.
 */
public fun Modifier.depthDrag(
    stage: DepthStage,
    onGrab: (DepthItem) -> Unit = {},
    onRelease: (DepthItem) -> Unit = {},
    onTap: (DepthItem) -> Unit = {},
): Modifier = pointerInput(stage) {
    detectTapGestures(onTap = { at -> stage.hit(at.x, at.y)?.let(onTap) })
}.pointerInput(stage) {
    coroutineScope {
        var grabbed: DepthItem? = null
        val lifting = HashMap<DepthItem, Job>()
        fun liftTo(item: DepthItem, target: Float) {
            lifting[item]?.cancel()
            lifting[item] = launch {
                // A spring, so a grab that is let go of half way turns round without a jump.
                animate(item.lift, target, animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)) { value, _ -> item.lift = value }
            }
        }
        detectDragGestures(
            onDragStart = { at ->
                grabbed = stage.hit(at.x, at.y)?.also { liftTo(it, 1f); onGrab(it) }
            },
            onDragEnd = { grabbed?.let { liftTo(it, 0f); onRelease(it) }; grabbed = null },
            onDragCancel = { grabbed?.let { liftTo(it, 0f); onRelease(it) }; grabbed = null },
            onDrag = { change, delta ->
                val item = grabbed ?: return@detectDragGestures
                change.consume()
                stage.moveBy(item, delta.x, delta.y)
            },
        )
    }
}
