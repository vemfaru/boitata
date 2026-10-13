package br.com.faru.boitata.scene

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/** Where the middle of the camera is now, in world units (it eases between cameras): read in the draw phase to shift far things less than near ones. */
val LocalCameraCenter = androidx.compose.runtime.compositionLocalOf<() -> androidx.compose.ui.geometry.Offset> { { androidx.compose.ui.geometry.Offset.Zero } }

/** A rect of the world, in world units, that the window shows. */
@Immutable
data class SceneCamera(val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * A world laid out once in its own units, seen through a [camera]: the window shows the camera's rect, fitted and centred. Inside,
 * `1.dp` is one world unit at the camera's scale, so text and images rasterise sharp. Moving between cameras animates through a
 * graphics layer (no relayout per frame, [cameraSpec] sets how it eases); the density follows the camera being moved to.
 *
 * [overlay] is drawn in window space on top of the world (screen chrome).
 */
@Composable
fun SceneViewport(
    camera: SceneCamera,
    sceneWidth: Float,
    sceneHeight: Float,
    cameraSpec: AnimationSpec<Float>,
    modifier: Modifier = Modifier,
    background: Color = Color.Black,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(background).clipToBounds()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        fun fit(width: Float, height: Float) = minOf(w / width, h / height)

        val x by animateFloatAsState(camera.x, cameraSpec, label = "cameraX")
        val y by animateFloatAsState(camera.y, cameraSpec, label = "cameraY")
        val cw by animateFloatAsState(camera.width, cameraSpec, label = "cameraWidth")
        val ch by animateFloatAsState(camera.height, cameraSpec, label = "cameraHeight")
        val target = fit(camera.width, camera.height)

        CompositionLocalProvider(LocalDensity provides Density(target, 1f), LocalCameraCenter provides { androidx.compose.ui.geometry.Offset(x + cw / 2f, y + ch / 2f) }) {
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(sceneWidth.dp, sceneHeight.dp)
                    .graphicsLayer {
                        val scale = fit(cw, ch)
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale / target
                        scaleY = scale / target
                        translationX = -x * scale + (w - cw * scale) / 2
                        translationY = -y * scale + (h - ch * scale) / 2
                    },
                content = content,
            )
        }
        overlay()
    }
}

/** Like a plain box at ([x], [y]) of [width] by [height] world units, but it may be bigger than its parent (the world is bigger than any one screen). */
fun Modifier.worldBounds(x: Number, y: Number, width: Number, height: Number): Modifier =
    wrapContentSize(Alignment.TopStart, unbounded = true).offset(x.toFloat().dp, y.toFloat().dp).requiredSize(width.toFloat().dp, height.toFloat().dp)
