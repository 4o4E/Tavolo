package top.e404.tavolo.draw.compose.charts

import org.jetbrains.skia.*
import top.e404.tavolo.draw.compose.*
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

fun UiElement.bar(theme: BarTheme, data: List<Pair<Int, Float>>) = add(
    CanvasElement(
        theme.outerRadius * 2,
        theme.outerRadius * 2
    ) { canvas ->
        drawDonutChart(canvas, parentX, parentY, data, theme)
    }
)

/**
 * Theme 数据类，用于定义图表的颜色方案
 * @param outerRadius 图表的外半径
 * @param innerRadius 图表中心空洞的半径
 * @param backgroundColor 画布的背景颜色
 * @param strokeColor 描边的颜色
 * @param strokeWidth 描边的宽度，默认为 2f
 * @param start 起始角度，默认为 -90f（从顶部开始绘制）
 */
data class BarTheme(
    val outerRadius: Float,
    val innerRadius: Float = outerRadius * 2 / 3,
    val backgroundColor: Int = Color.TRANSPARENT,
    val strokeColor: Int = Color.WHITE,
    val strokeWidth: Float = 2f,
    val start: Float = -90f,
) {
    val stroke: ChartStroke get() = ChartStroke(strokeColor, strokeWidth)
}

/**
 * 绘制一个空心饼图（甜甜圈图）到指定的 Canvas 上
 *
 * @param canvas Skia 画布对象
 * @param data 颜色和占比
 * @param theme 包含颜色配置的 Theme 对象
 */
fun drawDonutChart(
    canvas: DrawCanvas,
    left: Float,
    top: Float,
    data: List<Pair<Int, Float>>,
    theme: BarTheme,
) {
    val centerX = left + theme.outerRadius
    val centerY = top + theme.outerRadius
    val positiveData = data.filter { it.second > 0f }
    val total = positiveData.sumOf { it.second.toDouble() }.toFloat()

    // 计算弧形的边界
    val right = left + theme.outerRadius * 2
    val bottom = top + theme.outerRadius * 2
    val strokeWidth = theme.stroke.width

    // 定义用于填充和描边的 Paint
    val fillPaint = ChartFill(Color.TRANSPARENT).toPaint()
    val strokePaint = theme.stroke.toPaint()

    var startAngle = theme.start

    canvas.save()
    // 设置clip
    val clipPath = Path().apply {
        addCircle(centerX, centerY, theme.outerRadius)
        addCircle(centerX, centerY, theme.innerRadius, PathDirection.COUNTER_CLOCKWISE)
    }
    canvas.clipPath(clipPath)

    // 遍历数据并绘制每个扇形
    val l = left + strokeWidth
    val t = top + strokeWidth
    val r = right - strokeWidth
    val b = bottom - strokeWidth
    for ((color, value) in positiveData) {
        val sweepAngle = 360 * (value / total)
        fillPaint.color = color
        canvas.drawArc(l, t, r, b, startAngle, sweepAngle, true, fillPaint)

        // 2. 绘制描边部分
        canvas.drawArc(l, t, r, b, startAngle, sweepAngle, true, strokePaint)

        startAngle += sweepAngle
    }
    canvas.restore()

    // 描边
    canvas.drawCircle(centerX, centerY, theme.outerRadius - strokeWidth, strokePaint)
    canvas.drawCircle(centerX, centerY, theme.innerRadius, strokePaint)
}

data class PieSlice(
    val label: String,
    val value: Float,
    val color: Int? = null
)

data class DrilldownPieSlice(
    val label: String,
    val value: Float,
    val color: Int? = null,
    val drilldown: List<PieSlice> = emptyList()
)

enum class PieLabelPosition {
    INSIDE,
    OUTSIDE,
    AUTO
}

/**
 * 外侧饼图标签的横向对齐策略，语义接近 ECharts 的 label.alignTo。
 */
enum class PieLabelAlignTo {
    NONE,
    LABEL_LINE,
    EDGE,
    RADIAL
}

