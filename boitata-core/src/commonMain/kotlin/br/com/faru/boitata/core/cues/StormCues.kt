package br.com.faru.boitata.core.cues

import br.com.faru.boitata.core.sky.Strike
import br.com.faru.boitata.core.sky.StormModel

/**
 * Thunder to be heard: the moment it should start on the scene's clock, and the strike it comes from.
 *
 * It is worked out from the strike, not from the picture: the flash is at [Strike.at] and the thunder at `at + thunderDelay` (the speed of sound,
 * 343 m a second), both read off the one seeded timeline. So the flash and the thunder agree however the frames fall, and a sound engine can schedule
 * the thunder ahead of time instead of starting a timer when it happens to notice the flash.
 */
public class ThunderCue(
    /** Scene seconds when the thunder starts: the flash plus the time the sound takes to arrive. */
    public val playAt: Float,
    public val strike: Strike,
) {
    /** How hard it should sound, 0..1: bright and near is loud. */
    public val loudness: Float get() = (strike.brightness / (1f + 0.12f * strike.distanceKm)).coerceIn(0f, 1f)
}

public object StormCues {
    /** The farthest a strike can be heard from: the model's distances stay under this, so any thunder due now started its flash at most this long ago. */
    private const val LONGEST_DELAY = 45f

    /**
     * The thunder that starts in `(from, to]` (scene seconds), earliest first. Asking for consecutive windows gives every cue exactly once: a cue is in
     * the window that holds its [ThunderCue.playAt], however long after the flash that is.
     */
    public fun thunder(model: StormModel, from: Float, to: Float): List<ThunderCue> =
        model.strikes(from - LONGEST_DELAY, to)
            .map { ThunderCue(it.at + it.thunderDelay, it) }
            .filter { it.playAt > from && it.playAt <= to }
            .sortedBy { it.playAt }
}
