package top.e404.tavolo.http.server.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 图片键是 Markdown 中的原始目标，值是图片文件的 Base64；渲染器不读取远端或本地路径。 */
@Serializable
data class RenderMarkdownRequest(
    val source: String,
    val width: Float = 800f,
    val pixelRatio: Float = 2f,
    val style: MarkdownRenderStyle = MarkdownRenderStyle(),
    val softBreak: SoftBreak = SoftBreak.SPACE,
    val images: Map<String, String> = emptyMap(),
)

@Serializable
enum class SoftBreak { SPACE, NEWLINE }

@Serializable
data class MarkdownRenderStyle(
    val fontFamily: String? = null,
    val codeFontFamily: String? = null,
    val fontSize: Float = 16f,
    val darkTheme: Boolean = false,
    val textColor: String? = null,
    val backgroundColor: String? = null,
    val linkColor: String? = null,
    val codeBackgroundColor: String? = null,
    val quoteColor: String? = null,
    val tableBorderColor: String? = null,
    val blockSpacing: Float = 16f,
    val blockPadding: Float = 16f,
    val pagePadding: Float = 32f,
    val lineHeight: Float = 1.5f,
)

@Serializable
data class RenderChartRequest(
    val data: ChartData,
    val title: String = "",
    val unit: String = "",
    val style: ChartRenderStyle = ChartRenderStyle(),
)

/** type 显式区分六种数据模型；横坐标和空值由调用方提供。 */
@Serializable
sealed interface ChartData {
    @Serializable @SerialName("line")
    data class Line(val series: List<NumericSeries>, val xAxis: NumericAxis = NumericAxis(), val options: LineOptions = LineOptions()) : ChartData
    @Serializable @SerialName("timeLine")
    data class TimeLine(val series: List<TimeSeries>, val xAxis: TimeAxis = TimeAxis(), val options: LineOptions = LineOptions()) : ChartData
    @Serializable @SerialName("bars")
    data class Bars(val categories: List<String>, val series: List<BarSeriesData>, val mode: BarMode = BarMode.GROUPED,
        val yRange: NumberRange? = null, val yTickCount: Int = 4, val barAreaRatio: Float = 0.68f,
        val xAxis: CategoryAxis = CategoryAxis()) : ChartData
    @Serializable @SerialName("pie")
    data class Pie(val slices: List<Slice>, val startAngle: Float = -90f, val cornerRadius: Float = 0f, val sliceGap: Float = 0f) : ChartData
    @Serializable @SerialName("donut")
    data class Donut(val slices: List<Slice>, val innerRadiusRatio: Float = 0.6f, val startAngle: Float = -90f,
        val cornerRadius: Float = 0f, val sliceGap: Float = 0f) : ChartData
    @Serializable @SerialName("radar")
    data class Radar(val axes: List<RadarValue>, val maximum: Float, val color: String? = null, val gridCount: Int = 5) : ChartData
}

@Serializable
data class NumericPoint(val x: Float, val y: Float?)
@Serializable
data class TimePoint(val epochMillis: Long, val y: Float?)
@Serializable
data class NumericSeries(val name: String, val points: List<NumericPoint>, val color: String? = null,
    val fillColor: String? = null, val lineStyle: LineStroke = LineStroke.Solid, val showPoints: Boolean = true)
@Serializable
data class TimeSeries(val name: String, val points: List<TimePoint>, val color: String? = null,
    val fillColor: String? = null, val lineStyle: LineStroke = LineStroke.Solid, val showPoints: Boolean = true)
@Serializable
data class BarSeriesData(val name: String, val values: List<Float>, val color: String? = null)
@Serializable
enum class BarMode { GROUPED, STACKED }
@Serializable
data class Slice(val name: String, val value: Float, val color: String? = null)
@Serializable
data class RadarValue(val name: String, val value: Float)
@Serializable
data class NumberRange(val min: Float, val max: Float)
@Serializable
data class LineOptions(val yRange: NumberRange? = null, val includeZeroY: Boolean = true, val yTickCount: Int = 4,
    val lineWidth: Float = 2.5f, val pointRadius: Float = 3f, val showHorizontalGrid: Boolean = true)

@Serializable
sealed interface LineStroke {
    @Serializable @SerialName("solid") data object Solid : LineStroke
    @Serializable @SerialName("dashed") data class Dashed(val intervals: List<Float> = listOf(8f, 4f), val phase: Float = 0f) : LineStroke
    @Serializable @SerialName("dotted") data class Dotted(val dot: Float = 1f, val gap: Float = 3f, val phase: Float = 0f) : LineStroke
}

@Serializable
data class BoundaryGap(val start: Float = 0f, val end: Float = 0f)
@Serializable
data class NumericAxis(val min: Float? = null, val max: Float? = null, val boundaryGap: BoundaryGap = BoundaryGap(),
    val splitNumber: Int = 4, val interval: Float? = null, val unit: String = "", val style: AxisStyle = AxisStyle())
@Serializable
data class TimeAxis(val min: Long? = null, val max: Long? = null, val boundaryGap: BoundaryGap = BoundaryGap(),
    val splitNumber: Int = 4, val intervalMillis: Long? = null, val zone: String = "UTC",
    val format: TimeFormat = TimeFormat.MILLISECOND, val pattern: String? = null, val style: AxisStyle = AxisStyle())
@Serializable
enum class TimeFormat { DATE, HOUR_MINUTE, HOUR_MINUTE_SECOND, DATE_TIME, MILLISECOND }
@Serializable
data class CategoryAxis(val labelInterval: Int = 0, val style: AxisStyle = AxisStyle())
@Serializable
data class AxisStyle(val name: String = "", val nameGap: Float = 16f, val label: AxisLabel = AxisLabel(),
    val showAxisLine: Boolean = true, val axisLine: AxisStroke? = null, val showAxisTick: Boolean = true,
    val axisTick: AxisStroke? = null, val tickLength: Float = 5f, val showSplitLine: Boolean = true, val splitLine: AxisStroke? = null)
@Serializable
data class AxisLabel(val show: Boolean = true, val rotate: Float = 0f, val margin: Float = 10f,
    val maxWidth: Float? = null, val textStyle: AxisText? = null)
@Serializable
data class AxisStroke(val color: String, val width: Float = 1f, val style: LineStroke = LineStroke.Solid)
@Serializable
data class AxisText(val fontSize: Float, val color: String, val fontFamily: String? = null,
    val fontWeight: Int? = null, val italic: Boolean = false, val scaleX: Float? = null)
@Serializable
enum class LegendOrientation { HORIZONTAL, VERTICAL }
@Serializable
data class ChartRenderStyle(val width: Float = 900f, val plotHeight: Float = 360f, val padding: Float = 24f,
    val fontFamily: String? = null, val fontSize: Float = 18f, val titleFontSize: Float = 28f,
    val textColor: String = "#28303C", val backgroundColor: String = "#FFFFFF", val plotBackgroundColor: String = "#00000000",
    val gridColor: String = "#DBE1EA", val palette: List<String>? = null, val legendOrientation: LegendOrientation = LegendOrientation.HORIZONTAL)