data class PieChartTheme(
    val width: Float,
    val height: Float,
    val radius: Float,
    val innerRadius: Float = 0f,
    val insets: ChartInsets = ChartInsets(left = 24f, top = 24f, right = 24f, bottom = 24f),
    val palette: ChartPalette = ChartPalette.Default,
    val legend: ChartLegendTheme = ChartLegendTheme(),
    val startAngle: Float = -90f,
    val strokeColor: Int = Color.WHITE,
    val strokeWidth: Float = 1.5f,
    val showLabels: Boolean = true,
    val labelTextStyle: ChartTextStyle = ChartTextStyle(12f, Color.makeRGB(42, 48, 58)),
    val labelPosition: PieLabelPosition = PieLabelPosition.INSIDE,
    val minLabelPercent: Float = 0.04f,
    val minShowLabelAngle: Float = 0f,
    val autoInsideMinPercent: Float = 0.1f,
    val autoInsideMaxLabels: Int = 4,
    val outsideLabelAlignTo: PieLabelAlignTo = PieLabelAlignTo.EDGE,
    val outsideLabelOffset: Float = 18f,
    val outsideLabelLineLength: Float = 16f,
    val outsideLabelMinGap: Float = 4f,
    val outsideLabelEdgeDistance: Float = 8f,
    val outsideLabelBleedMargin: Float = 8f,
    val outsideLabelDistanceToLine: Float = 4f,
    val outsideLabelLineColor: Int = Color.makeARGB(150, 105, 122, 140),
    val outsideLabelLineWidth: Float = 1f,
    val maxNamedSlices: Int? = null,
    val othersLabel: String = "其它",
    val labelFormatter: (PieSlice, Float, Float) -> String = { slice, _, total ->
        "${slice.label} ${formatChartPercent(slice.value, total)}"
    },
    val legendLabelFormatter: (PieSlice, Float, Float) -> String = { slice, _, total ->
        "${slice.label} ${formatChartPercent(slice.value, total)}"
    }
) {
    val stroke: ChartStroke get() = ChartStroke(strokeColor, strokeWidth)
}

data class DrilldownPieChartTheme(
    val pieTheme: PieChartTheme,
    val summaryTitle: String = "下钻摘要",
    val summaryMaxGroups: Int = 4,
    val summaryMaxItemsPerGroup: Int = 3,
    val summaryLeftGap: Float = 24f,
    val summaryWidth: Float? = null,
    val summaryTitleTextStyle: ChartTextStyle = ChartTextStyle(14f, Color.makeRGB(35, 44, 58), fontWeight = 700),
    val summaryGroupTextStyle: ChartTextStyle = ChartTextStyle(12f, Color.makeRGB(56, 64, 76), fontWeight = 700),
    val summaryItemTextStyle: ChartTextStyle = ChartTextStyle(11f, Color.makeRGB(86, 96, 112)),
    val summarySwatchSize: Float = 8f,
    val summaryItemGap: Float = 6f,
    val summaryGroupGap: Float = 10f,
    val summaryOthersLabel: String = "其它",
    val summaryValueFormatter: (PieSlice, Float, Float) -> String = { slice, _, total ->
        "${slice.label} ${formatChartPercent(slice.value, total)}"
    }
)

fun UiElement.pieChart(theme: PieChartTheme, data: List<PieSlice>) = add(
    CanvasElement(theme.width, theme.height) { canvas, measureContext ->
        drawPieChart(canvas, parentX, parentY, data, theme, measureContext)
    }
)

fun UiElement.donutChart(theme: PieChartTheme, data: List<PieSlice>) = pieChart(theme, data)

fun UiElement.drilldownPieChart(theme: DrilldownPieChartTheme, data: List<DrilldownPieSlice>) = add(
    CanvasElement(theme.pieTheme.width, theme.pieTheme.height) { canvas, measureContext ->
        drawDrilldownPieChart(canvas, parentX, parentY, data, theme, measureContext)
    }
)

