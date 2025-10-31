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
        measureContent(surface)
        width = contentWidth
        height = contentHeight
        modifier.fold(Unit) { _, mod ->
            when (mod) {
                is Border -> {
                    width += mod.left + mod.right; height += mod.top + mod.bottom
                }

                is Padding -> {
                    width += mod.left + mod.right; height += mod.top + mod.bottom
                }

                is Margin -> {
                    width += mod.left + mod.right; height += mod.top + mod.bottom
                }
            }
        }
    }

    abstract fun measureContent(surface: Surface)

    override fun layout(parentX: Float, parentY: Float) {
        var currentX = parentX
        var currentY = parentY

        modifier.fold(Unit) { _, mod ->
            if (mod is Margin) {
                currentX += mod.left; currentY += mod.top
            }
        }
        this.x = currentX
        this.y = currentY

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

fun render(backgroundColor: Int = Color.WHITE, content: Composable): Image {
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
        surface.makeImageSnapshot()
    }
}
