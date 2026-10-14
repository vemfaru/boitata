package br.com.faru.boitata.core.wind

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import br.com.faru.boitata.core.clock.LocalSceneClock
import br.com.faru.boitata.core.clock.rememberSceneClock

/**
 * The wind of the scene a thing is in, for effects to read in their draw blocks. Everything that floats, falls or sways reads it, so changing the wind in one place changes all of it.
 *
 * Two things are kept: the [wind] itself (read it at a time and place with [at]) and [travelled], the distance the air has moved since the scene began. [travelled] is what slow things
 * (fog, clouds) drift by: it is the wind added up over time, so changing the wind makes them speed up or slow down smoothly and never jump.
 */
@Stable
public class SceneWind internal constructor(private val current: State<Wind>, private val moved: State<Float>) {
    public val wind: Wind get() = current.value

    /** The wind at [place] (0 left to 1 right) at [seconds]. */
    public fun at(seconds: Float, place: Float = 0f): WindSample = wind.at(seconds, place)

    /** How far the air has gone since the scene began (signed: negative is to the left). */
    public val travelled: Float get() = moved.value
}

/** The wind of the nearest [BoitataScene] (or [WithSceneWind]); null outside one. */
public val LocalSceneWind: ProvidableCompositionLocal<SceneWind?> = compositionLocalOf { null }

private val CalmWind = SceneWind(object : State<Wind> { override val value: Wind = Wind.Calm }, object : State<Float> { override val value: Float = 0f })

/** The scene's wind, or a calm one outside a scene: effects use it when no wind was given. */
@Composable
public fun rememberSceneWind(): SceneWind = LocalSceneWind.current ?: CalmWind

/**
 * Gives everything inside this [wind]. [BoitataScene] does it for you (`BoitataScene(wind = ...)`); use this directly to put a wind over a recording or a test that runs on a [br.com.faru.boitata.core.clock.FixedSceneClock].
 * The distance the air has travelled is added up from the clock, so it is right however the frames fall.
 */
@Composable
public fun WithSceneWind(wind: Wind, content: @Composable () -> Unit) {
    val clock = LocalSceneClock.current ?: rememberSceneClock()
    val current = rememberUpdatedState(wind)
    val moved = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(clock) {
        var last = clock.seconds()
        snapshotFlow { clock.seconds() }.collect { now ->
            val dt = (now - last).coerceIn(0f, 0.25f)
            last = now
            if (dt > 0f) moved.floatValue += current.value.at(now).x * dt
        }
    }
    val sceneWind = remember { SceneWind(current, moved) }
    CompositionLocalProvider(LocalSceneWind provides sceneWind) { content() }
}
