package top.e404.skiko.draw.compose

import org.jetbrains.skia.*

@DslMarker
annotation class UiDsl

fun render(backgroundColor: Int = Color.TRANSPARENT, root: UiElement = Column(), content: Composable): Image {
    root.apply(content)
    Surface.makeRasterN32Premul(1, 1).use { root.measure(it) }
    val finalWidth = root.width.toInt()
    val finalHeight = root.height.toInt()
    if (finalWidth <= 0 || finalHeight <= 0) {
        error("计算尺寸无效 (width=$finalWidth, height=$finalHeight)，无法生成图片。")
    }
    return Surface.makeRasterN32Premul(finalWidth, finalHeight).use { surface ->
        surface.canvas.apply {
            clear(backgroundColor)
            root.layout(0f, 0f)
            root.draw(this)
        }
        surface.makeImageSnapshot()
    }
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
