package top.e404.tavolo.draw.compose.charts

import org.jetbrains.skia.Color
import org.jetbrains.skia.Rect
import top.e404.tavolo.draw.compose.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 每个图例项整体换行；长名称在项内换行，不拆散线型样例和名称。 */
internal class StructuredLegendLayout(
    private val items: List<StructuredLegend>,
    private val availableWidth: Float,
    private val style: StructuredChartStyle
) : BaseElement() {
    private val positions = mutableListOf<Pair<Float, Float>>()

    override fun measureContent(context: MeasureContext) {
        children.clear()
        positions.clear()
        var cursorX = 0f
        var cursorY = 10f
        var rowHeight = 0f
        val textStyle = TextStyle(fontSize = style.fontSize, fontFamily = style.fontFamily, textColor = style.textColor)
        items.forEach { item ->
            val entry = Row(VerticalAlignment.Center).apply {
                add(CanvasElement(40f, style.fontSize) { canvas, _ ->
                    if (item.stroke != null) {
                        ChartStroke(item.color, 3f, item.stroke).toPaint().use {
                            canvas.drawLine(parentX, parentY + height / 2, parentX + width, parentY + height / 2, it)
                        }
                    } else {
                        ChartFill(item.color).toPaint().use {
                            canvas.drawRect(Rect.makeXYWH(parentX, parentY, 20f, height), it)
                        }
                    }
                })
                text(item.text, Modifier.padding(left = 10f).sizeIn(maxWidth = availableWidth - 50f), style = textStyle)
            }
            entry.measure(context)
            if (cursorX > 0f && (style.legendOrientation == StructuredLegendOrientation.VERTICAL || cursorX + entry.width > availableWidth)) {
                cursorX = 0f
                cursorY += rowHeight + 10f
                rowHeight = 0f
            }
            add(entry)
            positions += cursorX to cursorY
            cursorX += entry.width + 24f
            rowHeight = maxOf(rowHeight, entry.height)
        }
        contentWidth = availableWidth
        contentHeight = cursorY + rowHeight
    }

    override fun layoutChildren(content: Bounds) {
        children.forEachIndexed { index, child -> child.layout(content.x + positions[index].first, content.y + positions[index].second) }
    }
    override fun drawContent(context: DrawContext) {}
}

/** 旋转参与测量，保证文字外接框被计入图片高度，而不是绘制时越界。 */
private class RotatedAxisLabel(
    value: String,
    maxWidth: Float,
    private val angle: Float,
    style: TextStyle,
    antiAlias: Boolean
) : BaseElement() {
    private val text = Text(value, style = style).apply {
        modifier = Modifier.sizeIn(maxWidth = maxWidth).antiAlias(antiAlias)
    }

    override fun measureContent(context: MeasureContext) {
        text.measure(context)
        val radians = Math.toRadians(angle.toDouble())
        contentWidth = (abs(cos(radians)) * text.width + abs(sin(radians)) * text.height).toFloat()
        contentHeight = (abs(sin(radians)) * text.width + abs(cos(radians)) * text.height).toFloat()
    }
    override fun layoutChildren(content: Bounds) { text.layout(-text.width / 2, -text.height / 2) }
    override fun drawContent(context: DrawContext) {
        context.canvas.save()
        try {
            context.canvas.translate(x + width / 2, y + height / 2)
            context.canvas.rotate(-angle)
            text.draw(context)
        } finally { context.canvas.restore() }
    }
}

