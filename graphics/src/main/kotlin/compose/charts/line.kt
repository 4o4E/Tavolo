package top.e404.tavolo.draw.compose.charts

import org.jetbrains.skia.Color
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Path
import org.jetbrains.skia.Rect
import top.e404.tavolo.draw.compose.CanvasElement
import top.e404.tavolo.draw.compose.DrawCanvas
import top.e404.tavolo.draw.compose.MeasureContext
import top.e404.tavolo.draw.compose.StrokeStyle
import top.e404.tavolo.draw.compose.UiElement

data class LinePoint(
    val x: Float,
    val y: Float?
)

data class LineSeries(
    val name: String,
    val points: List<LinePoint>,
    val color: Int? = null,
    val fillColor: Int? = null,
    val lineStyle: StrokeStyle = StrokeStyle.Solid,
    val showPoints: Boolean = true
)

data class LineChartTheme(
    val width: Float,
    val height: Float,
    val insets: ChartInsets = ChartInsets(),
    val palette: ChartPalette = ChartPalette.Default,
    val axis: AxisTheme = AxisTheme(),
    val legend: ChartLegendTheme = ChartLegendTheme(),
    val backgroundColor: Int = Color.TRANSPARENT,
    val plotBackgroundColor: Int = Color.TRANSPARENT,
    val lineWidth: Float = 2f,
    val pointRadius: Float = 3f,
    val yTickCount: Int = 4,
    val xTickCount: Int = 4,
    val includeZeroY: Boolean = true,
    val maxPointsPerSeries: Int = 240,
    val xMin: Float? = null,
    val xMax: Float? = null,
    val yMin: Float? = null,
    val yMax: Float? = null,
    val xLabelFormatter: (Float) -> String = { formatChartNumber(it) },
    val yLabelFormatter: (Float) -> String = { formatChartNumber(it) },
)

fun UiElement.lineChart(theme: LineChartTheme, series: List<LineSeries>) = add(
    CanvasElement(theme.width, theme.height) { canvas, measureContext ->
        drawLineChart(canvas, parentX, parentY, series, theme, measureContext)
    }
)

fun drawLineChart(
    canvas: DrawCanvas,
    parentX: Float,
    parentY: Float,
    series: List<LineSeries>,
    theme: LineChartTheme,
    measureContext: MeasureContext = MeasureContext()
) {
    val resolvedSeries = series.mapIndexed { index, item ->
        item.copy(
            points = ChartDataLimiter.limit(item.points, theme.maxPointsPerSeries),
            color = item.color ?: theme.palette.colorAt(index)
        )
    }
    val legendItems = resolvedSeries.map { ChartLegendItem(it.name, it.color!!) }
    val legendSize = measureChartLegend(legendItems, theme.legend, measureContext)
    val rightReserve = if (theme.legend.position == ChartLegendPosition.RIGHT) legendSize.width + 18f else 0f
    val bottomReserve = if (theme.legend.position == ChartLegendPosition.BOTTOM) legendSize.height + 18f else 0f
    val plotLeft = parentX + theme.insets.left
    val plotTop = parentY + theme.insets.top
    val plotRight = parentX + theme.width - theme.insets.right - rightReserve
    val plotBottom = parentY + theme.height - theme.insets.bottom - bottomReserve
    val plotWidth = (plotRight - plotLeft).coerceAtLeast(0f)
    val plotHeight = (plotBottom - plotTop).coerceAtLeast(0f)
    if (plotWidth <= 0f || plotHeight <= 0f) return

    val backgroundPaint = ChartFill(theme.backgroundColor).toPaint()
    if (theme.backgroundColor != Color.TRANSPARENT) {
        canvas.drawRect(Rect.makeXYWH(parentX, parentY, theme.width, theme.height), backgroundPaint)
    }
    if (theme.plotBackgroundColor != Color.TRANSPARENT) {
        canvas.drawRect(Rect.makeXYWH(plotLeft, plotTop, plotWidth, plotHeight), ChartFill(theme.plotBackgroundColor).toPaint())
    }

    val finitePoints = resolvedSeries.flatMap { item ->
        item.points.mapNotNull { point ->
            val y = point.y
            if (point.x.isFinite() && y != null && y.isFinite()) point.x to y else null
        }
    }
    val xValues = finitePoints.map { it.first }
    val yValues = finitePoints.map { it.second }
    val xScale = ChartScale.linear(
        domainMin = theme.xMin ?: xValues.minOrNull() ?: 0f,
        domainMax = theme.xMax ?: xValues.maxOrNull() ?: 1f,
        rangeMin = plotLeft,
        rangeMax = plotRight
    )
    val yScale = ChartScale.linear(
        domainMin = theme.yMin ?: yValues.withOptionalZero(theme.includeZeroY).minOrNull() ?: 0f,
        domainMax = theme.yMax ?: yValues.withOptionalZero(theme.includeZeroY).maxOrNull() ?: 1f,
        rangeMin = plotBottom,
        rangeMax = plotTop
    )

    drawLineChartAxes(canvas, plotLeft, plotTop, plotRight, plotBottom, xScale, yScale, theme, measureContext)
    val zeroY = yScale.map(0f).coerceIn(plotTop, plotBottom)
    resolvedSeries.forEach { item ->
        drawLineSeries(canvas, item, xScale, yScale, theme, zeroY)
    }
    drawLineChartLegend(canvas, parentX, parentY, plotRight, plotBottom, legendItems, theme, legendSize, measureContext)
}

