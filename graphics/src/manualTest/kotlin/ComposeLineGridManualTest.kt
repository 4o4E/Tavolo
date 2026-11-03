package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.junit.Test
import top.e404.tavolo.draw.compose.charts.*

class ComposeLineGridManualTest {
    @Test
    fun renderGridDirections() {
        val font = ManualTestSupport.monoFont
        val theme = LineChartTheme(
            width = 480f,
            height = 260f,
            backgroundColor = Color.WHITE,
            axis = AxisTheme(labelTextStyle = ChartTextStyle(fontSize = 14f, color = Color.BLACK, fontFamily = font)),
            legend = ChartLegendTheme(position = ChartLegendPosition.NONE),
            xTickCount = 2,
            yTickCount = 2,
            xLabelFormatter = { it.toInt().toString() },
            yLabelFormatter = { it.toInt().toString() }
        )
        // issue 指定的数学边界输入，用于观察网格方向、数字月份与纵轴数值。
        val series = listOf(LineSeries("Monthly contributions", listOf(LinePoint(1f, 0f), LinePoint(2f, 1f), LinePoint(3f, 2f))))
        ManualTestSupport.saveCompose("折线图-双向网格") { lineChart(theme, series) }
        ManualTestSupport.saveCompose("折线图-仅水平网格") { lineChart(theme.copy(showVerticalGrid = false), series) }
    }
}
