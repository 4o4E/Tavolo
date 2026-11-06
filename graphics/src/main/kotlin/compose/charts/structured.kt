package top.e404.tavolo.draw.compose.charts

import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import top.e404.tavolo.draw.compose.*
import top.e404.tavolo.util.FontManager
import top.e404.tavolo.util.bytes
import java.util.Locale

/** 手动范围必须包含全部有效数据，不通过裁剪隐藏数据。 */
data class ChartRange(val min: Float, val max: Float) {
    init {
        require(min.isFinite() && max.isFinite() && min < max && (max - min).isFinite()) {
            "坐标范围必须是递增且可表示的有限数值"
        }
    }
}

data class StructuredLineOptions(
    val yRange: ChartRange? = null,
    val includeZeroY: Boolean = true,
    val yTickCount: Int = 4,
    val lineWidth: Float = 2.5f,
    val pointRadius: Float = 3f,
    val showHorizontalGrid: Boolean = true
) {
    init {
        require(yTickCount in 1..100) { "纵轴刻度间隔数必须在 1–100 之间" }
        require(lineWidth.isFinite() && lineWidth > 0f && pointRadius.isFinite() && pointRadius >= 0f) {
            "折线宽度必须为有限正数，点半径必须为有限非负数"
        }
    }
}

data class TimeLinePoint(val epochMillis: Long, val y: Float?)

data class TimeLineSeries(
    val name: String,
    val points: List<TimeLinePoint>,
    val color: Int? = null,
    val fillColor: Int? = null,
    val lineStyle: StrokeStyle = StrokeStyle.Solid,
    val showPoints: Boolean = true
)

/** 显式区分数值横轴和毫秒时间轴；不推断单位、趋势、采样周期或缺失点。 */
sealed interface StructuredChart {
    data class Line(
        val series: List<LineSeries>,
        val xAxis: NumericXAxis = NumericXAxis(),
        val options: StructuredLineOptions = StructuredLineOptions()
    ) : StructuredChart

    data class TimeLine(
        val series: List<TimeLineSeries>,
        val xAxis: TimeXAxis = TimeXAxis(),
        val options: StructuredLineOptions = StructuredLineOptions()
    ) : StructuredChart

    data class Bars(
        val data: CategoryBarData,
        val mode: BarChartMode = BarChartMode.GROUPED,
        val yRange: ChartRange? = null,
        val yTickCount: Int = 4,
        val barAreaRatio: Float = 0.68f,
        val xAxis: CategoryXAxis = CategoryXAxis()
    ) : StructuredChart

    data class Pie(
        val slices: List<PieSlice>,
        val startAngle: Float = -90f,
        val cornerRadius: Float = 0f,
        val sliceGap: Float = 0f
    ) : StructuredChart

    data class Donut(
        val slices: List<PieSlice>,
        val innerRadiusRatio: Float = 0.6f,
        val startAngle: Float = -90f,
        val cornerRadius: Float = 0f,
        val sliceGap: Float = 0f
    ) : StructuredChart

    /** 轴值使用调用方给出的单位；maximum 是最外圈值，不默认猜测为百分比。 */
    data class Radar(
        val axes: List<Pair<String, Float>>,
        val maximum: Float,
        val color: Int? = null,
        val gridCount: Int = 5
    ) : StructuredChart
}

enum class StructuredLegendOrientation { HORIZONTAL, VERTICAL }

data class StructuredChartStyle(
    val width: Float = 900f,
    val plotHeight: Float = 360f,
    val padding: Float = 24f,
    val fontFamily: String = FontManager.defaultFamily,
    val fontSize: Float = 18f,
    val titleFontSize: Float = 28f,
    val textColor: Int = Color.makeRGB(40, 48, 60),
    val backgroundColor: Int = Color.WHITE,
    val plotBackgroundColor: Int = Color.TRANSPARENT,
    val gridColor: Int = Color.makeRGB(219, 225, 234),
    val palette: ChartPalette = ChartPalette.Default,
    val legendOrientation: StructuredLegendOrientation = StructuredLegendOrientation.HORIZONTAL,
    /** 仅格式化标签，不修改绘图数值；默认保留六位有效数字。 */
    val valueFormatter: (Float) -> String = ::formatStructuredNumber
) {
    init {
        require(listOf(width, plotHeight, fontSize, titleFontSize).all { it.isFinite() && it > 0f }) {
            "图表宽高与字号必须是有限正数"
        }
        require(padding.isFinite() && padding >= 0f) { "图表内边距必须是有限非负数" }
        require(width - 2 * padding >= fontSize * 4) { "图表宽度不足以容纳文字和内边距" }
        require(palette.colors.isNotEmpty()) { "图表配色不能为空" }
    }
}

