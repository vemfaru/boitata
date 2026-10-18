package br.com.faru.boitata.testing

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import br.com.faru.boitata.plants.PlantSpecies
import br.com.faru.boitata.plants.PlantState
import br.com.faru.boitata.plants.drawPlant
import kotlin.test.Test
import kotlin.test.assertTrue

/** A plant drawn from its state: a thirsty one stands lower and leans more than a full one, and a grown one is taller than a seedling. */
class PlantsTest {
    private fun top(still: Still): Int = (0 until still.height).first { y -> (0 until still.width).any { x -> still.alpha(x, y) > 40 } }

    private fun plant(state: PlantState, bend: Float = 0f) = renderStill(width = 200, height = 200) {
        Spacer(Modifier.fillMaxSize().drawBehind {
            drawPlant(state, PlantSpecies.Flower, 1f, 100f, 190f, bend) { drawRect(Color.White, Offset(95f, 40f), Size(10f, 150f)) }
        })
    }

    @Test
    fun aGrownPlantIsTallerThanASeedling() {
        assertTrue(top(plant(PlantState(growth = 1f))) < top(plant(PlantState(growth = 0.05f))) - 20)
    }

    @Test
    fun aThirstyPlantStandsLowerThanAFullOne() {
        assertTrue(top(plant(PlantState(hydration = 0.05f, growth = 1f))) > top(plant(PlantState(hydration = 1f, growth = 1f))) + 5)
    }
}
