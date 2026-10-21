package br.com.faru.boitata.light

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import br.com.faru.boitata.core.clock.rememberSceneQuality
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import br.com.faru.boitata.scene.worldBounds

/** How much shorter a distance along the desk looks than the same distance across it, from the 35° viewpoint. */
private const val DEPTH_FORESHORTEN = 0.6f

/** One picture of a shadow-casting thing and where it is drawn, in world units. */
class ShadowLayer(val image: ImageBitmap, val rect: Rect)

/**
 * The shadow on the desk of anything made of pictures (a prop, the typewriter): each picture's silhouette laid flat on the desk and
 * slanted away from the light, with a soft footprint of [footW] x [footD] under the base at ([feetX], [feetY]). [flicker] multiplies the
 * density and the reach (the bulb's jitter).
 */
@Composable
fun CastSilhouette(layers: List<ShadowLayer>, feetX: Float, feetY: Float, footW: Float, footD: Float, light: LightSource?, flicker: Float = 1f) {
    // The shadow hinges on the middle of the base, not on the picture's bottom edge: the base is seen from above, so its centre sits
    // a little up the picture, and a shadow hinged lower than that floats below the object.
    val pivotY = feetY - footD * 0.42f
    val images = layers.map { it.image }
    val left = layers.minOf { it.rect.left }; val right = layers.maxOf { it.rect.right }
    val top = layers.minOf { it.rect.top }; val bottom = layers.maxOf { it.rect.bottom }
    val extent = bottom - top
    // Slant per unit of height, in screen units: away from the light along the desk (foreshortened by the 35° view).
    var sx = 0f; var sy = 0.12f; var alpha = 0.34f
    // How much of a long shadow there is: none right under the light (a lamp's own shadow would flip about as it is dragged),
    // rising smoothly with the distance from it.
    var present = 0f
    if (light != null && light.strength > 0f) {
        val dx = feetX - light.groundX
        // The desk's depth is foreshortened by the view: the real distance along it is longer than it looks.
        val dyReal = (pivotY - light.groundY) / DEPTH_FORESHORTEN
        val dist = kotlin.math.hypot(dx, dyReal)
        val lightHeight = (light.groundY - light.y).coerceAtLeast(150f)
        // Only the light's own prop fades out (right under its own bulb a shadow would flip about as it is dragged);
        // any other prop, however close, casts the full shadow.
        val t = ((dist - 10f) / 45f).coerceIn(0f, 1f)
        present = t * t * (3f - 2f * t)
        if (dist > 1f) {
            // A point light at height H throws a point at height z a distance d·z/(H−z): the lower the light and the taller the
            // object, the longer the shadow, and a prop standing close to a low light is stretched the most. The silhouette is
            // sheared by one factor, so z is the middle of the object.
            val reference = (extent * 0.5f).coerceAtMost(lightHeight * 0.7f)
            val m = (dist / (lightHeight - reference)).coerceIn(0.05f, 3f)
            sx = dx / dist * m
            sy = dyReal / dist * m * DEPTH_FORESHORTEN
            val floor = 0.1f
            if (kotlin.math.abs(sy) < floor) sy = if (sy < 0f) -floor else floor
        }
        alpha = ((0.2f + 0.3f * light.strength) * flicker).coerceAtMost(0.55f)
        sx *= flicker; sy *= flicker
    }
    // The shadow's extent: the pictures' corners, mapped.
    fun mapX(x: Float, y: Float) = x + sx * (pivotY - y)
    fun mapY(y: Float) = pivotY + sy * (pivotY - y)
    val xs = listOf(mapX(left, top), mapX(right, top), mapX(left, bottom), mapX(right, bottom))
    val ys = listOf(mapY(top), mapY(bottom))
    val pad = 8f
    val minX = minOf(xs.min(), feetX - footW / 2f) - pad; val maxX = maxOf(xs.max(), feetX + footW / 2f) + pad
    val minY = minOf(ys.min(), pivotY - footD / 2f) - pad; val maxY = maxOf(ys.max(), pivotY + footD / 2f, bottom) + pad
    // A shadow's edge is soft: a blur, wider the longer the shadow, hides what is left of the sampling. The blur is a layer of its own that
    // is redone whenever the scene redraws, so it follows the scene's quality: less at Medium, none at Low (the footprint stays soft).
    val blurScale = shadowBlurScale(rememberSceneQuality()())
    Spacer(
        Modifier
            .worldBounds(minX, minY, maxX - minX, maxY - minY)
            .let { m ->
                if (blurScale <= 0f) m
                else m.blur(((3f + 6f * (kotlin.math.abs(sy) + kotlin.math.abs(sx)).coerceAtMost(1.2f)) * blurScale).dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded)
            }
            .drawWithCache {
                // Everything here depends only on the shadow's geometry, worked out above: made once per change of it, not on every frame.
                val d = density
                val bounds = androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)
                val feet = Offset((feetX - minX) * d, (pivotY - minY) * d)
                val fw = footW * d; val fd = footD * d
                val footprint = Brush.radialGradient(0f to Color.Black.copy(alpha = alpha * 0.9f), 1f to Color.Transparent, center = feet, radius = fw * 0.55f)
                val layerPaint = androidx.compose.ui.graphics.Paint()
                val black = androidx.compose.ui.graphics.ColorFilter.tint(Color.Black)
                // The silhouette, laid flat: x' = x - sx*dy, y' = -sy*dy about the feet (dy up is negative).
                val m = androidx.compose.ui.graphics.Matrix()
                m.values[androidx.compose.ui.graphics.Matrix.ScaleX] = 1f
                m.values[androidx.compose.ui.graphics.Matrix.SkewX] = -sx
                m.values[androidx.compose.ui.graphics.Matrix.SkewY] = 0f
                m.values[androidx.compose.ui.graphics.Matrix.ScaleY] = -sy
                val sizes = layers.map { layer -> androidx.compose.ui.unit.IntSize((layer.rect.width * d).toInt().coerceAtLeast(1), (layer.rect.height * d).toInt().coerceAtLeast(1)) }
                // Fade with distance from the foot along the shadow.
                val tip = Offset(feet.x + sx * (extent + footD * 0.42f) * d, feet.y + sy * (extent + footD * 0.42f) * d)
                val fade = Brush.linearGradient(0f to Color.Black, 0.6f to Color.Black.copy(alpha = 0.55f), 1f to Color.Transparent, start = feet, end = tip)
                val silhouetteAlpha = alpha * (if (light != null && light.strength > 0f) present else 1f)
                onDrawBehind {
                // A soft footprint under the base.
                drawOval(footprint, topLeft = Offset(feet.x - fw / 2f, feet.y - fd / 2f), size = androidx.compose.ui.geometry.Size(fw, fd))
                if (light != null && light.strength > 0f && present < 0.02f) return@onDrawBehind
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(bounds, layerPaint)
                    withTransform({
                        translate(feet.x, feet.y)
                        transform(m)
                        translate(-feet.x, -feet.y)
                    }) {
                        for (i in layers.indices) {
                            val layer = layers[i]
                            translate((layer.rect.left - minX) * d, (layer.rect.top - minY) * d) {
                                // Mipmapped sampling (Medium): the picture is laid flat and shrunk, which aliases badly otherwise. High is bicubic with no mipmaps: it costs several times more and aliases more.
                                drawImage(
                                    images[i], dstSize = sizes[i],
                                    alpha = silhouetteAlpha, colorFilter = black, filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
                                )
                            }
                        }
                    }
                    drawRect(fade, blendMode = BlendMode.DstIn)
                    canvas.restore()
                }
                }
            },
    )
}

