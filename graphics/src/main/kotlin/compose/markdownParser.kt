package top.e404.tavolo.draw.compose

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.autolink.AutolinkType
import org.commonmark.ext.footnotes.FootnotesExtension
import org.commonmark.ext.gfm.alerts.AlertsExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Link
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.parser.SourceLine
import org.commonmark.parser.beta.*
import org.commonmark.parser.block.*
import org.commonmark.renderer.html.HtmlRenderer
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities

/** CommonMark 负责 Markdown，jsoup 负责 HTML 树；不能用正则替换嵌套 HTML。 */
internal object GithubMarkdownParser {
    private val extensions = listOf(
        TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create(),
        AlertsExtension.create(), AutolinkExtension.builder().linkTypes(AutolinkType.URL, AutolinkType.WWW).build(),
        FootnotesExtension.create()
    )
    private val emoji: Map<String, String> = Json.parseToJsonElement(
        requireNotNull(javaClass.getResource("/markdown/emoji.json")).readText()
    ).jsonObject.mapValues { it.value.jsonPrimitive.content }
    private val imageEmoji: Map<String, String> = Json.parseToJsonElement(
        requireNotNull(javaClass.getResource("/markdown/image-emoji.json")).readText()
    ).jsonObject.mapValues { it.value.jsonPrimitive.content }
    // 通用 URL 库允许国际化邮箱；GFM 扩展邮箱使用 ASCII 文法，中文标点必须留在链接外。
    private val email = Regex("(?<![A-Za-z0-9._+\\-])(?:(?:mailto|xmpp):)?[A-Za-z0-9._+\\-]+@[A-Za-z0-9_\\-]+(?:\\.[A-Za-z0-9_\\-]+)+(?![A-Za-z0-9_+\\-])")
    // 中文标点是正文分隔符；中文域名和路径本身仍允许由自动链接库识别。
    private val chinesePunctuation = Regex("[，。！？；：、（）【】《》〈〉「」『』“”‘’…]")
    private val parser = Parser.builder()
        .postProcessor { root ->
            root.accept(object : AbstractVisitor() {
                override fun visit(link: Link) {}
                override fun visit(image: org.commonmark.node.Image) {}
                override fun visit(text: Text) {
                    val literal = text.literal
                    var start = 0
                    chinesePunctuation.findAll(literal).forEach { match ->
                        if (match.range.first > start) text.insertBefore(Text(literal.substring(start, match.range.first)))
                        text.insertBefore(Text(match.value))
                        start = match.range.last + 1
                    }
                    if (start > 0) {
                        if (start < literal.length) text.insertBefore(Text(literal.substring(start)))
                        text.unlink()
                    }
                }
            })
            root
        }
        .extensions(extensions)
        .customInlineContentParserFactory(object : InlineContentParserFactory {
            override fun getTriggerCharacters() = setOf('$', ':')
            override fun create() = InlineContentParser { state ->
                val scanner = state.scanner()
                if (scanner.peek() == ':') parseEmoji(scanner) else parseMath(scanner)
            }
        })
        .customBlockParserFactory(MathBlockFactory())
        .postProcessor { root ->
            root.accept(object : AbstractVisitor() {
                override fun visit(link: Link) {} // 不改写显式链接及 URL。
                override fun visit(image: org.commonmark.node.Image) {}
                override fun visit(text: Text) {
                    val literal = text.literal
                    var end = 0
                    email.findAll(literal).forEach { match ->
                        if (match.range.first < end || match.value.last() in "-_") return@forEach
                        if (match.range.first > end) text.insertBefore(Text(literal.substring(end, match.range.first)))
                        end = match.range.last + 1
                        if (match.value.startsWith("xmpp:") && literal.getOrNull(end) == '/') {
                            end++
                            while (end < literal.length && (literal[end] in 'a'..'z' || literal[end] in 'A'..'Z' ||
                                    literal[end] in '0'..'9' || literal[end] in "@.")) end++
                        }
                        val label = literal.substring(match.range.first, end)
                        val target = if (label.startsWith("mailto:") || label.startsWith("xmpp:")) label else "mailto:$label"
                        text.insertBefore(Link(target, null).apply { appendChild(Text(label)) })
                    }
                    if (end > 0) {
                        if (end < literal.length) text.insertBefore(Text(literal.substring(end)))
                        text.unlink()
                    }
                }
            })
            root
        }
        .build()
    private val renderer = HtmlRenderer.builder().extensions(extensions).build()

