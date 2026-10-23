package br.com.faru.boitata.light

import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.faru.boitata.core.pour.VesselAction
import br.com.faru.boitata.scene.focus.rememberPopReveal
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A wheel of choices around a container: [actions] sit on an arc over [anchor] (a point in this composable's own pixels, the top of the container), and pop out of it one after another, from the left of the arc ([rememberPopReveal]); with [open] false they pop back in, last first, and the wheel stops taking taps. Tapping an enabled one
 * calls [onAction]; an action with `hold` calls [onHold] with true when it is pressed and with false when it is let go (it goes on for as long as it is held: sipping, pouring), and the wheel stays up while it is held; tapping anywhere else calls [onDismiss]. While [hidden] it is not drawn but still there, so a press that is held keeps its release. Draw it over the scene, filling it. With [animate] false it just appears (for people who ask for less motion).
 */
@Composable
public fun VesselWheel(
    actions: List<VesselAction>, anchor: Offset, onAction: (VesselAction) -> Unit, onDismiss: () -> Unit,
    modifier: Modifier = Modifier, onHold: (VesselAction, Boolean) -> Unit = { _, _ -> }, hidden: Boolean = false, radius: Dp = 84.dp, animate: Boolean = true, open: Boolean = true,
    ink: Color = Color(0xFF14110F), paper: Color = Color(0xFFE9DFC6),
) {
    // The keys pop in one after another from the left of the arc (and out the other way when [open] turns false).
    val reveal = rememberPopReveal(actions.size, open, reduced = if (animate) null else true)
    val r = with(LocalDensity.current) { radius.toPx() }
    Box(modifier.fillMaxSize().then(if (open) Modifier.pointerInput(Unit) { detectTapGestures { onDismiss() } } else Modifier)) {
        Layout(
            content = {
                for ((index, a) in actions.withIndex()) key(a.id) {
                    // The latest action and callbacks, so a press that is held keeps working while the wheel recomposes under the finger.
                    val now by rememberUpdatedState(a)
                    val tap by rememberUpdatedState(onAction)
                    val hold by rememberUpdatedState(onHold)
                    BasicText(
                        a.label,
                        style = TextStyle(color = paper, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier
                            .graphicsLayer { val k = reveal.scale(index); scaleX = k; scaleY = k; alpha = if (hidden) 0f else reveal.alpha(index) * (if (now.enabled) 1f else 0.4f) }
                            .semantics { role = Role.Button; contentDescription = a.label }
                            .background(ink.copy(alpha = 0.92f), RoundedCornerShape(50))
                            .border(1.5.dp, paper.copy(alpha = if (a.enabled) 0.9f else 0.4f), RoundedCornerShape(50))
                            .pointerInput(a.id) {
                                if (a.hold) awaitEachGesture {
                                    awaitFirstDown()
                                    if (now.enabled) {
                                        hold(now, true)
                                        waitForUpOrCancellation()
                                        hold(now, false)
                                    } else waitForUpOrCancellation()
                                } else detectTapGestures { if (now.enabled) tap(now) }
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val placeables = measurables.map { it.measure(loose) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                val n = placeables.size
                // Wide enough that the keys sit side by side along the arc without covering each other.
                val arc = PI.toFloat() * 0.8f
                val needed = (placeables.sumOf { it.width } + 8f * (n - 1)) / arc
                val rr = maxOf(r, needed)
                placeables.forEachIndexed { i, p ->
                    // Along the top half of a circle round the anchor, from the left to the right.
                    val turn = if (n == 1) 0.5f else i / (n - 1f)
                    val angle = PI.toFloat() * (1.1f + 0.8f * turn)
                    // Each key comes out from nearer the container as it pops.
                    val grow = reveal.scale(i)
                    val cx = anchor.x + rr * cos(angle) * grow
                    val cy = anchor.y + rr * sin(angle) * grow
                    p.place((cx - p.width / 2f).roundToInt().coerceIn(0, (constraints.maxWidth - p.width).coerceAtLeast(0)), (cy - p.height / 2f).roundToInt().coerceAtLeast(0))
                }
            }
        }
    }
}