private fun drawLineChartAxes(
    canvas: DrawCanvas,
    plotLeft: Float,
    plotTop: Float,
    plotRight: Float,
    plotBottom: Float,
    xScale: ChartScale,
    yScale: ChartScale,
    theme: LineChartTheme,
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

    xScale.ticks(theme.xTickCount).forEach { tick ->
        val x = xScale.map(tick)
        canvas.drawLine(x, plotTop, x, plotBottom, gridPaint)
        val label = theme.xLabelFormatter(tick)
        val measured = measureChartTextBox(label, theme.axis.labelTextStyle, measureContext)
        canvas.drawTextLine(
            measured.line,
            x - measured.box.width / 2f,
            plotBottom + theme.axis.tickSize + measured.box.height,
            labelPaint
        )
    }

    canvas.drawLine(plotLeft, plotBottom, plotRight, plotBottom, axisPaint)
    canvas.drawLine(plotLeft, plotTop, plotLeft, plotBottom, axisPaint)
}

private fun drawLineSeries(
    canvas: DrawCanvas,
    series: LineSeries,
    xScale: ChartScale,
    yScale: ChartScale,
    theme: LineChartTheme,
    zeroY: Float
) {
    val color = series.color ?: theme.palette.colorAt(0)
    val segments = continuousSegments(series.points)
    val strokePaint = ChartStroke(color, theme.lineWidth, series.lineStyle).toPaint()
    series.fillColor?.let { fillColor ->
        val fillPaint = ChartFill(fillColor).toPaint()
        segments.filter { it.size >= 2 }.forEach { segment ->
            val path = Path()
            val first = segment.first()
            path.moveTo(xScale.map(first.x), zeroY)
            segment.forEach { point -> path.lineTo(xScale.map(point.x), yScale.map(point.y!!)) }
            val last = segment.last()
            path.lineTo(xScale.map(last.x), zeroY)
            path.closePath()
            canvas.drawPath(path, fillPaint)
        }
    }

    segments.filter { it.size >= 2 }.forEach { segment ->
        val path = Path()
        segment.forEachIndexed { index, point ->
            val x = xScale.map(point.x)
            val y = yScale.map(point.y!!)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, strokePaint)
    }

    if (series.showPoints) {
        val pointPaint = ChartFill(color).toPaint().apply { mode = PaintMode.FILL }
        segments.flatten().forEach { point ->
            canvas.drawCircle(xScale.map(point.x), yScale.map(point.y!!), theme.pointRadius, pointPaint)
        }
    }
}

private fun drawLineChartLegend(
    canvas: DrawCanvas,
    parentX: Float,
    parentY: Float,
    plotRight: Float,
    plotBottom: Float,
    items: List<ChartLegendItem>,
    theme: LineChartTheme,
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

private fun continuousSegments(points: List<LinePoint>): List<List<LinePoint>> {
    val segments = mutableListOf<MutableList<LinePoint>>()
    var current = mutableListOf<LinePoint>()
    points.forEach { point ->
        val y = point.y
        if (point.x.isFinite() && y != null && y.isFinite()) {
            current += point
        } else if (current.isNotEmpty()) {
            segments += current
            current = mutableListOf()
        }
    }
    if (current.isNotEmpty()) segments += current
    return segments
}

private fun List<Float>.withOptionalZero(includeZero: Boolean): List<Float> =
    if (includeZero) this + 0f else this
