package top.e404.tavolo.draw.compose.charts

import top.e404.tavolo.draw.compose.StrokeStyle
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/** 参考 ECharts 的静态轴语义；正角度表示逆时针旋转，不提供交互缩放或对数轴。 */
data class XAxisLabelStyle(
    val show: Boolean = true,
    val rotate: Float = 0f,
    val margin: Float = 10f,
    val maxWidth: Float? = null,
    val textStyle: ChartTextStyle? = null
) {
    init {
        require(rotate.isFinite() && rotate in -90f..90f) { "横轴标签旋转角度必须在 -90–90 度之间" }
        require(margin.isFinite() && margin >= 0f) { "横轴标签间距必须为有限非负数" }
        require(maxWidth == null || maxWidth.isFinite() && maxWidth > 0f) { "横轴标签宽度必须为有限正数" }
        require(textStyle == null || textStyle.fontSize.isFinite() && textStyle.fontSize > 0f) { "横轴标签字号必须为有限正数" }
        require(textStyle?.scaleX == null || textStyle.scaleX.isFinite() && textStyle.scaleX > 0f) {
            "横轴标签横向缩放必须为有限正数"
        }
    }
}

data class XAxisStyle(
    val name: String = "",
    val nameGap: Float = 16f,
    val label: XAxisLabelStyle = XAxisLabelStyle(),
    val showAxisLine: Boolean = true,
    val axisLine: ChartStroke? = null,
    val showAxisTick: Boolean = true,
    val axisTick: ChartStroke? = null,
    val tickLength: Float = 5f,
    val showSplitLine: Boolean = true,
    val splitLine: ChartStroke? = null
) {
    init {
        require(nameGap.isFinite() && nameGap >= 0f && tickLength.isFinite() && tickLength >= 0f) {
            "横轴名称间距和刻度长度必须是有限非负数"
        }
        listOfNotNull(axisLine, axisTick, splitLine).forEach {
            require(it.width.isFinite() && it.width >= 0f) { "横轴线宽必须是有限非负数" }
            validateChartStroke(it.style)
        }
    }
}

/** 连续轴两端相对数据跨度的留白比例；显式 min/max 优先于对应侧留白。 */
data class AxisBoundaryGap(val start: Float = 0f, val end: Float = 0f) {
    init {
        require(start.isFinite() && end.isFinite() && start >= 0f && end >= 0f) { "横轴留白比例必须是有限非负数" }
    }
}

data class NumericXAxis(
    val min: Float? = null,
    val max: Float? = null,
    val boundaryGap: AxisBoundaryGap = AxisBoundaryGap(),
    val splitNumber: Int = 4,
    val interval: Float? = null,
    val unit: String = "",
    val formatter: ((Float) -> String)? = null,
    val style: XAxisStyle = XAxisStyle()
) {
    init {
        require(min == null || min.isFinite()) { "横轴 min 必须有限" }
        require(max == null || max.isFinite()) { "横轴 max 必须有限" }
        require(min == null || max == null || min < max) { "横轴 min 必须小于 max" }
        require(splitNumber in 1..100) { "横轴期望分段数必须在 1–100 之间" }
        require(interval == null || interval.isFinite() && interval > 0f) { "横轴固定刻度间隔必须为有限正数" }
    }
}

enum class TimeAxisFormat(val pattern: String) {
    DATE("yyyy-MM-dd"),
    HOUR_MINUTE("HH:mm"),
    HOUR_MINUTE_SECOND("HH:mm:ss"),
    DATE_TIME("MM-dd\nHH:mm"),
    MILLISECOND("MM-dd\nHH:mm:ss.SSS")
}

data class TimeXAxis(
    val min: Long? = null,
    val max: Long? = null,
    val boundaryGap: AxisBoundaryGap = AxisBoundaryGap(),
    val splitNumber: Int = 4,
    val intervalMillis: Long? = null,
    val zone: ZoneId = ZoneOffset.UTC,
    val format: TimeAxisFormat = TimeAxisFormat.MILLISECOND,
    val pattern: String? = null,
    val formatter: ((Instant) -> String)? = null,
    val style: XAxisStyle = XAxisStyle()
) {
    init {
        require(min == null || max == null || min < max) { "时间轴 min 必须早于 max" }
        require(splitNumber in 1..100) { "时间轴期望分段数必须在 1–100 之间" }
        require(intervalMillis == null || intervalMillis > 0) { "时间轴固定间隔必须为正毫秒数" }
    }
}