/** How much of a shadow's blur the scene can afford: all of it at High, some at Medium, none at Low (a blur is a layer redone every frame). */
fun shadowBlurScale(quality: br.com.faru.boitata.core.clock.Quality): Float = when (quality) {
    br.com.faru.boitata.core.clock.Quality.High -> 1f
    br.com.faru.boitata.core.clock.Quality.Medium -> 0.6f
    br.com.faru.boitata.core.clock.Quality.Low -> 0f
}



/**
 * The shadow of something hung or standing against a wall, on the wall behind it: its silhouette moved away from the light by
 * (its position - the light's) x gap / (depth - gap), where [gap] is how far it stands from the wall and [depth] how far the light is
 * from the wall plane, so a near light throws it farther. Soft, and fainter the weaker the light; without a light it falls a little
 * below. [rect] is the picture's box in world units.
 */
@Composable
fun WallShadow(image: ImageBitmap, rect: Rect, light: LightSource?, gap: Float = 40f, depth: Float = 450f, blurDp: Float = 7f, maxAlpha: Float = 0.42f) {
    var dx = 0f; var dy = gap * 0.4f; var alpha = 0.22f
    if (light != null && light.strength > 0f) {
        val k = gap / (depth - gap).coerceAtLeast(gap)
        dx = (rect.center.x - light.x) * k
        dy = (rect.center.y - light.y) * k
        alpha = (0.18f + 0.3f * light.strength).coerceAtMost(maxAlpha)
    }
    val pad = 24f
    val blurScale = shadowBlurScale(rememberSceneQuality()())
    Spacer(
        Modifier
            .worldBounds(rect.left + dx - pad, rect.top + dy - pad, rect.width + 2 * pad, rect.height + 2 * pad)
            .let { m -> if (blurScale <= 0f) m else m.blur((blurDp * blurScale).dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded) }
            .drawWithCache {
                val d = density
                val dst = androidx.compose.ui.unit.IntSize((rect.width * d).toInt().coerceAtLeast(1), (rect.height * d).toInt().coerceAtLeast(1))
                val black = androidx.compose.ui.graphics.ColorFilter.tint(Color.Black)
                onDrawBehind {
                    translate(pad * d, pad * d) {
                        drawImage(image, dstSize = dst, alpha = alpha, colorFilter = black, filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium)
                    }
                }
            },
    )
}
