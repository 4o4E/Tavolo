package top.e404.tavolo.draw.test

import org.junit.Test
import top.e404.tavolo.draw.compose.*
import kotlin.test.*

class GithubMarkdownUnitTest {
    private fun parse(source: String, softBreak: MarkdownSoftBreak = MarkdownSoftBreak.SPACE) = GithubMarkdownParser.parse(source, softBreak)
    private fun commands(source: String, resolver: MarkdownImageResolver? = null): List<DrawCommand> =
        renderCommands(measureContext = MeasureContext(FixedTextMeasurer(6f))) {
            markdown(source, 360f, MarkdownStyle(fontSize = 12f, pagePadding = 8f, blockPadding = 6f), imageResolver = resolver)
        }

    @Test
    fun colonAliasesAndUnicodeEmojiRemainTextWithoutImageResources() {
        val source = ":rocket: :octocat: 🚀 👨‍👩‍👧‍👦"
        val rendered = commands(source).joinToString("") {
            when (it) {
                is DrawCommand.Text -> it.text
                is DrawCommand.ParagraphText -> it.text.replace("\u00a0", "")
                else -> ""
            }
        }
        assertEquals(source, rendered)
    }

    @Test
    fun dollarDelimitersRemainOrdinaryText() {
        assertEquals("余额 \$5，表达式 \$a+b\$。", parse("余额 \\$5，表达式 \$a+b\$。").text())
        assertEquals("\$\$ 未闭合的分隔符属于正文", parse("\$\$\n未闭合的分隔符属于正文").text())
    }

    @Test
    fun specializedFenceNamesPreserveCodeInsteadOfRenderingGraphics() {
        val source = "```mermaid\nflowchart LR\n A --> B\n```\n\n```math\n\\frac{1}{2}\n```"
        assertEquals(listOf("flowchart LR\n A --> B\n", "\\frac{1}{2}\n"),
            parse(source).select("pre code").map { it.wholeText() })
    }

    @Test
    fun preservesTaskStateAlertsAndRepeatedFootnoteReferences() {
        val doc = parse("- [x] 已完成\n- [ ] 未完成\n\n> [!WARNING]\n> 注意正文\n\n甲[^a]乙[^a]\n\n[^a]: 唯一脚注正文")
        val inputs = doc.select("input[type=checkbox]")
        assertEquals(listOf(true, false), inputs.map { it.hasAttr("checked") })
        assertTrue(doc.select(".markdown-alert-warning").text().contains("注意正文"))
        val references = doc.select("a[data-footnote-ref]")
        assertEquals(listOf("1", "1"), references.map { it.text() })
        assertEquals(1, Regex("唯一脚注正文").findAll(doc.text()).count())
    }

    @Test
    fun distinguishesReadmeAndCommentSoftBreaksFromHardBreaks() {
        assertEquals(1, parse("甲\n乙  \n丙").select("br").size)
        assertEquals(2, parse("甲\n乙  \n丙", MarkdownSoftBreak.NEWLINE).select("br").size)
        assertEquals("https://example.com", parse("https://example.com").select("a").single().attr("href"))
    }

    @Test
    fun emailAutolinksRespectGfmBoundariesWithoutRewritingExplicitLinks() {
        val doc = parse("hello@example.com。引用：后文\n\na@b.c- a@b.c_ final@host.net.\n\n[保留](mailto:用户@例子.com)")
        assertEquals(listOf("mailto:hello@example.com", "mailto:final@host.net", "mailto:用户@例子.com"),
            doc.select("a").map { it.attr("href") })
        assertEquals("hello@example.com", doc.select("a").first()!!.text())
        assertTrue(doc.text().contains("。引用：后文"))
        assertTrue(doc.text().contains("a@b.c- a@b.c_"))
    }

    @Test
    fun urlAutolinksStopAtChinesePunctuationWithoutChangingExplicitLinksOrChinesePaths() {
        val doc = parse("网址：https://example.com，以及 www.example.org。后文\n\n" +
            "（https://example.com/中文路径）「https://example.com/a?x=1&y=2」\n\n" +
            "[显式](https://example.com/中文，路径) 和 `<https://example.com/a，b>`")
        assertEquals(
            listOf("https://example.com", "http://www.example.org", "https://example.com/中文路径",
                "https://example.com/a?x=1&y=2", "https://example.com/中文，路径"),
            doc.select("a").map { it.attr("href") }
        )
        assertTrue(doc.text().contains("，以及 www.example.org。后文"))
        assertTrue(doc.text().contains("（https://example.com/中文路径）"))
        assertEquals("<https://example.com/a，b>", doc.select("code").single().text())
    }

    @Test
    fun expandsDetailsPreservesHtmlStylesAndDropsExecutableContent() {
        val output = commands("<details><summary>摘要</summary><p>正文<strong>粗体</strong></p></details>\n\n<script>secret()</script>")
        val text = output.filterIsInstance<DrawCommand.Text>()
        val rendered = text.joinToString("") { it.text }
        assertTrue(rendered.contains("摘要"))
        assertTrue(rendered.contains("正文粗体"))
        assertFalse(rendered.contains("secret"))
        assertTrue(text.single { it.text == "粗体" }.font.emboldened)
    }

    @Test
    fun mergedTableCellsKeepContentAndImageDimensionsUseResolver() {
        val output = commands("<table><tr><th colspan=2>合并</th></tr><tr><td>A</td><td>B</td></tr></table>")
        val words = output.filterIsInstance<DrawCommand.Text>()
        assertEquals("合并AB", words.joinToString("") { it.text })
        assertTrue(words.single { it.text == "A" }.x < words.single { it.text == "B" }.x)
        testImage(120, 60).use { image ->
            val draws = commands("<p><img src='fixture:x' width='40'>尾</p>") { destination ->
                assertEquals("fixture:x", destination)
                image
            }.filterIsInstance<DrawCommand.ImageRect>()
            assertEquals(40f, draws.single().dst.width)
            assertEquals(20f, draws.single().dst.height)
        }
    }

}
