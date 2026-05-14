package top.e404.tavolo.draw.test

import org.jetbrains.skia.*
import org.junit.Test
import top.e404.tavolo.draw.compose.*
import top.e404.tavolo.util.Colors
import top.e404.tavolo.util.FontManager
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ComposeDslAndRenderUnitTest {
    @Test
    fun dslEntriesBuildExpectedTree() {
        val root = Column()

        root.apply {
            column(Modifier.width(20f)) {
                row(Modifier.height(10f)) {
                    box(Modifier.size(5f))
                }
            }
            table(Modifier.padding(horizontal = 1f, vertical = 2f), columnSpacing = 1f, rowSpacing = 1f) {
                tableRow {
                    cell { text("x") }
                }
            }
        }

        assertEquals(2, root.children.size)
        assertIs<Column>(root.children[0])
        assertIs<Table>(root.children[1])
    }

    @Test
    fun iconTextRequiresFontSizeAndBuildsRow() {
        val fontName = FontManager.register("unit-icon-text-empty", Typeface.makeEmpty())
        val commands = renderCommands(measureContext = MeasureContext(FixedTextMeasurer())) {
            iconText("ok", fontSize = 20f, fontFamily = fontName)
        }

        val clipIndex = commands.indexOfFirst { it is DrawCommand.ClipPath }
        val iconRectIndex = commands.indexOfFirst { it is DrawCommand.Rect && it.paint.color == Colors.LIGHT_BLUE.argb }
        assertTrue(clipIndex >= 0, "应记录 iconText 图标裁剪命令")
        assertTrue(iconRectIndex > clipIndex, "图标背景应在裁剪之后绘制")
        assertEquals("ok", commands.filterIsInstance<DrawCommand.Text>().single().text)
    }

    @Test
    fun invalidRenderSizeThrowsAndOverflowChildrenAreNormalized() {
        val error = assertFailsWith<IllegalStateException> {
            renderCommands(measureContext = MeasureContext(FixedTextMeasurer())) {}
        }
        assertTrue(error.message!!.contains("计算尺寸无效"))

        val root = Column().apply {
            modifier = Modifier.size(10f, 10f)
        }
        val commands = renderCommands(root = root) {
            val element = CanvasElement(20f, 30f) { canvas ->
                canvas.drawRect(Rect.makeXYWH(parentX, parentY, width, height), Paint())
            }
            add(element)
        }

        val rect = commands.filterIsInstance<DrawCommand.Rect>().single()
        assertFloatEquals(10f, rect.rect.width)
        assertFloatEquals(10f, rect.rect.height)
    }

    @Test
    fun renderAndSkiaBackendsAreExercised() {
        val image = render(backgroundColor = Color.WHITE) {
            box(Modifier.size(2f))
        }
        assertTrue(image.width > 0)
        assertTrue(image.height > 0)

        val font = Font(Typeface.makeEmpty(), 12f)
        val paint = Paint()
        assertFloatEquals(font.measureTextWidth("abc", paint), SkiaTextMeasurer.measureTextWidth("abc", font, paint))
        assertFloatEquals(font.metrics.descent - font.metrics.ascent, SkiaTextMeasurer.metrics(font).lineHeight)

        Surface.makeRasterN32Premul(20, 20).use { surface ->
            val canvas = SkiaDrawCanvas(surface.canvas)
            val path = Path().apply { moveTo(0f, 0f); lineTo(10f, 10f) }
            val skiaImage = testImage(2, 2)
            canvas.clear(Color.TRANSPARENT)
            canvas.save()
            canvas.translate(1f, 1f)
            canvas.rotate(0f)
            canvas.scale(1f, 1f)
            canvas.clipPath(path)
            canvas.drawRect(Rect.makeXYWH(0f, 0f, 1f, 1f), paint)
            canvas.drawString("x", 0f, 10f, font, paint)
            canvas.drawImageRect(skiaImage, Rect.makeXYWH(0f, 0f, 2f, 2f), Rect.makeXYWH(0f, 0f, 2f, 2f), paint)
            canvas.drawPath(path, paint)
            canvas.drawArc(0f, 0f, 10f, 10f, 0f, 90f, true, paint)
            canvas.drawCircle(5f, 5f, 2f, paint)
            canvas.drawLine(0f, 0f, 10f, 10f, paint)
            canvas.restore()
        }
    }

    @Test
    fun debugBaseElementPrintsTreeAndModifiers() {
        val root = Column()
        root.apply {
            text("x", Modifier.padding(1f), fontSize = 12f)
        }
        root.measure(MeasureContext(FixedTextMeasurer()))
        root.layout(0f, 0f)

        val output = buildString { debugBaseElement(0, root, this) }

        assertTrue(output.contains("children:"))
        assertTrue(output.contains("modifiers:"))
        assertTrue(output.contains("contentWidth:"))
    }
}
