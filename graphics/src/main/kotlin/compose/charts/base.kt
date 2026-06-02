package top.e404.tavolo.draw.compose.charts

import org.jetbrains.skia.Color
import org.jetbrains.skia.Rect
import org.jetbrains.skia.TextLine
import top.e404.tavolo.draw.compose.DrawCanvas
import top.e404.tavolo.draw.compose.MeasureContext
import top.e404.tavolo.draw.compose.StrokeStyle
import top.e404.tavolo.util.FontManager
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * 图表内边距，控制绘图区、轴标签和图例之间的空间。
 */
data class ChartInsets(
    val left: Float = 56f,
    val top: Float = 28f,
    val right: Float = 24f,
    val bottom: Float = 44f
)

/**
 * 图表调色板，按 series 或分类下标循环取色。
 */
data class ChartPalette(
    val colors: List<Int> = DEFAULT_COLORS
) {
    fun colorAt(index: Int): Int {
        require(colors.isNotEmpty()) { "图表调色板不能为空" }
        return colors[index.floorMod(colors.size)]
    }

    companion object {
        val DEFAULT_COLORS = listOf(
            Color.makeRGB(54, 112, 255),
            Color.makeRGB(50, 181, 128),
            Color.makeRGB(245, 167, 36),
            Color.makeRGB(232, 76, 92),
            Color.makeRGB(141, 92, 246),
            Color.makeRGB(51, 173, 214),
            Color.makeRGB(236, 91, 159),
            Color.makeRGB(105, 122, 140),
        )

        val Default = ChartPalette(DEFAULT_COLORS)
    }
}

/**
 * 线性坐标映射，将数据 domain 映射到绘图 range。
 */
data class ChartScale(
    val domainMin: Float,
    val domainMax: Float,
    val rangeMin: Float,
    val rangeMax: Float
) {
    fun map(value: Float): Float {
        val ratio = (value - domainMin) / (domainMax - domainMin)
        return rangeMin + ratio * (rangeMax - rangeMin)
    }

    fun ticks(count: Int): List<Float> {
        val safeCount = count.coerceAtLeast(0)
        if (safeCount == 0) return listOf(domainMin)
        val step = (domainMax - domainMin) / safeCount
        return (0..safeCount).map { domainMin + step * it }
    }

    companion object {
        fun linear(
            domainMin: Float,
            domainMax: Float,
            rangeMin: Float,
            rangeMax: Float
        ): ChartScale {
            require(domainMin.isFinite() && domainMax.isFinite()) { "图表 domain 必须是有限数值" }
            val (safeMin, safeMax) = expandFlatDomain(domainMin, domainMax)
            return ChartScale(safeMin, safeMax, rangeMin, rangeMax)
        }

        fun fromValues(
            values: Iterable<Float>,
            rangeMin: Float,
            rangeMax: Float,
            includeZero: Boolean = false
        ): ChartScale {
            val finite = values.filter { it.isFinite() }.toMutableList()
            if (includeZero) finite += 0f
            if (finite.isEmpty()) finite += listOf(0f, 1f)
            return linear(finite.min(), finite.max(), rangeMin, rangeMax)
        }

        private fun expandFlatDomain(min: Float, max: Float): Pair<Float, Float> {
            if (min < max) return min to max
            val base = abs(min).coerceAtLeast(1f)
            val delta = base * 0.1f
            return min - delta to max + delta
        }
    }
}

/**
 * 坐标轴主题，统一轴线、网格线和标签风格。
 */
data class AxisTheme(
    val axisColor: Int = Color.makeRGB(124, 136, 152),
    val axisWidth: Float = 1f,
    val gridColor: Int = Color.makeARGB(70, 124, 136, 152),
    val gridWidth: Float = 1f,
    val gridStyle: StrokeStyle = StrokeStyle.Solid,
    val labelTextStyle: ChartTextStyle = ChartTextStyle(
        fontSize = 12f,
        color = Color.makeRGB(86, 96, 112),
        fontFamily = FontManager.defaultFamily
    ),
    val tickSize: Float = 4f
) {
    val axisStroke: ChartStroke get() = ChartStroke(axisColor, axisWidth)
    val gridStroke: ChartStroke get() = ChartStroke(gridColor, gridWidth, gridStyle)
}

enum class ChartLegendPosition {
    NONE,
    RIGHT,
    BOTTOM
}

data class ChartLegendTheme(
    val position: ChartLegendPosition = ChartLegendPosition.RIGHT,
    val textStyle: ChartTextStyle = ChartTextStyle(
        fontSize = 12f,
        color = Color.makeRGB(56, 64, 76),
        fontFamily = FontManager.defaultFamily
    ),
    val swatchSize: Float = 10f,
    val itemGap: Float = 8f,
    val columnGap: Float = 18f,
    val maxWidth: Float = 180f
)