fun UiElement.structuredChart(
    data: StructuredChart,
    title: String = "",
    unit: String = "",
    style: StructuredChartStyle = StructuredChartStyle(),
    modifier: Modifier = Modifier
) = add(StructuredChartElement(data, title, unit, style).apply { this.modifier = modifier })

fun renderChart(
    data: StructuredChart,
    title: String = "",
    unit: String = "",
    style: StructuredChartStyle = StructuredChartStyle()
): Image = render(style.backgroundColor) { structuredChart(data, title, unit, style) }

fun renderChartPng(
    data: StructuredChart,
    title: String = "",
    unit: String = "",
    style: StructuredChartStyle = StructuredChartStyle()
): ByteArray = renderChart(data, title, unit, style).use { it.bytes() }

internal data class StructuredLegend(val text: String, val color: Int, val stroke: StrokeStyle? = null)

private class StructuredChartElement(
    private val data: StructuredChart,
    private val title: String,
    private val unit: String,
    private val style: StructuredChartStyle
) : BaseElement() {
    override fun measureContent(context: MeasureContext) {
        val available = minOf(style.width, renderDimensionLimit(horizontal = true))
        val inner = available - style.padding * 2
        require(inner >= style.fontSize * 4) { "图表可用宽度不足" }
        children.clear()
        column(Modifier.width(available).background(style.backgroundColor).padding(style.padding)) {
            if (title.isNotEmpty()) {
                text(title, Modifier.sizeIn(maxWidth = inner).padding(bottom = 16f),
                    style = TextStyle(fontSize = style.titleFontSize, fontFamily = style.fontFamily,
                        textColor = style.textColor, fontWeight = 700))
            }
            val legends = when (val chart = data) {
                is StructuredChart.Line -> {
                    val prepared = prepareNumericLines(chart.series)
                    val axis = prepareNumericAxis(prepared.flatMap { it.points }.map { it.x }, chart.xAxis, style.valueFormatter)
                    add(linePlot(prepared, inner, chart.options, axis.range, axis.ticks, chart.xAxis.style))
                    lineLegends(prepared)
                }
                is StructuredChart.TimeLine -> {
                    val prepared = prepareTimeAxis(chart)
                    add(linePlot(prepared.series, inner, chart.options, prepared.range, prepared.ticks, chart.xAxis.style))
                    lineLegends(prepared.series)
                }
                is StructuredChart.Bars -> {
                    add(barPlot(chart, inner))
                    chart.data.series.mapIndexed { index, series ->
                        StructuredLegend(series.name, series.color ?: style.palette.colorAt(index))
                    }
                }
                is StructuredChart.Pie -> pie(chart.slices, inner, 0f, chart.startAngle, chart.cornerRadius, chart.sliceGap)
                is StructuredChart.Donut -> {
                    require(chart.innerRadiusRatio.isFinite() && chart.innerRadiusRatio > 0f && chart.innerRadiusRatio < 1f) {
                        "环形图内半径比例必须在 0 与 1 之间"
                    }
                    pie(chart.slices, inner, chart.innerRadiusRatio, chart.startAngle, chart.cornerRadius, chart.sliceGap)
                }
                is StructuredChart.Radar -> {
                    radarPlot(chart, inner, context)
                    emptyList()
                }
            }
            if (legends.isNotEmpty()) add(StructuredLegendLayout(legends, inner, style))
        }
        children.single().measure(context)
        contentWidth = available
        contentHeight = children.single().height
        requireRenderHeight(contentHeight)
    }

    override fun layoutChildren(content: Bounds) = children.single().layout(content.x, content.y)
    override fun drawContent(context: DrawContext) {}
    private fun textStyle() = TextStyle(fontSize = style.fontSize, fontFamily = style.fontFamily, textColor = style.textColor)
    private fun label(value: Float) = style.valueFormatter(value) + unit

    private fun lineLegends(series: List<LineSeries>) = series.mapIndexed { index, item ->
        StructuredLegend(item.name, item.color ?: style.palette.colorAt(index), item.lineStyle)
    }

    private fun linePlot(
        series: List<LineSeries>,
        width: Float,
        options: StructuredLineOptions,
        xRange: ChartRange,
        ticks: List<StructuredAxisTick>,
        xAxis: XAxisStyle
    ): UiElement {
        val ys = series.flatMap { it.points }.mapNotNull { it.y }
        val yRange = checkedRange(ys, options.yRange, options.includeZeroY)
        val inkOutset = maxOf(options.lineWidth / 2f, if (series.any { it.showPoints }) options.pointRadius else 0f)
        return CoordinatePlot(width, style, yRange, options.yTickCount, ticks, xAxis, options.showHorizontalGrid, ::label, inkOutset) { canvas, context, insets, height ->
            drawLineChart(canvas, 0f, 0f, series, LineChartTheme(
                width = width, height = height, insets = insets,
                palette = style.palette, legend = ChartLegendTheme(ChartLegendPosition.NONE),
                lineWidth = options.lineWidth, pointRadius = options.pointRadius,
                maxPointsPerSeries = 0, xMin = xRange.min, xMax = xRange.max, yMin = yRange.min, yMax = yRange.max,
                showAxes = false
            ), context)
        }
    }

    private fun barPlot(chart: StructuredChart.Bars, width: Float): UiElement {
        require(chart.data.categories.isNotEmpty() && chart.data.series.isNotEmpty()) { "柱状图分类和序列不能为空" }
        require(chart.yTickCount in 1..100) { "柱状图刻度间隔数必须在 1–100 之间" }
        require(chart.barAreaRatio.isFinite() && chart.barAreaRatio in 0.1f..1f) { "柱状图组宽比例必须在 0.1–1 之间" }
        chart.data.categories.forEach { require(it.isNotBlank()) { "柱状图分类名称不能为空" } }
        chart.data.series.forEach { series ->
            require(series.name.isNotBlank()) { "柱状图序列名称不能为空" }
            require(series.values.size == chart.data.categories.size) { "柱状图每条序列必须完整对应分类，不补零或丢弃数据" }
            require(series.values.all { it.isFinite() }) { "柱状图数值必须有限" }
        }
        val values = if (chart.mode == BarChartMode.STACKED) chart.data.categories.indices.flatMap { index ->
            val items = chart.data.series.map { it.values[index] }
            listOf(items.filter { it > 0 }.sum(), items.filter { it < 0 }.sum())
        } else chart.data.series.flatMap { it.values }
        val range = checkedRange(values, chart.yRange, true)
        val count = chart.data.categories.size
        val ticks = chart.data.categories.mapIndexed { index, text ->
            val label = if (index % (chart.xAxis.labelInterval + 1) == 0) chart.xAxis.formatter?.invoke(text, index) ?: text else null
            StructuredAxisTick((index + 0.5f) / count, label)
        }
        return CoordinatePlot(width, style, range, chart.yTickCount, ticks, chart.xAxis.style, true, ::label) { canvas, context, insets, height ->
            drawCategoryBarChart(canvas, 0f, 0f, chart.data, CategoryBarTheme(
                width = width, height = height, insets = insets, palette = style.palette,
                legend = ChartLegendTheme(ChartLegendPosition.NONE), mode = chart.mode,
                barAreaRatio = chart.barAreaRatio, yMin = range.min, yMax = range.max, showAxes = false
            ), context)
        }
    }


    private fun UiElement.pie(
        slices: List<PieSlice>, width: Float, innerRatio: Float, start: Float, corner: Float, gap: Float
    ): List<StructuredLegend> {
        require(slices.isNotEmpty()) { "饼图数据不能为空" }
        require(slices.all { it.label.isNotBlank() && it.value.isFinite() && it.value >= 0f }) {
            "饼图标签不能为空，数值必须是有限非负数"
        }
        val total = slices.sumOf { it.value.toDouble() }
        require(total > 0 && total.toFloat().isFinite()) { "饼图总值必须是可表示的正数" }
        require(slices.all { it.value == 0f || it.value / total.toFloat() > 0f }) { "饼图份额超出可表示精度" }
        val colored = slices.mapIndexed { index, item -> item.copy(color = item.color ?: style.palette.colorAt(index)) }
        require(start.isFinite()) { "饼图起始角度必须有限" }
        val radius = minOf(width, style.plotHeight) / 2f - 8f
        require(radius > 0f) { "饼图尺寸不足" }
        pieChart(PieChartTheme(
            width = width, height = style.plotHeight, radius = radius, innerRadius = radius * innerRatio,
            insets = ChartInsets(left = width / 2 - radius, top = style.plotHeight / 2 - radius, right = 0f, bottom = 0f),
            palette = style.palette,
            legend = ChartLegendTheme(ChartLegendPosition.NONE), showLabels = false,
            startAngle = start, cornerRadius = corner, sliceGap = gap
        ), colored)
        return colored.map { item ->
            val percent = String.format(Locale.ROOT, "%.2f%%", item.value / total * 100)
            StructuredLegend("${item.label} · ${label(item.value)} ($percent)", item.color!!)
        }
    }

    private fun UiElement.radarPlot(chart: StructuredChart.Radar, width: Float, context: MeasureContext) {
        require(chart.axes.size >= 3) { "雷达图至少需要三个维度" }
        require(chart.maximum.isFinite() && chart.maximum > 0f && chart.gridCount in 1..100) { "雷达图最大值必须为有限正数，网格数必须在 1–100 之间" }
        require(chart.axes.all { it.first.isNotBlank() && it.second.isFinite() && it.second in 0f..chart.maximum }) {
            "雷达图维度不能为空，数值必须位于 0 与 maximum 之间"
        }
        val labels = chart.axes.map { (name, value) -> "$name ${label(value)}" }
        val maxLabelWidth = labels.maxOf { text ->
            Text(text, style = textStyle()).also { it.measure(context) }.width
        }
        val radius = minOf((width - 2 * maxLabelWidth - 48f) / 2, (style.plotHeight - 4 * style.fontSize - 32f) / 2)
        require(radius >= style.fontSize) { "雷达图宽度不足以容纳标签，请增大宽度或减小字号" }
        val chartRoot = Column(HorizontalAlignment.Center)
        chartRoot.radar(RadarTheme(
            width = width, height = style.plotHeight, radius = radius,
            fillOutlineColor = chart.color ?: style.palette.colorAt(0), gridCount = chart.gridCount,
            gridLineColor = style.gridColor, gridFontColor = style.textColor,
            gridFontFamily = style.fontFamily, gridFontSize = style.fontSize * 0.7f,
            gridFontProvider = { label((chart.maximum.toDouble() * (it + 1) / chart.gridCount).toFloat()) },
            labelFontColor = style.textColor, labelFontFamily = style.fontFamily, labelFontSize = style.fontSize,
            sizeMode = RadarSizeMode.NATURAL, naturalPadding = 8f
        ), chart.axes.mapIndexed { index, item -> labels[index] to item.second / chart.maximum })
        chartRoot.measure(context)
        require(chartRoot.width <= width) { "雷达图标签超出可用宽度，请增大宽度" }
        box(Modifier.width(width), horizontalAlignment = HorizontalAlignment.Center) { add(chartRoot) }
    }
}