fun drawPieChart(
    canvas: DrawCanvas,
    parentX: Float,
    parentY: Float,
    data: List<PieSlice>,
    theme: PieChartTheme,
    measureContext: MeasureContext = MeasureContext()
) {
    val slices = normalizePieSlices(data, theme)
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    if (total <= 0f) return

    val legendItems = slices.map { slice ->
        ChartLegendItem(theme.legendLabelFormatter(slice, slice.value / total, total), slice.color!!)
    }
    val legendSize = measureChartLegend(legendItems, theme.legend, measureContext)
    val centerX = parentX + theme.insets.left + theme.radius
    val centerY = parentY + theme.insets.top + theme.radius
    val arcInset = theme.strokeWidth / 2f
    val left = centerX - theme.radius + arcInset
    val top = centerY - theme.radius + arcInset
    val right = centerX + theme.radius - arcInset
    val bottom = centerY + theme.radius - arcInset
    val fillPaint = ChartFill(Color.TRANSPARENT).toPaint()
    val strokePaint = theme.stroke.toPaint()

    if (theme.innerRadius > 0f) {
        canvas.save()
        val clipPath = Path().apply {
            addCircle(centerX, centerY, theme.radius)
            addCircle(centerX, centerY, theme.innerRadius.coerceAtMost(theme.radius), PathDirection.COUNTER_CLOCKWISE)
        }
        canvas.clipPath(clipPath)
    }

    var start = theme.startAngle
    val labelStates = mutableListOf<PieLabelState>()
    slices.forEach { slice ->
        val sweep = 360f * (slice.value / total)
        fillPaint.color = slice.color!!
        canvas.drawArc(left, top, right, bottom, start, sweep, true, fillPaint)
        if (theme.strokeWidth > 0f) {
            canvas.drawArc(left, top, right, bottom, start, sweep, true, strokePaint)
        }
        if (theme.showLabels && slice.value / total >= theme.minLabelPercent && sweep >= theme.minShowLabelAngle) {
            labelStates += PieLabelState(slice, start, sweep)
        }
        start += sweep
    }

    if (theme.innerRadius > 0f) {
        canvas.restore()
        if (theme.strokeWidth > 0f) {
            canvas.drawCircle(centerX, centerY, theme.radius - arcInset, strokePaint)
            canvas.drawCircle(centerX, centerY, theme.innerRadius, strokePaint)
        }
    }

    drawPieLabels(canvas, centerX, centerY, labelStates, total, theme, measureContext)
    val legendLeft = when (theme.legend.position) {
        ChartLegendPosition.RIGHT -> parentX + theme.insets.left + theme.radius * 2f + 18f
        ChartLegendPosition.BOTTOM -> parentX + theme.insets.left
        ChartLegendPosition.NONE -> 0f
    }
    val legendTop = when (theme.legend.position) {
        ChartLegendPosition.RIGHT -> parentY + theme.insets.top
        ChartLegendPosition.BOTTOM -> centerY + theme.radius + 18f
        ChartLegendPosition.NONE -> 0f
    }
    if (legendSize.width >= 0f) {
        drawChartLegend(canvas, legendLeft, legendTop, legendItems, theme.legend, measureContext)
    }
}

fun drawDrilldownPieChart(
    canvas: DrawCanvas,
    parentX: Float,
    parentY: Float,
    data: List<DrilldownPieSlice>,
    theme: DrilldownPieChartTheme,
    measureContext: MeasureContext = MeasureContext()
) {
    val slices = resolveDrilldownPieSlices(data, theme.pieTheme.palette)
    if (slices.isEmpty()) return
    val topData = slices.map { PieSlice(it.label, it.value, it.color) }

    // drilldown_pie 的静态摘要会占用右侧空间，因此顶层图例固定由摘要区承担。
    drawPieChart(
        canvas = canvas,
        parentX = parentX,
        parentY = parentY,
        data = topData,
        theme = theme.pieTheme.copy(legend = ChartLegendTheme(position = ChartLegendPosition.NONE)),
        measureContext = measureContext
    )
    drawDrilldownSummary(canvas, parentX, parentY, slices, theme, measureContext)
}

private data class PieLabelState(
    val slice: PieSlice,
    val startAngle: Float,
    val sweepAngle: Float
) {
    val midAngle: Float get() = startAngle + sweepAngle / 2f
}

private enum class PieLabelSide {
    LEFT,
    RIGHT
}

private data class OutsidePieLabel(
    val state: PieLabelState,
    val measured: ChartMeasuredText,
    val side: PieLabelSide,
    val radialX: Float,
    val radialY: Float,
    val anchorX: Float,
    val anchorY: Float,
    val elbowX: Float,
    val elbowY: Float,
    val labelX: Float,
    val lineEndX: Float,
    val desiredTop: Float,
    val labelTop: Float = desiredTop
)

