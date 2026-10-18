package br.com.faru.boitata.core.sky

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoonCalendarTest {
    private fun on(day: Double, ageAtStart: Double = 0.0) = MoonPhase.of(DayClock(day * 24.0), ageAtStart)

    @Test
    fun theNamedPhasesSitWhereTheyShouldOnTheMonth() {
        assertEquals(0f, MoonPhase.New.age, 0.001f)
        assertEquals(0.5f, MoonPhase.Full.age, 0.001f)
        assertEquals(0.25f, MoonPhase.FirstQuarter.age, 0.001f)
        assertEquals(0.75f, MoonPhase.LastQuarter.age, 0.001f)
        for (p in MoonPhase.All) assertEquals(p.label, MoonPhase.ofAge(p.age).label)
    }

    @Test
    fun theMoonChangesFromDayToDayAndComesBackEachMonth() {
        assertTrue(on(0.0).fraction < 0.01f, "new at the start")
        assertTrue(on(7.4).fraction in 0.45f..0.55f && on(7.4).waxing, "first quarter after a week")
        assertTrue(on(14.77).fraction > 0.99f, "full after two weeks")
        assertTrue(on(22.1).fraction in 0.45f..0.55f && !on(22.1).waxing, "last quarter")
        assertEquals(on(3.0).fraction, on(3.0 + MoonPhase.SYNODIC_DAYS).fraction, 0.001f)
        assertTrue(on(3.0).fraction != on(4.0).fraction)
    }

    @Test
    fun itGrowsUntilFullThenShrinks() {
        assertTrue(on(10.0).waxing && !on(20.0).waxing)
        assertTrue(on(9.0).fraction < on(11.0).fraction)
        assertTrue(on(19.0).fraction > on(21.0).fraction)
    }

    @Test
    fun theStartingAgeShiftsTheCalendar() {
        assertTrue(MoonPhase.of(DayClock.at(0f), ageAtStartDays = 14.77).fraction > 0.99f)
    }

    @Test
    fun aChangeOfPhaseGoesTheShortWayRound() {
        // From a waning crescent (age ~0.9) to a waxing one (age ~0.1) it passes through the new moon, not the full.
        var age = MoonPhase.WaningCrescent.age; val target = MoonPhase.WaxingCrescent.age
        var brightest = 0f
        repeat(400) {
            age = MoonPhase.stepAge(age, target, 0.016f)
            brightest = maxOf(brightest, MoonPhase.ofAge(age).fraction)
        }
        assertTrue(brightest < 0.25f, "never brighter than the crescents: $brightest")
        assertEquals(target, age, 0.0001f, "and it arrives")
    }

    @Test
    fun aChangeSettlesInAboutTheTimeGiven() {
        var age = MoonPhase.New.age; var seconds = 0f
        while (age != MoonPhase.Full.age && seconds < 10f) { age = MoonPhase.stepAge(age, MoonPhase.Full.age, 0.016f, settleSeconds = 1.2f); seconds += 0.016f }
        assertTrue(seconds in 0.8f..3f, "settled in $seconds s")
    }
}
