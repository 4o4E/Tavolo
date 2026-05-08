package top.e404.skiko.draw.test

import org.jetbrains.skia.Color
import org.jetbrains.skia.Font
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Surface
import org.junit.Test
import top.e404.skiko.draw.compose.Box
import top.e404.skiko.draw.compose.CanvasElement
import top.e404.skiko.draw.compose.Column
import top.e404.skiko.draw.compose.DrawCommand
import top.e404.skiko.draw.compose.DrawContext
import top.e404.skiko.draw.compose.HorizontalAlignment
import top.e404.skiko.draw.compose.ImageOverflow
import top.e404.skiko.draw.compose.MeasureContext
import top.e404.skiko.draw.compose.Modifier
import top.e404.skiko.draw.compose.RecordingDrawCanvas
import top.e404.skiko.draw.compose.Row
import top.e404.skiko.draw.compose.Table
import top.e404.skiko.draw.compose.TextMeasurer
import top.e404.skiko.draw.compose.TextMetrics
import top.e404.skiko.draw.compose.TextOverflow
import top.e404.skiko.draw.compose.UiElement
import top.e404.skiko.draw.compose.VerticalAlignment
import top.e404.skiko.draw.compose.background
import top.e404.skiko.draw.compose.border
import top.e404.skiko.draw.compose.box
import top.e404.skiko.draw.compose.charts.BarTheme
import top.e404.skiko.draw.compose.charts.bar
import top.e404.skiko.draw.compose.clip
import top.e404.skiko.draw.compose.fontSize
import top.e404.skiko.draw.compose.image
import top.e404.skiko.draw.compose.imageOverflow
import top.e404.skiko.draw.compose.margin
import top.e404.skiko.draw.compose.maxSize
import top.e404.skiko.draw.compose.padding
import top.e404.skiko.draw.compose.renderCommands
import top.e404.skiko.draw.compose.size
import top.e404.skiko.draw.compose.tableRow
import top.e404.skiko.draw.compose.cell
import top.e404.skiko.draw.compose.text
import top.e404.skiko.draw.compose.textColor
import top.e404.skiko.draw.compose.textOverflow
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val EPSILON = 0.001f

private class FixedTextMeasurer(
    private val charWidth: Float = 10f,
    private val ascent: Float = -8f,
    private val descent: Float = 2f
) : TextMeasurer {
    override fun measureTextWidth(text: String, font: Font, paint: Paint): Float =
        text.length * charWidth

    override fun metrics(font: Font): TextMetrics =
        TextMetrics(ascent, descent)
}

private fun assertFloatEquals(expected: Float, actual: Float) {
    assertTrue(
        abs(expected - actual) <= EPSILON,
        "期望 $expected，实际 $actual"
    )
}

private fun assertElementBounds(element: UiElement, x: Float, y: Float, width: Float, height: Float) {
    assertFloatEquals(x, element.x)
    assertFloatEquals(y, element.y)
    assertFloatEquals(width, element.width)
    assertFloatEquals(height, element.height)
}

private fun testImage(width: Int, height: Int): Image =
    Surface.makeRasterN32Premul(width, height).use { it.makeImageSnapshot() }

class ComposeLayoutUnitTest {
    @Test
    fun columnMeasuresMarginsAndHorizontalAlignment() {
        val root = Column(horizontalAlignment = HorizontalAlignment.Center)
        val first = CanvasElement(20f, 10f) {}
        first.modifier = Modifier.margin(top = 1f, right = 4f, bottom = 2f, left = 3f)
        val second = CanvasElement(40f, 10f) {}

        root.add(first)
        root.add(second)
        root.measure(MeasureContext(FixedTextMeasurer()))
        root.layout(0f, 0f)

        assertFloatEquals(40f, root.width)
        assertFloatEquals(23f, root.height)
        assertElementBounds(first, x = 9.5f, y = 1f, width = 20f, height = 10f)
        assertElementBounds(second, x = 0f, y = 13f, width = 40f, height = 10f)
    }

