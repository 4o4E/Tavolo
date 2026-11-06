package top.e404.tavolo.draw.compose

import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import top.e404.tavolo.util.FontManager
import top.e404.tavolo.util.bytes

/** 图片由调用方解析并持有；在元素最后一次绘制完成前不可关闭返回的 Image。 */
typealias MarkdownImageResolver = (destination: String) -> Image

/** 只有调用方知道仓库路径及用户/Issue 的业务上下文；渲染器不发起 GitHub 请求。 */
data class MarkdownContext(
    val softBreak: MarkdownSoftBreak = MarkdownSoftBreak.SPACE,
    val referenceResolver: ((String) -> String?)? = null
)

data class MarkdownStyle(
    val fontFamily: String = FontManager.defaultFamily,
    val codeFontFamily: String = fontFamily,
    val fontSize: Float = 16f,
    val textColor: Int = Color.makeRGB(31, 35, 40),
    val backgroundColor: Int = Color.WHITE,
    val linkColor: Int = Color.makeRGB(9, 105, 218),
    val codeBackgroundColor: Int = Color.makeRGB(246, 248, 250),
    val quoteColor: Int = Color.makeRGB(209, 217, 224),
    val tableBorderColor: Int = Color.makeRGB(209, 217, 224),
    val blockSpacing: Float = 16f,
    val blockPadding: Float = 16f,
    val pagePadding: Float = 32f,
    val lineHeight: Float = 1.5f,
    val darkTheme: Boolean = false
) {
    init {
        require(fontSize.isFinite() && fontSize > 0f) { "Markdown 字号必须是有限正数" }
        require(lineHeight.isFinite() && lineHeight >= 1f) { "Markdown 行高倍数必须大于等于 1" }
        require(listOf(blockSpacing, blockPadding, pagePadding).all { it.isFinite() && it >= 0f }) {
            "Markdown 间距必须是有限非负数"
        }
    }
}

/** width 包含 pagePadding；复杂文档应由外部渲染后作为图片提供。 */
fun UiElement.markdown(
    source: String,
    width: Float,
    style: MarkdownStyle = MarkdownStyle(),
    modifier: Modifier = Modifier,
    imageResolver: MarkdownImageResolver? = null,
    context: MarkdownContext = MarkdownContext()
) = add(MarkdownElement(source, width, style, imageResolver, context).apply { this.modifier = modifier })

/** width 使用逻辑像素；默认以 2 倍像素密度直接绘制，调用方负责关闭返回的 Image。 */
fun renderMarkdown(
    source: String,
    width: Float = 800f,
    style: MarkdownStyle = MarkdownStyle(),
    imageResolver: MarkdownImageResolver? = null,
    context: MarkdownContext = MarkdownContext(),
    pixelRatio: Float = 2f
): Image = render(style.backgroundColor, pixelRatio = pixelRatio) {
    markdown(source, width, style, imageResolver = imageResolver, context = context)
}

/** 返回 PNG 字节；pixelRatio 只影响栅格分辨率，不改变逻辑排版宽度。 */
fun renderMarkdownPng(
    source: String,
    width: Float = 800f,
    style: MarkdownStyle = MarkdownStyle(),
    imageResolver: MarkdownImageResolver? = null,
    context: MarkdownContext = MarkdownContext(),
    pixelRatio: Float = 2f
): ByteArray = renderMarkdown(source, width, style, imageResolver, context, pixelRatio).use { it.bytes() }

private class MarkdownElement(
    source: String,
    private val readingWidth: Float,
    private val style: MarkdownStyle,
    imageResolver: MarkdownImageResolver?,
    context: MarkdownContext
) : BaseElement() {
    private val document = GithubMarkdownParser.parse(source, context.softBreak)
    private val renderer = MarkdownDocumentLayout(style, imageResolver, context)

    init {
        require(readingWidth.isFinite() && readingWidth > 0f) { "Markdown 宽度必须是有限正数" }
        require(source.isNotBlank()) { "Markdown 内容不能为空" }
    }

    override fun measureContent(context: MeasureContext) {
        val available = minOf(readingWidth, renderDimensionLimit(horizontal = true))
        val inner = available - style.pagePadding * 2
        renderer.requireWidth(inner)
        children.clear()
        column(Modifier.width(available).background(style.backgroundColor).padding(style.pagePadding)) {
            renderer.appendBlocks(this, document.childNodes(), inner, context)
        }
        children.single().measure(context)
        contentWidth = available
        contentHeight = children.single().height
        requireRenderHeight(contentHeight)
    }

    override fun layoutChildren(content: Bounds) = children.single().layout(content.x, content.y)
    override fun drawContent(context: DrawContext) {}
}
