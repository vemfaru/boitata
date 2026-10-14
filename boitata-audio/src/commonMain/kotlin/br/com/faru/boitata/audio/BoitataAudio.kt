package br.com.faru.boitata.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import br.com.faru.boitata.core.wind.rememberSceneWind
import androidx.compose.runtime.snapshotFlow
import br.com.faru.boitata.core.clock.LocalSceneClock
import br.com.faru.boitata.core.clock.rememberSceneClock
import br.com.faru.boitata.core.cues.AmbienceLevels
import br.com.faru.boitata.core.sky.SkyWeather
import br.com.faru.boitata.core.sky.StormModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The sound of a scene, in step with its pictures. Put it inside a [br.com.faru.boitata.core.clock.BoitataScene], next to the effects it goes with:
 *
 * ```kotlin
 * BoitataScene {
 *     SkyWindow(...); Rain(); rememberStorm(...)
 *     BoitataAudio(sink, storm = stormModel, weather = weather)
 * }
 * ```
 *
 * Thunder comes from the same seeded [storm] as the flash and is scheduled on the device's clock, so it arrives after the flash by distance over the speed of sound
 * however the frames fall. Rain, wind and a fire ease with [weather] and [fire]. The sound pauses when the app goes to the background; under reduced motion the
 * thunder keeps going (there is no flash) unless [SceneAudioConfig.keepThunderUnderReducedMotion] is off. Nothing here draws or recomposes per frame.
 *
 * Make one sink for the app with `createAudioSink()` and call its `unlock()` from the first tap or key press (browsers need it).
 */
@Composable
public fun BoitataAudio(
    sink: AudioSink,
    storm: StormModel? = null,
    weather: SkyWeather = SkyWeather.Clear,
    fire: Float = 0f,
    enabled: Boolean = true,
    config: SceneAudioConfig = SceneAudioConfig(),
) {
    val clock = LocalSceneClock.current ?: rememberSceneClock()
    val scheduler = remember(sink, config) { SceneAudioScheduler(sink, config) }
    DisposableEffect(scheduler) { onDispose { scheduler.release() } }

    LaunchedEffect(scheduler, storm, enabled) { scheduler.setStorm(if (enabled) storm else null) }
    val sceneWind = rememberSceneWind()
    val currentWeather = rememberUpdatedState(weather)
    val currentFire = rememberUpdatedState(fire)
    LaunchedEffect(scheduler, enabled) {
        if (!enabled) scheduler.setLevels(AmbienceLevels(0f, 0f, 0f, 0f))
    }
    // The device sleeps with the app.
    LaunchedEffect(sink, clock) {
        snapshotFlow { clock.inForeground() }.collect { if (it) sink.resume() else sink.suspend() }
    }
    LaunchedEffect(scheduler, clock, enabled) {
        if (!enabled) return@LaunchedEffect
        // The beds and thunder are made off the main thread; the scheduler only ever runs on this one.
        launch { scheduler.provideBeds(withContext(Dispatchers.Default) { AmbienceBeds.render() }) }
        snapshotFlow { if (clock.reducedMotion && config.keepThunderUnderReducedMotion) clock.realSeconds() else clock.seconds() }
            .conflate()
            .collect { now ->
                // The wind bed swells and falls with the gusts, so it is set every frame from the scene's wind (and the weather, and the fire).
                scheduler.setLevels(AmbienceLevels.of(currentWeather.value, currentFire.value, sceneWind.at(now, 0.5f).strength))
                scheduler.advance(now)
                for (cue in scheduler.cuesToRender(now)) scheduler.provide(cue, withContext(Dispatchers.Default) { scheduler.render(cue) })
            }
    }
}
