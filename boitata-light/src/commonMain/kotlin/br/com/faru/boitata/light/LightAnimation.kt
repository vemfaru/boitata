package br.com.faru.boitata.light

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color

/**
 * A light eased to where it is now, so switching a lamp, lighting a candle or dragging a lamp moves the light across the scene (its
 * pool, the shading, every shadow) instead of jumping. Where it stands follows quickly, so a lamp being dragged does not leave its
 * light behind; how hard it shines and its colour ease more slowly. With [reducedMotion] it snaps.
 *
 * Reading the result recomposes the caller on every frame of the easing. Where the light only feeds drawing (shading, glows), use
 * [rememberEasedLightState] and read `.value` inside the draw block instead: the frames of the easing then cost a redraw, not a recomposition.
 */
@Composable
fun rememberEasedLight(target: LightSource?, reducedMotion: Boolean = false): LightSource? =
    rememberEasedLightState(target, reducedMotion).value

/**
 * [rememberEasedLight] as a [State]: read `.value` in a draw block (`drawBehind`, `drawWithCache`, `graphicsLayer {}`) so the easing invalidates
 * only the drawing that uses it. The animations run as before; nothing is read in composition.
 */
@Composable
fun rememberEasedLightState(target: LightSource?, reducedMotion: Boolean = false): State<LightSource?> {
    fun <T> ease() = if (reducedMotion) snap<T>() else spring<T>(dampingRatio = 0.85f, stiffness = 90f)
    fun <T> follow() = if (reducedMotion) snap<T>() else spring<T>(dampingRatio = 0.9f, stiffness = 380f)
    val x = animateFloatAsState(target?.x ?: 0f, follow(), label = "lightX")
    val y = animateFloatAsState(target?.y ?: 0f, follow(), label = "lightY")
    val gx = animateFloatAsState(target?.groundX ?: 0f, follow(), label = "lightGroundX")
    val gy = animateFloatAsState(target?.groundY ?: 0f, follow(), label = "lightGroundY")
    val strength = animateFloatAsState(target?.strength ?: 0f, ease(), label = "lightStrength")
    val reach = animateFloatAsState(target?.reach ?: 1000f, ease(), label = "lightReach")
    val color = animateColorAsState(target?.color ?: Color.Transparent, ease(), label = "lightColor")
    val current = rememberUpdatedState(target)
    return remember {
        derivedStateOf {
            current.value?.copy(
                x = x.value, y = y.value, groundX = gx.value, groundY = gy.value,
                strength = strength.value, reach = reach.value, color = color.value,
            )
        }
    }
}