    fun parse(source: String, softBreak: MarkdownSoftBreak): Element {
        val node = parser.parse(source)
        val html = if (softBreak == MarkdownSoftBreak.NEWLINE) {
            HtmlRenderer.builder().extensions(extensions).softbreak("<br />\n").build().render(node)
        } else renderer.render(node)
        return Jsoup.parseBodyFragment(html).body().apply {
            // 输入只解释静态文档语义；脚本、样式、嵌入页面不会执行或触发资源读取。
            select("script,style,iframe,object,embed,link,meta,form,button,textarea,select").remove()
        }
    }

    private fun parseEmoji(scanner: Scanner): ParsedInline? {
        scanner.next()
        val alias = StringBuilder()
        while (scanner.hasNext()) {
            val ch = scanner.peek()
            if (ch == ':') {
                scanner.next()
                val name = alias.toString()
                emoji[name]?.let { return ParsedInline.of(Text(it), scanner.position()) }
                val url = imageEmoji[name] ?: return ParsedInline.none()
                return ParsedInline.of(HtmlInline().apply {
                    literal = "<img class=\"tavolo-emoji\" src=\"${Entities.escape(url)}\" alt=\":$name:\">"
                }, scanner.position())
            }
            if (!(ch.isLetterOrDigit() || ch in "_+-")) return ParsedInline.none()
            alias.append(ch)
            scanner.next()
        }
        return ParsedInline.none()
    }

    private fun parseMath(scanner: Scanner): ParsedInline? {
        scanner.next()
        val block = scanner.next('$')
        val backtick = !block && scanner.next('`')
        if (!block && !backtick && scanner.peek().isWhitespace()) return ParsedInline.none()
        val source = StringBuilder()
        while (scanner.hasNext()) {
            val ch = scanner.peek()
            if (ch == '\\') {
                source.append(ch)
                scanner.next()
                if (scanner.hasNext()) {
                    source.append(scanner.peek())
                    scanner.next()
                }
                continue
            }
            val closing = if (backtick) "`$" else if (block) "$$" else "$"
            if (scanner.next(closing)) {
                if (source.isEmpty() || (!block && !backtick && source.last().isWhitespace())) return ParsedInline.none()
                val html = "<tavolo-math display=\"$block\">${Entities.escape(source.toString())}</tavolo-math>"
                return ParsedInline.of(HtmlInline().apply { literal = html }, scanner.position())
            }
            if (ch == '\n' && !block && !backtick) return ParsedInline.none()
            source.append(ch)
            scanner.next()
        }
        return ParsedInline.none()
    }
}

private class MathBlockFactory : AbstractBlockParserFactory() {
    override fun tryStart(state: ParserState, matched: MatchedBlockParser): BlockStart? {
        val text = state.line.content.toString()
        val start = state.nextNonSpaceIndex
        if (state.indent >= 4 || !text.startsWith("$$", start)) return BlockStart.none()
        return BlockStart.of(MathBlockParser()).atIndex(start + 2)
    }
}

private class MathBlockParser : AbstractBlockParser() {
    private val node = FencedCodeBlock().apply { info = "math" }
    private val source = StringBuilder()
    private var closed = false
    override fun getBlock() = node
    override fun tryContinue(state: ParserState): BlockContinue? =
        if (closed) BlockContinue.none() else BlockContinue.atIndex(state.index)
    override fun addLine(line: SourceLine) {
        val text = line.content.toString()
        val trimmed = text.trimEnd()
        if (trimmed.endsWith("$$") && !trimmed.endsWith("\\$$")) {
            source.append(trimmed.dropLast(2))
            closed = true
        } else source.append(text).append('\n')
    }
    override fun closeBlock() {
        require(closed) { "Markdown 块级公式缺少结束的 $$" }
        node.literal = source.toString().trim('\n')
    }
}

/** GitHub Markdown 文件合并软换行，Issue/评论保留软换行；硬换行始终保留。 */
enum class MarkdownSoftBreak { SPACE, NEWLINE }