private fun normalizePieSlices(data: List<PieSlice>, theme: PieChartTheme): List<PieSlice> {
    val positive = data.filter { it.value > 0f }
    val namedLimit = theme.maxNamedSlices
    val normalized = if (namedLimit != null && namedLimit >= 0 && positive.size > namedLimit) {
        val sorted = positive.sortedByDescending { it.value }
        val named = sorted.take(namedLimit)
        val othersValue = sorted.drop(namedLimit).sumOf { it.value.toDouble() }.toFloat()
        if (othersValue > 0f) named + PieSlice(theme.othersLabel, othersValue) else named
    } else {
        positive
    }
    return normalized.mapIndexed { index, slice ->
        slice.copy(color = slice.color ?: theme.palette.colorAt(index))
    }
}

private fun resolveDrilldownPieSlices(data: List<DrilldownPieSlice>, palette: ChartPalette): List<DrilldownPieSlice> {
    return data
        .filter { it.value > 0f }
        .mapIndexed { index, slice ->
            slice.copy(
                color = slice.color ?: palette.colorAt(index),
                drilldown = resolvePieSliceColors(slice.drilldown, palette)
            )
        }
}

private fun resolvePieSliceColors(data: List<PieSlice>, palette: ChartPalette): List<PieSlice> {
    return data
        .filter { it.value > 0f }
        .sortedByDescending { it.value }
        .mapIndexed { index, slice -> slice.copy(color = slice.color ?: palette.colorAt(index)) }
}

private fun drawDrilldownSummary(
    canvas: DrawCanvas,
    parentX: Float,
    parentY: Float,
    slices: List<DrilldownPieSlice>,
    theme: DrilldownPieChartTheme,
    measureContext: MeasureContext
) {
    val pieTheme = theme.pieTheme
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    val summaryLeft = parentX + pieTheme.insets.left + pieTheme.radius * 2f + theme.summaryLeftGap
    val summaryRight = parentX + pieTheme.width - pieTheme.insets.right
    val summaryWidth = (theme.summaryWidth ?: (summaryRight - summaryLeft)).coerceAtLeast(0f)
    if (summaryWidth <= 0f) return

    var y = parentY + pieTheme.insets.top
    if (theme.summaryTitle.isNotBlank()) {
        val title = measureChartTextBox(theme.summaryTitle, theme.summaryTitleTextStyle, measureContext)
        drawMeasuredChartText(canvas, title, summaryLeft, y, theme.summaryTitleTextStyle)
        y += title.box.height + theme.summaryGroupGap
    }

    slices
        .sortedByDescending { it.value }
        .take(theme.summaryMaxGroups.coerceAtLeast(0))
        .forEach { slice ->
            val header = measureChartTextBox("${slice.label} ${formatChartPercent(slice.value, total)}", theme.summaryGroupTextStyle, measureContext)
            val swatchTop = y + (header.box.height - theme.summarySwatchSize) / 2f
            canvas.drawRect(Rect.makeXYWH(summaryLeft, swatchTop, theme.summarySwatchSize, theme.summarySwatchSize), ChartFill(slice.color!!).toPaint())
            drawMeasuredChartText(
                canvas,
                header,
                summaryLeft + theme.summarySwatchSize + theme.summaryItemGap,
                y,
                theme.summaryGroupTextStyle
            )
            y += header.box.height + theme.summaryItemGap

            normalizedDrilldownItems(slice.drilldown, theme).forEach { item ->
                val itemTotal = slice.drilldown.sumOf { it.value.toDouble() }.toFloat()
                val itemText = theme.summaryValueFormatter(item, item.value / itemTotal.coerceAtLeast(0.0001f), itemTotal)
                val measured = measureChartTextBox(itemText, theme.summaryItemTextStyle, measureContext)
                val itemX = summaryLeft + theme.summarySwatchSize + theme.summaryItemGap
                drawMeasuredChartText(canvas, measured, itemX, y, theme.summaryItemTextStyle)
                y += measured.box.height + theme.summaryItemGap
            }
            y += theme.summaryGroupGap
        }
}

private fun normalizedDrilldownItems(data: List<PieSlice>, theme: DrilldownPieChartTheme): List<PieSlice> {
    val positive = data.filter { it.value > 0f }.sortedByDescending { it.value }
    val limit = theme.summaryMaxItemsPerGroup.coerceAtLeast(0)
    if (positive.size <= limit) return positive
    val named = positive.take(limit)
    val othersValue = positive.drop(limit).sumOf { it.value.toDouble() }.toFloat()
    return if (othersValue > 0f) named + PieSlice(theme.summaryOthersLabel, othersValue) else named
}

