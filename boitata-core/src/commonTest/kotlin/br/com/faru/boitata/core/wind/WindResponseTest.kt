package br.com.faru.boitata.core.wind

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindResponseTest {
    private val steady = Wind(speed = 0.5f, gustiness = 0f, turbulence = 0f)
    private val gusty = Wind(speed = 0.5f, gustiness = 1f, turbulence = 0f, gustPeriod = 4f)

    @Test
    fun lightThingsAreBlownFartherThanHeavyOnes() {
        val snow = WindResponse.Snowflake.drift(steady, 10f); val rain = WindResponse.Raindrop.drift(steady, 10f)
        val leaf = WindResponse.Leaf.drift(steady, 10f); val stone = WindResponse.free(weight = 1f, surface = 0.1f).drift(steady, 10f)
        assertTrue(leaf > snow && snow > rain && rain > stone, "leaf $leaf, snow $snow, rain $rain, stone $stone")
        assertTrue(stone > 0f, "even a stone is nudged: the free kind always goes with the wind")
    }

    @Test
    fun aThingThatCatchesMoreIsBlownFarther() {
        assertTrue(WindResponse.free(0.3f, 1f).drift(steady, 5f) > WindResponse.free(0.3f, 0.2f).drift(steady, 5f))
    }

    @Test
    fun freeThingsAlwaysFollowTheDirectionOfTheWind() {
        val left = Wind(speed = 0.5f, direction = -1f, gustiness = 0f, turbulence = 0f)
        assertTrue(WindResponse.Snowflake.drift(left, 3f) < 0f && WindResponse.Snowflake.drift(steady, 3f) > 0f)
    }

    @Test
    fun aStiffThingBendsLessThanALimpOne() {
        fun mean(r: WindResponse) = (0 until 300).map { abs(r.bend(steady, 10f + it * 0.1f)) }.average()
        val grass = mean(WindResponse.Grass); val palm = mean(WindResponse.Palm); val pine = mean(WindResponse.Pine); val trunk = mean(WindResponse.Trunk)
        assertTrue(grass > palm && palm > pine && pine > trunk, "grass $grass palm $palm pine $pine trunk $trunk")
        assertTrue(trunk < 0.35 && grass > 0.6, "a trunk barely moves and grass bends a long way in a fair wind")
        assertTrue(WindResponse.Grass.bend(Wind(speed = 0.5f, gustiness = 0f, turbulence = 0f), 20f) > 0f, "it leans the way the wind blows")
    }

    @Test
    fun rigidThingsAndThingsWithNoInfluenceNeverMove() {
        assertEquals(0f, WindResponse.Rigid.bend(Wind.Gale, 5f)); assertEquals(0f, WindResponse.Rigid.drift(Wind.Gale, 5f))
        assertEquals(0f, WindResponse.Palm.withInfluence(0f).bend(Wind.Gale, 5f))
        assertEquals(0f, WindResponse.Leaf.withInfluence(0f).drift(Wind.Gale, 5f))
        assertTrue(abs(WindResponse.Palm.withInfluence(2f).bend(steady, 5f)) > abs(WindResponse.Palm.bend(steady, 5f)), "influence 2 doubles the push")
    }

    @Test
    fun aCalmDayLeavesEverythingStillExceptForNothing() {
        for (r in listOf(WindResponse.Palm, WindResponse.Snowflake, WindResponse.Flame, WindResponse.Grass)) {
            assertEquals(0f, r.bend(Wind.Calm, 9f)); assertEquals(0f, r.drift(Wind.Calm, 9f))
        }
    }

    @Test
    fun aHeavyThingAnswersAGustLateAndSmoothedWhileALightOneJerksWithIt() {
        fun swing(r: WindResponse) = (0 until 400).map { r.felt(gusty, it * 0.05f) }.let { it.max() - it.min() }
        val light = swing(WindResponse.free(weight = 0.05f, surface = 0.5f)); val heavy = swing(WindResponse.free(weight = 1f, surface = 0.5f))
        assertTrue(heavy < light * 0.85f, "the heavy thing's feeling of the gusts is smoothed: heavy $heavy, light $light")
        assertTrue(WindResponse.free(1f, 0.5f).lagSeconds > WindResponse.free(0.05f, 0.5f).lagSeconds)
    }

    @Test
    fun neighboursSwayOutOfStepAndTheFlutterIsSmallBesideTheLean() {
        val a = WindResponse.Palm.bend(steady, 7.3f, phase = 0f); val b = WindResponse.Palm.bend(steady, 7.3f, phase = 0.4f)
        assertTrue(abs(a - b) > 0.01f, "different phases sway differently")
        val lean = (WindResponse.Palm.felt(steady, 7.3f) / (0.3f + 0.3f * 2.2f)).coerceIn(-1f, 1f)
        assertTrue(abs(a - lean) < 0.2f)
    }
}
