package br.com.faru.boitata.light

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A wheel of choices around a container: [actions] sit on an arc over [anchor] (a point in this composable's own pixels, the top of the container), and open from it with a springy scale. Tapping an enabled one
 * calls [onAction]; tapping anywhere else calls [onDismiss]. Draw it over the scene, filling it. With [animate] false it just appears (for people who ask for less motion).
 */
@Composable
public fun VesselWheel(
    actions: List<VesselAction>, anchor: Offset, onAction: (VesselAction) -> Unit, onDismiss: () -> Unit,
    modifier: Modifier = Modifier, radius: Dp = 84.dp, animate: Boolean = true,
    ink: Color = Color(0xFF14110F), paper: Color = Color(0xFFE9DFC6),
) {
    val open = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) { if (animate) open.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)) else open.snapTo(1f) }
    val r = with(LocalDensity.current) { radius.toPx() }
    Box(modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
        Layout(
            content = {
                for (a in actions) {
                    BasicText(
                        a.label,
                        style = TextStyle(color = paper, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier
                            .graphicsLayer { val o = open.value; scaleX = o; scaleY = o; alpha = o.coerceIn(0f, 1f) * (if (a.enabled) 1f else 0.4f) }
                            .semantics { role = Role.Button; contentDescription = a.label }
                            .background(ink.copy(alpha = 0.92f), RoundedCornerShape(50))
                            .border(1.5.dp, paper.copy(alpha = if (a.enabled) 0.9f else 0.4f), RoundedCornerShape(50))
                            .pointerInput(a) { detectTapGestures { if (a.enabled) onAction(a) } }
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
                placeables.forEachIndexed { i, p ->
                    // Along the top half of a circle round the anchor, from the left to the right.
                    val turn = if (n == 1) 0.5f else i / (n - 1f)
                    val angle = PI.toFloat() * (1.1f + 0.8f * turn)
                    val grow = if (animate) open.value else 1f
                    val cx = anchor.x + r * cos(angle) * grow
                    val cy = anchor.y + r * sin(angle) * grow
                    p.place((cx - p.width / 2f).roundToInt().coerceIn(0, (constraints.maxWidth - p.width).coerceAtLeast(0)), (cy - p.height / 2f).roundToInt().coerceAtLeast(0))
                }
            }
        }
    }
}
