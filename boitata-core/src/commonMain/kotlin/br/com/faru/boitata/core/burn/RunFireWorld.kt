package br.com.faru.boitata.core.burn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import br.com.faru.boitata.core.clock.LocalSceneClock
import br.com.faru.boitata.core.clock.rememberSceneClock
import br.com.faru.boitata.core.wind.rememberSceneWind

/**
 * Runs [world] on the scene's clock: every frame it moves the fires on by the time that passed, with the scene's wind and the [rain] you give (0..1, read as it is needed).
 * [timeScale] runs the fires faster than the clock (3 is a time-lapse). The fire stops when the clock does (a paused app, reduced motion).
 */
@Composable
public fun RunFireWorld(world: FireWorld, rain: () -> Float = { 0f }, timeScale: Float = 1f) {
    val clock = LocalSceneClock.current ?: rememberSceneClock()
    val sceneWind = rememberSceneWind()
    val currentRain = rememberUpdatedState(rain)
    val scale = rememberUpdatedState(timeScale)
    LaunchedEffect(world, clock) {
        var last = clock.seconds()
        snapshotFlow { clock.seconds() }.collect { now ->
            val dt = (now - last).coerceIn(0f, 0.25f)
            last = now
            if (dt > 0f) world.step(dt * scale.value, now, sceneWind.wind, currentRain.value())
        }
    }
}
