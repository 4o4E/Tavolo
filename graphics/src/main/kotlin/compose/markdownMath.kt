package top.e404.tavolo.draw.compose

import org.jetbrains.skia.Color
import org.jetbrains.skia.DynamicMemoryWStream
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.TextLine
import org.jetbrains.skia.svg.SVGCanvas
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import top.e404.tavolo.util.FontManager

/**
 * MathJax 自带数学字形是 path，中文等字形是 SVG text。Skia SVGDOM 不使用 Tavolo 的字体注册表，
 * 因此先由原生文字成形和 SVGCanvas 将这些文字转换为路径，保留原 SVG 的定位、颜色及公式基线。
 */
internal fun outlineMarkdownMathText(graphic: MarkdownGraphic, fontFamily: String): MarkdownGraphic {
    if (!graphic.svg) return graphic
    val source = graphic.bytes.decodeToString()
    if (!source.contains("<text")) return graphic
    val document = Jsoup.parse(source, "", Parser.xmlParser())
    document.outputSettings().prettyPrint(false).syntax(Document.OutputSettings.Syntax.xml)
    document.select("text").forEach { node ->
        val value = node.wholeText()
        // 编号公式含有无字形的对齐占位 text，没有 font-size，无需转换。
        if (value.isEmpty()) return@forEach
        val size = node.attr("font-size").removeSuffix("px").toFloat()
        val variant = node.attr("data-variant")
        val x = node.attr("x").ifEmpty { "0" }.toFloat()
        val y = node.attr("y").ifEmpty { "0" }.toFloat()
        val outlined = Font(FontManager.resolve(fontFamily), size).use { font ->
            font.isEmboldened = variant.contains("bold")
            if (variant.contains("italic")) font.skewX = -0.25f
            TextLine.make(value, font).use { line ->
                require(line.glyphs.none { it.toInt() == 0 }) { "数学公式字体缺少字形: $value；请注册覆盖这些字符的字体" }
                DynamicMemoryWStream().use { stream ->
                    SVGCanvas.make(Rect.makeLTRB(0f, -size * 2, maxOf(line.width, 1f), size * 2), stream, true, false).use { canvas ->
                        Paint().use { paint ->
                            paint.color = Color.BLACK
                            paint.isAntiAlias = true
                            canvas.drawTextLine(line, 0f, 0f, paint)
                        }
                    }
                    val bytes = ByteArray(stream.bytesWritten())
                    check(stream.read(bytes, 0, bytes.size)) { "无法读取公式字形的 SVG 路径" }
                    Jsoup.parse(bytes.decodeToString(), "", Parser.xmlParser())
                }
            }
        }
        check(outlined.select("text").isEmpty()) { "公式字形未转换为矢量路径: $value" }
        // 字形墨迹继承 MathJax 的颜色，不能让 SVGCanvas 的默认黑色覆盖深色主题或 textcolor。
        outlined.select("[fill]").filter { it.attr("fill").lowercase() in setOf("black", "#000", "#000000", "rgb(0,0,0)") }
            .forEach { it.removeAttr("fill") }
        val paths = requireNotNull(outlined.selectFirst("svg")).childNodes().toList()
        node.tagName("g").empty().removeAttr("font-size").removeAttr("font-family")
        node.appendChild(Element("g").attr("transform", "translate($x,$y)").also { it.appendChildren(paths) })
    }
    return graphic.copy(bytes = document.outerHtml().encodeToByteArray())
}