    @Test
    fun rowUsesSizeOverrideAndVerticalAlignment() {
        val root = Row(verticalAlignment = VerticalAlignment.Center)
        root.modifier = Modifier.size(width = 30f, height = 40f)
        val first = CanvasElement(10f, 10f) {}
        first.modifier = Modifier.margin(top = 5f, bottom = 5f)
        val second = CanvasElement(5f, 30f) {}

        root.add(first)
        root.add(second)
        root.measure(MeasureContext(FixedTextMeasurer()))
        root.layout(0f, 0f)

        assertElementBounds(root, x = 0f, y = 0f, width = 30f, height = 40f)
        assertElementBounds(first, x = 0f, y = 15f, width = 10f, height = 10f)
        assertElementBounds(second, x = 10f, y = 5f, width = 5f, height = 30f)
    }

    @Test
    fun boxAlignsChildrenInsidePaddingAndBorder() {
        val root = Box(
            horizontalAlignment = HorizontalAlignment.Right,
            verticalAlignment = VerticalAlignment.Bottom
        )
        root.modifier = Modifier
            .size(100f, 80f)
            .padding(10f)
            .border(2f, Color.RED)
        val child = CanvasElement(20f, 10f) {}
        child.modifier = Modifier.margin(right = 5f, bottom = 3f)

        root.add(child)
        root.measure(MeasureContext(FixedTextMeasurer()))
        root.layout(0f, 0f)

        assertFloatEquals(124f, root.width)
        assertFloatEquals(104f, root.height)
        assertElementBounds(child, x = 87f, y = 79f, width = 20f, height = 10f)
    }
}

class ComposeModifierCommandUnitTest {
    @Test
    fun backgroundAndBorderRecordExpectedRects() {
        val root = Box()
        root.modifier = Modifier
            .size(10f, 20f)
            .background(Color.BLUE)
            .border(top = 1f, right = 2f, bottom = 3f, left = 4f, color = Color.RED)
        root.measure(MeasureContext(FixedTextMeasurer()))
        root.layout(0f, 0f)
        val recorder = RecordingDrawCanvas()

        root.draw(DrawContext(recorder))

        val rects = recorder.commands.filterIsInstance<DrawCommand.Rect>()
        assertEquals(5, rects.size)
        assertEquals(Color.BLUE, rects[0].paint.color)
        assertFloatEquals(16f, rects[0].rect.width)
        assertFloatEquals(24f, rects[0].rect.height)
        assertEquals(Color.RED, rects[1].paint.color)
        assertFloatEquals(1f, rects[1].rect.height)
        assertFloatEquals(3f, rects[2].rect.height)
        assertFloatEquals(4f, rects[3].rect.width)
        assertFloatEquals(2f, rects[4].rect.width)
    }

    @Test
    fun clipWrapsElementDrawingWithSaveAndRestore() {
        val commands = renderCommands(
            backgroundColor = Color.TRANSPARENT,
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            box(
                modifier = Modifier
                    .size(20f)
                    .background(Color.RED)
                    .clip(top.e404.skiko.draw.compose.Shape.Circle)
            )
        }

        assertTrue(commands[1] is DrawCommand.Save)
        assertTrue(commands[2] is DrawCommand.ClipPath)
        assertTrue(commands[3] is DrawCommand.Rect)
        assertTrue(commands[4] is DrawCommand.Restore)
    }
}

class ComposeTextUnitTest {
    @Test
    fun ellipsisTruncatesTextToFitMaxWidth() {
        val commands = renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            text(
                "abcdef",
                Modifier
                    .maxSize(maxWidth = 30f)
                    .textOverflow(TextOverflow.Ellipsis)
                    .fontSize(20f)
            )
        }