private fun drawMeasuredChartText(
    canvas: DrawCanvas,
    measured: ChartMeasuredText,
    x: Float,
    top: Float,
    style: ChartTextStyle
) {
    canvas.drawTextLine(measured.line, x, top - measured.box.ascent, style.toPaint())
}

private fun drawPieLabels(
    canvas: DrawCanvas,
    centerX: Float,
    centerY: Float,
    labels: List<PieLabelState>,
    total: Float,
    theme: PieChartTheme,
    measureContext: MeasureContext
) {
    when (theme.labelPosition) {
        PieLabelPosition.INSIDE -> drawInsidePieLabels(canvas, centerX, centerY, labels, total, theme, measureContext)
        PieLabelPosition.OUTSIDE -> drawOutsidePieLabels(canvas, centerX, centerY, labels, total, theme, measureContext)
        PieLabelPosition.AUTO -> {
            if (labels.size < 6) {
                drawInsidePieLabels(canvas, centerX, centerY, labels, total, theme, measureContext)
            } else {
                val insideSet = labels
                    .sortedByDescending { it.slice.value }
                    .filter { it.slice.value / total >= theme.autoInsideMinPercent }
                    .take(theme.autoInsideMaxLabels.coerceAtLeast(0))
                    .toSet()
                val insideLabels = labels.filter { it in insideSet }
                val outsideLabels = labels.filter { it !in insideSet }
                drawInsidePieLabels(canvas, centerX, centerY, insideLabels, total, theme, measureContext)
                drawOutsidePieLabels(canvas, centerX, centerY, outsideLabels, total, theme, measureContext)
            }
        }
    }
}

private fun drawInsidePieLabels(
    canvas: DrawCanvas,
    centerX: Float,
    centerY: Float,
    labels: List<PieLabelState>,
    total: Float,
    theme: PieChartTheme,
    measureContext: MeasureContext
) {
    val labelRadius = if (theme.innerRadius > 0f) {
        theme.innerRadius + (theme.radius - theme.innerRadius) * 0.55f
    } else {
        theme.radius * 0.62f
    }
    val textPaint = theme.labelTextStyle.toPaint()
    labels.forEach { state ->
        val angle = Math.toRadians((state.startAngle + state.sweepAngle / 2f).toDouble())
        val label = theme.labelFormatter(state.slice, state.slice.value / total, total)
        val measured = measureChartTextBox(label, theme.labelTextStyle, measureContext)
        val x = centerX + kotlin.math.cos(angle).toFloat() * labelRadius - measured.box.width / 2f
        val y = centerY + kotlin.math.sin(angle).toFloat() * labelRadius - (measured.box.ascent + measured.box.descent) / 2f
        canvas.drawTextLine(measured.line, x, y, textPaint)
    }
}

private fun drawOutsidePieLabels(
    canvas: DrawCanvas,
    centerX: Float,
    centerY: Float,
    labels: List<PieLabelState>,
    total: Float,
    theme: PieChartTheme,
    measureContext: MeasureContext
) {
    if (labels.isEmpty()) return
    val linePaint = ChartStroke(theme.outsideLabelLineColor, theme.outsideLabelLineWidth).toPaint()
    val textPaint = theme.labelTextStyle.toPaint()
    val parentX = centerX - theme.insets.left - theme.radius
    val parentY = centerY - theme.insets.top - theme.radius
    val viewLeft = parentX
    val viewRight = parentX + theme.width
    val viewTop = parentY
    val viewBottom = parentY + theme.height
    val labelOffset = theme.outsideLabelOffset.coerceAtLeast(0f)
    val labelLineLength = theme.outsideLabelLineLength.coerceAtLeast(0f)
    val topLimit = max(centerY - theme.radius - labelOffset, viewTop)
    val bottomLimit = min(centerY + theme.radius + labelOffset, viewBottom)
    // 先保留扇区自然折点，避让只移动折点后的纵向段，避免引线直接斜穿到文本。
    val rawLabels = labels.map { state ->
        val angle = Math.toRadians(state.midAngle.toDouble())
        val cosValue = cos(angle).toFloat()
        val sinValue = sin(angle).toFloat()
        val side = if (cosValue >= 0f) PieLabelSide.RIGHT else PieLabelSide.LEFT
        val label = theme.labelFormatter(state.slice, state.slice.value / total, total)
        val measured = measureChartTextBox(label, theme.labelTextStyle, measureContext)
        val anchorX = centerX + cosValue * theme.radius
        val anchorY = centerY + sinValue * theme.radius
        val sideSign = if (side == PieLabelSide.RIGHT) 1f else -1f
        val elbowX = centerX + cosValue * (theme.radius + labelOffset)
        val elbowY = centerY + sinValue * (theme.radius + labelOffset)
        val lineEndX = elbowX + sideSign * labelLineLength
        val labelX = if (side == PieLabelSide.RIGHT) {
            lineEndX + theme.outsideLabelDistanceToLine
        } else {
            lineEndX - theme.outsideLabelDistanceToLine - measured.box.width
        }
        OutsidePieLabel(
            state = state,
            measured = measured,
            side = side,
            radialX = cosValue,
            radialY = sinValue,
            anchorX = anchorX,
            anchorY = anchorY,
            elbowX = elbowX,
            elbowY = elbowY,
            labelX = labelX,
            lineEndX = lineEndX,
            desiredTop = elbowY - measured.box.height / 2f
        )
    }

    rawLabels
        .groupBy { it.side }
        .entries
        .flatMap { (side, sideLabels) ->
            layoutOutsideLabelSide(sideLabels, side, topLimit, bottomLimit, viewLeft, viewRight, labelLineLength, theme)
        }
        .forEach { label ->
            val labelCenterY = label.labelTop + label.measured.box.height / 2f
            canvas.drawLine(label.anchorX, label.anchorY, label.elbowX, labelCenterY, linePaint)
            canvas.drawLine(label.elbowX, labelCenterY, label.lineEndX, labelCenterY, linePaint)
            canvas.drawTextLine(
                label.measured.line,
                label.labelX,
                label.labelTop - label.measured.box.ascent,
                textPaint
            )
        }
}

