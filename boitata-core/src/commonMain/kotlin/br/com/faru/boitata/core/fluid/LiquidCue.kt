package br.com.faru.boitata.core.fluid

/**
 * Something that happens with a liquid that can be heard. The simulation says what happened and how strongly; a listener ([LiquidEvents]) decides what it sounds like, so nothing here knows about audio.
 *
 * Some are **continuous** and are sent every step while they go on, with the level they are at now ([Stream], [Boil], [Soak]); the rest are **discrete**, sent once.
 */
public enum class LiquidCueKind {
    /** Continuous: liquid running over a lip or being poured; the strength is the flow. */
    Stream,

    /** Liquid landing in liquid or on a surface; sent while a stream lands, so a listener should not play every one. */
    Splash,

    /** A mouthful swallowed. */
    Gulp,

    /** A bead letting go of a lip, or a drop landing. */
    Drip,

    /** A container set down on a surface; the strength is how hard and the tone how glassy (1) or dull (0) it is. */
    SetDown,

    /** A container falling over and coming to rest on its side. */
    Knock,

    /** Continuous: a liquid at the boil; the strength is how hard. */
    Boil,

    /** Continuous: water soaking into soil; the strength is how fast. */
    Soak,
}

/** One thing that happened: its [kind], how [strength]ly (0..1), to which container ([source], for where it is on the table) and its [tone] (0 dull, like a pot, to 1 glassy). */
public class LiquidCue(
    public val kind: LiquidCueKind,
    public val strength: Float = 1f,
    public val source: LiquidVessel? = null,
    public val tone: Float = 0.5f,
)

/** Hears what happens with liquids. Give one to a [LiquidVessel] (`events`) and it is told, with nothing else changed; no listener, no cost. */
public fun interface LiquidEvents {
    public fun onCue(cue: LiquidCue)
}
