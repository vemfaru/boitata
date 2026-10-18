package br.com.faru.boitata.core.sky

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import br.com.faru.boitata.core.clock.RunOnSceneClock

/**
 * The moon that is shown while it changes toward [target]: pick another phase and the lit part grows or shrinks into it over about [settleSeconds] instead of jumping, passing through the phases between
 * (the short way round the month). Runs on the scene clock, so it holds still while the scene is paused. It only writes while it moves, so a settled moon costs nothing.
 */
@Composable
public fun rememberSmoothMoon(target: MoonPhase, settleSeconds: Float = 1.2f): MoonPhase {
    var shown by remember { mutableStateOf(target) }
    var age by remember { mutableStateOf(target.age) }
    val goal = target.age
    RunOnSceneClock(goal) { dt, _ ->
        if (age != goal) {
            age = MoonPhase.stepAge(age, goal, dt, settleSeconds)
            shown = if (age == goal) target else MoonPhase.ofAge(age)
        }
    }
    return shown
}
