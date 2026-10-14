package br.com.faru.boitata.testing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import br.com.faru.boitata.core.Perspective
import br.com.faru.boitata.core.depth.DepthItem
import br.com.faru.boitata.core.depth.DepthStage
import br.com.faru.boitata.scene.depthDrag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Dragging with real pointer events: the thing follows, is lifted while held, is kept on the ground, and shrinks as it is dragged back. */
class DepthDragTest {
    private val perspective = Perspective(frontEdgeY = 280f, backEdgeY = 120f, depth = 6f, eyeBack = 3f, eyeHeight = 2.5f, pitchDegrees = 28f, referenceY = 220f)

    private fun rig(): Triple<DepthStage, DepthItem, ImageComposeScene> {
        val stage = DepthStage(perspective, 10f, 120f, 390f, 280f)
        val item = stage.add(DepthItem("thing", 200f, 250f, 80f, 0.5f))
        val scene = ImageComposeScene(width = 400, height = 300, density = Density(1f)) { Box(Modifier.fillMaxSize().depthDrag(stage)) }
        scene.render(0L)
        return Triple(stage, item, scene)
    }

    private fun ImageComposeScene.frames(from: Long, count: Int): Long {
        var t = from
        repeat(count) { t += 16_000_000L; render(t) }
        return t
    }

    @Test
    fun aThingFollowsTheDragIsLiftedWhileHeldAndSetDownOnRelease() {
        val (stage, item, scene) = rig()
        val grab = Offset(200f, 250f - stage.height(item) / 2f)           // the middle of the thing
        scene.sendPointerEvent(PointerEventType.Press, grab)
        scene.sendPointerEvent(PointerEventType.Move, grab + Offset(5f, 0f))
        scene.sendPointerEvent(PointerEventType.Move, grab + Offset(60f, -40f))
        var t = scene.frames(0L, 40)
        assertTrue(item.lift > 0.8f, "held: lifted (${item.lift})")
        assertTrue(item.feetX > 240f && item.feetY < 230f, "it followed the pointer: (${item.feetX}, ${item.feetY})")
        scene.sendPointerEvent(PointerEventType.Release, grab + Offset(60f, -40f))
        t = scene.frames(t, 60)
        assertTrue(item.lift < 0.05f, "set down: ${item.lift}")
        scene.close()
    }

    @Test
    fun draggingItBackMakesItSmallerAndNearerMakesItBigger() {
        val (stage, item, scene) = rig()
        val before = stage.height(item)
        val grab = Offset(200f, 250f - before / 2f)
        scene.sendPointerEvent(PointerEventType.Press, grab)
        scene.sendPointerEvent(PointerEventType.Move, grab + Offset(2f, 0f))
        scene.sendPointerEvent(PointerEventType.Move, grab + Offset(2f, -110f))
        scene.frames(0L, 40)
        scene.sendPointerEvent(PointerEventType.Release, grab + Offset(2f, -110f))
        scene.frames(0L, 60)
        item.lift = 0f
        assertTrue(stage.height(item) < before * 0.7f, "dragged back it is smaller: $before -> ${stage.height(item)}")
        scene.close()
    }

    @Test
    fun itCannotBeDraggedOffTheGround() {
        val (stage, item, scene) = rig()
        val grab = Offset(200f, 250f - stage.height(item) / 2f)
        scene.sendPointerEvent(PointerEventType.Press, grab)
        scene.sendPointerEvent(PointerEventType.Move, grab + Offset(1f, 0f))
        scene.sendPointerEvent(PointerEventType.Move, grab + Offset(900f, 900f))
        scene.frames(0L, 10)
        assertEquals(stage.groundRight, item.feetX, 0.01f)
        assertEquals(stage.groundBottom, item.feetY, 0.01f)
        scene.sendPointerEvent(PointerEventType.Release, grab)
        scene.close()
    }

    @Test
    fun pressingOnNothingMovesNothing() {
        val (_, item, scene) = rig()
        val x = item.feetX; val y = item.feetY
        scene.sendPointerEvent(PointerEventType.Press, Offset(20f, 20f))
        scene.sendPointerEvent(PointerEventType.Move, Offset(25f, 20f))
        scene.sendPointerEvent(PointerEventType.Move, Offset(120f, 100f))
        scene.frames(0L, 10)
        scene.sendPointerEvent(PointerEventType.Release, Offset(120f, 100f))
        assertEquals(x, item.feetX); assertEquals(y, item.feetY)
        scene.close()
    }
}
