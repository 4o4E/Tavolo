package top.e404.tavolo.draw.compose.charts

import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PathEffect
import top.e404.tavolo.draw.compose.StrokeStyle
import top.e404.tavolo.util.FontManager

data class ChartFill(
    val color: Int,
    val antiAlias: Boolean = true
)

data class ChartStroke(
    val color: Int,
    val width: Float = 1f,
    val style: StrokeStyle = StrokeStyle.Solid,
    val antiAlias: Boolean = true
)

data class ChartTextStyle(
    val fontSize: Float,
    val color: Int,
    val fontFamily: String = FontManager.defaultFamily,
    val fontWeight: Int? = null,
    val italic: Boolean = false,
    val scaleX: Float? = null,
    val antiAlias: Boolean = true
)

data class ChartTextBox(
    val width: Float,
    val height: Float,
    val ascent: Float,
    val descent: Float
)

internal fun ChartFill.toPaint(): Paint = Paint().apply {
    color = this@toPaint.color
    mode = PaintMode.FILL
    isAntiAlias = antiAlias
}

internal fun ChartStroke.toPaint(): Paint = Paint().apply {
    color = this@toPaint.color
    mode = PaintMode.STROKE
    strokeWidth = width
    isAntiAlias = antiAlias
    applyStrokeStyle(style)
}

internal fun ChartTextStyle.toFont(): Font = Font(FontManager.resolve(fontFamily), fontSize).apply {
    fontWeight?.let { isEmboldened = it >= 600 }
    if (italic) skewX = -0.25f
    scaleX?.let { this.scaleX = it.coerceAtLeast(0.01f) }
}

internal fun ChartTextStyle.toPaint(): Paint = Paint().apply {
    color = this@toPaint.color
    isAntiAlias = antiAlias
}

private fun Paint.applyStrokeStyle(style: StrokeStyle) {
    val dashIntervals = when (style) {
        StrokeStyle.Solid -> null
        is StrokeStyle.Dashed -> style.intervals
            .map { it.coerceAtLeast(0.1f) }
            .let { if (it.size % 2 == 0) it else it + it }
            .takeIf { it.isNotEmpty() }
            ?.toFloatArray()
        is StrokeStyle.Dotted -> floatArrayOf(
            style.dot.coerceAtLeast(0.1f),
            style.gap.coerceAtLeast(0.1f)
        )
    }
    pathEffect = dashIntervals?.let {
        val phase = when (style) {
            is StrokeStyle.Dashed -> style.phase
            is StrokeStyle.Dotted -> style.phase
            StrokeStyle.Solid -> 0f
        }
        PathEffect.makeDash(it, phase)
    }
}
