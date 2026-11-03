package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Path
import org.jetbrains.skia.Point
import org.junit.Test
import top.e404.tavolo.draw.compose.DrawCanvas
import top.e404.tavolo.draw.compose.PaintSnapshot
import top.e404.tavolo.draw.compose.DrawCommand
import top.e404.tavolo.draw.compose.RecordingDrawCanvas
import top.e404.tavolo.draw.compose.charts.*
import top.e404.tavolo.util.FontManager
import kotlin.test.assertEquals

class ComposeLineGridUnitTest {
    @Test
    fun gridDirectionsCanBeHiddenWithoutChangingLabelsAxesOrSeries() {
        val gridColor = Color.makeRGB(241, 53, 197)
        val textStyle = ChartTextStyle(fontSize = 12f, color = Color.BLACK, fontFamily = FontManager.systemFamilies().first())
        val series = listOf(
            LineSeries(
                name = "Monthly contributions",
                color = Color.BLUE,
                fillColor = Color.makeARGB(35, 0, 0, 255),
                points = listOf(LinePoint(1f, 0f), LinePoint(2f, 1f), LinePoint(3f, 2f))
            )
        )
        // 两种最终宽度分别验证，网格开关不能改变重新绘制后的数据或标签位置。
        for (width in listOf(320f, 440f)) {
            val theme = LineChartTheme(
                width = width,
                height = 240f,
                axis = AxisTheme(gridColor = gridColor, labelTextStyle = textStyle),
                legend = ChartLegendTheme(position = ChartLegendPosition.BOTTOM, textStyle = textStyle),
                xTickCount = 2,
                yTickCount = 2,
                xLabelFormatter = { it.toInt().toString() }
            )
            fun record(current: LineChartTheme): Pair<List<DrawCommand>, List<Pair<List<Point?>, PaintSnapshot>>> {
                val recorder = RecordingDrawCanvas()
                val geometry = mutableListOf<Pair<List<Point?>, PaintSnapshot>>()
                val canvas = object : DrawCanvas by recorder {
                    override fun drawPath(path: Path, paint: Paint) {
                        geometry += path.points.toList() to PaintSnapshot.from(paint)
                        recorder.drawPath(path, paint)
                    }
                }
                drawLineChart(canvas, 0f, 0f, series, current)
                return recorder.commands to geometry
            }
            fun isGrid(command: DrawCommand) = command is DrawCommand.Line && command.paint.color == gridColor
            val (baseline, baselineGeometry) = record(theme)
            val labels = baseline.filterIsInstance<DrawCommand.TextLine>()
            assertEquals(7, labels.size) // 三个月份、三个纵轴数值和一个图例。
            assertEquals(6, baseline.count(::isGrid))
            for ((horizontal, vertical) in listOf(true to true, true to false, false to true, false to false)) {
                val (commands, geometry) = record(theme.copy(showHorizontalGrid = horizontal, showVerticalGrid = vertical))
                val gridLines = commands.filterIsInstance<DrawCommand.Line>().filter { it.paint.color == gridColor }
                assertEquals(if (horizontal) 3 else 0, gridLines.count { it.y0 == it.y1 }, "水平网格: $horizontal/$vertical")
                assertEquals(if (vertical) 3 else 0, gridLines.count { it.x0 == it.x1 }, "竖直网格: $horizontal/$vertical")
                fun stableCommands(items: List<DrawCommand>) = items.filterNot { isGrid(it) || it is DrawCommand.Path }
                assertEquals(stableCommands(baseline), stableCommands(commands), "坐标轴、标签、数据点和图例应保持不变")
                assertEquals(baselineGeometry, geometry, "折线和填充的真实路径坐标与样式应保持不变")
            }
        }
    }
}