/** 测量标签、旋转外接框、轴名称后再确定绘图区；底层组件仅负责数据几何。 */
internal class CoordinatePlot(
    private val availableWidth: Float,
    private val style: StructuredChartStyle,
    private val range: ChartRange,
    private val yTickCount: Int,
    private val ticks: List<StructuredAxisTick>,
    private val xAxis: XAxisStyle,
    private val showHorizontalGrid: Boolean,
    private val yLabel: (Float) -> String,
    private val plotOutset: Float = 0f,
    private val drawPlot: (DrawCanvas, MeasureContext, ChartInsets, Float) -> Unit
) : BaseElement() {
    private val positions = mutableListOf<Pair<Float, Float>>()

    override fun measureContent(context: MeasureContext) {
        children.clear()
        positions.clear()
        val textStyle = TextStyle(fontSize = style.fontSize, fontFamily = style.fontFamily, textColor = style.textColor)
        fun text(value: String, width: Float = Float.POSITIVE_INFINITY) = Text(value, style = textStyle).apply {
            modifier = Modifier.sizeIn(maxWidth = width)
            measure(context)
        }
        val ys = (0..yTickCount).map { i ->
            text(yLabel((range.min.toDouble() + (range.max.toDouble() - range.min) * i / yTickCount).toFloat()))
        }
        val labelStyle = xAxis.label.textStyle?.let {
            TextStyle(fontSize = it.fontSize, fontFamily = it.fontFamily, textColor = it.color,
                fontWeight = it.fontWeight, italic = it.italic, scaleX = it.scaleX)
        } ?: textStyle
        val visibleTicks = if (xAxis.label.show) ticks.filter { !it.label.isNullOrEmpty() } else emptyList()
        val labelWidth = xAxis.label.maxWidth ?: (availableWidth / (visibleTicks.size + 2) - 8f)
        require(visibleTicks.isEmpty() || labelWidth >= (labelStyle.fontSize ?: style.fontSize) * 2) {
            "横轴标签可用宽度不足，请增大宽度、减少刻度或设置 label.maxWidth 与 rotate"
        }
        val xs = visibleTicks.map { tick ->
            RotatedAxisLabel(tick.label!!, labelWidth, xAxis.label.rotate, labelStyle, xAxis.label.textStyle?.antiAlias ?: true)
                .also { it.measure(context) }
        }
        val halfLabelWidth = (xs.maxOfOrNull { it.width } ?: 0f) / 2 + 4f
        val left = maxOf(ys.maxOf { it.width } + 12f + plotOutset, halfLabelWidth)
        val right = maxOf(8f, halfLabelWidth, plotOutset)
        val top = maxOf(ys.maxOf { it.height } / 2 + 8f, plotOutset)
        val tickLength = if (xAxis.showAxisTick) xAxis.tickLength else 0f
        val labelTop = plotOutset + tickLength + if (xs.isEmpty()) 0f else xAxis.label.margin
        val labelHeight = xs.maxOfOrNull { it.height } ?: 0f
        val name = xAxis.name.takeIf { it.isNotEmpty() }?.let { text(it, availableWidth) }
        val nameTop = labelTop + labelHeight + if (name == null) 0f else xAxis.nameGap
        val bottom = maxOf(ys.maxOf { it.height } / 2, nameTop + (name?.height ?: 0f)) + 8f
        val plotWidth = availableWidth - left - right
        require(plotWidth >= style.fontSize * 4) { "横纵轴文字过长，图表可用宽度不足" }
        require(style.plotHeight / yTickCount >= ys.maxOf { it.height } + 2f) { "纵轴刻度过密，请增大 plotHeight 或减少刻度数量" }
        val xPositions = visibleTicks.map { left + it.position * plotWidth }
        xs.zipWithNext().forEachIndexed { index, pair ->
            require(xPositions[index + 1] - xPositions[index] >= (pair.first.width + pair.second.width) / 2 + 4f) {
                "横轴标签重叠，请增大宽度、增大刻度间隔、减小 label.maxWidth 或调整旋转角度"
            }
        }
        val totalHeight = top + style.plotHeight + bottom
        val insets = ChartInsets(left, top, right, bottom)
        add(CanvasElement(availableWidth, totalHeight) { canvas, measure ->
            canvas.save()
            try {
                canvas.translate(parentX, parentY)
                drawGrid(canvas, insets, plotWidth)
                drawPlot(canvas, measure, insets, totalHeight)
                drawAxes(canvas, insets, plotWidth)
            } finally { canvas.restore() }
        })
        positions += 0f to 0f
        ys.forEachIndexed { index, element ->
            add(element)
            positions += left - plotOutset - 10f - element.width to top + style.plotHeight * (1f - index.toFloat() / yTickCount) - element.height / 2
        }
        xs.forEachIndexed { index, element ->
            add(element)
            positions += xPositions[index] - element.width / 2 to top + style.plotHeight + labelTop
        }
        name?.let {
            add(it)
            positions += (availableWidth - it.width) / 2 to top + style.plotHeight + nameTop
        }
        contentWidth = availableWidth
        contentHeight = totalHeight
    }

    private fun drawGrid(canvas: DrawCanvas, insets: ChartInsets, plotWidth: Float) {
        val left = insets.left
        val top = insets.top
        val bottom = top + style.plotHeight
        if (style.plotBackgroundColor != Color.TRANSPARENT) {
            ChartFill(style.plotBackgroundColor).toPaint().use { canvas.drawRect(Rect.makeXYWH(left, top, plotWidth, style.plotHeight), it) }
        }
        if (showHorizontalGrid) {
            ChartStroke(style.gridColor).toPaint().use { paint ->
                (0..yTickCount).forEach { index ->
                    val y = top + style.plotHeight * index / yTickCount
                    canvas.drawLine(left, y, left + plotWidth, y, paint)
                }
            }
        }
        if (xAxis.showSplitLine) {
            (xAxis.splitLine ?: ChartStroke(style.gridColor)).toPaint().use { paint ->
                ticks.forEach {
                    val x = left + it.position * plotWidth
                    canvas.drawLine(x, top, x, bottom, paint)
                }
            }
        }
    }

    private fun drawAxes(canvas: DrawCanvas, insets: ChartInsets, plotWidth: Float) {
        val left = insets.left
        val bottom = insets.top + style.plotHeight
        ChartStroke(style.textColor).toPaint().use { canvas.drawLine(left, insets.top, left, bottom, it) }
        if (xAxis.showAxisLine) {
            (xAxis.axisLine ?: ChartStroke(style.textColor)).toPaint().use { canvas.drawLine(left, bottom, left + plotWidth, bottom, it) }
        }
        if (xAxis.showAxisTick) {
            (xAxis.axisTick ?: ChartStroke(style.textColor)).toPaint().use { paint ->
                ticks.forEach {
                    val x = left + it.position * plotWidth
                    canvas.drawLine(x, bottom, x, bottom + xAxis.tickLength, paint)
                }
            }
        }
    }

    override fun layoutChildren(content: Bounds) {
        children.forEachIndexed { index, element -> element.layout(content.x + positions[index].first, content.y + positions[index].second) }
    }
    override fun drawContent(context: DrawContext) {}
}