private fun automaticRange(values: List<Float>, includeZero: Boolean): ChartRange {
    require(values.isNotEmpty() && values.all { it.isFinite() }) { "图表必须包含有限数值" }
    var min = values.min()
    var max = values.max()
    if (includeZero) { min = minOf(min, 0f); max = maxOf(max, 0f) }
    if (min == max) {
        val delta = maxOf(kotlin.math.abs(min) * 0.1f, 1f)
        min -= delta
        max += delta
    }
    return ChartRange(min, max)
}

private fun checkedRange(values: List<Float>, requested: ChartRange?, includeZero: Boolean): ChartRange {
    require(values.isNotEmpty() && values.all { it.isFinite() }) { "图表必须包含有限数值" }
    if (requested == null) return automaticRange(values, includeZero)
    require(values.all { it in requested.min..requested.max }) { "手动纵轴范围未包含全部数据" }
    return requested
}

internal fun validateStructuredSeries(name: String, ys: List<Float?>, stroke: StrokeStyle) {
    require(name.isNotBlank()) { "折线名称不能为空" }
    require(ys.isNotEmpty() && ys.any { it != null }) { "折线 $name 不能为空或全部缺失" }
    require(ys.all { it == null || it.isFinite() }) { "折线 $name 包含非有限数值，请用 null 表达缺失点" }
    validateChartStroke(stroke)
}

internal fun prepareNumericLines(series: List<LineSeries>): List<LineSeries> {
    require(series.isNotEmpty()) { "折线图序列不能为空" }
    return series.map { item ->
        validateStructuredSeries(item.name, item.points.map { it.y }, item.lineStyle)
        require(item.points.all { it.x.isFinite() }) { "横坐标必须有限" }
        val points = item.points.sortedBy { it.x }
        require(points.zipWithNext().all { it.first.x < it.second.x }) { "同一曲线不能在相同横坐标包含多个点" }
        item.copy(points = points)
    }
}

