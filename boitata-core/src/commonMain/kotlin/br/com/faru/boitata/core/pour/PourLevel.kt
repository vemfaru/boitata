package br.com.faru.boitata.core.pour

/** How much a [PourJob] pours before it stops. */
public sealed class PourLevel {
    /** Until the target is full (to just under the brim). */
    public data object TopUp : PourLevel()

    /** [share] of what the target holds (0..1), or until it is full if that comes first: a shot is a small share, a half is 0.5. */
    public data class Amount(public val share: Float) : PourLevel()

    /** Keep pouring until [PourJob.stop] is called or the source is empty; the target is allowed to run over. */
    public data object UntilStopped : PourLevel()

    public companion object {
        /** A small measure. */
        public val Shot: PourLevel = Amount(0.12f)

        /** Half of what the target holds. */
        public val Half: PourLevel = Amount(0.5f)
    }
}
