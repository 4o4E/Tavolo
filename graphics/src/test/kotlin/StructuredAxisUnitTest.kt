package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.junit.Test
import top.e404.tavolo.draw.compose.*
import top.e404.tavolo.draw.compose.charts.*
import java.time.Instant
import java.time.ZoneId
import kotlin.test.*

class StructuredAxisUnitTest {
    private val series = listOf(LineSeries("数据", listOf(LinePoint(0f, 1f), LinePoint(6f, 2f)), Color.RED))
    private fun commands(data: StructuredChart, style: StructuredChartStyle = StructuredChartStyle()) =
        renderCommands(measureContext = MeasureContext(FixedTextMeasurer())) { structuredChart(data, style = style) }

    @Test
    fun fixedIntervalOverridesSplitNumberAndManualRangePreservesPointPositions() {
        val output = commands(StructuredChart.Line(series, NumericXAxis(
            min = -2f, max = 8f, interval = 2f, splitNumber = 99, formatter = { "x=${it.toInt()}" }
        )))
        val labels = output.filterIsInstance<DrawCommand.Text>().map { it.text }.filter { it.startsWith("x=") }
        assertEquals(listOf("x=-2", "x=0", "x=2", "x=4", "x=6", "x=8"), labels)
        val points = output.filterIsInstance<DrawCommand.Circle>()
        val axis = output.filterIsInstance<DrawCommand.Line>().single {
            it.paint.color != Color.RED && it.y0 == it.y1 && it.x1 - it.x0 > 100f && it.paint.color == StructuredChartStyle().textColor
        }
        assertFloatEquals(0.2f, (points.first().x - axis.x0) / (axis.x1 - axis.x0))
        assertFloatEquals(0.8f, (points.last().x - axis.x0) / (axis.x1 - axis.x0))
    }

    @Test
    fun timeLabelsUseZoneAndWholeHourTicksWithoutChangingDataSpacing() {
        val start = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
        val data = StructuredChart.TimeLine(listOf(TimeLineSeries("时间", listOf(
            TimeLinePoint(start + 17 * 60_000, 10f), TimeLinePoint(start + 60 * 60_000, 20f), TimeLinePoint(start + 113 * 60_000, 30f)
        ), Color.RED)), TimeXAxis(
            min = start, max = start + 120 * 60_000, intervalMillis = 3_600_000,
            zone = ZoneId.of("Asia/Shanghai"), format = TimeAxisFormat.HOUR_MINUTE
        ))
        val output = commands(data)
        val labels = output.filterIsInstance<DrawCommand.Text>().map { it.text }.filter { ':' in it }
        assertEquals(listOf("08:00", "09:00", "10:00"), labels)
        val points = output.filterIsInstance<DrawCommand.Circle>()
        assertFloatEquals(43f / 53f, (points[1].x - points[0].x) / (points[2].x - points[1].x))
    }

    @Test
    fun categoryLabelIntervalAndRotationNeverDropBars() {
        val data = StructuredChart.Bars(CategoryBarData(listOf("A", "B", "C", "D"), listOf(BarSeries("数量", listOf(1f, 2f, 3f, 4f), Color.RED))),
            xAxis = CategoryXAxis(labelInterval = 1, formatter = { value, _ -> "分类$value" },
                style = XAxisStyle(label = XAxisLabelStyle(rotate = 45f))))
        val output = commands(data)
        assertEquals(listOf("分类A", "分类C"), output.filterIsInstance<DrawCommand.Text>().map { it.text }.filter { it.startsWith("分类") })
        assertEquals(2, output.filterIsInstance<DrawCommand.Rotate>().count { it.degrees == -45f })
        // 四个数据柱，加上图例色块。
        assertEquals(5, output.filterIsInstance<DrawCommand.Rect>().count { it.paint.color == Color.RED })
    }

    @Test
    fun horizontalLegendWrapsWholeItemsAndVerticalModeKeepsOnePerRow() {
        val data = StructuredChart.Line(listOf("甲甲甲", "乙乙乙", "丙丙丙").mapIndexed { index, name ->
            LineSeries(name, listOf(LinePoint(0f, 1f + index), LinePoint(1f, 2f + index)))
        })
        fun legends(style: StructuredChartStyle) = commands(data, style).filterIsInstance<DrawCommand.Text>()
            .filter { it.text in listOf("甲甲甲", "乙乙乙", "丙丙丙") }
        val horizontal = legends(StructuredChartStyle(width = 320f))
        assertFloatEquals(horizontal[0].baselineY, horizontal[1].baselineY)
        assertTrue(horizontal[1].x > horizontal[0].x)
        assertTrue(horizontal[2].baselineY > horizontal[1].baselineY)
        assertFloatEquals(horizontal[0].x, horizontal[2].x)
        val vertical = legends(StructuredChartStyle(width = 320f, legendOrientation = StructuredLegendOrientation.VERTICAL))
        assertTrue(vertical.zipWithNext().all { it.first.baselineY < it.second.baselineY && it.first.x == it.second.x })
    }

    @Test
    fun axisVisibilityIsIndependentOfGridTicksLabelsAndData() {
        val axisStyle = XAxisStyle(
            name = "横轴名称", label = XAxisLabelStyle(show = false), showAxisLine = false,
            axisTick = ChartStroke(Color.MAGENTA, 2f), splitLine = ChartStroke(Color.CYAN, 1f, StrokeStyle.Dashed()),
            showAxisTick = true, showSplitLine = true
        )
        val visible = commands(StructuredChart.Line(series, NumericXAxis(min = 0f, max = 6f, interval = 2f, style = axisStyle)))
        assertEquals(4, visible.filterIsInstance<DrawCommand.Line>().count { it.paint.color == Color.MAGENTA })
        assertEquals(4, visible.filterIsInstance<DrawCommand.Line>().count { it.paint.color == Color.CYAN && it.paint.hasPathEffect })
        val hidden = commands(StructuredChart.Line(series, NumericXAxis(min = 0f, max = 6f, interval = 2f,
            style = axisStyle.copy(showAxisTick = false, showSplitLine = false))))
        assertFalse(hidden.filterIsInstance<DrawCommand.Line>().any { it.paint.color == Color.MAGENTA || it.paint.color == Color.CYAN })
        assertEquals(visible.filterIsInstance<DrawCommand.Circle>().map { it.x to it.y }, hidden.filterIsInstance<DrawCommand.Circle>().map { it.x to it.y })
        assertTrue(hidden.filterIsInstance<DrawCommand.Text>().any { it.text == "横轴名称" })
    }

    @Test
    fun rejectsRangesThatHideDataAndIntervalsBeyondPrecision() {
        assertFailsWith<IllegalArgumentException> { commands(StructuredChart.Line(series, NumericXAxis(min = 1f))) }
        assertFailsWith<IllegalArgumentException> { commands(StructuredChart.Line(series, NumericXAxis(max = 5f))) }
        assertFailsWith<IllegalArgumentException> { commands(StructuredChart.Line(series, NumericXAxis(interval = 0.00001f))) }
        val start = 1_800_000_000_000L
        val points = listOf(TimeLineSeries("时间", listOf(TimeLinePoint(start, 1f), TimeLinePoint(start + 10, 2f))))
        assertFailsWith<IllegalArgumentException> { commands(StructuredChart.TimeLine(points, TimeXAxis(min = start + 1))) }
        assertFailsWith<IllegalArgumentException> { commands(StructuredChart.TimeLine(points, TimeXAxis(max = start + 9))) }
    }
}
