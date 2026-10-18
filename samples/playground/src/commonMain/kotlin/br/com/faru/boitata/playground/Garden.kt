package br.com.faru.boitata.playground

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import br.com.faru.boitata.core.sky.MoonPhase
import br.com.faru.boitata.core.sky.SkyModel
import br.com.faru.boitata.core.wind.rememberSceneWind
import br.com.faru.boitata.plants.PlantPatch
import br.com.faru.boitata.plants.PlantSpecies
import br.com.faru.boitata.plants.Soil
import br.com.faru.boitata.plants.TendPlants
import br.com.faru.boitata.plants.drawPlant
import br.com.faru.boitata.plants.RunningDay
import kotlin.math.abs

private val GardenSpecies = listOf("Fern" to PlantSpecies.Fern, "Grass" to PlantSpecies.Grass, "Flower" to PlantSpecies.Flower, "Succulent" to PlantSpecies.Succulent, "Palm" to PlantSpecies.Palm, "Pine" to PlantSpecies.Pine)
private val GardenSoils = listOf("Sand" to Soil.Sand, "Loam" to Soil.Loam, "Clay" to Soil.Clay)

/**
 * A bed of six kinds of plant under the sky: the day runs (or is held), the weather and wind are yours, and each plant answers to its own needs. Thirsty plants droop and bend more in the same wind,
 * leaves turn toward the sun, and plants yellow when the light or the water is wrong for them.
 */
@Composable
internal fun GardenDemo(seconds: () -> Float, moon: MoonPhase, southern: Boolean, day: RunningDay) {
    var kind by remember { mutableStateOf(WeatherKind.Clear) }
    var soilIndex by remember { mutableStateOf(1) }
    var speed by remember { mutableFloatStateOf(day.hoursPerSecond) }
    day.hoursPerSecond = speed
    val patch = remember(soilIndex) { PlantPatch(GardenSoils[soilIndex].second, GardenSpecies.map { it.second }, moisture = 0.5f) }
    val sceneWind = rememberSceneWind()
    TendPlants(patch, day, weather = { kind.skyWeather() }, windStrength = { abs(sceneWind.at(seconds(), 0.5f).strength).coerceAtMost(1f) })
    // The sky only needs to follow the hour in tenths, so the scene behind recomposes a few times a second, not every frame.
    val hour by remember(day) { derivedStateOf { (day.clock.hour * 10f).toInt() / 10f } }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            WeatherScene(seconds, hour, kind, moon = moon, southern = southern)
            Canvas(Modifier.fillMaxSize()) {
                val t = seconds()
                val sky = SkyModel.at(day.clock.hour, kind.skyWeather())
                val slot = size.width / (patch.plants.size + 1)
                for (i in patch.plants.indices) {
                    val plant = patch.plants[i]; val species = patch.species[i]
                    val feetX = slot * (i + 1); val feetY = size.height * 0.94f
                    val bend = species.windResponse(plant.turgor).bend(sceneWind.wind, t, feetX / size.width, phase = i * 0.173f)
                    drawPlant(plant, species, sky.daylight, feetX, feetY, bend) { drawBlade(feetX, feetY, size.height * (0.12f + 0.12f * species.stiffness + 0.1f * (1f - species.leafFlex)), lerp(Color(0xFF3E7A3A), Color(0xFF8A7A32), plant.stress)) }
                }
            }
        }
        Controls {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in WeatherKind.entries) FilterChip(selected = k == kind, onClick = { kind = k }, label = { Text(k.label) })
                for ((i, s) in GardenSoils.withIndex()) FilterChip(selected = i == soilIndex, onClick = { soilIndex = i }, label = { Text(s.first) })
            }
            Labelled("Days per minute", speed * 2.5f, 0f..30f) { speed = it / 2.5f }
            Text(
                "Day ${day.clock.day + 1}, ${moon.label} moon, soil ${(patch.wet.moisture * 100).toInt()}% wet. " +
                    patch.plants.indices.joinToString(", ") { "${GardenSpecies[it].first} stress ${(patch.plants[it].stress * 100).toInt()}%, grown ${(patch.plants[it].growth * 100).toInt()}%" },
                color = controlText(),
            )
        }
    }
}

/** A plain stalk with a few leaves, standing in for art: the library draws whatever is inside `drawPlant`. */
private fun DrawScope.drawBlade(feetX: Float, feetY: Float, height: Float, color: Color) {
    drawLine(color, Offset(feetX, feetY), Offset(feetX, feetY - height), strokeWidth = 4.dp.toPx())
    for (k in 1..4) {
        val y = feetY - height * k / 4.5f; val reach = height * 0.22f * (1.1f - k * 0.15f)
        drawLine(color, Offset(feetX, y), Offset(feetX - reach, y - reach * 0.6f), strokeWidth = 3.dp.toPx())
        drawLine(color, Offset(feetX, y), Offset(feetX + reach, y - reach * 0.6f), strokeWidth = 3.dp.toPx())
    }
}
