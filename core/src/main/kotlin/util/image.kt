@file:JvmName("ToImage")

package top.e404.tavolo.util

import org.jetbrains.skia.*
import kotlin.math.ceil

@Suppress("UNUSED")
fun String.toImage(
    maxWidth: Int = 500,
    udPadding: Int = 3,
    color: Int = Colors.WHITE.argb,
    bgColor: Int = Colors.BG.argb,
    font: Font = defaultFont
) = renderTextImage(
    text = this,
    maxWidth = maxWidth,
    udPadding = udPadding,
    color = color,
    bgColor = bgColor,
    font = font
)

private data class TextImageLine(
    val text: String,
    val width: Float
)

private fun renderTextImage(
    text: String,
    maxWidth: Int,
    udPadding: Int,
    color: Int,
    bgColor: Int,
    font: Font
): Image {
    val imagePadding = 20f
    val radius = 15f
    val paint = Paint().apply {
        this.color = color
        isAntiAlias = true
    }
    val contentWidth = (maxWidth - imagePadding * 2).coerceAtLeast(1f)
    val lines = text.wrapText(contentWidth, font, paint)
    val metrics = font.metrics
    val lineHeight = metrics.descent - metrics.ascent
    val textHeight = lineHeight * lines.size
    val width = maxWidth.coerceAtLeast(ceil(lines.maxOf { it.width } + imagePadding * 2).toInt())
    val height = ceil(textHeight + imagePadding * 2 + udPadding * 2).toInt().coerceAtLeast(1)

    return Surface.makeRasterN32Premul(width, height).withCanvas {
        val backgroundPaint = Paint().apply {
            this.color = bgColor
            isAntiAlias = true
        }
        drawRRect(
            RRect.makeXYWH(0f, 0f, width.toFloat(), height.toFloat(), radius),
            backgroundPaint
        )
        var baseline = imagePadding + udPadding - metrics.ascent
        for (line in lines) {
            drawString(line.text, imagePadding, baseline, font, paint)
            baseline += lineHeight
        }
    }
}

private fun String.wrapText(maxWidth: Float, font: Font, paint: Paint): List<TextImageLine> {
    val result = mutableListOf<TextImageLine>()
    for (paragraph in lineSequence()) {
        if (paragraph.isEmpty()) {
            result += TextImageLine("", 0f)
            continue
        }
        var current = ""
        for (char in paragraph) {
            val next = current + char
            val nextWidth = font.measureTextWidth(next, paint)
            if (current.isNotEmpty() && nextWidth > maxWidth) {
                result += TextImageLine(current, font.measureTextWidth(current, paint))
                current = char.toString()
            } else {
                current = next
            }
        }
        if (current.isNotEmpty()) {
            result += TextImageLine(current, font.measureTextWidth(current, paint))
        }
    }
    return result.ifEmpty { listOf(TextImageLine("", 0f)) }
}
