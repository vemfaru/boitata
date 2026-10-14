package br.com.faru.boitata.light

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import br.com.faru.boitata.core.clock.Quality
import br.com.faru.boitata.core.light.Billboard
import br.com.faru.boitata.core.light.LightMath
import br.com.faru.boitata.core.light.ShadowMath
import kotlin.math.cos
import kotlin.math.sin

/**
 * Every shadow a thing throws in a [LightRig], each from its own light in its own direction, as dark as that light's share of all the light there. [silhouette] draws the thing standing with its feet
 * at (0, 0) and up negative, in the colour it is given (as for [drawCastShadow]). [height] is how tall it is drawn, in pixels, which sets how long the shadows are.
 *
 * Quality caps how many lights cast (High 4, Medium 2, Low 1: the strongest first) and how many passes soften each edge; at Low shadows stay on the floor.
 *
 * Shadows also land on the rig's walls ([LightRig.walls]): a thing standing in front of a wall throws the part of its shadow that would pass the wall's foot up onto its face, enlarged and moved the way
 * the light puts it, and its floor shadow stops at the wall. A thing that is itself a wall's face passes it as [ignore].
 */
public fun DrawScope.drawCastShadows(
    rig: LightRig, feetX: Float, feetY: Float, height: Float, seconds: Float, maxAlpha: Float = 0.6f,
    quality: Quality = Quality.High, into: ShadowCasts = DefaultCasts, ignore: Any? = null, floor: Boolean = true, silhouette: DrawScope.(Color) -> Unit,
) {
    val n = rig.shadows(feetX, feetY, height, seconds, maxAlpha, when (quality) { Quality.High -> 4; Quality.Medium -> 2; Quality.Low -> 1 }, into, ignore)
    // How solid the thing is: a pane of glass throws a faint shadow, a crate a full one.
    val opacity = if (ignore == null) 1f else (rig.surfaces.firstOrNull { it.owner === ignore }?.optics?.opacity ?: 1f)
    // On the floor. A wall the thing stands in front of stops the shadow at its foot (it climbs the wall instead, below), so the floor's part is cut off there.
    val walls = rig.walls
    var blocked = 0
    // Plain walls stop a floor shadow at their foot. Things (with a silhouette) do not need to: they are drawn after the floor shadows, which is what covers them.
    for (wall in walls) if (wall.silhouette == null && !isOwner(wall, ignore) && feetY > wall.footY + 0.5f) blocked++
    fun onFloor(thick: Boolean) {
        for (i in 0 until n) {
            val cast = into.casts[i]
            if (cast.alpha * opacity <= 0.01f) continue
            // The thing flipped onto the ground and sheared along the light: x gets lean * height, y gets lean * height down the screen. Softened with a few passes, each a little longer and fainter.
            for (pass in 0..(if (quality == Quality.Low) 0 else 2)) {
                val m = Matrix()
                val limit: Float
                if (cast.projective) {
                    // The exact shadow of a light at a place: the thing projected onto the floor along the light's rays. In the thing's own drawing (x across, y up negative) the floor point is
                    //   x' = (lz * x + a * y) / (lz + y),  y' = c * y / (lz + y)        (a, c: the light's offset from the feet; lz: its height)
                    // which is a perspective transform: the matrix holds the two numerators and the shared divisor, and the canvas divides. A lower lamp (a softer pass) lengthens it, feet fixed.
                    val lz = cast.lightHeight * (1f - pass * (0.03f + 0.5f * cast.spread))
                    // A thing has a footprint and the ground is seen from a little above, so the shadow keeps some thickness toward the viewer even for a light level with the thing (see ShadowMath.FOOTPRINT).
                    m[0, 0] = lz; m[1, 0] = cast.lightDX + feetX; m[3, 0] = feetX * lz
                    m[0, 1] = 0f; m[1, 1] = cast.lightDY + feetY - (if (thick) ShadowMath.FOOTPRINT * lz else 0f); m[3, 1] = feetY * lz
                    m[0, 3] = 0f; m[1, 3] = 1f; m[3, 3] = lz
                    limit = 0.8f * lz            // above this the ray runs off to infinity: the shadow of what is above the light is not on the floor
                } else {
                    val soften = 1f + pass * (0.04f + cast.spread)
                    m[1, 0] = -cast.leanX * soften
                    m[1, 1] = -(cast.leanY * soften)
                    m[3, 0] = feetX
                    m[3, 1] = feetY
                    limit = Float.POSITIVE_INFINITY
                }
                withTransform({ transform(m) }) {
                    if (limit.isInfinite()) silhouette(Color.Black.copy(alpha = cast.alpha * opacity * (if (pass == 0) 0.8f else 0.4f)))
                    else clipRect(-BIG, -limit, BIG, BIG) { silhouette(Color.Black.copy(alpha = cast.alpha * opacity * (if (pass == 0) 0.8f else 0.4f))) }
                }
            }
        }
    }
    // With a wall behind, the floor shadow is the exact projection (so it meets the wall's shadow at the wall's foot with no seam); with none, it gets a little thickness (a footprint) so a light level with the thing still gives a shadow.
    if (floor) { if (blocked == 0) onFloor(true) else clipOutWalls(walls, ignore, feetY, 0) { onFloor(false) } }
    if (quality == Quality.Low) return
    // On the walls it stands in front of.
    for (wall in walls) {
        if (wall.silhouette != null || isOwner(wall, ignore)) continue
        val m = rig.wallShadows(feetX, feetY, wall, seconds, maxAlpha, 2, DefaultWallCasts, height, ignore)
        for (i in 0 until m) {
            val cast = DefaultWallCasts.casts[i]
            if (cast.alpha <= 0.01f) continue
            clipRect(wall.left, wall.footY - wall.height, wall.right, wall.footY) {
                for (pass in 0..2) {
                    val grow = cast.scale * (1f + pass * (0.03f + cast.spread))
                    withTransform({ translate(cast.x, wall.footY - cast.footLift); scale(grow, grow, Offset.Zero) }) {
                        silhouette(Color.Black.copy(alpha = cast.alpha * opacity * (if (pass == 0) 0.8f else 0.4f)))
                    }
                }
            }
        }
    }
}

