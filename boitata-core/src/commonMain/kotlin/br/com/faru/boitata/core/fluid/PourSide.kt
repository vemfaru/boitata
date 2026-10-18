package br.com.faru.boitata.core.fluid

/**
 * Which way a container can be tipped to pour. In a 2D scene it is seen from the side, so a jug with a spout on the left must only ever lean left, a bottle held by its neck pours one way, while a plain tumbler pours either way.
 * Right is the positive direction of `TippingBody.angle`, Left the negative.
 */
public enum class PourSide(internal val sign: Int) {
    /** Tips only to the right. */
    Right(1),

    /** Tips only to the left. */
    Left(-1),

    /** Tips either way. */
    Either(0),
}