/** 分类柱状图始终保留完整分类和两端半个分组宽度；labelInterval 只跳过标签，不跳过数据。 */
data class CategoryXAxis(
    val labelInterval: Int = 0,
    val formatter: ((String, Int) -> String)? = null,
    val style: XAxisStyle = XAxisStyle()
) {
    init { require(labelInterval in 0..1000) { "分类标签跳过数量必须在 0–1000 之间" } }
}

internal data class StructuredAxisTick(val position: Float, val label: String?)
internal data class PreparedNumericAxis(val range: ChartRange, val ticks: List<StructuredAxisTick>)
internal data class PreparedTimeAxis(val series: List<LineSeries>, val range: ChartRange, val ticks: List<StructuredAxisTick>)

internal fun formatStructuredNumber(value: Float): String =
    BigDecimal(value.toString()).round(MathContext(6)).stripTrailingZeros().toPlainString()

private fun niceStep(target: Double): Double {
    val magnitude = 10.0.pow(floor(log10(target)))
    val fraction = target / magnitude
    return magnitude * when {
        fraction <= 1 -> 1
        fraction <= 2 -> 2
        fraction <= 5 -> 5
        else -> 10
    }
}

internal fun prepareNumericAxis(values: List<Float>, axis: NumericXAxis, formatter: (Float) -> String): PreparedNumericAxis {
    var lo = values.min().toDouble()
    var hi = values.max().toDouble()
    require(axis.min == null || axis.min <= lo) { "横轴 min 排除了已有数据" }
    require(axis.max == null || axis.max >= hi) { "横轴 max 排除了已有数据" }
    if (lo == hi) {
        val delta = maxOf(kotlin.math.abs(lo) * 0.1, 1.0)
        lo -= delta
        hi += delta
    }
    val span = hi - lo
    lo = axis.min?.toDouble() ?: lo - span * axis.boundaryGap.start
    hi = axis.max?.toDouble() ?: hi + span * axis.boundaryGap.end
    require(lo < hi && lo.isFinite() && hi.isFinite()) { "横轴范围无法表示" }
    val step = axis.interval?.toDouble() ?: niceStep((hi - lo) / axis.splitNumber)
    if (axis.min == null) lo = floor(lo / step) * step
    if (axis.max == null) hi = ceil(hi / step) * step
    val range = ChartRange(lo.toFloat(), hi.toFloat())
    val first = ceil(lo / step)
    val last = floor(hi / step)
    val count = last - first + 1
    require(count in 1.0..1000.0) { "横轴范围内刻度数量必须在 1–1000 之间，请调整 interval 或范围" }
    val ticks = (0 until count.toInt()).map { index ->
        val value = ((first + index) * step).toFloat()
        StructuredAxisTick((value - range.min) / (range.max - range.min), axis.formatter?.invoke(value) ?: formatter(value) + axis.unit)
    }
    require(ticks.zipWithNext().all { it.first.position < it.second.position }) { "横轴刻度间隔超出 Float 可表示精度" }
    return PreparedNumericAxis(range, ticks)
}

private val timeSteps = longArrayOf(
    1, 2, 5, 10, 20, 50, 100, 200, 500,
    1_000, 2_000, 5_000, 10_000, 15_000, 30_000,
    60_000, 120_000, 300_000, 600_000, 900_000, 1_800_000,
    3_600_000, 7_200_000, 14_400_000, 21_600_000, 43_200_000, 86_400_000
)

private inline fun checkedTime(block: () -> Long): Long = try { block() } catch (error: ArithmeticException) {
    throw IllegalArgumentException("时间跨度超出可表示范围", error)
}

