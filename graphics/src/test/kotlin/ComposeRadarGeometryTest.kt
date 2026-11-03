package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Path
import org.jetbrains.skia.Point
import org.junit.Test
import top.e404.tavolo.draw.compose.CanvasElement
import top.e404.tavolo.draw.compose.Column
import top.e404.tavolo.draw.compose.Row
import top.e404.tavolo.draw.compose.MeasureContext
import top.e404.tavolo.draw.compose.WaterfallLayout
import top.e404.tavolo.draw.compose.DrawCommand
import top.e404.tavolo.draw.compose.DrawCanvas
import top.e404.tavolo.draw.compose.RecordingDrawCanvas
import top.e404.tavolo.draw.compose.Modifier
import top.e404.tavolo.draw.compose.padding
import top.e404.tavolo.draw.compose.render
import top.e404.tavolo.draw.compose.charts.RadarFixPolicy
import top.e404.tavolo.draw.compose.charts.RadarTheme
import top.e404.tavolo.draw.compose.charts.radar
import top.e404.tavolo.draw.compose.charts.drawRadarChart
import top.e404.tavolo.util.FontManager
import top.e404.tavolo.util.toBitmap
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposeRadarGeometryTest {
    @Test
    fun logarithmicCountsHitEachOfTheFivePositiveGridRings() {
        val theme = RadarTheme(
            width = 100f,
            height = 100f,
            radius = 30f,
            gridCount = 5,
            gridFontProvider = { null },
            fillColor = Color.RED,
            fillOutlineColor = Color.TRANSPARENT,
            fillOutlineWidth = 0f,
            gridLineColor = Color.TRANSPARENT,
            gridLineWidth = 0f,
            labelFixPolicy = RadarFixPolicy.NONE,
            labelFontSize = 1f
        )
        val axes = listOf("", "", "", "")
        val counts = listOf(0, 1, 10, 100, 1_000, 10_000)

        counts.forEach { count ->
            val rate = if (count == 0) 0f else ((1f + kotlin.math.log10(count.toFloat())) / 5f).coerceAtMost(1f)
            val bitmap = renderRadar(theme, axes.map { it to rate }).toBitmap()
            if (count == 0) {
                assertEquals(0, Color.getA(bitmap.getColor(50, 50)))
            } else {
                val top = (50f - theme.radius * rate).toInt()
                assertEquals(Color.RED, bitmap.getColor(50, top + 1), "count=$count 必须落在 rate=$rate 的对数网格")
                assertEquals(0, Color.getA(bitmap.getColor(50, top - 2)), "count=$count 外侧不应出现数据填充")
            }
        }
    }


    @Test
    fun normalizedValuesMapZeroToCenterAndOneToOuterRing() {
        val theme = RadarTheme(
            width = 100f,
            height = 100f,
            radius = 30f,
            gridCount = 5,
            gridFontProvider = { null },
            fillColor = Color.RED,
            fillOutlineColor = Color.TRANSPARENT,
            fillOutlineWidth = 0f,
            gridLineColor = Color.TRANSPARENT,
            gridLineWidth = 0f,
            labelFixPolicy = RadarFixPolicy.NONE,
            labelFontSize = 1f
        )
        val zero = renderRadar(theme, listOf("", "", "", "").map { it to 0f }).toBitmap()
        val one = renderRadar(theme, listOf("", "", "", "").map { it to 1f }).toBitmap()

        assertEquals(0, Color.getA(zero.getColor(50, 50)))
        assertEquals(Color.RED, one.getColor(50, 50))
        assertEquals(Color.RED, one.getColor(50, 21))
        assertEquals(0, Color.getA(one.getColor(50, 19)))
    }
    @Test
    fun allZeroDataKeepsFiveGridLabelsAndEveryAxisLabel() {
        val recorder = RecordingDrawCanvas()
        drawRadarChart(
            canvas = recorder,
            parentX = 0f,
            parentY = 0f,
            data = listOf("north" to 0f, "east" to 0f, "south" to 0f, "west" to 0f),
            theme = RadarTheme(
                width = 100f,
                height = 100f,
                radius = 30f,
                gridCount = 5,
                gridFontProvider = { "ring-$it" },
                labelFixPolicy = RadarFixPolicy.NONE
            )
        )

        assertEquals(9, recorder.commands.filterIsInstance<DrawCommand.TextLine>().size)
        assertEquals(4, recorder.commands.filterIsInstance<DrawCommand.Line>().size)
    }

    @Test
    fun parentOffsetIsAppliedOnceToDataGeometry() {
        val theme = RadarTheme(
            width = 100f,
            height = 100f,
            radius = 30f,
            gridCount = 0,
            fillColor = Color.RED,
            fillOutlineColor = Color.TRANSPARENT,
            fillOutlineWidth = 0f,
            bgColor = Color.TRANSPARENT
        )
        val root = Row().apply {
            add(CanvasElement(10f, 100f) { _ -> })
            radar(theme, listOf("A" to 1f, "B" to 1f, "C" to 1f, "D" to 1f))
        }
        val bitmap = render(root = root) {}.toBitmap()

        assertEquals(Color.RED, bitmap.getColor(60, 50))
        assertEquals(0, Color.getA(bitmap.getColor(20, 50)))
    }


    @Test
    fun mixedAndSingleNonZeroValuesProduceOnlyTheirNormalizedArea() {
        val theme = RadarTheme(
            width = 100f,
            height = 100f,
            radius = 30f,
            gridCount = 5,
            gridFontProvider = { null },
            fillColor = Color.RED,
            fillOutlineColor = Color.TRANSPARENT,
            fillOutlineWidth = 0f,
            gridLineColor = Color.TRANSPARENT,
            gridLineWidth = 0f,
            labelFixPolicy = RadarFixPolicy.NONE,
            labelFontSize = 1f
        )
        val mixed = renderRadar(theme, listOf("", "", "", "") zip listOf(1f, 0.7f, 0f, 0f)).toBitmap()
        val singleNonZero = renderRadar(
            theme.copy(fillOutlineColor = Color.BLUE, fillOutlineWidth = 2f),
            listOf("", "", "", "") zip listOf(0f, 0f, 0f, 1f)
        ).toBitmap()

        assertTrue(Color.getA(mixed.getColor(60, 40)) > 0)
        assertEquals(Color.BLUE, singleNonZero.getColor(25, 50))
        assertEquals(0, Color.getA(singleNonZero.getColor(65, 50)))
        assertEquals(0, Color.getA(singleNonZero.getColor(50, 35)))
    }

    private fun renderRadar(theme: RadarTheme, data: List<Pair<String, Float>>) = render(
        root = Column(),
        measureContext = MeasureContext()
    ) {
        radar(theme, data)
    }
}
