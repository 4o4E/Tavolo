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
import top.e404.tavolo.draw.compose.charts.RadarSizeMode
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

    @Test
    fun naturalModeKeepsRadiusAndLabelsWithinBoundsAcrossAxesAndPolicies() {
        val systemFont = FontManager.systemFamilies().first()
        for (policy in RadarFixPolicy.entries) {
            for (fontSize in listOf(14f, 31f)) for (data in listOf(
                listOf("A" to 0.2f, "Long east label" to 0.8f, "南" to 0.4f),
                listOf("Long top label" to 0.5f, "Right" to 1f, "Bottom" to 0f, "Long left label" to 0.7f, "Five" to 0.2f)
            )) {
                val theme = RadarTheme(
                    width = 12f,
                    height = 18f,
                    radius = 70f,
                    gridCount = 5,
                    gridFontFamily = systemFont,
                    gridFontSize = 19f,
                    gridItalic = true,
                    gridScaleX = 1.2f,
                    labelFontSize = fontSize,
                    labelFontFamily = systemFont,
                    labelItalic = true,
                    labelScaleX = 1.2f,
                    labelOuterLength = 17f,
                    labelFixPolicy = policy,
                    sizeMode = RadarSizeMode.NATURAL,
                    naturalPadding = 0f
                )
                val image = renderRadar(theme, data)
                val reference = renderRadar(theme.copy(naturalPadding = 51f), data)
                assertEquals(102, reference.width - image.width, "自然留白左右各增加 51px，策略=$policy")
                assertEquals(102, reference.height - image.height, "自然留白上下各增加 51px，策略=$policy")
                val bitmap = image.toBitmap()
                val referenceBitmap = reference.toBitmap()
                var minX = image.width
                var minY = image.height
                var maxX = -1
                var maxY = -1
                for (y in 0 until image.height) for (x in 0 until image.width) {
                    if (Color.getA(bitmap.getColor(x, y)) == 0) continue
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                }
                // 默认 2px 数据描边的 miter 包络最多预留 4px，文字还包含 1px 抗锯齿余量。
                assertTrue(minX in 0..5 && minY in 0..5, "自然布局不能保留无依据的左侧或顶部空白")
                assertTrue(image.width - 1 - maxX in 0..5 && image.height - 1 - maxY in 0..5, "自然布局不能保留无依据的右侧或底部空白")
                var outline: List<Point?>? = null
                val recorder = RecordingDrawCanvas()
                val canvas = object : DrawCanvas by recorder {
                    override fun drawPath(path: Path, paint: Paint) {
                        if (outline == null) outline = path.points.toList()
                        recorder.drawPath(path, paint)
                    }
                }
                drawRadarChart(canvas, 0f, 0f, data, theme)
                val first = outline!![0]!!
                val second = outline!![1]!!
                val dx = first.x - second.x
                val dy = first.y - second.y
                val actualEdge = kotlin.math.sqrt(dx * dx + dy * dy)
                val expectedEdge = 2 * theme.radius * kotlin.math.sin(Math.PI / data.size)
                assertTrue(kotlin.math.abs(actualEdge - expectedEdge) < 0.0001, "自然测量不能缩放指定半径")
                for (y in 0 until reference.height) {
                    for (x in 0 until reference.width) {
                        if (x < 51 || y < 51 || x >= image.width + 51 || y >= image.height + 51) {
                            assertEquals(0, Color.getA(referenceBitmap.getColor(x, y)), "自然边界外不能还有被裁掉的内容：策略=$policy，字号=$fontSize，轴数=${data.size}，字体=$systemFont，尺寸=${image.width}×${image.height}，参考像素=($x,$y)")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun naturalMeasurementFlowsIntoWaterfallPlacementWithoutShrinkingRadius() {
        val theme = RadarTheme(
            width = 1f,
            height = 1f,
            radius = 90f,
            labelFontSize = 28f,
            labelFontFamily = FontManager.systemFamilies().first(),
            labelFixPolicy = RadarFixPolicy.RATED_FIX,
            sizeMode = RadarSizeMode.NATURAL,
            naturalPadding = 12f
        )
        val waterfall = WaterfallLayout(columns = 1, width = 900f)
        waterfall.radar(theme, listOf("North long" to 0.8f, "East" to 0.4f, "South" to 1f))
        waterfall.add(CanvasElement(50f, 20f) { _ -> })
        waterfall.measure(MeasureContext())
        waterfall.layout(0f, 0f)
        val radarElement = waterfall.children[0]
        val nextCard = waterfall.children[1]

        assertTrue(radarElement.width < waterfall.columnWidth)
        assertTrue(radarElement.height > 180f)
        assertEquals(radarElement.height, nextCard.y)
    }

    @Test
    fun fixedTiltLabelsKeepTheirOutsidePositionInOffsetContainers() {
        val root = Column().apply { modifier = Modifier.padding(20f) }
        val image = render(root = root) {
            radar(
                RadarTheme(
                    width = 160f,
                    height = 160f,
                    radius = 35f,
                    labelOuterLength = 15f,
                    labelFontFamily = FontManager.systemFamilies().first(),
                    labelFontSize = 14f,
                    labelFontColor = Color.RED,
                    labelFixPolicy = RadarFixPolicy.TILT,
                    gridLineColor = Color.TRANSPARENT,
                    gridFontProvider = { null },
                    fillColor = Color.TRANSPARENT,
                    fillOutlineColor = Color.TRANSPARENT
                ),
                listOf("" to 0f, "right" to 0f, "" to 0f, "" to 0f)
            )
        }
        val bitmap = image.toBitmap()
        var left = image.width
        var right = -1
        for (y in 0 until image.height) for (x in 0 until image.width) {
            if (Color.getA(bitmap.getColor(x, y)) == 0) continue
            left = minOf(left, x)
            right = maxOf(right, x)
        }
        assertTrue(left >= 149, "固定模式的右侧 TILT 标签应保留圆心 100、半径 35 和标签外距 15 的位置")
        assertTrue(right >= left && right < image.width, "右侧标签应完整绘制在固定画布内")
    }

    @Test
    fun fixedModeRetainsConfiguredCanvasDimensions() {
        val theme = RadarTheme(
            width = 180f,
            height = 120f,
            radius = 70f,
            gridCount = 5,
            sizeMode = RadarSizeMode.FIXED
        )

        val image = renderRadar(theme, listOf("north" to 1f, "east" to 0.5f, "south" to 0.7f))

        assertEquals(180, image.width)
        assertEquals(120, image.height)
    }

    private fun renderRadar(theme: RadarTheme, data: List<Pair<String, Float>>) = render(
        root = Column(),
        measureContext = MeasureContext()
    ) {
        radar(theme, data)
    }
}
