package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.junit.Test
import top.e404.tavolo.draw.compose.*
import top.e404.tavolo.draw.compose.charts.*
import java.io.File
import java.time.Instant

/** 全部输入离线；通过公开库 API 生成 PNG，再人工查看内容与边界。 */
class StructuredRenderingManualTest {
    private val chartStyle = StructuredChartStyle(fontFamily = ManualTestSupport.uiFont)
    private val markdownStyle = MarkdownStyle(
        fontFamily = ManualTestSupport.uiFont,
        codeFontFamily = ManualTestSupport.monoFont
    )

    private fun resource(name: String) = requireNotNull(javaClass.getResource("/issue7/$name")) { "缺少验收资源 $name" }.readText()
    private fun save(name: String, bytes: ByteArray) {
        val file = File("out/issue7/$name.png")
        file.parentFile.mkdirs()
        file.writeBytes(bytes)
        println("已输出: ${file.absolutePath}")
    }

    @Test
    fun renderLibraryAcceptanceImages() {
        val rows = resource("codex.csv").lineSequence().drop(1).filter { it.isNotBlank() }
            .map { line -> line.split(',').map(String::toFloat) }.toList()
        val actual = LineSeries("实际已用", rows.map { LinePoint(it[0], it[1]) }, color = Color.makeRGB(30, 110, 210), showPoints = false)
        val reference = LineSeries("均速参考", rows.map { LinePoint(it[0], it[2]) }, color = Color.makeRGB(220, 120, 30),
            lineStyle = StrokeStyle.Dashed(listOf(10f, 6f)), showPoints = false)
        val codex = StructuredChart.Line(listOf(actual, reference),
            xAxis = NumericXAxis(min = 0f, max = 24f, interval = 4f, unit = "h"),
            options = StructuredLineOptions(yRange = ChartRange(0f, 100f)))
        save("codex", renderChartPng(codex, "Codex · 7d 趋势", "%", chartStyle))

        val epoch = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
        val timelines = StructuredChart.TimeLine(listOf(
            TimeLineSeries("序列甲：不同长度、乱序输入与 3ms 处缺失", listOf(
                TimeLinePoint(epoch + 8, 80f), TimeLinePoint(epoch, 20f), TimeLinePoint(epoch + 1, 35f),
                TimeLinePoint(epoch + 3, null), TimeLinePoint(epoch + 4, 65f)
            ), color = Color.BLUE),
            TimeLineSeries("序列乙：同一时间对齐，间隔按实际毫秒绘制", listOf(
                TimeLinePoint(epoch, 70f), TimeLinePoint(epoch + 1, 60f), TimeLinePoint(epoch + 8, 30f)
            ), color = Color.RED, lineStyle = StrokeStyle.Dashed(listOf(8f, 5f)))
        ), options = StructuredLineOptions(yRange = ChartRange(0f, 100f)))
        save("time-gaps", renderChartPng(timelines, "真实毫秒 · 独立曲线与缺失点", "%", chartStyle))

        val slices = listOf(PieSlice("中文分类甲", 35f), PieSlice("中文分类乙", 50f), PieSlice("长名称分类：完整保留标签，不省略任何内容", 15f))
        save("pie", renderChartPng(StructuredChart.Pie(slices), "分类占比", "次", chartStyle))
        save("donut", renderChartPng(StructuredChart.Donut(slices, cornerRadius = 8f, sliceGap = 3f), "环形占比", "次", chartStyle))
        save("bars", renderChartPng(StructuredChart.Bars(CategoryBarData(
            listOf("第一组长标签需要换行", "第二组中文", "第三组"),
            listOf(BarSeries("第一条序列", listOf(20f, 50f, 30f)), BarSeries("第二条序列", listOf(35f, 40f, 60f)))
        )), "分类柱状图", "次", chartStyle))
        save("radar", renderChartPng(StructuredChart.Radar(listOf(
            "速度" to 80f, "质量" to 90f, "稳定" to 85f, "覆盖" to 70f, "效率" to 75f
        ), maximum = 100f), "雷达图", "%", chartStyle))

        ManualTestSupport.drawnAvatar(1, 160, 100).use { avatar ->
            val resolver: MarkdownImageResolver = { destination ->
                require(destination == "fixture:avatar") { "未知验收资源: $destination" }
                avatar
            }
            save("markdown", renderMarkdownPng(resource("document.md"), 800f, markdownStyle, resolver))
            save("markdown-narrow", renderMarkdownPng(resource("document.md"), 540f, markdownStyle.copy(fontSize = 16f), resolver))
            val embedded = render(Color.WHITE) {
                column(Modifier.padding(12f)) {
                    markdown("## 嵌入已有布局\n\nMarkdown 和图表共用原生 Compose 布局。", 900f, markdownStyle)
                    structuredChart(codex, "Codex · 7d 趋势", "%", chartStyle)
                }
            }
            embedded.use { ManualTestSupport.saveImage("issue7/embedded.png", it) }
        }
    }

