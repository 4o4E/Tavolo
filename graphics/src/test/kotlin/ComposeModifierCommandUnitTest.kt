package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.jetbrains.skia.PaintMode
import org.junit.Test
import top.e404.tavolo.draw.compose.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        assertFloatEquals(10f, rects[0].rect.width)
        assertFloatEquals(20f, rects[0].rect.height)
        assertEquals(Color.RED, rects[1].paint.color)
        assertFloatEquals(1f, rects[1].rect.height)
        assertFloatEquals(3f, rects[2].rect.height)
        assertFloatEquals(4f, rects[3].rect.width)
        assertFloatEquals(2f, rects[4].rect.width)
    }

    @Test
    fun orderedPaddingBackgroundAndBorderUseNestedBounds() {
        val root = Box()
        root.modifier = Modifier
            .padding(2f)
            .background(Color.BLUE)
            .padding(top = 1f, right = 4f, bottom = 2f, left = 3f)
            .border(1f, Color.RED)
            .size(10f, 8f)
        root.measure(MeasureContext(FixedTextMeasurer()))
        root.layout(0f, 0f)
        val recorder = RecordingDrawCanvas()

        root.draw(DrawContext(recorder))

        assertElementBounds(root, x = 0f, y = 0f, width = 23f, height = 17f)
        val rects = recorder.commands.filterIsInstance<DrawCommand.Rect>()
        assertEquals(5, rects.size)

        val background = rects[0]
        assertEquals(Color.BLUE, background.paint.color)
        assertFloatEquals(2f, background.rect.left)
        assertFloatEquals(2f, background.rect.top)
        assertFloatEquals(19f, background.rect.width)
        assertFloatEquals(13f, background.rect.height)

        val topBorder = rects[1]
        assertEquals(Color.RED, topBorder.paint.color)
        assertFloatEquals(5f, topBorder.rect.left)
        assertFloatEquals(3f, topBorder.rect.top)
        assertFloatEquals(12f, topBorder.rect.width)
        assertFloatEquals(1f, topBorder.rect.height)
    }

    @Test
    fun sizeOrderChangesMeasuredBoundsLikeNestedModifiers() {
        val outerSize = Box().apply {
            modifier = Modifier
                .size(10f)
                .padding(2f)
        }
        val innerSize = Box().apply {
            modifier = Modifier
                .padding(2f)
                .size(10f)
        }

        listOf(outerSize, innerSize).forEach {
            it.measure(MeasureContext(FixedTextMeasurer()))
            it.layout(0f, 0f)
        }

        assertElementBounds(outerSize, x = 0f, y = 0f, width = 10f, height = 10f)
        assertElementBounds(innerSize, x = 0f, y = 0f, width = 14f, height = 14f)
    }

    @Test
    fun sizeInConstrainsGenericElementMeasurement() {
        val shrink = Box().apply {
            modifier = Modifier
                .sizeIn(maxWidth = 30f, maxHeight = 40f)
                .size(50f)
        }
        val expand = Box().apply {
            modifier = Modifier
                .sizeIn(minWidth = 20f, minHeight = 30f)
                .size(10f)
        }

        listOf(shrink, expand).forEach {
            it.measure(MeasureContext(FixedTextMeasurer()))
            it.layout(0f, 0f)
        }

        assertElementBounds(shrink, x = 0f, y = 0f, width = 30f, height = 40f)
        assertElementBounds(expand, x = 0f, y = 0f, width = 20f, height = 30f)
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
                    .clip(top.e404.tavolo.draw.compose.Shape.Circle)
                    .background(Color.RED)
            )
        }

        assertTrue(commands[1] is DrawCommand.Save)
        assertTrue(commands[2] is DrawCommand.ClipPath)
        assertTrue(commands[3] is DrawCommand.Rect)
        assertTrue(commands[4] is DrawCommand.Restore)
    }

    @Test
    fun roundedRectClipAndAntiAliasAreRecorded() {
        val commands = renderCommands {
            box(
                modifier = Modifier
                    .size(20f)
                    .antiAlias(false)
                    .clip(Shape.RoundedRect(4f))
                    .background(Color.RED)
            )
        }

        assertTrue(commands.any { it is DrawCommand.ClipPath })
        val rect = commands.filterIsInstance<DrawCommand.Rect>().single()
        assertEquals(false, rect.paint.antiAlias)
    }

    @Test
    fun wakatimeStyleProgressBarRecordsBackgroundAndForegroundBars() {
        val commands = renderCommands {
            box(Modifier.width(100f)) {
                box(
                    Modifier
                        .width(100f)
                        .height(10f)
                        .clip(Shape.RoundedRect(50f))
                        .background(Color.makeRGB(128, 128, 128))
                )
                box(
                    Modifier
                        .width(25f)
                        .height(10f)
                        .clip(Shape.RoundedRect(50f))
                        .background(Color.GREEN)
                )
            }
        }

        val rects = commands.filterIsInstance<DrawCommand.Rect>()
        assertEquals(2, rects.size)
        assertFloatEquals(100f, rects[0].rect.width)
        assertFloatEquals(25f, rects[1].rect.width)
        assertTrue(commands.filterIsInstance<DrawCommand.ClipPath>().size >= 2)
    }

    @Test
    fun dashedBorderRecordsStrokeLinesWithPathEffect() {
        val commands = renderCommands {
            box(
                Modifier
                    .size(20f)
                    .border(2f, Color.RED, StrokeStyle.Dashed(listOf(3f, 2f)))
            )
        }

        val lines = commands.filterIsInstance<DrawCommand.Line>()
        assertEquals(4, lines.size)
        lines.forEach {
            assertEquals(Color.RED, it.paint.color)
            assertEquals(PaintMode.STROKE, it.paint.mode)
            assertFloatEquals(2f, it.paint.strokeWidth)
            assertTrue(it.paint.hasPathEffect)
        }
    }

    @Test
    fun shapedBorderRecordsStrokePathWithPathEffect() {
        val commands = renderCommands {
            box(
                Modifier
                    .size(40f, 24f)
                    .border(
                        3f,
                        Color.RED,
                        strokeStyle = StrokeStyle.Dashed(listOf(6f, 3f)),
                        shape = Shape.RoundedRect(8f)
                    )
            )
        }

        assertEquals(0, commands.filterIsInstance<DrawCommand.Line>().size)
        val path = commands.filterIsInstance<DrawCommand.Path>().single()
        assertEquals(Color.RED, path.paint.color)
        assertEquals(PaintMode.STROKE, path.paint.mode)
        assertFloatEquals(3f, path.paint.strokeWidth)
        assertTrue(path.paint.hasPathEffect)
    }

    @Test
    fun shadowAndRotateWrapContainerDrawing() {
        val commands = renderCommands {
            box(
                Modifier
                    .size(20f, 10f)
                    .shadow(blurRadius = 4f, color = Color.BLACK, offsetX = 2f, offsetY = 3f)
                    .rotate(15f)
                    .background(Color.WHITE)
            )
        }

        val shadow = commands.filterIsInstance<DrawCommand.Path>().single()
        assertEquals(Color.BLACK, shadow.paint.color)
        assertTrue(shadow.paint.hasMaskFilter)
        assertTrue(commands.any { it is DrawCommand.Save })
        assertTrue(commands.any { it == DrawCommand.Rotate(15f) })
        assertTrue(commands.last() is DrawCommand.Restore)
        val rect = commands.filterIsInstance<DrawCommand.Rect>().single()
        assertEquals(Color.WHITE, rect.paint.color)
    }
}

