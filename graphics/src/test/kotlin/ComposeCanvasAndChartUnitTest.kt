package top.e404.tavolo.draw.test

import org.jetbrains.skia.*
import org.junit.Test
import top.e404.tavolo.draw.compose.*
import top.e404.tavolo.draw.compose.charts.*
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ComposeCanvasAndChartUnitTest {
    @Test
    fun canvasElementReceivesParentPositionAndRecordsCustomDraw() {
        val commands = renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            val element = CanvasElement(10f, 10f) { canvas ->
                canvas.drawCircle(parentX + 1f, parentY + 2f, 3f, Paint().apply { color = Color.RED })
            }
            element.modifier = Modifier.padding(left = 4f, top = 5f)
            add(element)
        }

        val command = commands.filterIsInstance<DrawCommand.Circle>().single()
        assertFloatEquals(5f, command.x)
        assertFloatEquals(7f, command.y)
        assertFloatEquals(3f, command.radius)
        assertEquals(Color.RED, command.paint.color)
    }

    @Test
    fun canvasElementDrawCanUseMeasureContext() {
        var measuredWidth = 0f

        renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer(charWidth = 7f))
        ) {
            add(CanvasElement(10f, 10f) { _, measureContext ->
                measuredWidth = measureContext.textMeasurer.measureTextWidth(
                    "abc",
                    Font(Typeface.makeEmpty(), 10f),
                    Paint()
                )
            })
        }

        assertFloatEquals(21f, measuredWidth)
    }

    @Test
    fun barChartRecordsClipArcsAndOutlineCircles() {
        val commands = renderCommands {
            bar(
                theme = BarTheme(
                    outerRadius = 10f,
                    innerRadius = 5f,
                    strokeColor = Color.WHITE,
                    strokeWidth = 1f
                ),
                data = listOf(Color.RED to 1f, Color.BLUE to 3f)
            )
        }

        assertTrue(commands.any { it is DrawCommand.ClipPath })
        val arcs = commands.filterIsInstance<DrawCommand.Arc>()
        assertEquals(4, arcs.size)
        assertFloatEquals(-90f, arcs[0].startAngle)
        assertFloatEquals(90f, arcs[0].sweepAngle)
        assertEquals(PaintMode.FILL, arcs[0].paint.mode)
        assertFloatEquals(0f, arcs[2].startAngle)
        assertFloatEquals(270f, arcs[2].sweepAngle)
        val circles = commands.filterIsInstance<DrawCommand.Circle>()
        assertEquals(2, circles.size)
        assertFloatEquals(9f, circles[0].radius)
        assertFloatEquals(5f, circles[1].radius)
    }

    @Test
    fun donutChartRecordsExactCommandSequenceWithOffsetAndThemePaints() {
        val recorder = RecordingDrawCanvas()
        val theme = BarTheme(
            outerRadius = 20f,
            innerRadius = 8f,
            strokeColor = Color.GREEN,
            strokeWidth = 2f,
            start = 10f
        )

        drawDonutChart(
            canvas = recorder,
            left = 5f,
            top = 7f,
            data = listOf(Color.RED to 2f, Color.BLUE to 1f),
            theme = theme
        )

        assertIs<DrawCommand.Save>(recorder.commands[0])
        assertIs<DrawCommand.ClipPath>(recorder.commands[1])
        assertIs<DrawCommand.Restore>(recorder.commands[6])

        val arcs = recorder.commands.filterIsInstance<DrawCommand.Arc>()
        assertEquals(4, arcs.size)
        assertFloatEquals(7f, arcs[0].left)
        assertFloatEquals(9f, arcs[0].top)
        assertFloatEquals(43f, arcs[0].right)
        assertFloatEquals(45f, arcs[0].bottom)
        assertFloatEquals(10f, arcs[0].startAngle)
        assertFloatEquals(240f, arcs[0].sweepAngle)
        assertEquals(true, arcs[0].includeCenter)
        assertEquals(Color.RED, arcs[0].paint.color)
        assertEquals(PaintMode.FILL, arcs[0].paint.mode)
        assertEquals(Color.GREEN, arcs[1].paint.color)
        assertEquals(PaintMode.STROKE, arcs[1].paint.mode)
        assertFloatEquals(2f, arcs[1].paint.strokeWidth)
        assertFloatEquals(250f, arcs[2].startAngle)
        assertFloatEquals(120f, arcs[2].sweepAngle)
        assertEquals(Color.BLUE, arcs[2].paint.color)

        val circles = recorder.commands.filterIsInstance<DrawCommand.Circle>()
        assertEquals(2, circles.size)
        assertFloatEquals(25f, circles[0].x)
        assertFloatEquals(27f, circles[0].y)
        assertFloatEquals(18f, circles[0].radius)
        assertEquals(PaintMode.STROKE, circles[0].paint.mode)
        assertFloatEquals(8f, circles[1].radius)
    }

    @Test
    fun donutChartSkipsNonPositiveSegmentsWithoutInvalidArcs() {
        val recorder = RecordingDrawCanvas()

        drawDonutChart(
            canvas = recorder,
            left = 0f,
            top = 0f,
            data = listOf(Color.RED to 0f, Color.BLUE to -1f),
            theme = BarTheme(outerRadius = 20f, innerRadius = 8f)
        )

        assertEquals(0, recorder.commands.filterIsInstance<DrawCommand.Arc>().size)
        assertEquals(2, recorder.commands.filterIsInstance<DrawCommand.Circle>().size)
    }

    @Test
    fun radarChartRecordsPathsLinesAndTextLines() {
        val recorder = RecordingDrawCanvas()
        val theme = RadarTheme(
            width = 100f,
            height = 100f,
            radius = 30f,
            gridCount = 2,
            gridFontProvider = { "g$it" },
            labelFixPolicy = RadarFixPolicy.NONE,
            labelFontSize = 10f,
            gridFontSize = 8f
        )

        drawRadarChart(
            recorder,
            parentX = 0f,
            parentY = 0f,
            data = listOf("a" to 0.2f, "b" to 0.6f, "c" to 1f),
            theme = theme,
            measureContext = MeasureContext(FixedTextMeasurer())
        )

        assertEquals(5, recorder.commands.filterIsInstance<DrawCommand.Path>().size)
        assertEquals(3, recorder.commands.filterIsInstance<DrawCommand.Line>().size)
        assertEquals(5, recorder.commands.filterIsInstance<DrawCommand.TextLine>().size)
    }

    @Test
    fun radarChartRecordsGridDataAndSkippedGridTextCommands() {
        val recorder = RecordingDrawCanvas()
        val theme = RadarTheme(
            width = 100f,
            height = 80f,
            radius = 20f,
            bgColor = Color.YELLOW,
            fillOutlineColor = Color.RED,
            gridCount = 2,
            gridLineColor = Color.GREEN,
            gridFontProvider = { if (it == 0) null else "g$it" },
            gridFontSize = 8f,
            gridFontColor = Color.BLUE,
            labelFixPolicy = RadarFixPolicy.NONE,
            labelFontSize = 10f,
            labelFontColor = Color.WHITE
        )

        drawRadarChart(
            canvas = recorder,
            parentX = 10f,
            parentY = 20f,
            data = listOf("top" to 1f, "right" to 0.5f, "bottom" to 0.25f, "left" to 0.75f),
            theme = theme,
            measureContext = MeasureContext(FixedTextMeasurer())
        )

        val paths = recorder.commands.filterIsInstance<DrawCommand.Path>()
        assertEquals(5, paths.size)
        assertEquals(Color.YELLOW, paths[0].paint.color)
        assertEquals(Color.GREEN, paths[1].paint.color)
        assertEquals(PaintMode.STROKE, paths[1].paint.mode)
        assertEquals(Color.GREEN, paths[2].paint.color)
        assertEquals(0x66FF0000, paths[3].paint.color)
        assertEquals(PaintMode.FILL, paths[3].paint.mode)
        assertEquals(Color.RED, paths[4].paint.color)
        assertEquals(PaintMode.STROKE, paths[4].paint.mode)

        val lines = recorder.commands.filterIsInstance<DrawCommand.Line>()
        assertEquals(4, lines.size)
        assertFloatEquals(60f, lines[0].x0)
        assertFloatEquals(50f, lines[0].y0)
        assertFloatEquals(60f, lines[0].x1)
        assertFloatEquals(40f, lines[0].y1)
        assertFloatEquals(70f, lines[1].x0)
        assertFloatEquals(60f, lines[1].y0)
        assertFloatEquals(80f, lines[1].x1)
        assertFloatEquals(60f, lines[1].y1)

        val textLines = recorder.commands.filterIsInstance<DrawCommand.TextLine>()
        assertEquals(5, textLines.size)
        assertFloatEquals(63f, textLines[0].x)
        assertFloatEquals(37f, textLines[0].y)
        assertEquals(Color.BLUE, textLines[0].paint.color)
        textLines.drop(1).forEach {
            assertEquals(Color.WHITE, it.paint.color)
        }
    }

    @Test
    fun radarChartLabelPositionsUseInjectedTextMeasurer() {
        val recorder = RecordingDrawCanvas()
        val theme = RadarTheme(
            width = 100f,
            height = 100f,
            radius = 20f,
            gridCount = 0,
            labelOuterLength = 10f,
            labelFixPolicy = RadarFixPolicy.NONE,
            labelFontSize = 10f
        )

        drawRadarChart(
            canvas = recorder,
            parentX = 0f,
            parentY = 0f,
            data = listOf("wide" to 1f),
            theme = theme,
            measureContext = MeasureContext(FixedTextMeasurer(charWidth = 20f))
        )

        val label = recorder.commands.filterIsInstance<DrawCommand.TextLine>().single()
        assertFloatEquals(10f, label.x)
        assertFloatEquals(20f, label.y)
    }

    @Test
    fun radarChartHandlesNonEmptyDataWithZeroGridCount() {
        val recorder = RecordingDrawCanvas()

        drawRadarChart(
            canvas = recorder,
            parentX = 0f,
            parentY = 0f,
            data = listOf("a" to -1f, "b" to 2f),
            theme = RadarTheme(
                width = 100f,
                height = 100f,
                radius = 20f,
                gridCount = 0,
                gridFontProvider = { "g$it" },
                labelFixPolicy = RadarFixPolicy.NONE
            ),
            measureContext = MeasureContext(FixedTextMeasurer())
        )

        val lines = recorder.commands.filterIsInstance<DrawCommand.Line>()
        assertEquals(2, lines.size)
        lines.forEach {
            assertTrue(it.x0.isFinite())
            assertTrue(it.y0.isFinite())
            assertTrue(it.x1.isFinite())
            assertTrue(it.y1.isFinite())
        }
    }

    @Test
    fun radarChartAppliesLabelFixPolicyToRecordedTextLinePositions() {
        val data = listOf("top" to 1f, "right" to 1f, "bottom" to 1f, "left" to 1f)

        listOf(RadarFixPolicy.MOVE_OUTSIDE, RadarFixPolicy.RATED_FIX).forEach { policy ->
            val recorder = RecordingDrawCanvas()
            val theme = RadarTheme(
                width = 100f,
                height = 100f,
                radius = 20f,
                gridCount = 1,
                gridFontProvider = { null },
                labelOuterLength = 10f,
                labelFixPolicy = policy,
                labelFontSize = 10f
            )

            drawRadarChart(
                canvas = recorder,
                parentX = 0f,
                parentY = 0f,
                data = data,
                theme = theme,
                measureContext = MeasureContext(FixedTextMeasurer())
            )

            val labels = recorder.commands.filterIsInstance<DrawCommand.TextLine>()
            assertEquals(data.size, labels.size)
            data.forEachIndexed { index, (label) ->
                val angleStep = 2 * Math.PI / data.size
                val angle = (index * angleStep + Math.PI / 2 * 3) % (2 * Math.PI)
                val (expectedX, expectedY) = policy.fix(
                    angle,
                    angle / Math.PI,
                    fixedTextBox(label),
                    50f,
                    50f,
                    theme
                )
                assertFloatEquals(expectedX, labels[index].x)
                assertFloatEquals(expectedY, labels[index].y)
            }
        }
    }

    @Test
    fun radarChartRecordsEmptyDataAndZeroGridWithoutGridOrLabelCommands() {
        val recorder = RecordingDrawCanvas()
        val theme = RadarTheme(
            width = 100f,
            height = 100f,
            radius = 20f,
            gridCount = 0,
            gridFontProvider = { "g$it" },
            labelFontSize = 10f
        )

        drawRadarChart(
            canvas = recorder,
            parentX = 0f,
            parentY = 0f,
            data = emptyList(),
            theme = theme
        )

        assertEquals(3, recorder.commands.filterIsInstance<DrawCommand.Path>().size)
        assertEquals(0, recorder.commands.filterIsInstance<DrawCommand.Line>().size)
        assertEquals(0, recorder.commands.filterIsInstance<DrawCommand.TextLine>().size)
    }

    @Test
    fun radarFixPoliciesCoverRightSideAndTiltIntersectionBranches() {
        val box = fixedTextBox("Commit")
        val theme = RadarTheme(width = 100f, height = 100f, radius = 20f, labelOuterLength = 10f)

        listOf(RadarFixPolicy.MOVE_OUTSIDE, RadarFixPolicy.RATED_FIX).forEach { policy ->
            val (x, y) = policy.fix(
                Math.PI * 1.75,
                1.75,
                box,
                50f,
                50f,
                theme
            )
            assertTrue(x.isFinite(), "$policy 右侧分支 x 应为有限值")
            assertTrue(y.isFinite(), "$policy 右侧分支 y 应为有限值")
        }

        data class TiltCase(
            val angle: Double,
            val centerX: Float,
            val centerY: Float,
            val radius: Float,
            val labelOuterLength: Float
        )

        val halfWidth = box.width / 2f
        listOf(
            // 文本中心和雷达中心重合，覆盖零向量分支。
            TiltCase(0.0, 0f, 0f, 0f, 0f),
            // 水平线命中或错过字体盒竖边，覆盖 dx 分支和 t 正负分支。
            TiltCase(0.0, 0f, 0f, 20f, 10f),
            TiltCase(0.0, 0f, 100f, 20f, 10f),
            TiltCase(0.0, 0f, -100f, 20f, 10f),
            TiltCase(0.0, -100f, 0f, 20f, 10f),
            // 垂直线命中或错过字体盒横边，覆盖 dy 分支和范围判断。
            TiltCase(Math.PI / 2, halfWidth, 0f, 20f, 10f),
            TiltCase(Math.PI / 2, 0f, 0f, 20f, 10f),
            TiltCase(Math.PI / 2, halfWidth + 100f, 0f, 20f, 10f),
            TiltCase(Math.PI / 2, 0f, -100f, 20f, 10f)
        ).forEach { case ->
            val (x, y) = RadarFixPolicy.TILT.fix(
                case.angle,
                case.angle / Math.PI,
                box,
                case.centerX,
                case.centerY,
                RadarTheme(
                    width = 100f,
                    height = 100f,
                    radius = case.radius,
                    labelOuterLength = case.labelOuterLength,
                    labelFontSize = 10f
                )
            )
            assertTrue(x.isFinite(), "TILT x 应为有限值: $case")
            assertTrue(y.isFinite(), "TILT y 应为有限值: $case")
        }
    }

    @Test
    fun radarFixPoliciesUsedByConsumersProduceFiniteLabelPositions() {
        val box = fixedTextBox("Commit")
        val theme = RadarTheme(width = 100f, height = 100f, radius = 30f, labelOuterLength = 10f)

        listOf(
            RadarFixPolicy.NONE,
            RadarFixPolicy.MOVE_OUTSIDE,
            RadarFixPolicy.TILT
        ).forEach { policy ->
            val (x, y) = policy.fix(
                -Math.PI / 2,
                1.5,
                box,
                50f,
                50f,
                theme
            )
            assertTrue(x.isFinite(), "$policy x 应为有限值")
            assertTrue(y.isFinite(), "$policy y 应为有限值")
        }
    }

    @Test
    fun radarDslAddsCanvasElementWithThemeSize() {
        val root = Column()

        root.apply {
            radar(
                RadarTheme(width = 80f, height = 60f, radius = 20f),
                listOf("a" to 1f, "b" to 0.5f, "c" to 0.2f)
            )
        }
        root.measure(MeasureContext())

        assertFloatEquals(80f, root.width)
        assertFloatEquals(60f, root.height)
    }
}

