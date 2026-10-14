package br.com.faru.boitata.core.clock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow

/**
 * Runs [step] on the scene's clock: every time the clock moves it is called with how much time passed ([dt], seconds, held to a quarter of a second so a long frame does not blow a simulation up) and the scene's time. It stops
 * when the clock does (a paused app, reduced motion), and starts again from the clock when it resumes. For simulations that keep state (liquids, a pane's frost); [key] restarts it when it changes.
 */
@Composable
public fun RunOnSceneClock(key: Any?, step: (dt: Float, seconds: Float) -> Unit) {
    val clock = LocalSceneClock.current ?: rememberSceneClock()
    val current = rememberUpdatedState(step)
    LaunchedEffect(key, clock) {
        var last = clock.seconds()
        snapshotFlow { clock.seconds() }.collect { now ->
            val dt = (now - last).coerceIn(0f, 0.25f)
            last = now
            if (dt > 0f) current.value(dt, now)
        }
    }
}
