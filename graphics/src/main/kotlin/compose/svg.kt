package top.e404.tavolo.draw.compose

import org.jetbrains.skia.Data
import org.jetbrains.skia.Point
import org.jetbrains.skia.Rect
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.skia.svg.SVGLengthContext

private const val SVG_LAYOUT_EPSILON = 0.999f

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
        val targetSize = resolveTargetSize(baseWidth, baseHeight, sizeIn())
        targetWidth = targetSize.x
        targetHeight = targetSize.y
        contentWidth = targetWidth
        contentHeight = targetHeight
    }

    override fun layoutChildren(content: Bounds) {}

    override fun drawContent(context: DrawContext) {
        val content = contentBounds()
        if (targetWidth <= 0f || targetHeight <= 0f || content.width <= 0f || content.height <= 0f) return
        // 目标尺寸受内容区域限制，避免 size 外层的 padding/border 被内部 SVG 覆盖。
        // 渲染目标最终会取整到像素，保留 1px 内误差，避免 12.5px 这类尺寸被根节点取整误缩放。
        val widthScale = if (content.width + SVG_LAYOUT_EPSILON < targetWidth) content.width / targetWidth else 1f
        val heightScale = if (content.height + SVG_LAYOUT_EPSILON < targetHeight) content.height / targetHeight else 1f
        val scale = minOf(widthScale, heightScale, 1f)
        val drawWidth = targetWidth * scale
        val drawHeight = targetHeight * scale
        parseSvgDom(bytes).use { svg ->
            context.canvas.drawSvg(
                svg,
                Rect.makeXYWH(content.x, content.y, drawWidth, drawHeight)
            )
        }
    }

    private fun resolveTargetSize(baseWidth: Float, baseHeight: Float, sizeIn: SizeIn): Point {
        val minScale = maxOf(
            0f,
            sizeIn.minWidth.coerceAtLeast(0f) / baseWidth,
            sizeIn.minHeight.coerceAtLeast(0f) / baseHeight
        )
        val maxScale = minOf(
            if (sizeIn.maxWidth.isFinite()) sizeIn.maxWidth.coerceAtLeast(0f) / baseWidth else Float.POSITIVE_INFINITY,
            if (sizeIn.maxHeight.isFinite()) sizeIn.maxHeight.coerceAtLeast(0f) / baseHeight else Float.POSITIVE_INFINITY
        )
        require(minScale <= maxScale) {
            "SVG sizeIn 约束冲突，无法在保持宽高比时同时满足最小和最大尺寸"
        }

        // SVG 默认保持宽高比：小于最小尺寸时放大，超出最大尺寸时缩小。
        val scale = 1f.coerceIn(minScale, maxScale)
        return Point(baseWidth * scale, baseHeight * scale)
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
