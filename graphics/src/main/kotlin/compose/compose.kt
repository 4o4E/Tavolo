package top.e404.tavolo.draw.compose

import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import kotlin.math.ceil

@DslMarker
annotation class UiDsl

data class PreparedRenderTree(
    val root: UiElement,
    val width: Int,
    val height: Int
)

private fun prepareRenderTree(
    root: UiElement,
    measureContext: MeasureContext,
    snapRootSizeToPixel: Boolean,
    content: Composable
): PreparedRenderTree {
    root.apply(content)
    root.measure(measureContext)
    val finalWidth = root.width.toPixelSize(snapRootSizeToPixel)
    val finalHeight = root.height.toPixelSize(snapRootSizeToPixel)
    if (finalWidth <= 0 || finalHeight <= 0) {
        error("计算尺寸无效 (width=$finalWidth, height=$finalHeight)，无法渲染图片")
    }
    if (snapRootSizeToPixel) {
        // 根容器尺寸需要贴合最终像素尺寸，避免小数边框被 Surface 裁掉。
        root.width = finalWidth.toFloat()
        root.height = finalHeight.toFloat()
    }
    root.layout(0f, 0f)
    normalizeLayoutBounds(root, finalWidth, finalHeight)
    return PreparedRenderTree(root, finalWidth, finalHeight)
}

private fun Float.toPixelSize(snapRootSizeToPixel: Boolean): Int =
    if (snapRootSizeToPixel) ceil(this).toInt() else toInt()

private fun normalizeLayoutBounds(root: UiElement, finalWidth: Int, finalHeight: Int) {
    // 保持既有渲染行为：根节点的直接子元素会被裁剪到根节点边界内。
    for (children in root.children) {
        if (children.x + children.width > finalWidth) {
            children.width = finalWidth - children.x
        }
        if (children.y + children.height > finalHeight) {
            children.height = finalHeight - children.y
        }
    }
}

/**
 * 使用默认像素贴合策略渲染图片。
 */
fun render(
    backgroundColor: Int = Color.TRANSPARENT,
    root: UiElement = Column(),
    measureContext: MeasureContext = MeasureContext(),
    content: Composable
): Image =
    render(backgroundColor, root, measureContext, true, content)

/**
 * 渲染图片，可通过 snapRootSizeToPixel 控制根容器尺寸是否向上贴合到整数像素。
 */
fun render(
    backgroundColor: Int = Color.TRANSPARENT,
    root: UiElement = Column(),
    measureContext: MeasureContext = MeasureContext(),
    snapRootSizeToPixel: Boolean,
    content: Composable
): Image {
    val prepared = prepareRenderTree(root, measureContext, snapRootSizeToPixel, content)
    return Surface.makeRasterN32Premul(prepared.width, prepared.height).use { surface ->
        val drawContext = DrawContext(SkiaDrawCanvas(surface.canvas), measureContext)
        drawContext.canvas.clear(backgroundColor)
        prepared.root.draw(drawContext)
        surface.makeImageSnapshot()
    }
}

fun render(backgroundColor: Int, root: UiElement, content: Composable): Image =
    render(backgroundColor, root, MeasureContext(), content)

/**
 * 使用默认像素贴合策略记录绘图命令。
 */
fun renderCommands(
    backgroundColor: Int = Color.TRANSPARENT,
    root: UiElement = Column(),
    measureContext: MeasureContext = MeasureContext(),
    content: Composable
): List<DrawCommand> =
    renderCommands(backgroundColor, root, measureContext, true, content)

/**
 * 记录绘图命令，可通过 snapRootSizeToPixel 控制根容器尺寸是否向上贴合到整数像素。
 */
fun renderCommands(
    backgroundColor: Int = Color.TRANSPARENT,
    root: UiElement = Column(),
    measureContext: MeasureContext = MeasureContext(),
    snapRootSizeToPixel: Boolean,
    content: Composable
): List<DrawCommand> {
    val prepared = prepareRenderTree(root, measureContext, snapRootSizeToPixel, content)
    val recorder = RecordingDrawCanvas()
    val drawContext = DrawContext(recorder, measureContext)
    drawContext.canvas.clear(backgroundColor)
    prepared.root.draw(drawContext)
    return recorder.commands
}

fun debugBaseElement(layer: Int, el: UiElement, sb: StringBuilder) {
    sb.append("  ".repeat(layer).let {
        if (it.isEmpty()) it
        else it.removeSuffix("  ") + "- "
    }).appendLine("type: ${el.javaClass.name}")
    sb.append("  ".repeat(layer)).appendLine("width: ${el.width}")
    sb.append("  ".repeat(layer)).appendLine("height: ${el.height}")
    sb.append("  ".repeat(layer)).appendLine("contentWidth: ${el.contentWidth}")
    sb.append("  ".repeat(layer)).appendLine("contentHeight: ${el.contentHeight}")
    sb.append("  ".repeat(layer)).appendLine("x: ${el.x}")
    sb.append("  ".repeat(layer)).appendLine("y: ${el.y}")
    val modifiers = el.modifier.toList()
    if (modifiers.isNotEmpty()) {
        sb.append("  ".repeat(layer)).appendLine("modifiers:")
        for (modifier in modifiers) {
            sb.append("  ".repeat(layer)).append("- ").appendLine("""'${modifier}'""")
        }
    }
    if (el.children.isNotEmpty()) {
        sb.append("  ".repeat(layer)).appendLine("children:")
        for (element in el.children) {
            debugBaseElement(layer + 1, element, sb)
        }
    }
}
