package br.com.faru.boitata.core.light

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Kubelka–Munk through a sheet of paper: what real paper of each weight lets through. */
class PaperTransmittanceTest {
    @Test
    fun realPaperWeightsLetThroughWhatTheyShould() {
        assertEquals(0.37f, paperTransmittance(34f), 0.02f, "onionskin, 9 lb")
        assertEquals(0.23f, paperTransmittance(60f), 0.02f, "typing paper, 16 lb")
        assertEquals(0.19f, paperTransmittance(75f), 0.02f, "bond, 20 lb")
    }

    @Test
    fun heavierPaperLetsLessThrough() {
        var last = 1f
        for (g in listOf(10f, 30f, 60f, 90f, 150f)) { val t = paperTransmittance(g); assertTrue(t < last, "$g g/m²: $t"); last = t }
    }

    @Test
    fun noPaperLetsEverythingThroughAndAPaperOpticsStopsTheRest() {
        assertEquals(1f, paperTransmittance(0f))
        assertEquals(1f - paperTransmittance(75f), Optics.paper(75f).opacity, 1e-6f)
    }
}
