package top.e404.tavolo.http.server.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import top.e404.tavolo.draw.compose.*
import top.e404.tavolo.draw.compose.charts.*
import top.e404.tavolo.http.server.dto.*
import java.time.DateTimeException
import top.e404.tavolo.util.FontManager
import java.time.ZoneId
import java.util.Base64

/** 请求只携带文档／图表和显式图片内容，不把路径或 URL 当作资源读取指令。 */
class RenderService {
    suspend fun markdown(request: RenderMarkdownRequest): ByteArray = renderRequest {
        val decoded = mutableMapOf<String, Image>()
        try {
            val input = request.style
            val dark = input.darkTheme
            val style = MarkdownStyle(
                fontFamily = input.fontFamily ?: FontManager.defaultFamily,
                codeFontFamily = input.codeFontFamily ?: input.fontFamily ?: FontManager.defaultFamily,
                fontSize = input.fontSize,
                textColor = input.textColor.colorOr(if (dark) 0xffe6edf3.toInt() else 0xff1f2328.toInt()),
                backgroundColor = input.backgroundColor.colorOr(if (dark) 0xff0d1117.toInt() else 0xffffffff.toInt()),
                linkColor = input.linkColor.colorOr(if (dark) 0xff79c0ff.toInt() else 0xff0969da.toInt()),
                codeBackgroundColor = input.codeBackgroundColor.colorOr(if (dark) 0xff161b22.toInt() else 0xfff6f8fa.toInt()),
                quoteColor = input.quoteColor.colorOr(if (dark) 0xff30363d.toInt() else 0xffd1d9e0.toInt()),
                tableBorderColor = input.tableBorderColor.colorOr(if (dark) 0xff30363d.toInt() else 0xffd1d9e0.toInt()),
                blockSpacing = input.blockSpacing, blockPadding = input.blockPadding,
                pagePadding = input.pagePadding, lineHeight = input.lineHeight, darkTheme = dark,
            )
            renderMarkdownPng(request.source, request.width, style,
                imageResolver = { destination -> decoded.getOrPut(destination) {
                    val encoded = requireNotNull(request.images[destination]) { "缺少图片内容: $destination" }
                    val bytes = Base64.getDecoder().decode(encoded)
                    Image.makeFromEncoded(bytes)
                } },
                context = MarkdownContext(softBreak = MarkdownSoftBreak.valueOf(request.softBreak.name)),
                pixelRatio = request.pixelRatio)
        } finally {
            decoded.values.forEach { it.close() }
        }
    }

    suspend fun chart(request: RenderChartRequest): ByteArray = renderRequest {
        val input = request.style
        val style = StructuredChartStyle(
            width = input.width, plotHeight = input.plotHeight, padding = input.padding,
            fontFamily = input.fontFamily ?: FontManager.defaultFamily,
            fontSize = input.fontSize, titleFontSize = input.titleFontSize,
            textColor = input.textColor.color(), backgroundColor = input.backgroundColor.color(),
            plotBackgroundColor = input.plotBackgroundColor.color(), gridColor = input.gridColor.color(),
            palette = input.palette?.let { ChartPalette(it.map(String::color)) } ?: ChartPalette.Default,
            legendOrientation = StructuredLegendOrientation.valueOf(input.legendOrientation.name),
        )
        renderChartPng(request.data.model(), request.title, request.unit, style)
    }

    private suspend fun renderRequest(block: () -> ByteArray): ByteArray = withContext(Dispatchers.Default) {
        try { block() } catch (error: IllegalArgumentException) {
            throw BadRenderRequestException(error.message ?: "渲染参数错误", error)
        }
    }
}

class BadRenderRequestException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

private fun String.color(): Int {
    require(startsWith('#') && (length == 7 || length == 9) && drop(1).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
        "颜色必须使用 #RRGGBB 或 #AARRGGBB: $this"
    }
    val value = substring(1).toLong(16).toInt()
    return if (length == 7) value or 0xff000000.toInt() else value
}
private fun String?.colorOr(default: Int): Int = this?.color() ?: default
private fun NumberRange.model() = ChartRange(min, max)
private fun LineOptions.model() = StructuredLineOptions(yRange?.model(), includeZeroY, yTickCount, lineWidth, pointRadius, showHorizontalGrid)
private fun LineStroke.model(): StrokeStyle = when (this) {
    LineStroke.Solid -> StrokeStyle.Solid
    is LineStroke.Dashed -> StrokeStyle.Dashed(intervals, phase)
    is LineStroke.Dotted -> StrokeStyle.Dotted(dot, gap, phase)
}
private fun BoundaryGap.model() = AxisBoundaryGap(start, end)
private fun AxisStroke.model() = ChartStroke(color.color(), width, style.model())
private fun AxisText.model() = ChartTextStyle(fontSize, color.color(), fontFamily ?: FontManager.defaultFamily, fontWeight, italic, scaleX)
private fun AxisStyle.model() = XAxisStyle(name, nameGap,
    XAxisLabelStyle(label.show, label.rotate, label.margin, label.maxWidth, label.textStyle?.model()),
    showAxisLine, axisLine?.model(), showAxisTick, axisTick?.model(), tickLength, showSplitLine, splitLine?.model())
private fun NumericAxis.model() = NumericXAxis(min, max, boundaryGap.model(), splitNumber, interval, unit, style = style.model())
private fun timeZone(value: String): ZoneId = try { ZoneId.of(value) } catch (error: DateTimeException) {
    throw IllegalArgumentException("无效的时间轴时区: $value", error)
}
private fun TimeAxis.model() = TimeXAxis(min, max, boundaryGap.model(), splitNumber, intervalMillis,
    timeZone(zone), TimeAxisFormat.valueOf(format.name), pattern, style = style.model())
private fun CategoryAxis.model() = CategoryXAxis(labelInterval, style = style.model())
private fun Slice.model() = PieSlice(name, value, color?.color())
private fun ChartData.model(): StructuredChart = when (this) {
    is ChartData.Line -> StructuredChart.Line(series.map { LineSeries(it.name, it.points.map { point -> LinePoint(point.x, point.y) },
        it.color?.color(), it.fillColor?.color(), it.lineStyle.model(), it.showPoints) }, xAxis.model(), options.model())
    is ChartData.TimeLine -> StructuredChart.TimeLine(series.map { TimeLineSeries(it.name,
        it.points.map { point -> TimeLinePoint(point.epochMillis, point.y) }, it.color?.color(), it.fillColor?.color(),
        it.lineStyle.model(), it.showPoints) }, xAxis.model(), options.model())
    is ChartData.Bars -> StructuredChart.Bars(CategoryBarData(categories, series.map { BarSeries(it.name, it.values, it.color?.color()) }),
        BarChartMode.valueOf(mode.name), yRange?.model(), yTickCount, barAreaRatio, xAxis.model())
    is ChartData.Pie -> StructuredChart.Pie(slices.map { it.model() }, startAngle, cornerRadius, sliceGap)
    is ChartData.Donut -> StructuredChart.Donut(slices.map { it.model() }, innerRadiusRatio, startAngle, cornerRadius, sliceGap)
    is ChartData.Radar -> StructuredChart.Radar(axes.map { it.name to it.value }, maximum, color?.color(), gridCount)
}