private fun layoutOutsideLabelSide(
    labels: List<OutsidePieLabel>,
    side: PieLabelSide,
    topLimit: Float,
    bottomLimit: Float,
    viewLeft: Float,
    viewRight: Float,
    labelLineLength: Float,
    theme: PieChartTheme
): List<OutsidePieLabel> {
    val adjusted = avoidOutsideLabelOverlap(labels, topLimit, bottomLimit, theme.outsideLabelMinGap)
    if (adjusted.isEmpty()) return emptyList()

    // 纵向位置已在上一步确定，这里只根据对齐策略重新计算文本和水平引线的 X 坐标。
    val sideSign = if (side == PieLabelSide.RIGHT) 1f else -1f
    val distanceToLine = theme.outsideLabelDistanceToLine.coerceAtLeast(0f)
    return when (theme.outsideLabelAlignTo) {
        PieLabelAlignTo.EDGE -> adjusted.map { label ->
            val safeEdge = theme.outsideLabelEdgeDistance.coerceAtLeast(0f)
            val labelCenterY = label.labelTop + label.measured.box.height / 2f
            val elbowX = avoidPieCrossingElbowX(label, labelCenterY)
            val labelX = if (side == PieLabelSide.RIGHT) {
                viewRight - safeEdge - label.measured.box.width
            } else {
                viewLeft + safeEdge
            }
            val lineEndX = if (side == PieLabelSide.RIGHT) {
                labelX - distanceToLine
            } else {
                labelX + label.measured.box.width + distanceToLine
            }
            label.copy(elbowX = elbowX, labelX = labelX, lineEndX = lineEndX)
        }

        PieLabelAlignTo.LABEL_LINE -> {
            val bleedMargin = theme.outsideLabelBleedMargin.coerceAtLeast(0f)
            val widest = adjusted.maxOf { it.measured.box.width }
            val naturalEndX = if (side == PieLabelSide.RIGHT) {
                adjusted.maxOf { label ->
                    avoidPieCrossingElbowX(label, label.labelTop + label.measured.box.height / 2f)
                } + labelLineLength
            } else {
                adjusted.minOf { label ->
                    avoidPieCrossingElbowX(label, label.labelTop + label.measured.box.height / 2f)
                } - labelLineLength
            }
            val lineEndX = if (side == PieLabelSide.RIGHT) {
                naturalEndX.coerceAtMost(viewRight - bleedMargin - widest - distanceToLine)
            } else {
                naturalEndX.coerceAtLeast(viewLeft + bleedMargin + widest + distanceToLine)
            }
            adjusted.map { label ->
                val labelCenterY = label.labelTop + label.measured.box.height / 2f
                val elbowX = avoidPieCrossingElbowX(label, labelCenterY)
                val labelX = if (side == PieLabelSide.RIGHT) {
                    lineEndX + distanceToLine
                } else {
                    lineEndX - distanceToLine - label.measured.box.width
                }
                label.copy(elbowX = elbowX, labelX = labelX, lineEndX = lineEndX)
            }
        }

        PieLabelAlignTo.NONE -> adjusted.map { label ->
            val bleedMargin = theme.outsideLabelBleedMargin.coerceAtLeast(0f)
            val labelCenterY = label.labelTop + label.measured.box.height / 2f
            val elbowX = avoidPieCrossingElbowX(label, labelCenterY)
            val naturalLineEndX = elbowX + sideSign * labelLineLength
            val labelX = if (side == PieLabelSide.RIGHT) {
                (naturalLineEndX + distanceToLine)
                    .coerceAtMost(viewRight - bleedMargin - label.measured.box.width)
            } else {
                (naturalLineEndX - distanceToLine - label.measured.box.width)
                    .coerceAtLeast(viewLeft + bleedMargin)
            }
            val lineEndX = if (side == PieLabelSide.RIGHT) {
                labelX - distanceToLine
            } else {
                labelX + label.measured.box.width + distanceToLine
            }
            label.copy(elbowX = elbowX, labelX = labelX, lineEndX = lineEndX)
        }

        PieLabelAlignTo.RADIAL -> adjusted.map { label ->
            val bleedMargin = theme.outsideLabelBleedMargin.coerceAtLeast(0f)
            val labelCenterY = label.labelTop + label.measured.box.height / 2f
            val elbowX = avoidPieCrossingElbowX(label, labelCenterY)
            val naturalLineEndX = elbowX + sideSign * labelLineLength
            val naturalLabelX = if (side == PieLabelSide.RIGHT) {
                naturalLineEndX + distanceToLine
            } else {
                naturalLineEndX - distanceToLine - label.measured.box.width
            }
            val labelX = if (side == PieLabelSide.RIGHT) {
                naturalLabelX.coerceAtMost(viewRight - bleedMargin - label.measured.box.width)
            } else {
                naturalLabelX.coerceAtLeast(viewLeft + bleedMargin)
            }
            val lineEndX = if (side == PieLabelSide.RIGHT) {
                labelX - distanceToLine
            } else {
                labelX + label.measured.box.width + distanceToLine
            }
            label.copy(elbowX = elbowX, labelX = labelX, lineEndX = lineEndX)
        }
    }
}

