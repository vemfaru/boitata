package br.com.faru.boitata.core.draw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import br.com.faru.boitata.core.clock.Quality

/**
 * Draws a picture at a position that may be a fraction of a pixel, which is what anything that drifts needs.
 *
 * `drawImage` takes an integer offset, so a layer that moves 0.05 px a frame would sit still for twenty frames and then jump a whole pixel: a
 * stutter you can see. Placing it with a float translation moves it smoothly instead. Every picture that moves in Boitatá is drawn through here, so
 * the mistake cannot be made by accident. [quality] picks the sampling (bilinear with mipmaps by default, which is what a big picture shrunk to the
 * screen wants; bicubic costs several times more here and shows nothing).
 */
public fun DrawScope.drawImageSmooth(
    image: ImageBitmap,
    x: Float,
    y: Float,
    width: Int = image.width,
    height: Int = image.height,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
    quality: Quality = Quality.High,
    srcOffset: IntOffset = IntOffset.Zero,
    srcSize: IntSize = IntSize(image.width, image.height),
) {
    translate(x, y) {
        drawImage(
            image, srcOffset = srcOffset, srcSize = srcSize, dstSize = IntSize(width.coerceAtLeast(1), height.coerceAtLeast(1)),
            alpha = alpha, colorFilter = colorFilter, filterQuality = quality.imageFilter,
        )
    }
}

/** [drawImageSmooth] at an [Offset]. */
public fun DrawScope.drawImageSmooth(image: ImageBitmap, at: Offset, width: Int = image.width, height: Int = image.height, alpha: Float = 1f, colorFilter: ColorFilter? = null, quality: Quality = Quality.High) {
    drawImageSmooth(image, at.x, at.y, width, height, alpha, colorFilter, quality)
}
