package top.e404.tavolo.draw.compose

import org.jetbrains.skia.*
import org.jetbrains.skia.paragraph.*
import top.e404.tavolo.util.FontManager
import org.jetbrains.skia.paragraph.TextStyle as ParagraphTextStyle

internal sealed interface MarkdownInlinePart {
    data class Words(
        val text: String,
        val style: TextSpanStyle,
        val underline: Boolean = false,
        val preserveWhitespace: Boolean = false
    ) : MarkdownInlinePart
    data class Object(val element: UiElement, val baseline: Float) : MarkdownInlinePart
}

/** 行内对象与带内边距的文字共用 Paragraph 测量；背景按换行后的实际片段绘制。 */
internal class MarkdownParagraph(
    private val parts: List<MarkdownInlinePart>,
    private val readingWidth: Float,
    private val style: MarkdownStyle,
    private val align: Alignment = Alignment.LEFT,
    private val lineHeight: Float = style.lineHeight
) : BaseElement() {
    private data class Placement(val part: MarkdownInlinePart.Object, val index: Int)
    private data class Decoration(val start: Int, val end: Int, val color: Int, val radius: Float, val height: Float)
    private data class Background(val path: Path, val color: Int)
    private var paragraph: Paragraph? = null
    private val placements = mutableListOf<Placement>()
    private val backgrounds = mutableListOf<Background>()
    private var boxes = emptyArray<TextBox>()
    var baseline = 0f
        private set

    override fun measureContent(context: MeasureContext) {
        paragraph?.close()
        backgrounds.forEach { it.path.close() }
        backgrounds.clear()
        placements.clear()
        val decorations = mutableListOf<Decoration>()
        var offset = 0
        var placeholderIndex = 0
        val paragraphStyle = ParagraphStyle().apply {
            alignment = align
            textStyle = spanStyle(TextSpanStyle())
        }
        val result = paragraphStyle.use { settings ->
            ParagraphBuilder(settings, FontManager.fonts).use { builder ->
                builder.addText("")
                fun text(value: String) { builder.addText(value); offset += value.length }
                fun placeholder(width: Float, height: Float, baseline: Float) {
                    builder.addPlaceholder(PlaceholderStyle(width, height, PlaceholderAlignment.BASELINE, BaselineMode.ALPHABETIC, baseline))
                    offset++
                    placeholderIndex++
                }
                parts.forEach { part ->
                    when (part) {
                        is MarkdownInlinePart.Words -> {
                            val span = part.style
                            val decorated = span.backgroundColor != null && span.backgroundRadius != null
                            val padding = span.backgroundPaddingHorizontal ?: 0f
                            val vertical = span.backgroundPaddingVertical ?: 0f
                            var height = 0f
                            var spaceWidth = 0f
                            if (decorated) {
                                val faces = FontManager.fonts.findTypefaces(arrayOf(span.fontFamily ?: style.fontFamily, style.fontFamily),
                                    FontStyle(span.fontWeight ?: 400, FontWidth.NORMAL, if (span.italic == true) FontSlant.ITALIC else FontSlant.UPRIGHT))
                                try {
                                    Font(requireNotNull(faces.firstOrNull()) { "行内代码缺少可用字体" }, span.fontSize ?: style.fontSize).use { font ->
                                        height = font.metrics.descent - font.metrics.ascent + vertical * 2
                                        spaceWidth = font.measureTextWidth("\u00a0")
                                    }
                                } finally { faces.forEach { it?.close() } }
                            }
                            spanStyle(span, part.underline, height).use { textStyle ->
                                builder.pushStyle(textStyle)
                                // 不换行空格的字距精确补足内边距；它属于文字，不会像独立 placeholder 一样脱离代码换行。
                                fun pad() {
                                    spanStyle(span, part.underline, height).setLetterSpacing(padding - spaceWidth).use { spacing ->
                                        builder.pushStyle(spacing)
                                        text("\u00a0")
                                        builder.popStyle()
                                    }
                                }
                                val start = offset
                                if (decorated) pad()
                                text(part.text)
                                if (decorated) {
                                    pad()
                                    decorations += Decoration(start, offset, requireNotNull(span.backgroundColor), requireNotNull(span.backgroundRadius), height)
                                }
                                builder.popStyle()
                            }
                        }
                        is MarkdownInlinePart.Object -> {
                            part.element.measure(context)
                            require(part.element.width <= readingWidth + 0.01f) { "Markdown 行内对象超过可用宽度" }
                            placements += Placement(part, placeholderIndex)
                            placeholder(part.element.width, part.element.height, part.baseline)
                        }
                    }
                }
                builder.build().layout(readingWidth)
            }
        }
        paragraph = result
        baseline = result.alphabeticBaseline
        boxes = if (placements.isEmpty()) emptyArray() else result.rectsForPlaceholders
        decorations.forEach { decoration ->
            val lines = mutableListOf<Rect>()
            result.getRectsForRange(decoration.start, decoration.end, RectHeightMode.TIGHT, RectWidthMode.TIGHT).forEach { box ->
                val rect = box.rect
                val previous = lines.lastOrNull()
                if (previous != null && rect.top < previous.bottom && rect.bottom > previous.top) {
                    lines[lines.lastIndex] = Rect.makeLTRB(minOf(previous.left, rect.left), minOf(previous.top, rect.top),
                        maxOf(previous.right, rect.right), maxOf(previous.bottom, rect.bottom))
                } else lines += rect
            }
            lines.forEach { rect ->
                val height = maxOf(rect.height, decoration.height)
                val path = Shape.RoundedRect(decoration.radius).createPath(rect.width, height).apply {
                    transform(Matrix33.makeTranslate(rect.left, rect.top - (height - rect.height) / 2))
                }
                backgrounds += Background(path, decoration.color)
            }
        }
        contentWidth = if (align == Alignment.LEFT) minOf(readingWidth, result.maxIntrinsicWidth) else readingWidth
        contentHeight = result.height
    }

    private fun spanStyle(span: TextSpanStyle, underline: Boolean = false, minimumHeight: Float = 0f): ParagraphTextStyle = ParagraphTextStyle()
        .setFontFamilies(arrayOf(span.fontFamily ?: style.fontFamily, style.fontFamily))
        .setFontSize(span.fontSize ?: style.fontSize)
        .setColor(span.textColor ?: style.textColor)
        .setFontStyle(FontStyle(span.fontWeight ?: 400, FontWidth.NORMAL, if (span.italic == true) FontSlant.ITALIC else FontSlant.UPRIGHT))
        .setDecorationStyle(DecorationStyle.NONE.withUnderline(underline).withLineThrough(span.strikethrough == true)
            .withColor(span.textColor ?: style.textColor))
        .apply {
            height = maxOf(lineHeight, minimumHeight / (span.fontSize ?: style.fontSize))
            if (span.backgroundRadius == null) {
                span.backgroundColor?.let { color -> Paint().use { paint -> paint.color = color; setBackground(paint) } }
            }
        }

    override fun layoutChildren(content: Bounds) {
        placements.forEach { placement ->
            val rect = boxes[placement.index].rect
            placement.part.element.layout(content.x + rect.left, content.y + rect.top)
        }
    }

    override fun drawContent(context: DrawContext) {
        val bounds = contentBounds()
        if (backgrounds.isNotEmpty()) {
            context.canvas.save()
            try {
                context.canvas.translate(bounds.x, bounds.y)
                Paint().use { paint ->
                    paint.isAntiAlias = true
                    backgrounds.forEach { background -> paint.color = background.color; context.canvas.drawPath(background.path, paint) }
                }
            } finally { context.canvas.restore() }
        }
        context.canvas.drawParagraph(requireNotNull(paragraph), bounds.x, bounds.y)
        placements.forEach { it.part.element.draw(context) }
    }
}