/** Runs [draw] with the faces of the walls cut out of the clip, so nothing drawn lands on them. */
private fun DrawScope.clipOutWalls(walls: List<Wall>, ignore: Any?, feetY: Float, from: Int, draw: () -> Unit) {
    var i = from
    while (i < walls.size && (walls[i].silhouette != null || isOwner(walls[i], ignore) || feetY <= walls[i].footY + 0.5f)) i++
    if (i >= walls.size) { draw(); return }
    val w = walls[i]
    clipRect(w.left, w.footY - w.height, w.right, w.footY, ClipOp.Difference) { clipOutWalls(walls, ignore, feetY, i + 1, draw) }
}

private val DefaultWallCasts = WallCasts()

private const val BIG = 100000f

private fun isOwner(wall: Wall, thing: Any?): Boolean = thing != null && wall.owner === thing
private val DefaultCasts = ShadowCasts()

/**
 * The glow of the lights on the ground and in the air: a pool of light on the ground under each point light (flattened, because the ground is seen at a slant), a cone for each spot, and
 * a bright bulb where each source is. Draw it over the scene (it adds light, [BlendMode.Plus]). Directional lights (sun and moon) have no pool: they are the sky's.
 */
public fun DrawScope.drawLightPools(rig: LightRig, seconds: Float, bulbs: Boolean = true) {
    drawLights(rig, seconds, pools = true, bulbs = bulbs)
}

/** Just the bright bulbs where the lights are, to draw over the things standing in the scene after the pools were drawn under them. */
public fun DrawScope.drawLightBulbs(rig: LightRig, seconds: Float) {
    drawLights(rig, seconds, pools = false, bulbs = true)
}

private fun DrawScope.drawLights(rig: LightRig, seconds: Float, pools: Boolean, bulbs: Boolean) {
    for (l in rig.lights) {
        if (!l.enabled || l.strength <= 0.01f) continue
        val flick = l.flickerAt(seconds)
        when (l) {
            is PointLight -> {
                if (pools) pool(l.x, l.y, l.reach, l.height, l.color, l.strength * flick * 0.42f)
                if (bulbs) bulb(l.x, l.y - l.height, l.color, l.strength * flick, l.height * 0.5f)
            }
            is SpotLight -> {
                if (pools) {
                    // The beam is a few wedges, each narrower and adding a little, so its edge is soft like a real penumbra and not a polygon.
                    val path = Path()
                    for (layer in 0 until BEAM_LAYERS) {
                        val half = l.cone * (1f - l.penumbra * layer / (BEAM_LAYERS - 1))
                        path.rewind()
                        path.moveTo(l.x, l.y)
                        for (i in 0..8) {
                            val a = l.aim - half + 2f * half * i / 8
                            path.lineTo(l.x + cos(a) * l.reach, l.y + sin(a) * l.reach * ShadowMath.GROUND_SQUASH)
                        }
                        path.close()
                        clipPath(path) { pool(l.x, l.y, l.reach, l.height, l.color, l.strength * flick * 0.5f / BEAM_LAYERS * 1.6f) }
                    }
                }
                if (bulbs) bulb(l.x, l.y - l.height, l.color, l.strength * flick, l.height * 0.5f)
            }
            is DirectionalLight -> Unit
        }
    }
}

