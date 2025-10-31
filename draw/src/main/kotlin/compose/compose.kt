package top.e404.skiko.draw.compose

import org.jetbrains.skia.*

@DslMarker
annotation class UiDsl

/**
 * UI 元素的基础接口，定义了所有 UI 组件共有的属性和行为。
 */
interface UiElement {
    var width: Float
    var height: Float
    var contentWidth: Float
    var contentHeight: Float
    var x: Float
    var y: Float
    var modifier: Modifier
    val children: MutableList<UiElement>

    /**
     * 第一阶段：测量尺寸。
     */
    fun measure(surface: Surface)

    /**
     * 第二阶段：布局位置。
     */
    fun layout(parentX: Float, parentY: Float)

    /**
     * 第三阶段：绘制。
     */
    fun draw(canvas: Canvas)
    fun add(element: UiElement) {
        children.add(element)
    }
}

/**
 * Element 接口的基础实现类，提供了通用属性的默认实现。
 */
abstract class BaseElement : UiElement {
    override var width: Float = 0f
    override var height: Float = 0f
    override var contentWidth: Float = 0f
    override var contentHeight: Float = 0f
    override var x: Float = 0f
    override var y: Float = 0f
    override var modifier: Modifier = Modifier
    override val children: MutableList<UiElement> = mutableListOf()

    final override fun measure(surface: Surface) {
        // 步骤 1: 先调用 measureContent，让元素（如 Text, Image）计算出其内容的自然尺寸。
        // 这一步会填充 this.contentWidth 和 this.contentHeight 的初始值。
        measureContent(surface)

        // 步骤 2: 检查是否存在 Modifier.size()。如果存在，用它来覆盖内容的自然尺寸。
        // 这实现了您的核心要求：“预设size是预设的内容的size”。
        modifier.fold(Unit) { _, mod ->
            if (mod is Size) {
                if (!mod.width.isNaN()) {
                    this.contentWidth = mod.width
                }
                if (!mod.height.isNaN()) {
                    this.contentHeight = mod.height
                }
            }
        }

        // 步骤 3: 基于最终确定的 content size，加上 padding 和 border 来计算元素的最终尺寸。
        // Margin 在这里完全不参与计算，因为它不属于元素自身尺寸的一部分。
        var finalWidth = this.contentWidth
        var finalHeight = this.contentHeight

        modifier.fold(Unit) { _, mod ->
            when (mod) {
                is Border -> {
                    finalWidth += mod.left + mod.right
                    finalHeight += mod.top + mod.bottom
                }
                is Padding -> {
                    finalWidth += mod.left + mod.right
                    finalHeight += mod.top + mod.bottom
                }

                is Margin -> {
                    finalWidth += mod.left + mod.right
                    finalHeight += mod.top + mod.bottom
                }
            }
        }

        this.width = finalWidth
        this.height = finalHeight
    }

    abstract fun measureContent(surface: Surface)

    override fun layout(parentX: Float, parentY: Float) {
        // 关键修复：
        // 父容器在调用 layout 时，已经为 margin 预留了空间。
        // 因此，子元素自身的 x, y 坐标应该直接等于父容器计算好的坐标，
        // 而不是在这个基础上再叠加自己的 margin。
        this.x = parentX
        this.y = parentY

        // -------------------------------------------------------------------
        // 以下逻辑保持不变，它负责计算 *内容* 的起始点，是正确的。
        // 内容的起始点 = 元素的起始点 + border + padding
        var childStartX = this.x
        var childStartY = this.y

        modifier.fold(Unit) { _, mod ->
            when (mod) {
                is Border -> {
                    childStartX += mod.left; childStartY += mod.top
                }

                is Padding -> {
                    childStartX += mod.left; childStartY += mod.top
                }
            }
        }
        layoutChildren(childStartX, childStartY)
    }

    abstract fun layoutChildren(parentX: Float, parentY: Float)

    final override fun draw(canvas: Canvas) {
        var clipPath: Path? = null
        // 应用Clip
        modifier.fold(Unit) { _, mod ->
            if (mod is Clip) {
                val margin = modifier.fold(Margin()) { acc, m -> if (m is Margin) m else acc }
                val clipWidth = width - (margin.left + margin.right)
                val clipHeight = height - (margin.top + margin.bottom)
                clipPath = mod.shape.createPath(clipWidth, clipHeight)
                    .apply { transform(Matrix33.makeTranslate(x, y)) }
            }
        }

        clipPath?.let {
            canvas.save()
            canvas.clipPath(it, true)
        }

        try {
            drawBehind(canvas)
            drawContent(canvas)
            children.forEach { it.draw(canvas) }
        } finally {
            clipPath?.let { canvas.restore() }
        }
    }

    private fun drawBehind(canvas: Canvas) {
        val margin = modifier.fold(Margin()) { acc, m -> if (m is Margin) m else acc }
        // val border = modifier.fold(Border(color = Color.TRANSPARENT)) { acc, m -> m as? Border ?: acc }
        val antiAlias = modifier.fold(AntiAlias()) { acc, m -> m as? AntiAlias ?: acc }

        val outerWidth = width - (margin.left + margin.right)
        val outerHeight = height - (margin.top + margin.bottom)

        modifier.fold(Unit) { _, mod ->
            when (mod) {
                is Background -> {
                    val paint = Paint().apply { color = mod.color; isAntiAlias = antiAlias.enabled }
                    canvas.drawRect(Rect.makeXYWH(x, y, outerWidth, outerHeight), paint)
                }

                is Border -> if (mod.color != Color.TRANSPARENT) {
                    val paint = Paint().apply {
                        color = mod.color
                        mode = PaintMode.STROKE
                        isAntiAlias = antiAlias.enabled
                        strokeWidth = 0f
                        mode = PaintMode.FILL
                    }
                    // 绘制四条边框
                    if (mod.top > 0) canvas.drawRect(
                        Rect.makeXYWH(x, y, outerWidth, mod.top),
                        paint
                    )
                    if (mod.bottom > 0) canvas.drawRect(
                        Rect.makeXYWH(
                            x,
                            y + outerHeight - mod.bottom,
                            outerWidth,
                            mod.bottom
                        ), paint
                    )
                    if (mod.left > 0) canvas.drawRect(
                        Rect.makeXYWH(x, y, mod.left, outerHeight),
                        paint
                    )
                    if (mod.right > 0) canvas.drawRect(
                        Rect.makeXYWH(
                            x + outerWidth - mod.right,
                            y,
                            mod.right,
                            outerHeight
                        ), paint
                    )
                }
            }
        }
    }

    abstract fun drawContent(canvas: Canvas)
}

fun render(backgroundColor: Int = Color.TRANSPARENT, debug: Boolean = false, content: Composable): Image {
    val root = Column().apply(content)
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
        if (debug) {
            buildString {
                debugBaseElement(0, root, this)
            }.let {
                println(it)
            }
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
