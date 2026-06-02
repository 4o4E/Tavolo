package top.e404.tavolo.draw.compose.charts

import org.jetbrains.skia.Color
import org.jetbrains.skia.Rect
import top.e404.tavolo.draw.compose.CanvasElement
import top.e404.tavolo.draw.compose.DrawCanvas
import top.e404.tavolo.draw.compose.MeasureContext
import top.e404.tavolo.draw.compose.UiElement
import kotlin.math.max
import kotlin.math.min

data class BarSeries(
    val name: String,
    val values: List<Float>,
    val color: Int? = null
)

data class CategoryBarData(
    val categories: List<String>,
    val series: List<BarSeries>
)

enum class BarChartMode {
    GROUPED,
    STACKED
}

data class CategoryBarTheme(
    val width: Float,
    val height: Float,
    val insets: ChartInsets = ChartInsets(),
    val palette: ChartPalette = ChartPalette.Default,
    val axis: AxisTheme = AxisTheme(),
    val legend: ChartLegendTheme = ChartLegendTheme(),
    val mode: BarChartMode = BarChartMode.GROUPED,
    val backgroundColor: Int = Color.TRANSPARENT,
    val plotBackgroundColor: Int = Color.TRANSPARENT,
    val yTickCount: Int = 4,
    val categoryLabelEvery: Int = 1,
    val barAreaRatio: Float = 0.68f,
    val stackedGap: Float = 0f,
    val showValueLabels: Boolean = false,
    val valueLabelTextStyle: ChartTextStyle = ChartTextStyle(11f, Color.makeRGB(56, 64, 76)),
    val yMin: Float? = null,
    val yMax: Float? = null,
    val yLabelFormatter: (Float) -> String = { formatChartNumber(it) }
)

fun UiElement.categoryBarChart(theme: CategoryBarTheme, data: CategoryBarData) = add(
    CanvasElement(theme.width, theme.height) { canvas, measureContext ->
        drawCategoryBarChart(canvas, parentX, parentY, data, theme, measureContext)
    }
)

fun drawCategoryBarChart(
    canvas: DrawCanvas,
    parentX: Float,
    parentY: Float,
    data: CategoryBarData,
    theme: CategoryBarTheme,
    measureContext: MeasureContext = MeasureContext()
) {
    val series = data.series.mapIndexed { index, item ->
        item.copy(color = item.color ?: theme.palette.colorAt(index))
    }
    val legendItems = series.map { ChartLegendItem(it.name, it.color!!) }
    val legendSize = measureChartLegend(legendItems, theme.legend, measureContext)
    val rightReserve = if (theme.legend.position == ChartLegendPosition.RIGHT) legendSize.width + 18f else 0f
    val bottomReserve = if (theme.legend.position == ChartLegendPosition.BOTTOM) legendSize.height + 18f else 0f
    val plotLeft = parentX + theme.insets.left
    val plotTop = parentY + theme.insets.top
    val plotRight = parentX + theme.width - theme.insets.right - rightReserve
    val plotBottom = parentY + theme.height - theme.insets.bottom - bottomReserve
    val plotWidth = (plotRight - plotLeft).coerceAtLeast(0f)
    val plotHeight = (plotBottom - plotTop).coerceAtLeast(0f)
    if (plotWidth <= 0f || plotHeight <= 0f || data.categories.isEmpty() || series.isEmpty()) return

    if (theme.backgroundColor != Color.TRANSPARENT) {
        canvas.drawRect(Rect.makeXYWH(parentX, parentY, theme.width, theme.height), ChartFill(theme.backgroundColor).toPaint())
    }
    if (theme.plotBackgroundColor != Color.TRANSPARENT) {
        canvas.drawRect(Rect.makeXYWH(plotLeft, plotTop, plotWidth, plotHeight), ChartFill(theme.plotBackgroundColor).toPaint())
    }

    val domainValues = when (theme.mode) {
        BarChartMode.GROUPED -> series.flatMap { it.values }
        BarChartMode.STACKED -> stackedDomainValues(data.categories.indices, series)
    }
    val yScale = ChartScale.linear(
        domainMin = theme.yMin ?: (domainValues + 0f).filter { it.isFinite() }.minOrNull() ?: 0f,
        domainMax = theme.yMax ?: (domainValues + 0f).filter { it.isFinite() }.maxOrNull() ?: 1f,
        rangeMin = plotBottom,
        rangeMax = plotTop
    )

    drawBarChartAxes(canvas, plotLeft, plotTop, plotRight, plotBottom, data.categories, yScale, theme, measureContext)
    when (theme.mode) {
        BarChartMode.GROUPED -> drawGroupedBars(canvas, plotLeft, plotBottom, plotWidth, data.categories, series, yScale, theme, measureContext)
        BarChartMode.STACKED -> drawStackedBars(canvas, plotLeft, plotBottom, plotWidth, data.categories, series, yScale, theme, measureContext)
    }
    drawCategoryBarLegend(canvas, parentX, parentY, plotRight, plotBottom, legendItems, theme, legendSize, measureContext)
}