        val command = commands.filterIsInstance<DrawCommand.Text>().single()
        assertEquals("ab…", command.text)
        assertFloatEquals(8f, command.baselineY)
        assertFloatEquals(20f, command.font.size)
    }

    @Test
    fun wrapSplitsWordsAndAppliesMaxHeightEllipsis() {
        val commands = renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            text(
                "aa bbbb c",
                Modifier
                    .maxSize(maxWidth = 40f, maxHeight = 20f)
            )
        }

        val texts = commands.filterIsInstance<DrawCommand.Text>()
        assertEquals(listOf("aa", "bbb…"), texts.map { it.text })
        assertFloatEquals(8f, texts[0].baselineY)
        assertFloatEquals(18f, texts[1].baselineY)
    }

    @Test
    fun textDrawPositionIncludesPaddingAndBorder() {
        val commands = renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            text(
                "hi",
                Modifier
                    .padding(left = 3f, top = 4f)
                    .border(left = 2f, top = 1f, color = Color.RED)
                    .textColor(Color.GREEN)
            )
        }

        val command = commands.filterIsInstance<DrawCommand.Text>().single()
        assertFloatEquals(5f, command.x)
        assertFloatEquals(13f, command.baselineY)
        assertEquals(Color.GREEN, command.paint.color)
    }
}

class ComposeImageUnitTest {
    @Test
    fun imageScaleKeepsAspectRatioAndDoesNotCrop() {
        val image = testImage(100, 50)
        val commands = renderCommands {
            image(
                image,
                Modifier
                    .maxSize(maxWidth = 50f, maxHeight = 50f)
                    .imageOverflow(ImageOverflow.Scale)
            )
        }

        val command = commands.filterIsInstance<DrawCommand.ImageRect>().single()
        assertFloatEquals(100f, command.src.width)
        assertFloatEquals(50f, command.src.height)
        assertFloatEquals(50f, command.dst.width)
        assertFloatEquals(25f, command.dst.height)
    }

    @Test
    fun imageCropUsesCenteredSourceRect() {
        val image = testImage(100, 50)
        val commands = renderCommands {
            image(
                image,
                Modifier
                    .maxSize(maxWidth = 40f, maxHeight = 30f)
                    .imageOverflow(ImageOverflow.Crop)
            )
        }

        val command = commands.filterIsInstance<DrawCommand.ImageRect>().single()
        assertFloatEquals(30f, command.src.left)
        assertFloatEquals(10f, command.src.top)
        assertFloatEquals(40f, command.src.width)
        assertFloatEquals(30f, command.src.height)
        assertFloatEquals(40f, command.dst.width)
        assertFloatEquals(30f, command.dst.height)
    }
}

class ComposeTableUnitTest {
    @Test
    fun tableUsesMaxColumnWidthsAndRowSpacing() {
        val table = Table(columnSpacing = 2f, rowSpacing = 3f)
        table.apply {
            tableRow {
                cell { text("aa") }
                cell { text("b") }
            }
            tableRow {
                cell { text("c") }
                cell { text("dddd") }
            }
        }

        table.measure(MeasureContext(FixedTextMeasurer()))
        table.layout(0f, 0f)

        assertFloatEquals(62f, table.width)
        assertFloatEquals(23f, table.height)
        assertElementBounds(table.rows[0].cells[0], x = 0f, y = 0f, width = 20f, height = 10f)
        assertElementBounds(table.rows[0].cells[1], x = 22f, y = 0f, width = 40f, height = 10f)
        assertElementBounds(table.rows[1].cells[0], x = 0f, y = 13f, width = 20f, height = 10f)
        assertElementBounds(table.rows[1].cells[1], x = 22f, y = 13f, width = 40f, height = 10f)
    }
}

class ComposeCanvasAndChartUnitTest {
    @Test
    fun canvasElementReceivesParentPositionAndRecordsCustomDraw() {
        val commands = renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            val element = CanvasElement(10f, 10f) { canvas ->
                canvas.drawCircle(parentX + 1f, parentY + 2f, 3f, Paint().apply { color = Color.RED })
            }
            element.modifier = Modifier.margin(left = 4f, top = 5f)
            add(element)
        }

        val command = commands.filterIsInstance<DrawCommand.Circle>().single()
        assertFloatEquals(5f, command.x)
        assertFloatEquals(7f, command.y)
        assertFloatEquals(3f, command.radius)
        assertEquals(Color.RED, command.paint.color)
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
}