internal fun prepareTimeAxis(chart: StructuredChart.TimeLine): PreparedTimeAxis {
    require(chart.series.isNotEmpty()) { "时间序列不能为空" }
    chart.series.forEach { validateStructuredSeries(it.name, it.points.map { point -> point.y }, it.lineStyle) }
    val times = chart.series.flatMap { it.points }.map { it.epochMillis }.distinct().sorted()
    val axis = chart.xAxis
    val dataMin = times.first()
    val dataMax = times.last()
    require(axis.min == null || axis.min <= dataMin) { "时间轴 min 排除了已有数据" }
    require(axis.max == null || axis.max >= dataMax) { "时间轴 max 排除了已有数据" }
    val span = checkedTime { Math.subtractExact(dataMax, dataMin) }.coerceAtLeast(1L)
    val leftGap = (span.toDouble() * axis.boundaryGap.start).let {
        require(it.isFinite() && it < Long.MAX_VALUE.toDouble()) { "时间轴左侧留白超出可表示范围" }
        it.roundToLong()
    }
    val rightGap = (span.toDouble() * axis.boundaryGap.end).let {
        require(it.isFinite() && it < Long.MAX_VALUE.toDouble()) { "时间轴右侧留白超出可表示范围" }
        it.roundToLong()
    }
    var lo = axis.min ?: checkedTime { Math.subtractExact(dataMin, leftGap) }
    var hi = axis.max ?: checkedTime { Math.addExact(dataMax, rightGap) }
    if (lo == hi) {
        if (axis.min == null) lo = checkedTime { Math.subtractExact(lo, 1) }
        if (axis.max == null) hi = checkedTime { Math.addExact(hi, 1) }
    }
    val domainSpan = checkedTime { Math.subtractExact(hi, lo) }
    require(domainSpan > 0) { "时间轴范围必须递增" }
    val target = domainSpan.toDouble() / axis.splitNumber
    val step = axis.intervalMillis ?: timeSteps.firstOrNull { it >= target }
        ?: (niceStep(target / 86_400_000) * 86_400_000).let {
            require(it < Long.MAX_VALUE.toDouble()) { "时间轴刻度间隔超出可表示范围" }
            it.roundToLong()
        }
    // 固定时长刻度按 epoch 对齐；时区仅影响标签，不改变时间点位置或夏令时下的实际间距。
    val remainder = Math.floorMod(lo, step)
    val firstOffset = if (remainder == 0L) 0L else step - remainder
    require(firstOffset <= domainSpan) { "时间轴范围内没有刻度，请减小 intervalMillis" }
    val count = (domainSpan - firstOffset) / step + 1
    require(count in 1..1000) { "时间轴刻度过密，请增大 intervalMillis" }
    val dateFormatter = if (axis.formatter == null) DateTimeFormatter.ofPattern(axis.pattern ?: axis.format.pattern, Locale.ROOT).withZone(axis.zone) else null
    val ticks = (0 until count.toInt()).map { index ->
        val offset = checkedTime { Math.addExact(firstOffset, Math.multiplyExact(index.toLong(), step)) }
        val instant = Instant.ofEpochMilli(checkedTime { Math.addExact(lo, offset) })
        StructuredAxisTick(offset.toFloat() / domainSpan.toFloat(), axis.formatter?.invoke(instant) ?: dateFormatter!!.format(instant))
    }
    val offsets = times.associateWith { checkedTime { Math.subtractExact(it, lo) }.toFloat() }
    require(times.zipWithNext().all { offsets.getValue(it.first) < offsets.getValue(it.second) }) {
        "时间跨度与采样精度无法同时表示，请缩小时间范围；不会合并相邻时间点"
    }
    require(ticks.zipWithNext().all { it.first.position < it.second.position }) { "时间刻度间隔超出可表示精度" }
    val series = chart.series.map { item ->
        LineSeries(item.name, item.points.map { LinePoint(offsets.getValue(it.epochMillis), it.y) },
            item.color, item.fillColor, item.lineStyle, item.showPoints)
    }
    return PreparedTimeAxis(prepareNumericLines(series), ChartRange(0f, domainSpan.toFloat()), ticks)
}

internal fun validateChartStroke(stroke: StrokeStyle) {
    when (stroke) {
        StrokeStyle.Solid -> Unit
        is StrokeStyle.Dashed -> require(stroke.intervals.isNotEmpty() && stroke.intervals.all { it.isFinite() && it > 0f } && stroke.phase.isFinite()) {
            "虚线间隔必须是有限正数，phase 必须有限"
        }
        is StrokeStyle.Dotted -> require(stroke.dot.isFinite() && stroke.dot > 0f && stroke.gap.isFinite() && stroke.gap > 0f && stroke.phase.isFinite()) {
            "点线尺寸和间隔必须是有限正数，phase 必须有限"
        }
    }
}
