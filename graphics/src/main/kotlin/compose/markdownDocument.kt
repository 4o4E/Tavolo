package top.e404.tavolo.draw.compose

import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import org.jetbrains.skia.paragraph.Alignment
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal class MarkdownDocumentLayout(
    private val style: MarkdownStyle,
    private val imageResolver: MarkdownImageResolver?,
    private val documentContext: MarkdownContext
) {
    private val images = mutableMapOf<String, Image>()
    private val highlighted = mutableMapOf<Pair<String, String>, AnnotatedText>()
    private val blocks = setOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "pre", "blockquote", "ul", "ol", "table", "hr", "details", "summary", "dl", "dt", "dd", "figure", "figcaption", "caption", "section")
    private val spaces = Regex("[\\t\\r\\n ]+")
    private val references = Regex("(?<![\\w/])(?:@[A-Za-z0-9][A-Za-z0-9_/-]*|(?:[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)?#[0-9]+)(?![\\w])")

    fun requireWidth(width: Float) {
        require(width.isFinite() && width >= style.fontSize * 2) { "Markdown 可用宽度不足，请增大宽度或减小字号、缩进及表格列数" }
    }
    private val mutedColor = if (style.darkTheme) Color.makeRGB(145, 152, 161) else Color.makeRGB(89, 99, 110)
    private val inlineCodeColor = if (style.darkTheme) Color.makeARGB(102, 110, 118, 129) else Color.makeARGB(51, 175, 184, 193)

    fun appendBlocks(parent: UiElement, nodes: List<Node>, width: Float, context: MeasureContext,
                     initial: TextSpanStyle = TextSpanStyle(), blockSpacing: Float = style.blockSpacing) {
        requireWidth(width)
        val inline = mutableListOf<Node>()
        var hasContent = false
        fun separate(heading: Boolean = false) {
            // 仅在相邻块之间留白，避免列表、表格、引用的嵌套容器累加尾部间距。
            if (hasContent) parent.box(Modifier.height(blockSpacing * if (heading) 1.5f else 1f))
            hasContent = true
        }
        fun flush() {
            if (inline.any { it !is TextNode || it.wholeText.isNotBlank() }) {
                separate()
                appendInline(parent, inline, width, context, initial)
            }
            inline.clear()
        }
        nodes.forEach { node ->
            if (node !is Element || node.normalName() !in blocks) {
                inline += node
                return@forEach
            }
            flush()
            val tag = node.normalName()
            val align = alignment(node)
            separate(tag.length == 2 && tag[0] == 'h' && tag[1] in '1'..'6')
            when (tag) {
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    val scale = when (tag) { "h1" -> 2f; "h2" -> 1.5f; "h3" -> 1.25f; "h4" -> 1f; "h5" -> 0.875f; else -> 0.85f }
                    val heading = initial.merge(TextSpanStyle(fontSize = style.fontSize * scale, fontWeight = 600,
                        textColor = if (tag == "h6") mutedColor else null))
                    if (tag == "h1" || tag == "h2") {
                        parent.column(Modifier.width(width).border(bottom = 1f, color = style.tableBorderColor)
                            .padding(bottom = style.fontSize * scale * 0.3f)) {
                            appendInline(this, node.childNodes(), width, context, heading, align, lineHeight = 1.25f)
                        }
                    } else appendInline(parent, node.childNodes(), width, context, heading, align, lineHeight = 1.25f)
                }
                "p", "summary", "dt", "figcaption", "caption" -> {
                    val alertTitle = node.hasClass("markdown-alert-title")
                    val alert = node.parent()?.classNames()?.firstOrNull { it.startsWith("markdown-alert-") }
                    val span = when {
                        alertTitle -> initial.merge(TextSpanStyle(fontWeight = 600, textColor = alertColor(alert.orEmpty().removePrefix("markdown-alert-"))))
                        tag == "summary" || tag == "dt" -> initial.merge(TextSpanStyle(fontWeight = 600))
                        else -> initial
                    }
                    val content = if (tag == "summary") listOf(TextNode("▾ ")) + node.childNodes() else node.childNodes()
                    appendInline(parent, content, width, context, span, align)
                }
                "blockquote" -> parent.column(Modifier.border(left = 4f, color = style.quoteColor).padding(horizontal = style.blockPadding)) {
                    appendBlocks(this, node.childNodes(), width - style.blockPadding * 2 - 4f, context,
                        initial.merge(TextSpanStyle(textColor = mutedColor)))
                }
                "ul", "ol" -> appendList(parent, node, width, context, initial)
                "pre" -> appendCode(parent, node, width)
                "table" -> appendTable(parent, node, width, context)
                "hr" -> parent.box(Modifier.size(width, 4f).background(style.tableBorderColor))
                "details" -> parent.column(Modifier.width(width)) {
                    // 静态图片无法点击展开，始终保留摘要和全部正文。
                    appendBlocks(this, node.childNodes(), width, context, initial)
                }
                "dd" -> parent.column(Modifier.padding(left = style.blockPadding)) {
                    appendBlocks(this, node.childNodes(), width - style.blockPadding, context, initial)
                }
                "div" -> {
                    val alert = node.classNames().firstOrNull { it.startsWith("markdown-alert-") && it != "markdown-alert-title" }
                    if (alert != null) {
                        val color = alertColor(alert.removePrefix("markdown-alert-"))
                        parent.column(Modifier.width(width).border(left = 4f, color = color)
                            .padding(horizontal = style.blockPadding, vertical = style.blockPadding * 0.5f)) {
                            appendBlocks(this, node.childNodes(), width - 4 - style.blockPadding * 2, context, initial)
                        }
                    } else parent.column(Modifier.width(width), horizontalAlignment = horizontal(align)) {
                        appendBlocks(this, node.childNodes(), width, context, initial)
                    }
                }
                else -> appendBlocks(parent, node.childNodes(), width, context, initial)
            }
        }
        flush()
    }

    private fun appendList(parent: UiElement, list: Element, width: Float, context: MeasureContext, initial: TextSpanStyle) {
        val items = list.children().filter { it.normalName() == "li" }
        var index = list.attr("start").toLongOrNull() ?: 1L
        data class Marker(val element: UiElement, val task: Boolean)
        val markers = items.map { item ->
            val checkbox = item.children().firstOrNull { it.normalName() == "input" }
                ?: item.children().firstOrNull { it.normalName() == "p" }?.children()?.firstOrNull { it.normalName() == "input" }
            val marker = if (checkbox != null) {
                val checked = checkbox.hasAttr("checked")
                val color = "#%06x".format((if (checked) style.linkColor else mutedColor) and 0xffffff)
                val check = if (checked) """<path d="M3.2 7 5.7 9.5 10.8 4.4" fill="none" stroke="white" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>""" else ""
                val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 14 14"><rect x=".75" y=".75" width="12.5" height="12.5" rx="2" fill="${if (checked) color else "none"}" stroke="$color" stroke-width="1.5"/>$check</svg>"""
                SvgElement(svg.encodeToByteArray()).apply { modifier = Modifier.size(style.fontSize * 0.875f) }
            } else {
                val label = if (list.normalName() == "ol") {
                    index = item.attr("value").toLongOrNull() ?: index
                    "${index++}."
                } else "•"
                Text(label, fontSize = style.fontSize, fontFamily = style.fontFamily, textColor = style.textColor,
                    style = TextStyle(lineHeight = style.fontSize * style.lineHeight))
            }
            marker.measure(context)
            Marker(marker, checkbox != null)
        }
        if (items.isEmpty()) return
        val markerWidth = markers.maxOf { it.element.width } + if (markers.any { it.task }) style.fontSize * 0.5f else style.blockPadding
        requireWidth(width - markerWidth)
        items.forEachIndexed { itemIndex, item ->
            if (itemIndex > 0) parent.box(Modifier.height(style.fontSize * 0.25f))
            val body = Column()
            val spacing = if (item.children().any { it.normalName() == "p" }) style.blockSpacing else style.fontSize * 0.25f
            appendBlocks(body, item.childNodes(), width - markerWidth, context, initial, spacing)
            val marker = markers[itemIndex]
            if (marker.task) parent.add(MarkdownTaskRow(marker.element, body, markerWidth, style))
            else parent.row {
                box(Modifier.width(markerWidth)) { add(marker.element) }
                add(body)
            }
        }
    }

    private fun appendCode(parent: UiElement, pre: Element, width: Float) {
        val code = pre.children().firstOrNull { it.normalName() == "code" } ?: pre
        val language = code.classNames().firstOrNull { it.startsWith("language-") }?.removePrefix("language-")?.lowercase().orEmpty()
        val source = code.wholeText()
        val inner = width - style.blockPadding * 2
        requireWidth(inner)
        val content = source.removeSuffix("\n")
        val highlightedCode = highlighted.getOrPut(language to content) {
            MarkdownCodeHighlighter.highlight(content, language, style.darkTheme)
        }
        // 代码整体也是一个样式区间：无语言／未知语言同样按字符布局，保留缩进和连续空格。
        val annotated = buildAnnotatedText {
            withStyle(TextSpanStyle(fontFamily = style.codeFontFamily, fontSize = style.fontSize * 0.85f)) {
                append(highlightedCode)
            }
        }
        parent.column(Modifier.width(width).clip(Shape.RoundedRect(6f)).background(style.codeBackgroundColor).padding(style.blockPadding)) {
            text(annotated, Modifier.sizeIn(maxWidth = inner), style = TextStyle(
                fontFamily = style.codeFontFamily, fontSize = style.fontSize * 0.85f,
                textColor = style.textColor, lineHeight = style.fontSize * 0.85f * 1.45f))
        }
    }

    private fun appendInline(parent: UiElement, nodes: List<Node>, width: Float, context: MeasureContext,
                             initial: TextSpanStyle = TextSpanStyle(), align: Alignment = Alignment.LEFT,
                             preserveWhitespace: Boolean = false, lineHeight: Float = style.lineHeight) {
        val parts = mutableListOf<MarkdownInlinePart>()
        fun words(text: String, span: TextSpanStyle, underline: Boolean = false, literal: Boolean = false) {
            if (text.isNotEmpty()) parts += MarkdownInlinePart.Words(text, span, underline, literal)
        }
        fun flush() {
            if (parts.isEmpty()) return
            if (!preserveWhitespace) trimSpaces(parts)
            if (parts.isNotEmpty()) {
                if (parts.all { it is MarkdownInlinePart.Words && !it.underline && it.style.backgroundRadius == null } && align == Alignment.LEFT) {
                    val annotated = buildAnnotatedText {
                        parts.forEach { part -> part as MarkdownInlinePart.Words; withStyle(part.style) { append(part.text) } }
                    }
                    parent.text(annotated, Modifier.sizeIn(maxWidth = width), style = TextStyle(
                        fontSize = style.fontSize, fontFamily = style.fontFamily, textColor = style.textColor,
                        lineHeight = parts.maxOf { (it as MarkdownInlinePart.Words).style.fontSize ?: style.fontSize } * lineHeight
                    ))
                } else parent.add(MarkdownParagraph(parts.toList(), width, style, align, lineHeight))
            }
            parts.clear()
        }
        fun append(node: Node, span: TextSpanStyle, underline: Boolean = false, literal: Boolean = preserveWhitespace) {
            if (node is TextNode) {
                val value = if (literal) node.wholeText else spaces.replace(node.wholeText, " ")
                val resolver = documentContext.referenceResolver
                if (resolver == null || literal) words(value, span, underline, literal)
                else {
                    var end = 0
                    references.findAll(value).forEach { match ->
                        words(value.substring(end, match.range.first), span, underline)
                        val target = resolver(match.value)
                        words(match.value,
                            if (target == null) span else span.merge(TextSpanStyle(textColor = style.linkColor)), underline)
                        end = match.range.last + 1
                    }
                    words(value.substring(end), span, underline)
                }
                return
            }
            if (node !is Element) return
            fun children(next: TextSpanStyle = span, nextUnderline: Boolean = underline, nextLiteral: Boolean = literal) =
                node.childNodes().forEach { append(it, next, nextUnderline, nextLiteral) }
            when (node.normalName()) {
                "br" -> words("\n", span, underline)
                "wbr" -> words("\u200b", span, underline)
                "b", "strong" -> children(span.merge(TextSpanStyle(fontWeight = 600)))
                "i", "em", "var", "cite", "dfn" -> children(span.merge(TextSpanStyle(italic = true)))
                "del", "s", "strike" -> children(span.merge(TextSpanStyle(strikethrough = true)))
                "ins" -> children(nextUnderline = true)
                "mark" -> children(span.merge(TextSpanStyle(backgroundColor =
                    if (style.darkTheme) Color.makeRGB(105, 80, 15) else Color.makeRGB(255, 248, 197))))
                "small" -> children(span.merge(TextSpanStyle(fontSize = (span.fontSize ?: style.fontSize) * 0.9f)))
                "code", "tt", "kbd", "samp" -> children(span.merge(TextSpanStyle(fontFamily = style.codeFontFamily,
                    fontSize = (span.fontSize ?: style.fontSize) * 0.85f,
                    backgroundColor = inlineCodeColor, backgroundRadius = 6f,
                    backgroundPaddingHorizontal = style.fontSize * 0.4f,
                    backgroundPaddingVertical = style.fontSize * 0.2f)), nextLiteral = true)
                "q" -> { words("“", span); children(); words("”", span) }
                "sub", "sup" -> {
                    val size = span.fontSize ?: style.fontSize
                    val target = Column()
                    appendInline(target, node.childNodes(), width, context, span.merge(TextSpanStyle(fontSize = size * 0.75f)))
                    target.measure(context)
                    val shift = if (node.normalName() == "sup") size * 0.4f else -size * 0.2f
                    parts += MarkdownInlinePart.Object(target, target.height * 0.75f + shift)
                }
                "img" -> parts += imagePart(node, width)
                "picture" -> {
                    val source = node.children().firstOrNull { it.normalName() == "source" && matchesMedia(it.attr("media")) }
                    val img = requireNotNull(node.children().firstOrNull { it.normalName() == "img" }) { "Markdown picture 缺少 img" }.clone()
                    if (source != null) img.attr("src", source.attr("srcset").substringBefore(',').trim().substringBefore(' '))
                    parts += imagePart(img, width)
                }
                "a" -> {
                    if (node.hasClass("footnote-backref")) return
                    val next = span.merge(TextSpanStyle(textColor = style.linkColor))
                    children(next)
                    val destination = node.attr("href")
                    // 脚注通过编号对应文末注释，普通静态链接同时显示文案和目标。
                    if (destination.isNotEmpty() && node.text() != destination && !node.hasClass("footnote-ref")) {
                        words(" ($destination)", next)
                    }
                }
                "input", "source", "rp" -> Unit
                "ruby" -> {
                    val base = node.childNodes().filter { it !is Element || it.normalName() !in setOf("rt", "rp") }
                    val annotation = node.children().filter { it.normalName() == "rt" }.flatMap { it.childNodes() }
                    val target = Column(HorizontalAlignment.Center)
                    appendInline(target, annotation, width, context, span.merge(TextSpanStyle(fontSize = (span.fontSize ?: style.fontSize) * 0.5f)))
                    appendInline(target, base, width, context, span)
                    target.measure(context)
                    parts += MarkdownInlinePart.Object(target, target.height - (span.fontSize ?: style.fontSize) * 0.3f)
                }
                "bdo" -> {
                    words(if (node.attr("dir") == "rtl") "\u202e" else "\u202d", span)
                    children(); words("\u202c", span)
                }
                else -> children()
            }
        }
        nodes.forEach { append(it, initial) }
        flush()
    }

    private fun trimSpaces(parts: MutableList<MarkdownInlinePart>) {
        while (parts.firstOrNull() is MarkdownInlinePart.Words) {
            val first = parts.first() as MarkdownInlinePart.Words
            if (first.preserveWhitespace) break
            val trimmed = first.text.trimStart(' ')
            if (trimmed.isEmpty()) parts.removeAt(0) else { parts[0] = first.copy(text = trimmed); break }
        }
        while (parts.lastOrNull() is MarkdownInlinePart.Words) {
            val last = parts.last() as MarkdownInlinePart.Words
            if (last.preserveWhitespace) break
            val trimmed = last.text.trimEnd(' ')
            if (trimmed.isEmpty()) parts.removeAt(parts.lastIndex) else { parts[parts.lastIndex] = last.copy(text = trimmed); break }
        }
    }

    private fun imagePart(node: Element, width: Float): MarkdownInlinePart.Object {
        val destination = node.attr("src")
        val resolver = requireNotNull(imageResolver) { "Markdown 图片需要调用方提供 imageResolver: $destination" }
        val image = images.getOrPut(destination) { resolver(destination) }
        require(image.width > 0 && image.height > 0) { "Markdown 图片尺寸无效: $destination" }
        fun dimension(value: String): Float? {
            if (value.isEmpty()) return null
            val result = if (value.endsWith('%')) value.dropLast(1).toFloat() * width / 100 else value.toFloat()
            require(result.isFinite() && result > 0) { "Markdown 图片尺寸必须为有限正数" }
            return result
        }
        val requestedWidth = dimension(node.attr("width"))
        val requestedHeight = dimension(node.attr("height"))
        val baseWidth = requestedWidth ?: requestedHeight?.let { it * image.width / image.height }
            ?: image.width.toFloat()
        val baseHeight = requestedHeight ?: baseWidth * image.height / image.width
        val scale = minOf(1f, width / baseWidth)
        val target = ImageElement(image, ImageOverflow.Scale).apply { modifier = Modifier.size(baseWidth * scale, baseHeight * scale) }
        return MarkdownInlinePart.Object(target, baseHeight * scale)
    }

    private fun appendTable(parent: UiElement, table: Element, width: Float, context: MeasureContext) {
        table.children().filter { it.normalName() == "caption" }.forEach {
            appendInline(parent, it.childNodes(), width, context, TextSpanStyle(fontWeight = 600))
            parent.box(Modifier.height(style.fontSize * 0.5f))
        }
        val rows = table.select("tr").filter { row -> row.parents().firstOrNull { it.normalName() == "table" } === table }
        data class Slot(val node: Element, val row: Int, val column: Int, val rowSpan: Int, val columnSpan: Int)
        val slots = mutableListOf<Slot>()
        val occupied = mutableSetOf<Pair<Int, Int>>()
        var count = 0
        rows.forEachIndexed { rowIndex, row ->
            var column = 0
            row.children().filter { it.normalName() in setOf("td", "th") }.forEach { cell ->
                while ((rowIndex to column) in occupied) column++
                val colSpan = cell.attr("colspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
                val requestedRows = cell.attr("rowspan").toIntOrNull() ?: 1
                val rowSpan = if (requestedRows == 0) rows.size - rowIndex else requestedRows.coerceIn(1, rows.size - rowIndex)
                slots += Slot(cell, rowIndex, column, rowSpan, colSpan)
                for (r in rowIndex until rowIndex + rowSpan) for (c in column until column + colSpan) occupied += r to c
                column += colSpan
                count = maxOf(count, column)
            }
        }
        if (slots.isEmpty()) return
        val cells = slots.map { slot ->
            val cellWidth = width * slot.columnSpan / count
            val horizontalPadding = style.fontSize * 0.8125f
            val contentWidth = cellWidth - 2 - horizontalPadding * 2
            requireWidth(contentWidth)
            val cell = Column(horizontal(alignment(slot.node))).apply {
                modifier = Modifier.width(cellWidth).background(if (slot.row % 2 == 1) style.codeBackgroundColor else style.backgroundColor)
                    .border(0.5f, style.tableBorderColor).padding(horizontal = horizontalPadding, vertical = style.fontSize * 0.375f)
                appendBlocks(this, slot.node.childNodes(), contentWidth, context,
                    TextSpanStyle(fontWeight = if (slot.node.normalName() == "th") 600 else null))
            }
            MarkdownTable.Cell(cell, slot.row, slot.column, slot.rowSpan, slot.columnSpan)
        }
        parent.add(MarkdownTable(cells, count, rows.size, width))
    }

    private fun matchesMedia(media: String): Boolean = when (media.trim().lowercase()) {
        "", "all", "screen" -> true
        "(prefers-color-scheme: dark)" -> style.darkTheme
        "(prefers-color-scheme: light)" -> !style.darkTheme
        else -> false
    }
    private fun alignment(node: Element): Alignment = when (node.attr("align").lowercase()) {
        "center" -> Alignment.CENTER
        "right" -> Alignment.RIGHT
        else -> Alignment.LEFT
    }
    private fun horizontal(align: Alignment) = when (align) {
        Alignment.CENTER -> HorizontalAlignment.Center
        Alignment.RIGHT -> HorizontalAlignment.Right
        else -> HorizontalAlignment.Left
    }
    private fun alertColor(kind: String): Int = when (kind.lowercase()) {
        "tip" -> if (style.darkTheme) Color.makeRGB(63, 185, 80) else Color.makeRGB(26, 127, 55)
        "important" -> if (style.darkTheme) Color.makeRGB(171, 125, 248) else Color.makeRGB(130, 80, 223)
        "warning" -> if (style.darkTheme) Color.makeRGB(210, 153, 34) else Color.makeRGB(154, 103, 0)
        "caution" -> if (style.darkTheme) Color.makeRGB(248, 81, 73) else Color.makeRGB(207, 34, 46)
        else -> style.linkColor
    }
}
