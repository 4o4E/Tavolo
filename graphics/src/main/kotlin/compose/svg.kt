package top.e404.tavolo.draw.compose

import org.jetbrains.skia.Data
import org.jetbrains.skia.Point
import org.jetbrains.skia.Rect
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.skia.svg.SVGLengthContext

class SvgElement(
    private val bytes: ByteArray
) : BaseElement() {
    private val naturalSize by lazy {
        parseSvgDom(bytes).use { svg ->
            resolveNaturalSize(svg)
        }
    }
    private var targetWidth = 0f
    private var targetHeight = 0f

    override fun measureContent(context: MeasureContext) {
        val explicitSize = modifier.fold(Size()) { acc, mod -> mod as? Size ?: acc }
        val hasExplicitWidth = explicitSize.width.isFinite() && explicitSize.width > 0f
        val hasExplicitHeight = explicitSize.height.isFinite() && explicitSize.height > 0f
        val baseWidth: Float
        val baseHeight: Float
        when {
            hasExplicitWidth && hasExplicitHeight -> {
                baseWidth = explicitSize.width
                baseHeight = explicitSize.height
            }
            hasExplicitWidth -> {
                baseWidth = explicitSize.width
                baseHeight = naturalSize.y * (explicitSize.width / naturalSize.x)
            }
            hasExplicitHeight -> {
                baseWidth = naturalSize.x * (explicitSize.height / naturalSize.y)
                baseHeight = explicitSize.height
            }
            else -> {
                baseWidth = naturalSize.x
                baseHeight = naturalSize.y
            }
        }
        val sizeIn = sizeIn()

        if (sizeIn.maxWidth.isFinite() || sizeIn.maxHeight.isFinite()) {
            // SVG 默认按自身宽高比例缩放到 sizeIn 限制内，避免压扁图形。
            val maxWidth = if (sizeIn.maxWidth.isFinite()) sizeIn.maxWidth else baseWidth
            val maxHeight = if (sizeIn.maxHeight.isFinite()) sizeIn.maxHeight else baseHeight
            val scale = minOf(maxWidth / baseWidth, maxHeight / baseHeight, 1f)
            targetWidth = baseWidth * scale
            targetHeight = baseHeight * scale
        } else {
            targetWidth = baseWidth
            targetHeight = baseHeight
        }
        contentWidth = targetWidth
        contentHeight = targetHeight
    }

    override fun layoutChildren(content: Bounds) {}

    override fun drawContent(context: DrawContext) {
        val content = contentBounds()
        parseSvgDom(bytes).use { svg ->
            context.canvas.drawSvg(
                svg,
                Rect.makeXYWH(content.x, content.y, targetWidth, targetHeight)
            )
        }
    }

    private fun resolveNaturalSize(svg: SVGDOM): Point {
        val root = svg.root ?: error("SVG 内容无效，无法读取根节点")
        root.use {
            val intrinsic = it.getIntrinsicSize(SVGLengthContext(0f, 0f, 96f))
            if (intrinsic.x > 0f && intrinsic.y > 0f) return intrinsic

            val viewBox = it.viewBox
            if (viewBox != null && viewBox.width > 0f && viewBox.height > 0f) {
                return Point(viewBox.width, viewBox.height)
            }
        }

        @Suppress("DEPRECATION")
        val containerSize = svg.containerSize
        if (containerSize.x > 0f && containerSize.y > 0f) return containerSize

        error("SVG 缺少可用尺寸，请在 SVG 中提供 width/height 或 viewBox")
    }
}

fun parseSvgDom(svg: String): SVGDOM =
    parseSvgDom(svg.encodeToByteArray())

fun parseSvgDom(bytes: ByteArray): SVGDOM {
    val dom = Data.makeFromBytes(bytes).use { data ->
        SVGDOM(data)
    }
    try {
        val root = dom.root
        require(root != null) { "SVG 内容无效，无法解析为标准 SVG 文档" }
        root.close()
    } catch (t: Throwable) {
        dom.close()
        throw t
    }
    return dom
}
