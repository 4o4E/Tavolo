package top.e404.tavolo.draw.test

import org.jetbrains.skia.*
import org.junit.Test
import top.e404.tavolo.draw.compose.*
import top.e404.tavolo.draw.compose.charts.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.math.sqrt

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
    fun chartScaleExpandsFlatDomainAndDataLimiterKeepsEdges() {
        val scale = ChartScale.linear(
            domainMin = 10f,
            domainMax = 10f,
            rangeMin = 0f,
            rangeMax = 100f
        )

        assertFloatEquals(50f, scale.map(10f))
        assertEquals(listOf(0, 2, 4, 6, 9), ChartDataLimiter.limit((0..9).toList(), maxItems = 5))
    }

    @Test
    fun lineChartRecordsAxesSplitSegmentsPointsAndLegend() {
        val commands = renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            lineChart(
                theme = LineChartTheme(
                    width = 260f,
                    height = 160f,
                    xTickCount = 2,
                    yTickCount = 2,
                    pointRadius = 2f
                ),
                series = listOf(
                    LineSeries(
                        name = "在线",
                        color = Color.RED,
                        fillColor = Color.makeARGB(40, 255, 0, 0),
                        points = listOf(
                            LinePoint(0f, 1f),
                            LinePoint(1f, null),
                            LinePoint(2f, 4f),
                            LinePoint(3f, 2f)
                        )
                    )
                )
            )
        }

        assertTrue(commands.filterIsInstance<DrawCommand.Line>().size >= 8)
        assertEquals(2, commands.filterIsInstance<DrawCommand.Path>().size)
        assertEquals(3, commands.filterIsInstance<DrawCommand.Circle>().size)
        assertEquals(1, commands.filterIsInstance<DrawCommand.Rect>().count { it.paint.color == Color.RED })
    }

    @Test
    fun pieChartSupportsTopNamedSlicesLabelsLegendAndDonutHole() {
        val recorder = RecordingDrawCanvas()

        drawPieChart(
            canvas = recorder,
            parentX = 0f,
            parentY = 0f,
            data = listOf(
                PieSlice("A", 4f, Color.RED),
                PieSlice("B", 3f, Color.BLUE),
                PieSlice("C", 2f, Color.GREEN),
                PieSlice("D", 1f, Color.YELLOW)
            ),
            theme = PieChartTheme(
                width = 240f,
                height = 150f,
                radius = 40f,
                innerRadius = 16f,
                maxNamedSlices = 2,
                minLabelPercent = 0f,
            ),
            measureContext = MeasureContext(FixedTextMeasurer())
        )

        val arcs = recorder.commands.filterIsInstance<DrawCommand.Arc>()
        assertEquals(6, arcs.size)
        assertEquals(3, arcs.count { it.paint.mode == PaintMode.FILL })
        assertTrue(recorder.commands.any { it is DrawCommand.ClipPath })
        assertEquals(2, recorder.commands.filterIsInstance<DrawCommand.Circle>().size)
        assertEquals(6, recorder.commands.filterIsInstance<DrawCommand.TextLine>().size)
    }

    @Test
    fun categoryBarChartRecordsGroupedAndStackedBars() {
        val grouped = RecordingDrawCanvas()
        val data = CategoryBarData(
            categories = listOf("A", "B"),
            series = listOf(
                BarSeries("玩家", listOf(2f, 4f), Color.RED),
                BarSeries("服务器", listOf(3f, 1f), Color.BLUE)
            )
        )

        drawCategoryBarChart(
            canvas = grouped,
            parentX = 0f,
            parentY = 0f,
            data = data,
            theme = CategoryBarTheme(
                width = 240f,
                height = 160f,
                legend = ChartLegendTheme(position = ChartLegendPosition.NONE)
            ),
            measureContext = MeasureContext(FixedTextMeasurer())
        )

        val groupedBars = grouped.commands
            .filterIsInstance<DrawCommand.Rect>()
            .filter { it.paint.color == Color.RED || it.paint.color == Color.BLUE }
        assertEquals(4, groupedBars.size)
        assertTrue(groupedBars.all { it.rect.height > 0f })

        val stacked = RecordingDrawCanvas()
        drawCategoryBarChart(
            canvas = stacked,
            parentX = 0f,
            parentY = 0f,
            data = data,
            theme = CategoryBarTheme(
                width = 240f,
                height = 160f,
                mode = BarChartMode.STACKED,
                legend = ChartLegendTheme(position = ChartLegendPosition.NONE)
            ),
            measureContext = MeasureContext(FixedTextMeasurer())
        )

        val stackedBars = stacked.commands
            .filterIsInstance<DrawCommand.Rect>()
            .filter { it.paint.color == Color.RED || it.paint.color == Color.BLUE }
        assertEquals(4, stackedBars.size)
        assertTrue(stackedBars.all { it.rect.height > 0f })
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
    fun relationGraphFixedLayoutRecordsEdgesNodesAndLabels() {
        val recorder = RecordingDrawCanvas()
        val theme = RelationGraphTheme(
            width = 100f,
            height = 80f,
            layout = RelationGraphLayout.Fixed(
                mapOf(
                    "a" to (20f to 30f),
                    "b" to (80f to 30f)
                )
            ),
            nodeRadius = 10f,
            nodeStrokeWidth = 1f,
            edgeWidth = 2f,
            nodeTextStyle = ChartTextStyle(10f, Color.WHITE),
            edgeTextStyle = ChartTextStyle(8f, Color.BLUE)
        )

        drawRelationGraph(
            canvas = recorder,
            parentX = 5f,
            parentY = 7f,
            nodes = listOf(RelationNode("a", "A"), RelationNode("b", "B")),
            edges = listOf(RelationEdge("a", "b", label = "rel", directed = false)),
            theme = theme,
            measureContext = MeasureContext(FixedTextMeasurer(charWidth = 5f))
        )

        val line = recorder.commands.filterIsInstance<DrawCommand.Line>().single()
        assertFloatEquals(35f, line.x0)
        assertFloatEquals(37f, line.y0)
        assertFloatEquals(75f, line.x1)
        assertFloatEquals(37f, line.y1)
        assertEquals(PaintMode.STROKE, line.paint.mode)

        val circles = recorder.commands.filterIsInstance<DrawCommand.Circle>()
        assertEquals(4, circles.size)
        assertFloatEquals(25f, circles[0].x)
        assertFloatEquals(37f, circles[0].y)
        assertFloatEquals(10f, circles[0].radius)
        assertEquals(PaintMode.FILL, circles[0].paint.mode)
        assertEquals(PaintMode.STROKE, circles[1].paint.mode)

        val textLines = recorder.commands.filterIsInstance<DrawCommand.TextLine>()
        assertEquals(3, textLines.size)
        assertEquals(Color.BLUE, textLines[0].paint.color)
        assertEquals(Color.WHITE, textLines[1].paint.color)
    }

    @Test
    fun relationGraphLayeredLayoutHandlesCyclesAndUnknownEdges() {
        val recorder = RecordingDrawCanvas()

        drawRelationGraph(
            canvas = recorder,
            parentX = 0f,
            parentY = 0f,
            nodes = listOf(
                RelationNode("a", "A"),
                RelationNode("b", "B"),
                RelationNode("c", "C")
            ),
            edges = listOf(
                RelationEdge("a", "b"),
                RelationEdge("b", "a"),
                RelationEdge("a", "missing")
            ),
            theme = RelationGraphTheme(
                width = 220f,
                height = 140f,
                layout = RelationGraphLayout.Layered(roots = listOf("a")),
                nodeRadius = 12f
            ),
            measureContext = MeasureContext(FixedTextMeasurer())
        )

        assertEquals(2, recorder.commands.filterIsInstance<DrawCommand.Line>().size)
        assertEquals(2, recorder.commands.filterIsInstance<DrawCommand.Path>().size)
        recorder.commands.filterIsInstance<DrawCommand.Line>().forEach {
            assertTrue(it.x0.isFinite())
            assertTrue(it.y0.isFinite())
            assertTrue(it.x1.isFinite())
            assertTrue(it.y1.isFinite())
        }
    }

    @Test
    fun relationGraphHandlesEmptyAndSingleCircularNode() {
        val emptyRecorder = RecordingDrawCanvas()
        drawRelationGraph(
            canvas = emptyRecorder,
            parentX = 0f,
            parentY = 0f,
            nodes = emptyList(),
            edges = listOf(RelationEdge("a", "b")),
            theme = RelationGraphTheme(width = 80f, height = 60f)
        )
        assertEquals(0, emptyRecorder.commands.size)

        val singleRecorder = RecordingDrawCanvas()
        drawRelationGraph(
            canvas = singleRecorder,
            parentX = 2f,
            parentY = 3f,
            nodes = listOf(RelationNode("only")),
            edges = emptyList(),
            theme = RelationGraphTheme(
                width = 80f,
                height = 60f,
                layout = RelationGraphLayout.Circular,
                nodeRadius = 9f,
                nodeStrokeWidth = 0f,
                nodeTextStyle = ChartTextStyle(10f, Color.WHITE)
            ),
            measureContext = MeasureContext(FixedTextMeasurer(charWidth = 4f))
        )

        val circle = singleRecorder.commands.filterIsInstance<DrawCommand.Circle>().single()
        assertFloatEquals(42f, circle.x)
        assertFloatEquals(33f, circle.y)
        assertFloatEquals(9f, circle.radius)
        assertEquals(1, singleRecorder.commands.filterIsInstance<DrawCommand.TextLine>().size)
    }

    @Test
    fun relationGraphFixedLayoutFallsBackAndDslDrawsCanvasElement() {
        val commands = renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            relationGraph(
                RelationGraphTheme(
                    width = 120f,
                    height = 90f,
                    layout = RelationGraphLayout.Fixed(mapOf("a" to (20f to 25f))),
                    padding = 20f,
                    nodeRadius = 8f,
                    nodeTextStyle = ChartTextStyle(10f, Color.WHITE)
                ),
                nodes = listOf(RelationNode("a"), RelationNode("b")),
                edges = emptyList()
            )
        }

        val circles = commands.filterIsInstance<DrawCommand.Circle>()
        assertEquals(4, circles.size)
        assertFloatEquals(20f, circles[0].x)
        assertFloatEquals(25f, circles[0].y)
        circles.forEach {
            assertTrue(it.x.isFinite())
            assertTrue(it.y.isFinite())
        }
    }

    @Test
    fun relationGraphSelfLoopRecordsArcAndOptionalArrow() {
        val recorder = RecordingDrawCanvas()

        drawRelationGraph(
            canvas = recorder,
            parentX = 0f,
            parentY = 0f,
            nodes = listOf(RelationNode("a", "A", radius = 14f)),
            edges = listOf(
                RelationEdge(
                    from = "a",
                    to = "a",
                    label = "loop",
                    directed = true,
                    color = Color.RED,
                    width = 3f,
                    style = StrokeStyle.Dotted(2f, 3f)
                ),
                RelationEdge("a", "a", directed = false)
            ),
            theme = RelationGraphTheme(
                width = 100f,
                height = 80f,
                nodeRadius = 10f,
                nodeTextStyle = ChartTextStyle(10f, Color.WHITE)
            ),
            measureContext = MeasureContext(FixedTextMeasurer())
        )

        val arcs = recorder.commands.filterIsInstance<DrawCommand.Arc>()
        assertEquals(2, arcs.size)
        assertEquals(Color.RED, arcs[0].paint.color)
        assertFloatEquals(3f, arcs[0].paint.strokeWidth)
        assertTrue(arcs[0].paint.hasPathEffect)
        assertEquals(1, recorder.commands.filterIsInstance<DrawCommand.Path>().size)
        assertEquals(2, recorder.commands.filterIsInstance<DrawCommand.TextLine>().size)
    }

    @Test
    fun relationGraphSkipsZeroLengthAndBlankLabelEdges() {
        val recorder = RecordingDrawCanvas()

        drawRelationGraph(
            canvas = recorder,
            parentX = 0f,
            parentY = 0f,
            nodes = listOf(RelationNode("a", "A"), RelationNode("b", "B")),
            edges = listOf(
                RelationEdge("a", "b", label = "   ", directed = true),
                RelationEdge("a", "b", label = null, directed = true)
            ),
            theme = RelationGraphTheme(
                width = 100f,
                height = 80f,
                layout = RelationGraphLayout.Fixed(
                    mapOf(
                        "a" to (40f to 40f),
                        "b" to (40f to 40f)
                    )
                ),
                nodeRadius = 10f,
                nodeTextStyle = ChartTextStyle(10f, Color.WHITE)
            ),
            measureContext = MeasureContext(FixedTextMeasurer())
        )

        assertEquals(0, recorder.commands.filterIsInstance<DrawCommand.Line>().size)
        assertEquals(0, recorder.commands.filterIsInstance<DrawCommand.Path>().size)
        assertEquals(2, recorder.commands.filterIsInstance<DrawCommand.TextLine>().size)
    }

    @Test
    fun relationGraphCoversNormalEdgeStyleFallbacksAndArrowSizeZero() {
        val recorder = RecordingDrawCanvas()

        drawRelationGraph(
            canvas = recorder,
            parentX = 0f,
            parentY = 0f,
            nodes = listOf(
                RelationNode("a", "A", radius = 11f),
                RelationNode("b", "B"),
                RelationNode("c", "C", radius = 13f)
            ),
            edges = listOf(
                RelationEdge("a", "b", label = "   ", directed = true),
                RelationEdge("b", "c", label = null, directed = true, color = Color.RED, width = 4f, style = StrokeStyle.Dashed(listOf(4f, 2f))),
                RelationEdge("missing", "a", directed = true)
            ),
            theme = RelationGraphTheme(
                width = 180f,
                height = 150f,
                layout = RelationGraphLayout.Circular,
                padding = 24f,
                nodeRadius = 10f,
                arrowSize = 0f,
                nodeTextStyle = ChartTextStyle(10f, Color.WHITE)
            ),
            measureContext = MeasureContext(FixedTextMeasurer())
        )

        val lines = recorder.commands.filterIsInstance<DrawCommand.Line>()
        assertEquals(2, lines.size)
        assertEquals(Color.RED, lines[1].paint.color)
        assertFloatEquals(4f, lines[1].paint.strokeWidth)
        assertTrue(lines[1].paint.hasPathEffect)
        assertEquals(0, recorder.commands.filterIsInstance<DrawCommand.Path>().size)
        assertEquals(3, recorder.commands.filterIsInstance<DrawCommand.TextLine>().size)
    }

    @Test
    fun relationGraphLayeredLayoutInfersRootsAndFallsBackForCycle() {
        val inferredRootRecorder = RecordingDrawCanvas()
        drawRelationGraph(
            canvas = inferredRootRecorder,
            parentX = 0f,
            parentY = 0f,
            nodes = listOf(RelationNode("a"), RelationNode("b"), RelationNode("c")),
            edges = listOf(RelationEdge("a", "b"), RelationEdge("a", "c")),
            theme = RelationGraphTheme(
                width = 180f,
                height = 120f,
                layout = RelationGraphLayout.Layered(),
                nodeRadius = 8f,
                nodeTextStyle = ChartTextStyle(10f, Color.WHITE)
            ),
            measureContext = MeasureContext(FixedTextMeasurer())
        )
        assertEquals(2, inferredRootRecorder.commands.filterIsInstance<DrawCommand.Line>().size)

        val cycleRecorder = RecordingDrawCanvas()
        drawRelationGraph(
            canvas = cycleRecorder,
            parentX = 0f,
            parentY = 0f,
            nodes = listOf(RelationNode("a"), RelationNode("b")),
            edges = listOf(RelationEdge("a", "b"), RelationEdge("b", "a")),
            theme = RelationGraphTheme(
                width = 160f,
                height = 100f,
                layout = RelationGraphLayout.Layered(),
                nodeRadius = 8f,
                nodeTextStyle = ChartTextStyle(10f, Color.WHITE)
            ),
            measureContext = MeasureContext(FixedTextMeasurer())
        )
        assertEquals(2, cycleRecorder.commands.filterIsInstance<DrawCommand.Line>().size)
        cycleRecorder.commands.filterIsInstance<DrawCommand.Line>().forEach {
            assertTrue(it.x0.isFinite())
            assertTrue(it.y0.isFinite())
        }

        val singleLayerRecorder = RecordingDrawCanvas()
        drawRelationGraph(
            canvas = singleLayerRecorder,
            parentX = 0f,
            parentY = 0f,
            nodes = listOf(RelationNode("a")),
            edges = emptyList(),
            theme = RelationGraphTheme(
                width = 100f,
                height = 80f,
                layout = RelationGraphLayout.Layered(),
                nodeRadius = 8f,
                nodeTextStyle = ChartTextStyle(10f, Color.WHITE)
            ),
            measureContext = MeasureContext(FixedTextMeasurer())
        )
        val circle = singleLayerRecorder.commands.filterIsInstance<DrawCommand.Circle>().first()
        assertFloatEquals(50f, circle.x)
        assertFloatEquals(40f, circle.y)
    }

    @Test
    fun relationGraphLayeredLayoutUsesLongestPathForDag() {
        val recorder = RecordingDrawCanvas()

        drawRelationGraph(
            canvas = recorder,
            parentX = 0f,
            parentY = 0f,
            nodes = listOf(RelationNode("a"), RelationNode("b"), RelationNode("c")),
            edges = listOf(
                RelationEdge("a", "b", directed = false),
                RelationEdge("a", "c", directed = false),
                RelationEdge("b", "c", directed = false)
            ),
            theme = RelationGraphTheme(
                width = 300f,
                height = 160f,
                layout = RelationGraphLayout.Layered(roots = listOf("a")),
                padding = 30f,
                nodeRadius = 8f,
                nodeTextStyle = ChartTextStyle(10f, Color.WHITE)
            ),
            measureContext = MeasureContext(FixedTextMeasurer())
        )

        val circles = recorder.commands.filterIsInstance<DrawCommand.Circle>()
        assertFloatEquals(30f, circles[0].x)
        assertFloatEquals(150f, circles[2].x)
        assertFloatEquals(270f, circles[4].x)
    }

    @Test
    fun relationGraphForceLayoutKeepsComplexGraphBoundedAndSeparated() {
        val recorder = RecordingDrawCanvas()
        val nodes = (1..10).map { RelationNode("n$it") }
        val edges = listOf(
            RelationEdge("n1", "n2"),
            RelationEdge("n1", "n3"),
            RelationEdge("n2", "n4"),
            RelationEdge("n3", "n4"),
            RelationEdge("n4", "n5"),
            RelationEdge("n5", "n6"),
            RelationEdge("n6", "n7"),
            RelationEdge("n7", "n3"),
            RelationEdge("n5", "n8"),
            RelationEdge("n8", "n9"),
            RelationEdge("n9", "n10"),
            RelationEdge("n10", "n5"),
            RelationEdge("missing", "n1")
        )

        drawRelationGraph(
            canvas = recorder,
            parentX = 0f,
            parentY = 0f,
            nodes = nodes,
            edges = edges,
            theme = RelationGraphTheme(
                width = 960f,
                height = 720f,
                layout = RelationGraphLayout.Force(
                    iterations = 380,
                    linkDistance = 230f,
                    repulsion = 16000f,
                    collisionPadding = 86f
                ),
                padding = 90f,
                nodeRadius = 18f,
                nodeTextStyle = ChartTextStyle(10f, Color.WHITE)
            ),
            measureContext = MeasureContext(FixedTextMeasurer())
        )

        val circles = recorder.commands.filterIsInstance<DrawCommand.Circle>()
            .filter { it.paint.mode == PaintMode.FILL }
        assertEquals(nodes.size, circles.size)
        circles.forEach {
            assertTrue(it.x in 90f..870f)
            assertTrue(it.y in 90f..630f)
        }

        val minCenterDistance = circles.flatMapIndexed { index, a ->
            circles.drop(index + 1).map { b -> distance(a.x, a.y, b.x, b.y) }
        }.minOrNull() ?: Float.MAX_VALUE
        assertTrue(minCenterDistance >= 95f, "力导向布局应避免节点贴得过近")

        val xSpread = circles.maxOf { it.x } - circles.minOf { it.x }
        val ySpread = circles.maxOf { it.y } - circles.minOf { it.y }
        assertTrue(ySpread > 260f, "复杂关系不应被压成横向长条")
        assertTrue(xSpread < ySpread * 2.2f, "复杂关系不应被拉得过长")

        val relationLines = recorder.commands.filterIsInstance<DrawCommand.Line>()
        assertEquals(edges.size - 1, relationLines.size)
        assertTrue(relationLines.all { distance(it.x0, it.y0, it.x1, it.y1) > 60f })
    }

    @Test
    fun relationGraphForceLayoutSeparatesOverlappedInitialNodes() {
        val recorder = RecordingDrawCanvas()
        val nodes = (1..5).map { RelationNode("n$it") }

        drawRelationGraph(
            canvas = recorder,
            parentX = 0f,
            parentY = 0f,
            nodes = nodes,
            edges = listOf(
                RelationEdge("n1", "n2"),
                RelationEdge("n2", "n3"),
                RelationEdge("n3", "n4"),
                RelationEdge("n4", "n5")
            ),
            theme = RelationGraphTheme(
                width = 360f,
                height = 300f,
                layout = RelationGraphLayout.Force(
                    iterations = 40,
                    linkDistance = 95f,
                    repulsion = 6000f,
                    collisionPadding = 36f,
                    initialRadiusRatio = 0f
                ),
                padding = 48f,
                nodeRadius = 18f,
                nodeTextStyle = ChartTextStyle(10f, Color.WHITE)
            ),
            measureContext = MeasureContext(FixedTextMeasurer())
        )

        val circles = recorder.commands.filterIsInstance<DrawCommand.Circle>()
            .filter { it.paint.mode == PaintMode.FILL }
        assertEquals(nodes.size, circles.size)
        val minCenterDistance = circles.flatMapIndexed { index, a ->
            circles.drop(index + 1).map { b -> distance(a.x, a.y, b.x, b.y) }
        }.minOrNull() ?: Float.MAX_VALUE
        assertTrue(minCenterDistance > 40f, "初始重叠节点应被稳定拆开")
    }

    @Test
    fun relationGraphRejectsDuplicateNodeIds() {
        val error = assertFailsWith<IllegalArgumentException> {
            drawRelationGraph(
                canvas = RecordingDrawCanvas(),
                parentX = 0f,
                parentY = 0f,
                nodes = listOf(RelationNode("dup", "A"), RelationNode("dup", "B")),
                edges = emptyList(),
                theme = RelationGraphTheme(width = 100f, height = 80f)
            )
        }

        assertTrue(error.message!!.contains("重复 id: dup"))
    }

    @Test
    fun relationGraphUsesThemeAndElementDrawersWithDefaultDrawing() {
        val recorder = RecordingDrawCanvas()
        val nodeCalls = mutableListOf<String>()
        val edgeCalls = mutableListOf<String>()

        drawRelationGraph(
            canvas = recorder,
            parentX = 0f,
            parentY = 0f,
            nodes = listOf(
                RelationNode("a", "A"),
                RelationNode(
                    id = "b",
                    label = "B",
                    drawer = RelationNodeDrawer { scope ->
                        nodeCalls += "node:${scope.node.id}"
                        scope.canvas.drawCircle(
                            scope.centerX,
                            scope.centerY,
                            scope.radius + 3f,
                            Paint().apply { color = Color.YELLOW }
                        )
                        scope.drawDefault()
                    }
                )
            ),
            edges = listOf(
                RelationEdge("a", "b", label = "theme"),
                RelationEdge(
                    from = "b",
                    to = "b",
                    label = "custom",
                    drawer = RelationEdgeDrawer { scope ->
                        edgeCalls += "edge:${scope.edge.from}->${scope.edge.to}:${scope.isSelfLoop}"
                        scope.drawDefault()
                    }
                )
            ),
            theme = RelationGraphTheme(
                width = 140f,
                height = 100f,
                layout = RelationGraphLayout.Fixed(
                    mapOf(
                        "a" to (30f to 50f),
                        "b" to (110f to 50f)
                    )
                ),
                nodeRadius = 10f,
                nodeTextStyle = ChartTextStyle(10f, Color.WHITE),
                edgeTextStyle = ChartTextStyle(8f, Color.BLUE),
                nodeDrawer = RelationNodeDrawer { scope ->
                    nodeCalls += "theme:${scope.node.id}"
                    scope.drawDefault()
                },
                edgeDrawer = RelationEdgeDrawer { scope ->
                    edgeCalls += "theme:${scope.edge.from}->${scope.edge.to}:${scope.isSelfLoop}"
                    scope.drawDefault()
                }
            ),
            measureContext = MeasureContext(FixedTextMeasurer())
        )

        assertEquals(listOf("theme:a", "node:b"), nodeCalls)
        assertEquals(listOf("theme:a->b:false", "edge:b->b:true"), edgeCalls)
        assertEquals(5, recorder.commands.filterIsInstance<DrawCommand.Circle>().size)
        assertEquals(1, recorder.commands.filterIsInstance<DrawCommand.Line>().size)
        assertEquals(1, recorder.commands.filterIsInstance<DrawCommand.Arc>().size)
    }

    @Test
    fun relationGraphCustomDrawersCanReplaceDefaultDrawingAndUseScopeGeometry() {
        val recorder = RecordingDrawCanvas()
        val edgeGeometry = mutableListOf<Pair<Pair<Float, Float>, Pair<Float, Float>>>()

        drawRelationGraph(
            canvas = recorder,
            parentX = 3f,
            parentY = 4f,
            nodes = listOf(RelationNode("a", "A"), RelationNode("b", "B")),
            edges = listOf(RelationEdge("a", "b")),
            theme = RelationGraphTheme(
                width = 120f,
                height = 80f,
                layout = RelationGraphLayout.Fixed(
                    mapOf(
                        "a" to (20f to 30f),
                        "b" to (100f to 30f)
                    )
                ),
                nodeRadius = 10f,
                nodeDrawer = RelationNodeDrawer { scope ->
                    scope.canvas.drawRect(
                        Rect.makeXYWH(
                            scope.centerX - scope.radius,
                            scope.centerY - scope.radius,
                            scope.radius * 2f,
                            scope.radius * 2f
                        ),
                        Paint().apply {
                            color = Color.YELLOW
                            mode = PaintMode.FILL
                        }
                    )
                },
                edgeDrawer = RelationEdgeDrawer { scope ->
                    edgeGeometry += (scope.startX to scope.startY) to (scope.endX to scope.endY)
                    scope.canvas.drawLine(
                        scope.startX,
                        scope.startY,
                        scope.endX,
                        scope.endY,
                        Paint().apply {
                            color = Color.RED
                            mode = PaintMode.STROKE
                            strokeWidth = 4f
                        }
                    )
                }
            ),
            measureContext = MeasureContext(FixedTextMeasurer())
        )

        assertEquals(2, recorder.commands.filterIsInstance<DrawCommand.Rect>().size)
        assertEquals(0, recorder.commands.filterIsInstance<DrawCommand.Circle>().size)
        assertEquals(0, recorder.commands.filterIsInstance<DrawCommand.TextLine>().size)
        val line = recorder.commands.filterIsInstance<DrawCommand.Line>().single()
        assertEquals(Color.RED, line.paint.color)
        assertFloatEquals(4f, line.paint.strokeWidth)
        assertEquals(listOf((33f to 34f) to (93f to 34f)), edgeGeometry)
    }

    @Test
    fun relationGraphElementDrawerCanReplaceThemeDrawerWithoutCallingDefault() {
        val recorder = RecordingDrawCanvas()
        val nodeCalls = mutableListOf<String>()

        drawRelationGraph(
            canvas = recorder,
            parentX = 0f,
            parentY = 0f,
            nodes = listOf(
                RelationNode("theme"),
                RelationNode(
                    id = "custom",
                    drawer = RelationNodeDrawer { scope ->
                        nodeCalls += "custom:${scope.node.id}"
                        scope.canvas.drawCircle(
                            scope.centerX,
                            scope.centerY,
                            scope.radius + 6f,
                            Paint().apply { color = Color.RED }
                        )
                    }
                )
            ),
            edges = emptyList(),
            theme = RelationGraphTheme(
                width = 120f,
                height = 80f,
                layout = RelationGraphLayout.Fixed(
                    mapOf(
                        "theme" to (30f to 40f),
                        "custom" to (90f to 40f)
                    )
                ),
                nodeRadius = 10f,
                nodeTextStyle = ChartTextStyle(10f, Color.WHITE),
                nodeDrawer = RelationNodeDrawer { scope ->
                    nodeCalls += "theme:${scope.node.id}"
                    scope.drawDefault()
                }
            ),
            measureContext = MeasureContext(FixedTextMeasurer())
        )

        assertEquals(listOf("theme:theme", "custom:custom"), nodeCalls)
        val circles = recorder.commands.filterIsInstance<DrawCommand.Circle>()
        assertEquals(3, circles.size)
        assertEquals(Color.RED, circles.last().paint.color)
        assertFloatEquals(16f, circles.last().radius)
        assertEquals(1, recorder.commands.filterIsInstance<DrawCommand.TextLine>().size)
    }

    @Test
    fun relationGraphThemeConveniencePaintsExposeThemeStyles() {
        val theme = RelationGraphTheme(
            width = 100f,
            height = 80f,
            nodeFillColor = Color.RED,
            nodeStrokeColor = Color.GREEN,
            nodeStrokeWidth = 4f,
            edgeColor = Color.BLUE,
            edgeWidth = 5f,
            edgeLineStyle = StrokeStyle.Dashed(listOf(3f, 2f))
        )

        assertEquals(Color.RED, theme.nodeFill.color)
        assertEquals(Color.GREEN, theme.nodeStroke.color)
        assertFloatEquals(4f, theme.nodeStroke.width)
        assertEquals(Color.BLUE, theme.edgeStroke.color)
        assertFloatEquals(5f, theme.edgeStroke.width)
        assertTrue(theme.edgeStroke.style is StrokeStyle.Dashed)
    }

    @Test
    fun relationGraphDslAddsCanvasElementWithThemeSize() {
        val root = Column()

        root.apply {
            relationGraph(
                RelationGraphTheme(width = 120f, height = 90f),
                nodes = listOf(RelationNode("a"), RelationNode("b")),
                edges = listOf(RelationEdge("a", "b"))
            )
        }
        root.measure(MeasureContext())

        assertFloatEquals(120f, root.width)
        assertFloatEquals(90f, root.height)
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

    private fun distance(x0: Float, y0: Float, x1: Float, y1: Float): Float {
        val dx = x1 - x0
        val dy = y1 - y0
        return sqrt(dx * dx + dy * dy)
    }
}