    @Test
    fun renderAxisAndLegendOptions() {
        val series = (0..4).map { index ->
            LineSeries("序列 ${index + 1}：横向图例自动换行", (0..6).map { x -> LinePoint(x.toFloat(), (x * 5 + index * 10).toFloat()) },
                lineStyle = if (index % 2 == 0) StrokeStyle.Solid else StrokeStyle.Dashed())
        }
        val numeric = StructuredChart.Line(series, NumericXAxis(
            min = -1f, max = 7f, interval = 2f,
            formatter = { "第 ${it.toInt()} 小时" },
            style = XAxisStyle(
                name = "相对时间（小时）",
                label = XAxisLabelStyle(rotate = 35f, margin = 12f, maxWidth = 150f),
                axisLine = ChartStroke(Color.makeRGB(80, 90, 110), 2f),
                axisTick = ChartStroke(Color.makeRGB(80, 90, 110), 2f),
                tickLength = 8f,
                splitLine = ChartStroke(Color.makeRGB(190, 205, 220), 1f, StrokeStyle.Dashed())
            )
        ))
        save("axis-numeric-legend-wrap", renderChartPng(numeric, "横轴范围、间隔、旋转与横向图例", "%", chartStyle.copy(width = 800f)))
        save("legend-vertical", renderChartPng(numeric, "可选纵向图例", "%", chartStyle.copy(legendOrientation = StructuredLegendOrientation.VERTICAL)))
        val origin = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
        val timed = StructuredChart.TimeLine(listOf(TimeLineSeries("实际采样时间", listOf(
            TimeLinePoint(origin + 17 * 60_000, 20f),
            TimeLinePoint(origin + 60 * 60_000, 50f),
            TimeLinePoint(origin + 113 * 60_000, 35f)
        ))), TimeXAxis(
            min = origin, max = origin + 120 * 60_000, intervalMillis = 1_800_000,
            zone = java.time.ZoneId.of("Asia/Shanghai"), format = TimeAxisFormat.HOUR_MINUTE,
            style = XAxisStyle(name = "北京时间", showSplitLine = false)
        ))
        save("axis-time", renderChartPng(timed, "固定半小时间隔 · 时区标签", "%", chartStyle))
        val categories = StructuredChart.Bars(CategoryBarData(
            listOf("第一季度", "第二季度", "第三季度", "第四季度", "第五季度", "第六季度"),
            listOf(BarSeries("完整保留六组数据", listOf(10f, 30f, 20f, 40f, 35f, 50f)))
        ), xAxis = CategoryXAxis(
            labelInterval = 1,
            style = XAxisStyle(name = "每隔一项显示标签，数据不跳过", label = XAxisLabelStyle(rotate = 45f, maxWidth = 160f))
        ))
        save("axis-category", renderChartPng(categories, "分类轴标签间隔", "次", chartStyle))
        val gap = StructuredChart.Line(series.take(2), NumericXAxis(
            boundaryGap = AxisBoundaryGap(0.2f, 0.2f), interval = 2f,
            style = XAxisStyle(name = "两端留白 · 隐藏标签与轴线，保留刻度", showAxisLine = false, label = XAxisLabelStyle(show = false))
        ))
        save("axis-gap-hidden-labels", renderChartPng(gap, "轴部件独立控制", "%", chartStyle))
    }
}
