package br.com.faru.boitata.plants

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import br.com.faru.boitata.core.AdvancedBoitataApi
import br.com.faru.boitata.core.clock.RunOnSceneClock
import br.com.faru.boitata.core.draw.sway
import br.com.faru.boitata.core.sky.DayClock
import br.com.faru.boitata.core.sky.SkyModel
import br.com.faru.boitata.core.sky.SkyWeather

/**
 * A bed of plants sharing one soil: a fixed set of [PlantState]s made once, so stepping it allocates nothing. Plants of different [species] can share the bed; each is its own [PlantState].
 */
@Stable
public class PlantPatch(public val soil: Soil, public val species: List<PlantSpecies>, moisture: Float = 0.5f) {
    public val plants: List<PlantState> = species.map { PlantState() }
    public val wet: SoilMoisture = SoilMoisture(moisture)

    /** Moves the soil and every plant on by [dtHours] under [env]. */
    public fun step(env: PlantEnvironment, dtHours: Float) {
        var thirst = 0f
        for (i in plants.indices) thirst += PlantModel.step(plants[i], species[i], soil, wet, env, dtHours)
        PlantModel.stepSoil(wet, soil, env, dtHours, thirst)
    }
}

/**
 * The day, running on the scene's clock: [hoursPerSecond] hours of the day go by each real second. Read [clock] in a draw block (it changes every frame; reading it there does not recompose).
 */
@Stable
public class RunningDay internal constructor(start: DayClock, hoursPerSecond: Float) {
    public var clock: DayClock by mutableStateOf(start)
        internal set

    /** How fast the day goes; change it any time (0 stops it). */
    public var hoursPerSecond: Float by mutableStateOf(hoursPerSecond)
}

/** A [RunningDay] that starts at [start] and moves with the scene's clock, so it stops when the scene pauses. */
@Composable
public fun rememberRunningDay(start: DayClock = DayClock.at(8f), hoursPerSecond: Float = 0.5f): RunningDay {
    val day = remember { RunningDay(start, hoursPerSecond) }
    RunOnSceneClock(day) { dt, _ -> day.clock = day.clock.advanced(dt * day.hoursPerSecond) }
    return day
}

/**
 * Lets [patch] live under [day] with the given [weather] and wind strength: each scene frame it works out the sky for the hour and steps the plants by the hours that went by.
 * The weather and wind are read as functions so a slider can change them without recomposing.
 */
@Composable
public fun TendPlants(patch: PlantPatch, day: RunningDay, weather: () -> SkyWeather = { SkyWeather.Clear }, windStrength: () -> Float = { 0f }) {
    RunOnSceneClock(patch) { dt, _ ->
        val hours = dt * day.hoursPerSecond
        if (hours > 0f) patch.step(PlantEnvironment.of(SkyModel.at(day.clock.hour, weather()), windStrength()), hours)
    }
}

/**
 * Draws [block] as [state] looks: grown to its size, leaning with the wind ([bend], from `PlantSpecies.windResponse(...).bend`), drooping if thirsty or at rest, and turned toward the light.
 * The foot stays at ([feetX], [feetY]). [block] draws the plant at full size, upright; colour it by [PlantState.stress] yourself (`lerp(green, brown, state.stress)`).
 */
@AdvancedBoitataApi
public fun DrawScope.drawPlant(state: PlantState, species: PlantSpecies, daylight: Float, feetX: Float, feetY: Float, bend: Float, maxLean: Float = 0.14f, block: DrawScope.() -> Unit) {
    val size = 0.35f + 0.65f * state.growth
    val droop = state.droop(species, daylight)
    // Drooping is a lean the way the plant already leans (or to the right), plus a loss of height; turning toward the light is a lean toward it.
    val side = if (bend < 0f) -1f else 1f
    val lean = (bend + droop * 0.9f * side + state.leafTurn * 0.25f).coerceIn(-1.5f, 1.5f)
    withTransform({ scale(size, size * (1f - 0.25f * droop), Offset(feetX, feetY)) }) {
        sway(feetX, feetY, lean, maxLean, block)
    }
}
