package br.com.faru.boitata.core.draw

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Where a picture that was cropped to its content sits in the canvas it was drawn on: the canvas is [canvasWidth] × [canvasHeight] pixels, the stored picture is the part
 * from ([left], [top]) to ([right], [bottom]). A layer of a room (a shelf, a piece of furniture, a curtain) is drawn on the room's whole canvas so the layers line up; most
 * of it is empty, and a cropped picture keeps only what has paint on it. Everything that places the picture keeps using the canvas's box; the frame says where in it to draw.
 *
 * Written by `tools/art/prep_art.py` (docs/art-pipeline.md).
 */
public class ArtFrame(
    public val canvasWidth: Int,
    public val canvasHeight: Int,
    public val left: Int,
    public val top: Int,
    public val right: Int,
    public val bottom: Int,
) {
    /** The stored picture's part of a box [box] that stands for the whole canvas. */
    public fun inBox(box: Rect): Rect {
        val sx = box.width / canvasWidth; val sy = box.height / canvasHeight
        return Rect(box.left + left * sx, box.top + top * sy, box.left + right * sx, box.top + bottom * sy)
    }

    /** As [inBox], for a box at ([x], [y]) of [width] × [height]. */
    public fun inBox(x: Float, y: Float, width: Float, height: Float): Rect = inBox(Rect(x, y, x + width, y + height))
}

/**
 * Paints a cropped picture as if it were its whole canvas: its intrinsic size is the canvas's (scaled like the picture: a picture decoded at half size has a canvas half
 * the size), and it draws the picture in its part of whatever box it is given, so an `Image` that fills a box with the canvas lines up exactly as before.
 */
public class FramedPainter(private val image: ImageBitmap, private val frame: ArtFrame, private val filterQuality: FilterQuality = FilterQuality.Low) : Painter() {
    private val scale = image.width.toFloat() / (frame.right - frame.left).coerceAtLeast(1)

    override val intrinsicSize: Size = Size(frame.canvasWidth * scale, frame.canvasHeight * scale)

    override fun DrawScope.onDraw() {
        drawFramed(image, frame, Rect(0f, 0f, size.width, size.height), filterQuality = filterQuality)
    }
}

/** Draws a cropped [image] in its part of [box], the box that stands for its whole canvas ([ArtFrame.inBox]). */
public fun DrawScope.drawFramed(image: ImageBitmap, frame: ArtFrame, box: Rect, alpha: Float = 1f, filterQuality: FilterQuality = FilterQuality.Low) {
    val r = frame.inBox(box)
    drawImage(
        image, srcOffset = IntOffset.Zero, srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(r.left.roundToInt(), r.top.roundToInt()), dstSize = IntSize(r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1)),
        alpha = alpha, filterQuality = filterQuality,
    )
}
