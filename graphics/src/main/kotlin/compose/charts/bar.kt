package top.e404.tavolo.draw.compose.charts

import org.jetbrains.skia.*
import top.e404.tavolo.draw.compose.*

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
    val minLabelPercent: Float = 0.04f,
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

fun UiElement.pieChart(theme: PieChartTheme, data: List<PieSlice>) = add(
    CanvasElement(theme.width, theme.height) { canvas, measureContext ->
        drawPieChart(canvas, parentX, parentY, data, theme, measureContext)
    }
)

fun UiElement.donutChart(theme: PieChartTheme, data: List<PieSlice>) = pieChart(theme, data)

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
        if (theme.showLabels && slice.value / total >= theme.minLabelPercent) {
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

private data class PieLabelState(
    val slice: PieSlice,
    val startAngle: Float,
    val sweepAngle: Float
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

private fun drawPieLabels(
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