/** 任务图标按第一行的文字基线定位；正文含多行、代码或图片时也不按整项高度居中。 */
internal class MarkdownTaskRow(
    private val marker: UiElement,
    private val body: Column,
    private val markerWidth: Float,
    private val style: MarkdownStyle
) : BaseElement() {
    private var markerTop = 0f
    init { add(marker); add(body) }
    override fun measureContent(context: MeasureContext) {
        marker.measure(context)
        body.measure(context)
        FontManager.font(style.fontFamily, style.fontSize).use { font ->
            val baseline = (body.children.firstOrNull() as? MarkdownParagraph)?.baseline ?: -context.textMeasurer.metrics(font).ascent
            markerTop = maxOf(0f, baseline - font.metrics.capHeight / 2 - marker.height / 2)
        }
        contentWidth = markerWidth + body.width
        contentHeight = maxOf(body.height, markerTop + marker.height)
    }
    override fun layoutChildren(content: Bounds) {
        marker.layout(content.x, content.y + markerTop)
        body.layout(content.x + markerWidth, content.y)
    }
    override fun drawContent(context: DrawContext) {}
}

/** 原生合并单元格布局：宽度按列分配，行高由跨行约束共同决定。 */
internal class MarkdownTable(private val cells: List<Cell>, private val columns: Int, private val rows: Int, private val tableWidth: Float) : BaseElement() {
    data class Cell(val element: UiElement, val row: Int, val column: Int, val rowSpan: Int, val columnSpan: Int)
    private var rowHeights = FloatArray(rows)
    override fun measureContent(context: MeasureContext) {
        rowHeights = FloatArray(rows)
        cells.forEach { it.element.measure(context) }
        cells.sortedBy { it.rowSpan }.forEach { cell ->
            val available = (cell.row until cell.row + cell.rowSpan).sumOf { rowHeights[it].toDouble() }.toFloat()
            if (cell.element.height > available) {
                val extra = (cell.element.height - available) / cell.rowSpan
                for (row in cell.row until cell.row + cell.rowSpan) rowHeights[row] += extra
            }
        }
        contentWidth = tableWidth
        contentHeight = rowHeights.sum()
    }
    override fun layoutChildren(content: Bounds) {
        val positions = FloatArray(rows + 1)
        for (row in 0 until rows) positions[row + 1] = positions[row] + rowHeights[row]
        cells.forEach { cell ->
            cell.element.height = positions[cell.row + cell.rowSpan] - positions[cell.row]
            cell.element.layout(content.x + cell.column * tableWidth / columns, content.y + positions[cell.row])
        }
    }
    override fun drawContent(context: DrawContext) = cells.forEach { it.element.draw(context) }
}