private const val BEAM_LAYERS = 5

/**
 * A pool of light on a flat surface with the light [perpendicular] away from it, as wide as the light reaches along the surface, shaded by Lambert's law: `falloff(r) * cos(theta)` (see [LightMath.onSurface]).
 * Sampled into a few stops of a radial gradient, which is exact because the light depends only on how far you are from the point nearest the light. Centred on ([x], [y]), [alpha] at the brightest.
 */
private fun DrawScope.surfaceLight(x: Float, y: Float, perpendicular: Float, reach: Float, color: Color, alpha: Float, stops: Int = 7): Boolean {
    val radius = LightMath.surfaceRadius(perpendicular, reach)
    if (radius < 2f) return false
    // Denser near the light where it changes fastest.
    val list = Array(stops) { i ->
        val u = i / (stops - 1f)
        Pair(u, color.copy(alpha = (alpha * LightMath.onSurface(perpendicular, radius * u * u, reach)).coerceIn(0f, 1f)))
    }
    drawCircle(Brush.radialGradient(colorStops = list, center = Offset(x, y), radius = radius), radius = radius, center = Offset(x, y), blendMode = BlendMode.Plus)
    return true
}

private fun DrawScope.pool(x: Float, y: Float, reach: Float, height: Float, color: Color, alpha: Float) {
    withTransform({ scale(1f, ShadowMath.GROUND_SQUASH, Offset(x, y)) }) { surfaceLight(x, y, height.coerceAtLeast(8f), reach, color, alpha * 1.6f) }
}

private fun DrawScope.bulb(x: Float, y: Float, color: Color, strength: Float, radius: Float) {
    val r = radius.coerceIn(6f, 34f)
    drawCircle(Brush.radialGradient(0f to Color(0xFFFFF4D6).copy(alpha = strength.coerceIn(0f, 1f)), 0.25f to color.copy(alpha = 0.5f * strength), 1f to Color.Transparent, center = Offset(x, y), radius = r), radius = r, center = Offset(x, y), blendMode = BlendMode.Plus)
}

/**
 * The light of the rig's lights on a [wall], by the physics of a flat surface: a lamp [D] in front of the wall lights it in a round pool centred on the point of the wall nearest the lamp (at the lamp's height),
 * brightest there and falling off with the distance to the lamp and with Lambert's cosine (so a lamp close to the wall makes a small bright pool, a far one a wide dim one, and one beyond its reach none).
 * Only lights that are in front of the wall and, for a spot, aimed toward it, light it. Draw it over the wall and under the things standing in front.
 */
public fun DrawScope.drawWallLight(rig: LightRig, wall: Billboard, seconds: Float, gain: Float = 1f) {
    val o = wall.optics
    clipRect(wall.left, wall.footY - wall.height, wall.right, wall.footY) {
        for (l in rig.lights) {
            if (!l.enabled || l.strength <= 0.01f) continue
            val flick = l.flickerAt(seconds)
            when (l) {
                is PointLight -> {
                    if (l.y > wall.footY) surfaceLight(l.x, wall.footY - l.height, (l.y - wall.footY) / ShadowMath.GROUND_SQUASH, l.reach, l.color, 0.5f * l.strength * flick * gain)
                    else backLight(wall, l.x, l.y, l.height, l.reach, l.color, l.strength * flick * gain)
                }
                is SpotLight -> {
                    if (l.y > wall.footY && sin(l.aim) < -0.2f) surfaceLight(l.x + cos(l.aim) * l.reach * 0.25f, wall.footY - l.height, (l.y - wall.footY) / ShadowMath.GROUND_SQUASH, l.reach, l.color, 0.5f * l.strength * flick * gain)
                    else if (l.y < wall.footY && sin(l.aim) > 0.2f) backLight(wall, l.x + cos(l.aim) * l.reach * 0.25f, l.y, l.height, l.reach, l.color, l.strength * flick * gain)
                }
                is DirectionalLight -> Unit
            }
        }
    }
}

