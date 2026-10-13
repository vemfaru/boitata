package br.com.faru.boitata.light

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

/**
 * A light eased to where it is now, so switching a lamp, lighting a candle or dragging a lamp moves the light across the scene (its
 * pool, the shading, every shadow) instead of jumping. Where it stands follows quickly, so a lamp being dragged does not leave its
 * light behind; how hard it shines and its colour ease more slowly. With [reducedMotion] it snaps.
 */
@Composable
fun rememberEasedLight(target: LightSource?, reducedMotion: Boolean = false): LightSource? {
    fun <T> ease() = if (reducedMotion) snap<T>() else spring<T>(dampingRatio = 0.85f, stiffness = 90f)
    fun <T> follow() = if (reducedMotion) snap<T>() else spring<T>(dampingRatio = 0.9f, stiffness = 380f)
    val x by animateFloatAsState(target?.x ?: 0f, follow(), label = "lightX")
    val y by animateFloatAsState(target?.y ?: 0f, follow(), label = "lightY")
    val gx by animateFloatAsState(target?.groundX ?: 0f, follow(), label = "lightGroundX")
    val gy by animateFloatAsState(target?.groundY ?: 0f, follow(), label = "lightGroundY")
    val strength by animateFloatAsState(target?.strength ?: 0f, ease(), label = "lightStrength")
    val reach by animateFloatAsState(target?.reach ?: 1000f, ease(), label = "lightReach")
    val color by animateColorAsState(target?.color ?: Color.Transparent, ease(), label = "lightColor")
    if (target == null) return null
    return target.copy(x = x, y = y, groundX = gx, groundY = gy, strength = strength, reach = reach, color = color)
}
