package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.junit.Test
import top.e404.tavolo.draw.compose.*
import kotlin.test.*

class MarkdownUnitTest {
    private val style = MarkdownStyle(fontSize = 12f, pagePadding = 8f, blockPadding = 6f)
    private fun commands(source: String, width: Float = 400f, resolver: MarkdownImageResolver? = null) =
        renderCommands(measureContext = MeasureContext(FixedTextMeasurer(charWidth = 6f))) {
            markdown(source, width, style, imageResolver = resolver)
        }
    private fun text(commands: List<DrawCommand>) = commands.joinToString("") {
        when (it) {
            is DrawCommand.Text -> it.text
            is DrawCommand.ParagraphText -> it.text.replace("\u00a0", "")
            else -> ""
        }
    }

    @Test
    fun preservesNestedListStartAndLinkLabelsAcrossInlineFormatting() {
        val commands = commands("7. ***粗斜体***与`代码`\n   - 子项\n8. [文字](https://example.com) ~~删除~~")
        assertEquals("7.粗斜体与代码•子项8.文字 删除", text(commands))
        assertTrue(commands.filterIsInstance<DrawCommand.Line>().any { it.paint.color == style.textColor })
    }

    @Test
    fun preservesCodeCharactersAcrossWrappingAndNestedContainers() {
        val literal = "  alpha  `beta`   gamma_delta_epsilon_zeta_eta_theta"
        val output = commands("> ~~~text\n> $literal\n> ~~~", width = 200f)
        assertEquals(literal, text(output))
        output.filterIsInstance<DrawCommand.Text>().forEach {
            assertTrue(it.x >= 8f)
            assertTrue(it.x + it.text.length * 6f <= 200f, "代码文字越过右边界: ${it.text}")
        }
    }

    @Test
    fun tableRetainsCellOrderAndWrapsWithinAvailableWidth() {
        val output = commands("| 甲 | 乙 |\n| --- | --- |\n| 很长很长很长很长很长很长 | 末尾 |", 220f)
        assertEquals("甲乙很长很长很长很长很长很长末尾", text(output))
        output.filterIsInstance<DrawCommand.Text>().forEach {
            assertTrue(it.x + it.text.length * 6f <= 220f)
        }
    }

    @Test
    fun requiresExplicitImageResolverAndPreservesResourceFailures() {
        assertFailsWith<IllegalArgumentException> { commands("![图片](file:///secret)") }
        val failure = IllegalStateException("调用方资源错误")
        assertSame(failure, assertFailsWith<IllegalStateException> {
            commands("![图片](resource:id)", resolver = { throw failure })
        })
        testImage(600, 300).use { image ->
            val output = commands("前\n\n![图片](resource:id)\n\n后", 200f) { image }
            val draw = output.filterIsInstance<DrawCommand.ImageRect>().single()
            assertFloatEquals(184f, draw.dst.right - draw.dst.left)
            assertFloatEquals(92f, draw.dst.bottom - draw.dst.top)
            assertEquals("前后", text(output))
        }
    }

    @Test
    fun embeddedWidthIncludesModifierPaddingAndRejectsClippedHeight() {
        val root = Column()
        val output = renderCommands(root = root, measureContext = MeasureContext(FixedTextMeasurer(6f))) {
            markdown("引用中的长内容".repeat(8), 800f, style,
                modifier = Modifier.width(200f).padding(10f))
        }
        assertFloatEquals(200f, root.width)
        output.filterIsInstance<DrawCommand.Text>().forEach {
            assertTrue(it.x + it.text.length * 6f <= 190f)
        }
        assertFailsWith<IllegalArgumentException> {
            renderCommands(measureContext = MeasureContext(FixedTextMeasurer())) {
                markdown("完整内容", 200f, style, modifier = Modifier.height(1f))
            }
        }
    }

    @Test
    fun rejectsEmptyDocumentsAndWidthsThatCannotFitContent() {
        assertFailsWith<IllegalArgumentException> { commands(" ") }
        assertFailsWith<IllegalArgumentException> { commands("文字", 10f) }
    }
}