private fun drawBarChartAxes(
    canvas: DrawCanvas,
    plotLeft: Float,
    plotTop: Float,
    plotRight: Float,
    plotBottom: Float,
    categories: List<String>,
    yScale: ChartScale,
    theme: CategoryBarTheme,
    measureContext: MeasureContext
) {
    val gridPaint = theme.axis.gridStroke.toPaint()
    val axisPaint = theme.axis.axisStroke.toPaint()
    val labelPaint = theme.axis.labelTextStyle.toPaint()
    yScale.ticks(theme.yTickCount).forEach { tick ->
        val y = yScale.map(tick)
        canvas.drawLine(plotLeft, y, plotRight, y, gridPaint)
        val label = theme.yLabelFormatter(tick)
        val measured = measureChartTextBox(label, theme.axis.labelTextStyle, measureContext)
        canvas.drawTextLine(
            measured.line,
            plotLeft - measured.box.width - 8f,
            y - (measured.box.ascent + measured.box.descent) / 2f,
            labelPaint
        )
    }
    val categoryStep = ((plotRight - plotLeft) / categories.size).coerceAtLeast(0f)
    val every = theme.categoryLabelEvery.coerceAtLeast(1)
    categories.forEachIndexed { index, category ->
        if (index % every == 0) {
            val measured = measureChartTextBox(category, theme.axis.labelTextStyle, measureContext)
            val x = plotLeft + categoryStep * (index + 0.5f) - measured.box.width / 2f
            canvas.drawTextLine(measured.line, x, plotBottom + theme.axis.tickSize + measured.box.height, labelPaint)
        }
    }
    canvas.drawLine(plotLeft, plotBottom, plotRight, plotBottom, axisPaint)
    canvas.drawLine(plotLeft, plotTop, plotLeft, plotBottom, axisPaint)
}

private fun drawGroupedBars(
    canvas: DrawCanvas,
    plotLeft: Float,
    plotBottom: Float,
    plotWidth: Float,
    categories: List<String>,
    series: List<BarSeries>,
    yScale: ChartScale,
    theme: CategoryBarTheme,
    measureContext: MeasureContext
) {
    val groupWidth = plotWidth / categories.size
    val barAreaWidth = groupWidth * theme.barAreaRatio.coerceIn(0.1f, 1f)
    val barWidth = barAreaWidth / series.size.coerceAtLeast(1)
    val zeroY = yScale.map(0f).coerceIn(min(yScale.rangeMin, yScale.rangeMax), max(yScale.rangeMin, yScale.rangeMax))
    series.forEachIndexed { seriesIndex, item ->
        val fillPaint = ChartFill(item.color!!).toPaint()
        categories.indices.forEach { categoryIndex ->
            val value = item.values.getOrNull(categoryIndex)?.takeIf { it.isFinite() } ?: 0f
            val left = plotLeft + groupWidth * categoryIndex + (groupWidth - barAreaWidth) / 2f + barWidth * seriesIndex
            val valueY = yScale.map(value)
            drawBarRect(canvas, left, valueY, barWidth, zeroY, fillPaint.color)
            drawBarValueLabel(canvas, left + barWidth / 2f, valueY, value, theme, measureContext)
        }
    }
}

