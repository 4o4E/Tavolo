package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.junit.Test
import top.e404.tavolo.draw.compose.*
import top.e404.tavolo.draw.compose.charts.*
import kotlin.test.*

class StructuredChartUnitTest {
    private fun commands(data: StructuredChart) = renderCommands(measureContext = MeasureContext(FixedTextMeasurer())) {
        structuredChart(data, unit = "%")
    }

    @Test
    fun alignsIndependentTimeSeriesWithoutLosingMillisecondPrecision() {
        val origin = 1_800_000_000_000L
        val data = StructuredChart.TimeLine(listOf(
            TimeLineSeries("甲", listOf(TimeLinePoint(origin + 8, 80f), TimeLinePoint(origin, 20f), TimeLinePoint(origin + 1, 30f)), Color.RED),
            TimeLineSeries("乙", listOf(TimeLinePoint(origin, 70f), TimeLinePoint(origin + 1, 60f)), Color.BLUE,
                lineStyle = StrokeStyle.Dashed(listOf(6f, 4f)))
        ))
        val output = commands(data)
        val red = output.filterIsInstance<DrawCommand.Circle>().filter { it.paint.color == Color.RED }
        val blue = output.filterIsInstance<DrawCommand.Circle>().filter { it.paint.color == Color.BLUE }
        assertEquals(3, red.size)
        assertEquals(2, blue.size)
        assertFloatEquals(red[0].x, blue[0].x)
        assertFloatEquals(red[1].x, blue[1].x)
        assertFloatEquals(8f, (red[2].x - red[0].x) / (red[1].x - red[0].x))
        assertTrue(output.filterIsInstance<DrawCommand.Path>().single { it.paint.color == Color.BLUE }.paint.hasPathEffect)
        assertFalse(output.filterIsInstance<DrawCommand.Path>().single { it.paint.color == Color.RED }.paint.hasPathEffect)
        assertTrue(output.filterIsInstance<DrawCommand.Line>().single { it.paint.color == Color.BLUE }.paint.hasPathEffect)
    }

    @Test
    fun missingPointsBreakLinesAndAreNotDrawnAtZero() {
        val data = StructuredChart.Line(listOf(LineSeries("缺失", listOf(
            LinePoint(0f, 30f), LinePoint(1f, 40f), LinePoint(2f, null), LinePoint(3f, 60f), LinePoint(4f, 70f)
        ), color = Color.RED)), options = StructuredLineOptions(yRange = ChartRange(0f, 100f)))
        val output = commands(data)
        assertEquals(2, output.filterIsInstance<DrawCommand.Path>().count { it.paint.color == Color.RED })
        val points = output.filterIsInstance<DrawCommand.Circle>().filter { it.paint.color == Color.RED }
        assertEquals(4, points.size)
        assertTrue(points.zipWithNext().all { it.first.y > it.second.y })
    }

    @Test
    fun reservesInkBoundsForLargeMarkersAndThickLinesWithoutPagePadding() {
        for ((radius, lineWidth) in listOf(30f to 2.5f, 1f to 60f)) {
            val root = Column()
            val style = StructuredChartStyle(width = 300f, plotHeight = 120f, padding = 0f, fontSize = 12f)
            val output = renderCommands(root = root, measureContext = MeasureContext(FixedTextMeasurer())) {
                structuredChart(StructuredChart.Line(
                    listOf(LineSeries("边界", listOf(LinePoint(0f, 0f), LinePoint(2f, 100f)), Color.RED)),
                    xAxis = NumericXAxis(min = 0f, max = 2f, interval = 1f),
                    options = StructuredLineOptions(yRange = ChartRange(0f, 100f), pointRadius = radius, lineWidth = lineWidth)
                ), style = style)
            }
            val outset = maxOf(radius, lineWidth / 2f)
            val points = output.filterIsInstance<DrawCommand.Circle>().filter { it.paint.color == Color.RED }
            assertEquals(2, points.size)
            points.forEach { point ->
                assertTrue(point.x - outset >= -0.01f)
                assertTrue(point.y - outset >= -0.01f)
                assertTrue(point.x + outset <= root.width + 0.01f)
                assertTrue(point.y + outset <= root.height + 0.01f)
            }
        }
    }

    @Test
    fun doesNotSilentlySampleLongSeries() {
        val data = StructuredChart.Line(listOf(LineSeries("全部点", (0..300).map { LinePoint(it.toFloat(), (it % 17).toFloat()) }, Color.RED)))
        assertEquals(301, commands(data).filterIsInstance<DrawCommand.Circle>().count { it.paint.color == Color.RED })
    }

    @Test
    fun rejectsUnrepresentableTimesAndDuplicateTimesWithinOneSeries() {
        val origin = 1_800_000_000_000L
        fun timeline(vararg offsets: Long) = StructuredChart.TimeLine(listOf(TimeLineSeries("数据", offsets.map { TimeLinePoint(origin + it, 1f) })))
        assertFailsWith<IllegalArgumentException> { commands(timeline(0, 100_000_000, 100_000_001)) }
        assertFailsWith<IllegalArgumentException> { commands(timeline(0, 0)) }
    }

    @Test
    fun rejectsEmptyInvalidOrHiddenData() {
        val invalid = listOf(
            StructuredChart.Line(emptyList()),
            StructuredChart.Line(listOf(LineSeries("空", emptyList()))),
            StructuredChart.Line(listOf(LineSeries("缺失", listOf(LinePoint(0f, null))))),
            StructuredChart.Line(listOf(LineSeries("异常", listOf(LinePoint(0f, Float.NaN))))),
            StructuredChart.Line(listOf(LineSeries("越界", listOf(LinePoint(0f, 101f)))), options = StructuredLineOptions(yRange = ChartRange(0f, 100f))),
            StructuredChart.Bars(CategoryBarData(listOf("甲", "乙"), listOf(BarSeries("不完整", listOf(1f))))),
            StructuredChart.Pie(listOf(PieSlice("负数", -1f))),
            StructuredChart.Radar(listOf("甲" to 1f, "乙" to 2f, "丙" to 101f), 100f)
        )
        invalid.forEach { data -> assertFailsWith<IllegalArgumentException>(data.toString()) { commands(data) } }
    }
}
