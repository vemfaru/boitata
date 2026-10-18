package br.com.faru.boitata.plants

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlantFitTest {
    private val pot = PotSize(width = 0.14f, height = 0.14f)
    private val fern = PlantSpecies.Fern

    @Test
    fun aSeedlingIsAQuarterOfItsHeightAndAFullPlantIsAllOfIt() {
        assertEquals(fern.matureHeight * 0.25f, fern.heightAt(0f), 1e-6f)
        assertEquals(fern.matureHeight, fern.heightAt(1f), 1e-6f)
        assertEquals(fern.matureHeight, fern.heightAt(3f), 1e-6f, "growth is clamped")
    }

    @Test
    fun aPlantFitsWhereNothingIsAbove() {
        assertEquals(PlantFit.Fits, fern.fit(Surface(width = 0.5f, depth = 0.4f), pot))
    }

    @Test
    fun aPlantThatWouldTouchTheBoardAboveDoesNotFitAndSaysWhy() {
        val low = Surface(width = 0.5f, depth = 0.4f, clearanceAbove = 0.5f)
        assertEquals(PlantFit.TooTall, fern.fit(low, pot))
        val high = Surface(width = 0.5f, depth = 0.4f, clearanceAbove = pot.height + fern.matureHeight + 0.01f)
        assertEquals(PlantFit.Fits, fern.fit(high, pot))
    }

    @Test
    fun aYoungPlantFitsUnderABoardItWillOutgrowOnlyWhenAskedAtItsCurrentSize() {
        val board = Surface(width = 0.5f, depth = 0.4f, clearanceAbove = 0.35f)
        assertEquals(PlantFit.TooTall, fern.fit(board, pot), "checked at full size by default")
        assertEquals(PlantFit.Fits, fern.fit(board, pot, growth = 0.2f))
    }

    @Test
    fun aPotMustFitTheSurfaceBothWays() {
        assertEquals(PlantFit.TooWide, fern.fit(Surface(width = 0.1f, depth = 0.5f), pot))
        assertEquals(PlantFit.TooDeep, fern.fit(Surface(width = 0.5f, depth = 0.1f), pot))
    }

    @Test
    fun trailingStemsNeedRoomBelowTheEdge() {
        val ivy = PlantSpecies(0.3f, 0.5f, 0.3f, 0.1f, 0.9f, 0.3f, matureHeight = 0.3f, spread = 0.4f, hang = 0.5f)
        assertEquals(PlantFit.HangsTooFar, ivy.fit(Surface(0.5f, 0.4f, hangRoom = 0.2f), pot))
        assertEquals(PlantFit.Fits, ivy.fit(Surface(0.5f, 0.4f, hangRoom = 0.6f), pot))
        assertTrue(ivy.fit(Surface(0.5f, 0.4f), pot) == PlantFit.Fits, "the open floor has all the room")
    }
}