private fun drawStackedBars(
    canvas: DrawCanvas,
    plotLeft: Float,
    plotBottom: Float,
    plotWidth: Float,
    categories: List<String>,
    series: List<BarSeries>,
    yScale: ChartScale,
    theme: CategoryBarTheme,
    measureContext: MeasureContext
) {
    val groupWidth = plotWidth / categories.size
    val barWidth = groupWidth * theme.barAreaRatio.coerceIn(0.1f, 1f)
    categories.indices.forEach { categoryIndex ->
        var positiveBase = 0f
        var negativeBase = 0f
        series.forEach { item ->
            val value = item.values.getOrNull(categoryIndex)?.takeIf { it.isFinite() } ?: 0f
            val startValue: Float
            val endValue: Float
            if (value >= 0f) {
                startValue = positiveBase
                positiveBase += value
                endValue = positiveBase
            } else {
                startValue = negativeBase
                negativeBase += value
                endValue = negativeBase
            }
            val left = plotLeft + groupWidth * categoryIndex + (groupWidth - barWidth) / 2f
            val y0 = yScale.map(startValue)
            val y1 = yScale.map(endValue)
            val top = min(y0, y1)
            val height = max(0f, kotlin.math.abs(y1 - y0) - theme.stackedGap)
            canvas.drawRect(Rect.makeXYWH(left, top, barWidth, height), ChartFill(item.color!!).toPaint())
            drawBarValueLabel(canvas, left + barWidth / 2f, y1, endValue, theme, measureContext)
        }
    }
}

private fun drawBarRect(
    canvas: DrawCanvas,
    left: Float,
    valueY: Float,
    width: Float,
    zeroY: Float,
    color: Int
) {
    val top = min(valueY, zeroY)
    val height = kotlin.math.abs(zeroY - valueY)
    canvas.drawRect(Rect.makeXYWH(left, top, width, height), ChartFill(color).toPaint())
}

private fun drawBarValueLabel(
    canvas: DrawCanvas,
    centerX: Float,
    valueY: Float,
    value: Float,
    theme: CategoryBarTheme,
    measureContext: MeasureContext
) {
    if (!theme.showValueLabels) return
    val measured = measureChartTextBox(formatChartNumber(value), theme.valueLabelTextStyle, measureContext)
    canvas.drawTextLine(
        measured.line,
        centerX - measured.box.width / 2f,
        valueY - 4f,
        theme.valueLabelTextStyle.toPaint()
    )
}

private fun drawCategoryBarLegend(
    canvas: DrawCanvas,
    parentX: Float,
    parentY: Float,
    plotRight: Float,
    plotBottom: Float,
    items: List<ChartLegendItem>,
    theme: CategoryBarTheme,
    legendSize: ChartLegendSize,
    measureContext: MeasureContext
) {
    val left = when (theme.legend.position) {
        ChartLegendPosition.RIGHT -> plotRight + 18f
        ChartLegendPosition.BOTTOM -> parentX + theme.insets.left
        ChartLegendPosition.NONE -> 0f
    }
    val top = when (theme.legend.position) {
        ChartLegendPosition.RIGHT -> parentY + theme.insets.top
        ChartLegendPosition.BOTTOM -> plotBottom + theme.insets.bottom - legendSize.height
        ChartLegendPosition.NONE -> 0f
    }
    drawChartLegend(canvas, left, top, items, theme.legend, measureContext)
}

private fun stackedDomainValues(indices: IntRange, series: List<BarSeries>): List<Float> =
    indices.flatMap { categoryIndex ->
        var positive = 0f
        var negative = 0f
        series.forEach { item ->
            val value = item.values.getOrNull(categoryIndex)?.takeIf { it.isFinite() } ?: 0f
            if (value >= 0f) positive += value else negative += value
        }
        listOf(positive, negative)
    }
