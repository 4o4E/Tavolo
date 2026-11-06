package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.junit.Test
import top.e404.tavolo.draw.compose.*
import top.e404.tavolo.util.FontManager
import top.e404.tavolo.util.toBitmap
import kotlin.math.roundToInt
import kotlin.test.*

class MarkdownInlineLayoutUnitTest {
    private fun style() = MarkdownStyle(fontFamily = FontManager.systemFamilies().first(), pagePadding = 8f)

    @Test
    fun inlineCodePaddingAffectsLayoutAndBackgroundHasRoundedCorners() {
        val style = style()
        fun paragraph(padding: Float) = MarkdownParagraph(listOf(
            MarkdownInlinePart.Words("before", TextSpanStyle()),
            MarkdownInlinePart.Words("code", TextSpanStyle(textColor = Color.TRANSPARENT, backgroundColor = Color.RED,
                backgroundRadius = 6f, backgroundPaddingHorizontal = padding, backgroundPaddingVertical = 4f)),
            MarkdownInlinePart.Words("after", TextSpanStyle())
        ), 400f, style)
        val plain = paragraph(0f).apply { measure(MeasureContext()) }
        val padded = paragraph(8f).apply { measure(MeasureContext()) }
        assertEquals(16f, padded.width - plain.width, 0.1f, "两侧内边距必须占据排版宽度，而不是覆盖相邻文字")
        render(Color.WHITE) { add(padded) }.use { image ->
            image.toBitmap().use { bitmap ->
                var left = bitmap.width
                var top = bitmap.height
                var right = -1
                for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                    if (bitmap.getColor(x, y) == Color.RED) {
                        left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y)
                    }
                }
                assertEquals(Color.RED, bitmap.getColor((left + right) / 2, top))
                assertNotEquals(Color.RED, bitmap.getColor(left, top), "背景外接矩形的角必须留出圆角")
            }
        }
    }

    @Test
    fun taskMarkersHaveFixedSquareSizeAndPaintCheckedState() {
        val style = style()
        val source = "- [x] done\n- [ ] todo"
        val markers = renderCommands { markdown(source, 200f, style) }.filterIsInstance<DrawCommand.Svg>()
        assertEquals(listOf(14f, 14f), markers.map { it.dst.width })
        assertEquals(listOf(14f, 14f), markers.map { it.dst.height })
        renderMarkdown(source, 200f, style, pixelRatio = 1f).use { image ->
            image.toBitmap().use { bitmap ->
                val colors = markers.map { marker ->
                    bitmap.getColor((marker.dst.left + marker.dst.width * 0.3f).roundToInt(),
                        (marker.dst.top + marker.dst.height * 0.3f).roundToInt())
                }
                assertEquals(listOf(style.linkColor, style.backgroundColor), colors)
            }
        }
    }

    @Test
    fun pixelRatioChangesRasterResolutionWithoutChangingLogicalLayout() {
        val root = Column()
        render(Color.WHITE, root = root, pixelRatio = 2f) {
            box(Modifier.size(20f, 10f)) { box(Modifier.size(5f).background(Color.RED)) }
        }.use { image ->
            assertEquals(20f, root.width)
            assertEquals(10f, root.height)
            assertEquals(40, image.width)
            assertEquals(20, image.height)
            image.toBitmap().use { bitmap ->
                assertEquals(Color.RED, bitmap.getColor(9, 9))
                assertEquals(Color.WHITE, bitmap.getColor(10, 10))
            }
        }
        assertFailsWith<IllegalArgumentException> { render(pixelRatio = 0f) { box(Modifier.size(1f)) } }
        assertFailsWith<IllegalArgumentException> { render(pixelRatio = Float.NaN) { box(Modifier.size(1f)) } }
    }
}
