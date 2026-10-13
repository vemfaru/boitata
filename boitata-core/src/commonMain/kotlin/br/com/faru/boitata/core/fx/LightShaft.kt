package br.com.faru.boitata.core.fx

/**
 * Where the light that comes in at a window lands on the floor, seen from the front of the room.
 *
 * The patch starts at the foot of the wall ([floorY]) and runs [depth] pixels down the floor towards the viewer. It is shifted sideways along the
 * way by [lean] (where the light is travelling: positive to the right), the more the farther it goes, which slants it like a real window's patch.
 * A band of it is asked for with `v0` and `v1`, 0 at the wall and 1 at the far end, so the panes' bars show as the gaps between bands.
 */
public object LightShaft {
    /**
     * One band of the patch, from the window's [left] to [right], as the four corners x0,y0,x1,y1,... (wall-left, wall-right, far-right, far-left).
     * With a [reach] of 0 the patch has no lean and stands straight below the window.
     */
    public fun band(left: Float, right: Float, floorY: Float, depth: Float, lean: Float, reach: Float, v0: Float, v1: Float): FloatArray {
        val y0 = floorY + depth * v0
        val y1 = floorY + depth * v1
        val s0 = lean * reach * (NEAR + (1f - NEAR) * v0)
        val s1 = lean * reach * (NEAR + (1f - NEAR) * v1)
        return floatArrayOf(left + s0, y0, right + s0, y0, right + s1, y1, left + s1, y1)
    }

    /** How far the light leans out for a light [elevation] above the horizon (1 overhead, 0 on the horizon): long when low, short at noon. */
    public fun reachFor(elevation: Float, length: Float): Float = length * (1.2f - elevation.coerceIn(0f, 1f))

    private const val NEAR = 0.3f
}