data class ChartLegendItem(
    val label: String,
    val color: Int
)

data class ChartLegendSize(
    val width: Float,
    val height: Float
)

/**
 * 数据抽样器，用于限制静态图片中绘制的点数，避免密集数据压成一团。
 */
object ChartDataLimiter {
    fun <T> limit(data: List<T>, maxItems: Int): List<T> {
        if (maxItems <= 0 || data.size <= maxItems) return data
        if (maxItems == 1) return listOf(data.first())
        val step = (data.size - 1).toFloat() / (maxItems - 1)
        return (0 until maxItems).map { data[(it * step).toInt().coerceAtMost(data.lastIndex)] }
    }
}

internal data class ChartMeasuredText(
    val line: TextLine,
    val box: ChartTextBox
)

internal fun measureChartTextBox(
    text: String,
    style: ChartTextStyle,
    measureContext: MeasureContext
): ChartMeasuredText {
    val font = style.toFont()
    val paint = style.toPaint()
    val metrics = measureContext.textMeasurer.metrics(font)
    val width = measureContext.textMeasurer.measureTextWidth(text, font, paint)
    return ChartMeasuredText(
        line = TextLine.make(text, font),
        box = ChartTextBox(
            width = width,
            height = metrics.lineHeight,
            ascent = metrics.ascent,
            descent = metrics.descent
        )
    )
}

internal fun measureChartLegend(
    items: List<ChartLegendItem>,
    theme: ChartLegendTheme,
    measureContext: MeasureContext
): ChartLegendSize {
    if (theme.position == ChartLegendPosition.NONE || items.isEmpty()) return ChartLegendSize(0f, 0f)
    val measured = items.map { measureChartTextBox(it.label, theme.textStyle, measureContext).box }
    val lineHeight = measured.maxOfOrNull { it.height } ?: theme.textStyle.fontSize
    return when (theme.position) {
        ChartLegendPosition.RIGHT -> {
            val textWidth = measured.maxOfOrNull { it.width } ?: 0f
            ChartLegendSize(
                width = (theme.swatchSize + theme.itemGap + textWidth).coerceAtMost(theme.maxWidth),
                height = items.size * lineHeight + (items.size - 1).coerceAtLeast(0) * theme.itemGap
            )
        }

        ChartLegendPosition.BOTTOM -> {
            val width = items.zip(measured).sumOf { (_, box) ->
                (theme.swatchSize + theme.itemGap + box.width + theme.columnGap).toDouble()
            }.toFloat().let { it - theme.columnGap }.coerceAtLeast(0f)
            ChartLegendSize(width, lineHeight)
        }

        ChartLegendPosition.NONE -> ChartLegendSize(0f, 0f)
    }
}

internal fun drawChartLegend(
    canvas: DrawCanvas,
    left: Float,
    top: Float,
    items: List<ChartLegendItem>,
    theme: ChartLegendTheme,
    measureContext: MeasureContext
) {
    if (theme.position == ChartLegendPosition.NONE || items.isEmpty()) return
    val swatchPaint = ChartFill(Color.TRANSPARENT).toPaint()
    val textPaint = theme.textStyle.toPaint()
    var x = left
    var y = top
    items.forEach { item ->
        val measured = measureChartTextBox(item.label, theme.textStyle, measureContext)
        swatchPaint.color = item.color
        canvas.drawRect(Rect.makeXYWH(x, y + (measured.box.height - theme.swatchSize) / 2f, theme.swatchSize, theme.swatchSize), swatchPaint)
        canvas.drawTextLine(
            measured.line,
            x + theme.swatchSize + theme.itemGap,
            y - measured.box.ascent,
            textPaint
        )
        when (theme.position) {
            ChartLegendPosition.BOTTOM -> x += theme.swatchSize + theme.itemGap + measured.box.width + theme.columnGap
            ChartLegendPosition.RIGHT -> y += measured.box.height + theme.itemGap
            ChartLegendPosition.NONE -> Unit
        }
    }
}

internal fun formatChartNumber(value: Float): String {
    val rounded = round(value * 100f) / 100f
    return when {
        rounded % 1f == 0f -> rounded.toInt().toString()
        abs(rounded * 10f % 1f) < 0.0001f -> "%.1f".format(Locale.ROOT, rounded)
        else -> "%.2f".format(Locale.ROOT, rounded)
    }
}

internal fun formatChartPercent(value: Float, total: Float): String {
    if (total <= 0f) return "0%"
    val percent = value / total * 100f
    return if (abs(percent % 1f) < 0.0001f) "${percent.toInt()}%" else "%.1f%%".format(Locale.ROOT, percent)
}

internal fun withAlpha(color: Int, alpha: Int): Int =
    (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

private fun Int.floorMod(mod: Int): Int =
    ((this % mod) + mod) % mod