/**
 * Light that comes through a translucent [wall] from a lamp behind it (a shadow theatre's paper screen): the same pool as light on the front of a wall (Lambert, centred on the point of the screen nearest the lamp),
 * but what the audience sees is the light that gets through, so it is dimmer by what the screen stops, tinted by it, and **spread wider and softer** by how much it scatters (paper glows; clear glass shows the lamp as it is).
 */
private fun DrawScope.backLight(wall: Billboard, x: Float, lampY: Float, height: Float, reach: Float, color: Color, strength: Float) {
    val o = wall.optics
    if (!o.translucent) return
    val through = 1f - o.opacity
    val spread = 1f + 1.2f * o.scatter
    val tint = Color(color.red * o.tint.red, color.green * o.tint.green, color.blue * o.tint.blue)
    surfaceLight(x, wall.footY - height, (wall.footY - lampY) / ShadowMath.GROUND_SQUASH, reach * spread, tint, 0.9f * strength * through / (1f + 0.8f * o.scatter))
}

/**
 * The shadows that other things throw on the face of [receiver], cut to its outline: where a crate stands in front of a person with a light behind it, the crate's shadow lies across the person, and only
 * where the person is. Call it right after drawing [receiver], so the shadow lies over it. For every [Billboard] in front of it with a silhouette, and each light that reaches it, the thing's outline is projected
 * onto the receiver's plane (the wall projection: enlarged by how much farther the receiver is from the light) and kept where the receiver's own picture is.
 *
 * Costs two small offscreen layers per shadow that lands on the receiver (things whose shadow cannot reach it are skipped before that), so it is a High and Medium quality feature; at Low nothing is drawn.
 */
public fun DrawScope.drawFaceShadows(rig: LightRig, receiver: Billboard, seconds: Float, maxAlpha: Float = 0.7f, quality: Quality = Quality.High, into: WallCasts = FaceCasts) {
    val picture = receiver.silhouette ?: return
    if (!receiver.receivesShadows || quality == Quality.Low) return
    val bounds = androidx.compose.ui.geometry.Rect(receiver.left - 2f, receiver.footY - receiver.height - 2f, receiver.right + 2f, receiver.footY + 2f)
    val centre = (receiver.left + receiver.right) / 2f
    for (thing in rig.surfaces) {
        if (thing === receiver || !thing.blocksLight || thing.owner === receiver.owner || thing.footY <= receiver.footY + 0.5f) continue
        val outline = thing.silhouette ?: continue
        val half = (thing.right - thing.left) / 2f
        val n = rig.wallShadows((thing.left + thing.right) / 2f, thing.footY, receiver, seconds, maxAlpha * thing.optics.opacity, 2, into, thing.height, thing.owner)
        for (i in 0 until n) {
            val cast = into.casts[i]
            if (cast.alpha <= 0.01f) continue
            // Skip a shadow that cannot reach the receiver's box.
            val reach = cast.scale * half
            val top = receiver.footY - (cast.footLift + cast.scale * thing.height); val bottom = receiver.footY - cast.footLift
            if (cast.x + reach < receiver.left || cast.x - reach > receiver.right || bottom < receiver.footY - receiver.height || top > receiver.footY) continue
            drawIntoCanvas { canvas ->
                // One layer holds the receiver's picture; a second, composited onto it with DstIn, holds the shadow: what is left is the shadow where the receiver is.
                canvas.saveLayer(bounds, Paint())
                withTransform({ translate(centre, receiver.footY) }) { picture(Color.Black) }
                canvas.saveLayer(bounds, FaceMask)
                for (pass in 0..1) {
                    val grow = cast.scale * (1f + pass * (0.03f + cast.spread))
                    withTransform({ translate(cast.x, receiver.footY - cast.footLift); scale(grow, grow, Offset.Zero) }) {
                        outline(Color.Black.copy(alpha = cast.alpha * (if (pass == 0) 0.85f else 0.4f)))
                    }
                }
                canvas.restore()
                canvas.restore()
            }
        }
    }
}

private val FaceCasts = WallCasts()

private val FaceMask = Paint().apply { blendMode = BlendMode.DstIn }