private fun avoidPieCrossingElbowX(label: OutsidePieLabel, labelCenterY: Float): Float {
    val radialX = label.radialX
    if (kotlin.math.abs(radialX) < 0.0001f) return label.elbowX
    // 标签纵向避让后，按扇区切线外侧重新约束折点，避免第一段引线穿过饼图主体。
    val tangentSafeX = label.anchorX - (labelCenterY - label.anchorY) * label.radialY / radialX
    return when (label.side) {
        PieLabelSide.RIGHT -> max(label.elbowX, tangentSafeX)
        PieLabelSide.LEFT -> min(label.elbowX, tangentSafeX)
    }
}

private fun avoidOutsideLabelOverlap(
    labels: List<OutsidePieLabel>,
    topLimit: Float,
    bottomLimit: Float,
    minGap: Float
): List<OutsidePieLabel> {
    if (labels.isEmpty()) return emptyList()
    val sorted = labels.sortedBy { it.desiredTop }
    val forward = mutableListOf<OutsidePieLabel>()
    sorted.forEach { label ->
        val minTop = forward.lastOrNull()?.let { it.labelTop + it.measured.box.height + minGap } ?: topLimit
        forward += label.copy(labelTop = max(label.desiredTop, minTop))
    }

    val backward = forward.toMutableList()
    for (index in backward.indices.reversed()) {
        val label = backward[index]
        val maxTop = if (index == backward.lastIndex) {
            bottomLimit - label.measured.box.height
        } else {
            backward[index + 1].labelTop - label.measured.box.height - minGap
        }
        backward[index] = label.copy(labelTop = min(label.labelTop, maxTop))
    }

    return backward.mapIndexed { index, label ->
        val minTop = backward.getOrNull(index - 1)?.let { it.labelTop + it.measured.box.height + minGap } ?: topLimit
        label.copy(labelTop = max(label.labelTop, minTop))
    }
}
