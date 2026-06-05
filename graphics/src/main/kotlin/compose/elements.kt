package top.e404.tavolo.draw.compose

import org.jetbrains.skia.*
import org.jetbrains.skia.paragraph.LineMetrics
import org.jetbrains.skia.paragraph.Paragraph
import org.jetbrains.skia.paragraph.ParagraphBuilder
import org.jetbrains.skia.paragraph.ParagraphStyle
import org.jetbrains.skia.paragraph.RectHeightMode
import org.jetbrains.skia.paragraph.RectWidthMode
import org.jetbrains.skia.paragraph.TextStyle as ParagraphTextStyle
import top.e404.tavolo.util.FontManager
import kotlin.math.abs

private fun Paint.applyStrokeStyle(style: StrokeStyle) {
    val dashIntervals = when (style) {
        StrokeStyle.Solid -> null
        is StrokeStyle.Dashed -> style.intervals
            .map { it.coerceAtLeast(0.1f) }
            .let { if (it.size % 2 == 0) it else it + it }
            .takeIf { it.isNotEmpty() }
            ?.toFloatArray()
        is StrokeStyle.Dotted -> floatArrayOf(
            style.dot.coerceAtLeast(0.1f),
            style.gap.coerceAtLeast(0.1f)
        )
    }
    pathEffect = dashIntervals?.let {
        val phase = when (style) {
            is StrokeStyle.Dashed -> style.phase
            is StrokeStyle.Dotted -> style.phase
            StrokeStyle.Solid -> 0f
        }
        PathEffect.makeDash(it, phase)
    }
}

private fun textClusters(text: String): List<String> {
    return segmentGraphemeClusters(text).map { it.text }
}

private val enclosingMarkFallbackNames = listOf(
    "FreeMono",
    "Free Mono",
    "gnu-unifont-full",
    "GNU Unifont",
    "Unifont"
)

private const val ENCLOSING_MARK_MIN_OVERLAP_RATIO = 0.35f

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
    fun measure(context: MeasureContext)

    /**
     * 第二阶段：布局位置。
     */
    fun layout(parentX: Float, parentY: Float)

    /**
     * 第三阶段：绘制。
     */
    fun draw(context: DrawContext)
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

    protected data class ModifierInsets(
        val top: Float = 0f,
        val right: Float = 0f,
        val bottom: Float = 0f,
        val left: Float = 0f
    ) {
        val horizontal: Float get() = left + right
        val vertical: Float get() = top + bottom
    }

    protected data class Bounds(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    ) {
        fun inset(insets: ModifierInsets): Bounds = Bounds(
            x = x + insets.left,
            y = y + insets.top,
            width = (width - insets.horizontal).coerceAtLeast(0f),
            height = (height - insets.vertical).coerceAtLeast(0f)
        )
    }

    private fun Padding.asInsets() = ModifierInsets(top, right, bottom, left)

    private fun Border.asInsets() = ModifierInsets(top, right, bottom, left)

    protected fun sizeIn(): SizeIn = modifier.fold(SizeIn()) { acc, m -> m as? SizeIn ?: acc }

    private fun Float.coerceInConstraint(min: Float, max: Float): Float {
        val constrainedMin = min.coerceAtLeast(0f)
        val constrainedMax = if (max.isFinite()) max.coerceAtLeast(constrainedMin) else Float.POSITIVE_INFINITY
        return this.coerceAtLeast(constrainedMin).let {
            if (constrainedMax.isFinite()) it.coerceAtMost(constrainedMax) else it
        }
    }

    protected fun contentBounds(): Bounds {
        var bounds = Bounds(x, y, width, height)
        for (mod in modifier.toList()) {
            bounds = when (mod) {
                is Padding -> bounds.inset(mod.asInsets())
                is Border -> bounds.inset(mod.asInsets())
                else -> bounds
            }
        }
        return bounds
    }

    final override fun measure(context: MeasureContext) {
        // 步骤 1: 先调用 measureContent，让元素（如 Text, Image）计算出其内容的自然尺寸。
        measureContent(context)

        // 步骤 2: 从内到外应用会影响布局尺寸的 Modifier，让 size/padding/border 的顺序和链式嵌套一致。
        var finalWidth = this.contentWidth
        var finalHeight = this.contentHeight

        for (mod in modifier.toList().asReversed()) {
            when (mod) {
                is Size -> {
                    if (!mod.width.isNaN()) finalWidth = mod.width
                    if (!mod.height.isNaN()) finalHeight = mod.height
                }
                is SizeIn -> {
                    finalWidth = finalWidth.coerceInConstraint(mod.minWidth, mod.maxWidth)
                    finalHeight = finalHeight.coerceInConstraint(mod.minHeight, mod.maxHeight)
                }
                is Padding -> {
                    val insets = mod.asInsets()
                    finalWidth += insets.horizontal
                    finalHeight += insets.vertical
                }
                is Border -> {
                    val insets = mod.asInsets()
                    finalWidth += insets.horizontal
                    finalHeight += insets.vertical
                }
            }
        }

        this.width = finalWidth
        this.height = finalHeight
    }

    /**
     * 第一阶段：测量内容尺寸，由具体元素实现。
     */
    abstract fun measureContent(context: MeasureContext)

    override fun layout(parentX: Float, parentY: Float) {
        this.x = parentX
        this.y = parentY
        val content = contentBounds()
        layoutChildren(content)
    }

    /**
     * 第二阶段：布局子元素，由具体容器元素实现。
     */
    protected abstract fun layoutChildren(content: Bounds)

    final override fun draw(context: DrawContext) {
        val antiAlias = modifier.fold(AntiAlias()) { acc, m -> m as? AntiAlias ?: acc }
        var bounds = Bounds(x, y, width, height)
        var saveCount = 0
        for (mod in modifier.toList()) {
            when (mod) {
                is Padding -> bounds = bounds.inset(mod.asInsets())
                is Shadow -> drawShadow(context, bounds, mod, antiAlias)
                is Rotate -> {
                    val pivotX = mod.pivotX ?: bounds.x + bounds.width / 2f
                    val pivotY = mod.pivotY ?: bounds.y + bounds.height / 2f
                    context.canvas.save()
                    context.canvas.translate(pivotX, pivotY)
                    context.canvas.rotate(mod.degrees)
                    context.canvas.translate(-pivotX, -pivotY)
                    saveCount += 1
                }
                is Background -> {
                    val paint = Paint().apply { color = mod.color; isAntiAlias = antiAlias.enabled }
                    context.canvas.drawRect(Rect.makeXYWH(bounds.x, bounds.y, bounds.width, bounds.height), paint)
                }
                is BackgroundImage -> drawBackgroundImage(context, bounds, mod, antiAlias)
                is Border -> {
                    drawBorder(context, bounds, mod, antiAlias)
                    bounds = bounds.inset(mod.asInsets())
                }
                is Clip -> {
                    val clipPath = mod.shape.createPath(bounds.width, bounds.height)
                        .apply { transform(Matrix33.makeTranslate(bounds.x, bounds.y)) }
                    context.canvas.save()
                    context.canvas.clipPath(clipPath, true)
                    saveCount += 1
                }
            }
        }

        try {
            drawContent(context)
            children.forEach { it.draw(context) }
        } finally {
            repeat(saveCount) {
                context.canvas.restore()
            }
        }
    }

    private fun drawShadow(context: DrawContext, bounds: Bounds, shadow: Shadow, antiAlias: AntiAlias) {
        if (shadow.color == Color.TRANSPARENT) return
        val shadowWidth = bounds.width + shadow.spread * 2f
        val shadowHeight = bounds.height + shadow.spread * 2f
        if (shadowWidth <= 0f || shadowHeight <= 0f) return
        val path = shadow.shape.createPath(shadowWidth, shadowHeight).apply {
            transform(Matrix33.makeTranslate(bounds.x + shadow.offsetX - shadow.spread, bounds.y + shadow.offsetY - shadow.spread))
        }
        val paint = Paint().apply {
            color = shadow.color
            isAntiAlias = antiAlias.enabled
            if (shadow.blurRadius > 0f) {
                maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, shadow.blurRadius)
            }
        }
        context.canvas.drawPath(path, paint)
    }

    private fun drawBackgroundImage(context: DrawContext, bounds: Bounds, background: BackgroundImage, antiAlias: AntiAlias) {
        if (bounds.width <= 0f || bounds.height <= 0f) return
        val image = background.image
        val imageWidth = image.width.toFloat()
        val imageHeight = image.height.toFloat()
        if (imageWidth <= 0f || imageHeight <= 0f) return
        val paint = Paint().apply { isAntiAlias = antiAlias.enabled }
        val dst = Rect.makeXYWH(bounds.x, bounds.y, bounds.width, bounds.height)
        when (background.overflow) {
            ImageOverflow.Scale -> {
                val scale = minOf(bounds.width / imageWidth, bounds.height / imageHeight)
                val dstWidth = imageWidth * scale
                val dstHeight = imageHeight * scale
                val centeredDst = Rect.makeXYWH(
                    bounds.x + (bounds.width - dstWidth) / 2f,
                    bounds.y + (bounds.height - dstHeight) / 2f,
                    dstWidth,
                    dstHeight
                )
                val src = Rect.makeXYWH(0f, 0f, imageWidth, imageHeight)
                context.canvas.drawImageRect(image, src, centeredDst, paint)
            }
            ImageOverflow.Crop -> {
                val scale = maxOf(bounds.width / imageWidth, bounds.height / imageHeight)
                val srcWidth = bounds.width / scale
                val srcHeight = bounds.height / scale
                val src = Rect.makeXYWH(
                    (imageWidth - srcWidth) / 2f,
                    (imageHeight - srcHeight) / 2f,
                    srcWidth,
                    srcHeight
                )
                context.canvas.drawImageRect(image, src, dst, paint)
            }
            ImageOverflow.Stretch -> {
                val src = Rect.makeXYWH(0f, 0f, imageWidth, imageHeight)
                context.canvas.drawImageRect(image, src, dst, paint)
            }
        }
    }

    private fun drawBorder(context: DrawContext, bounds: Bounds, border: Border, antiAlias: AntiAlias) {
        if (border.color == Color.TRANSPARENT) return
        if (border.shape != null) {
            drawShapeBorder(context, bounds, border, antiAlias)
            return
        }
        if (border.strokeStyle == StrokeStyle.Solid) {
            val paint = Paint().apply {
                color = border.color
                mode = PaintMode.FILL
                isAntiAlias = antiAlias.enabled
            }
            if (border.top > 0) context.canvas.drawRect(Rect.makeXYWH(bounds.x, bounds.y, bounds.width, border.top), paint)
            if (border.bottom > 0) context.canvas.drawRect(Rect.makeXYWH(bounds.x, bounds.y + bounds.height - border.bottom, bounds.width, border.bottom), paint)
            if (border.left > 0) context.canvas.drawRect(Rect.makeXYWH(bounds.x, bounds.y, border.left, bounds.height), paint)
            if (border.right > 0) context.canvas.drawRect(Rect.makeXYWH(bounds.x + bounds.width - border.right, bounds.y, border.right, bounds.height), paint)
            return
        }
        fun strokePaint(strokeWidth: Float) = Paint().apply {
            color = border.color
            mode = PaintMode.STROKE
            this.strokeWidth = strokeWidth
            isAntiAlias = antiAlias.enabled
            applyStrokeStyle(border.strokeStyle)
        }
        if (border.top > 0) context.canvas.drawLine(bounds.x, bounds.y + border.top / 2f, bounds.x + bounds.width, bounds.y + border.top / 2f, strokePaint(border.top))
        if (border.bottom > 0) context.canvas.drawLine(bounds.x, bounds.y + bounds.height - border.bottom / 2f, bounds.x + bounds.width, bounds.y + bounds.height - border.bottom / 2f, strokePaint(border.bottom))
        if (border.left > 0) context.canvas.drawLine(bounds.x + border.left / 2f, bounds.y, bounds.x + border.left / 2f, bounds.y + bounds.height, strokePaint(border.left))
        if (border.right > 0) context.canvas.drawLine(bounds.x + bounds.width - border.right / 2f, bounds.y, bounds.x + bounds.width - border.right / 2f, bounds.y + bounds.height, strokePaint(border.right))
    }

    private fun drawShapeBorder(context: DrawContext, bounds: Bounds, border: Border, antiAlias: AntiAlias) {
        val strokeWidth = maxOf(border.top, border.right, border.bottom, border.left)
        if (strokeWidth <= 0f) return
        val pathWidth = (bounds.width - strokeWidth).coerceAtLeast(0f)
        val pathHeight = (bounds.height - strokeWidth).coerceAtLeast(0f)
        if (pathWidth <= 0f || pathHeight <= 0f) return
        val path = border.shape!!.createPath(pathWidth, pathHeight).apply {
            transform(Matrix33.makeTranslate(bounds.x + strokeWidth / 2f, bounds.y + strokeWidth / 2f))
        }
        val paint = Paint().apply {
            color = border.color
            mode = PaintMode.STROKE
            this.strokeWidth = strokeWidth
            isAntiAlias = antiAlias.enabled
            applyStrokeStyle(border.strokeStyle)
        }
        context.canvas.drawPath(path, paint)
    }

    /**
     * 第三阶段：绘制内容，由具体元素实现。
     */
    abstract fun drawContent(context: DrawContext)
}

class Column(private val horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Left) : BaseElement() {
    override fun measureContent(context: MeasureContext) {
        children.forEach { it.measure(context) }
        contentWidth = children.maxOfOrNull { it.width } ?: 0f
        contentHeight = children.sumOf { it.height.toDouble() }.toFloat()
    }

    override fun layoutChildren(content: Bounds) {
        val parentX = content.x
        val parentY = content.y
        val availableWidth = content.width

        var currentY = parentY
        children.forEach { child ->
            val childStartX = when (horizontalAlignment) {
                HorizontalAlignment.Left -> parentX
                HorizontalAlignment.Center -> parentX + (availableWidth - child.width) / 2
                HorizontalAlignment.Right -> parentX + (availableWidth - child.width)
            }

            child.layout(childStartX, currentY)

            currentY += child.height
        }
    }

    override fun drawContent(context: DrawContext) { /* Column 本身无内容 */ }
}

class Row(private val verticalAlignment: VerticalAlignment = VerticalAlignment.Top) : BaseElement() {
    override fun measureContent(context: MeasureContext) {
        children.forEach { it.measure(context) }
        contentWidth = children.sumOf { it.width.toDouble() }.toFloat()
        contentHeight = children.maxOfOrNull { it.height } ?: 0f
    }

    override fun layoutChildren(content: Bounds) {
        val parentX = content.x
        val parentY = content.y
        val availableHeight = content.height

        var currentX = parentX
        children.forEach { child ->
            val childStartY = when (verticalAlignment) {
                VerticalAlignment.Top -> parentY
                VerticalAlignment.Center -> parentY + (availableHeight - child.height) / 2
                VerticalAlignment.Bottom -> parentY + (availableHeight - child.height)
            }

            child.layout(currentX, childStartY)

            currentX += child.width
        }
    }

    override fun drawContent(context: DrawContext) { /* Row 本身无内容 */ }
}

class Box(
    private val horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Left,
    private val verticalAlignment: VerticalAlignment = VerticalAlignment.Top
) : BaseElement() {
    override fun measureContent(context: MeasureContext) {
        children.forEach { it.measure(context) }
        contentWidth = children.maxOfOrNull { it.width } ?: 0f
        contentHeight = children.maxOfOrNull { it.height } ?: 0f
    }

    override fun layoutChildren(content: Bounds) {
        val parentX = content.x
        val parentY = content.y
        val availableWidth = content.width
        val availableHeight = content.height

        children.forEach { child ->
            val childStartX = when (horizontalAlignment) {
                HorizontalAlignment.Left -> parentX
                HorizontalAlignment.Center -> parentX + (availableWidth - child.width) / 2
                HorizontalAlignment.Right -> parentX + (availableWidth - child.width)
            }
            val childStartY = when (verticalAlignment) {
                VerticalAlignment.Top -> parentY
                VerticalAlignment.Center -> parentY + (availableHeight - child.height) / 2
                VerticalAlignment.Bottom -> parentY + (availableHeight - child.height)
            }
            child.layout(childStartX, childStartY)
        }
    }

    override fun drawContent(context: DrawContext) { /* Box 本身无内容 */ }
}

/**
 * 画布元素，类似于 Box，用于更自由的布局场景，固定尺寸
 */
class CanvasElement(
    override var width: Float,
    override var height: Float,
    val draw: CanvasElement.(DrawCanvas, MeasureContext) -> Unit
) : BaseElement() {
    constructor(
        width: Float,
        height: Float,
        draw: CanvasElement.(DrawCanvas) -> Unit
    ) : this(width, height, { canvas, _ -> draw(canvas) })

    internal var parentX: Float = 0f
    internal var parentY: Float = 0f
    override var contentWidth = width
    override var contentHeight = height

    override fun measureContent(context: MeasureContext) {}
    override fun layoutChildren(content: Bounds) {
        this.parentX = content.x
        this.parentY = content.y
    }
    override fun drawContent(context: DrawContext) {
        draw.invoke(this, context.canvas, context.measureContext)
    }
}

/**
 * 单元格元素，用于包裹表格中的具体内容。
 */
class TableCell(
    private val horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Left,
    private val verticalAlignment: VerticalAlignment = VerticalAlignment.Top
) : BaseElement() {
    val content: UiElement?
        get() = children.firstOrNull()

    override fun measureContent(context: MeasureContext) {
        content?.measure(context)
        contentWidth = content?.width ?: 0f
        contentHeight = content?.height ?: 0f
    }

    // 关键修改：像 Box 一样，在自己的可用空间内对齐子元素
    override fun layoutChildren(content: Bounds) {
        val parentX = content.x
        val parentY = content.y
        val availableWidth = content.width
        val availableHeight = content.height

        this.content?.let { child ->
            val childStartX = when (horizontalAlignment) {
                HorizontalAlignment.Left -> parentX
                HorizontalAlignment.Center -> parentX + (availableWidth - child.width) / 2
                HorizontalAlignment.Right -> parentX + (availableWidth - child.width)
            }
            val childStartY = when (verticalAlignment) {
                VerticalAlignment.Top -> parentY
                VerticalAlignment.Center -> parentY + (availableHeight - child.height) / 2
                VerticalAlignment.Bottom -> parentY + (availableHeight - child.height)
            }
            child.layout(childStartX, childStartY)
        }
    }

    override fun drawContent(context: DrawContext) {
        // TableCell 本身不绘制，只作为容器
    }
}

/**
 * 表格行元素，管理一行内的单元格。
 */
class TableRow : BaseElement() {
    val cells: List<TableCell>
        get() = children.filterIsInstance<TableCell>()

    override fun measureContent(context: MeasureContext) {
        // 在 Table 的协调下进行测量，这里只是一个备用实现
        children.forEach { it.measure(context) }
        contentWidth = children.sumOf { it.width.toDouble() }.toFloat()
        contentHeight = children.maxOfOrNull { it.height } ?: 0f
    }

    override fun layoutChildren(content: Bounds) {
        // 布局也由 Table 统一处理
    }

    override fun drawContent(context: DrawContext) {
        // TableRow 本身不绘制
    }
}

/**
 * 表格主元素，负责协调列宽计算和整体布局。
 */
class Table(
    private val columnSpacing: Float = 0f,
    private val rowSpacing: Float = 0f
) : BaseElement() {
    private lateinit var columnWidths: List<Float>

    val rows: List<TableRow>
        get() = children.filterIsInstance<TableRow>()

    override fun measureContent(context: MeasureContext) {
        // --- 阶段一：发现列宽 ---
        val naturalColumnWidths = mutableMapOf<Int, Float>()
        rows.forEach { row ->
            row.cells.forEachIndexed { index, cell ->
                cell.measure(context)
                val currentMaxWidth = naturalColumnWidths.getOrDefault(index, 0f)
                naturalColumnWidths[index] = maxOf(currentMaxWidth, cell.width)
            }
        }
        val maxColumns = naturalColumnWidths.keys.maxOrNull()?.let { it + 1 } ?: 0
        this.columnWidths = (0 until maxColumns).map { naturalColumnWidths.getOrDefault(it, 0f) }

        // --- 阶段二：重新测量并设置最终尺寸 ---
        var totalHeight = 0f
        rows.forEach { row ->
            var maxRowHeight = 0f
            row.cells.forEachIndexed { cellIndex, cell ->
                val constrainedWidth = columnWidths[cellIndex]
                val content = cell.content
                if (content != null) {
                    val originalModifier = content.modifier
                    content.modifier = content.modifier.sizeIn(maxWidth = constrainedWidth)
                    content.measure(context)
                    content.modifier = originalModifier
                }

                cell.measure(context)
                maxRowHeight = maxOf(maxRowHeight, cell.height)
            }

            row.height = maxRowHeight
            row.contentHeight = maxRowHeight
            val rowWidth = (0 until row.cells.size).sumOf { columnWidths[it].toDouble() }.toFloat() + maxOf(0, row.cells.size - 1) * columnSpacing
            row.width = rowWidth
            row.contentWidth = rowWidth

            // =================================================================================
            // 关键新增：强制为每个 Cell 设置由 Table 计算出的最终尺寸
            // =================================================================================
            row.cells.forEachIndexed { cellIndex, cell ->
                cell.width = columnWidths[cellIndex]
                cell.height = row.height
            }
            // =================================================================================

            totalHeight += row.height
        }

        contentWidth = columnWidths.sum() + (maxOf(0, columnWidths.size - 1) * columnSpacing)
        contentHeight = totalHeight + (maxOf(0, rows.size - 1) * rowSpacing)
    }

    override fun layoutChildren(content: Bounds) {
        var currentY = content.y
        rows.forEach { row ->
            var currentX = content.x
            row.cells.forEachIndexed { index, cell ->
                // 使用最终的列宽来布局单元格
                cell.layout(currentX, currentY)
                currentX += columnWidths[index] + columnSpacing
            }
            currentY += row.height + rowSpacing
        }
    }

    override fun drawContent(context: DrawContext) {
        // Table 本身不绘制
    }
}

class Text(
    private val text: String,
    private val textModifier: TextModifier = TextModifier,
    private val fontSize: Float? = null,
    private val textColor: Int? = null,
    private val fontFamily: String? = null,
    private val textOverflow: TextOverflow? = null,
    private val textOverflowPlaceholder: String? = null,
    private val style: TextStyle? = null,
    private val underline: TextUnderline? = null
) : BaseElement() {
    constructor(
        text: AnnotatedText,
        textModifier: TextModifier = TextModifier,
        fontSize: Float? = null,
        textColor: Int? = null,
        fontFamily: String? = null,
        textOverflow: TextOverflow? = null,
        textOverflowPlaceholder: String? = null,
        style: TextStyle? = null,
        underline: TextUnderline? = null
    ) : this(
        text = text.text,
        textModifier = textModifier,
        fontSize = fontSize,
        textColor = textColor,
        fontFamily = fontFamily,
        textOverflow = textOverflow,
        textOverflowPlaceholder = textOverflowPlaceholder,
        style = style,
        underline = underline
    ) {
        annotatedText = text
    }

    private var annotatedText: AnnotatedText = AnnotatedText(text)
    private lateinit var font: Font
    private lateinit var paint: Paint
    private var textMetrics: TextMetrics = TextMetrics(0f, 0f)
    private var lines: List<String> = listOf()
    private var lineWidths: List<Float> = listOf()
    private var clusterOffsets: List<List<Float>> = listOf()
    private var styledLines: List<StyledLine> = listOf()
    private var paragraphLayout: ParagraphTextLayout? = null
    private var overflow = TextOverflow.Wrap
    private var overflowPlaceholder = TextDefaults.OVERFLOW_PLACEHOLDER
    private var underlineStyle: TextUnderline? = null
    private var effectiveLineHeight: Float = 0f
    private var effectiveLetterSpacing: Float = 0f
    private var effectiveScaleX: Float = 1f
    private var effectiveFontFamily: String = FontManager.defaultFamily
    private var effectiveFontSize: Float = 24f
    private var effectiveFontWeight: Int? = null
    private var effectiveItalic: Boolean = false
    private var enableClusterTypefaceFallback: Boolean = false

    private data class ParagraphTextLayout(
        val paragraph: Paragraph,
        val lines: List<LineMetrics>
    )

    private data class ResolvedTextSpanStyle(
        val fontSize: Float,
        val textColor: Int,
        val fontFamily: String,
        val typefaceOverride: Typeface?,
        val backgroundColor: Int?,
        val backgroundBorderColor: Int?,
        val backgroundBorderWidth: Float,
        val backgroundRadius: Float,
        val backgroundPaddingHorizontal: Float,
        val backgroundPaddingVertical: Float,
        val fontWeight: Int?,
        val italic: Boolean,
        val letterSpacing: Float
    ) {
        val hasBackground: Boolean
            get() = backgroundColor != null || (backgroundBorderColor != null && backgroundBorderWidth > 0f)

        val needsCustomBackground: Boolean
            get() = hasBackground && (
                backgroundBorderColor != null ||
                    backgroundBorderWidth > 0f ||
                    backgroundRadius > 0f ||
                    backgroundPaddingHorizontal > 0f ||
                    backgroundPaddingVertical > 0f
                )

        fun withTypefaceOverride(typeface: Typeface?): ResolvedTextSpanStyle =
            if (typeface == null || typefaceOverride === typeface) this else copy(typefaceOverride = typeface)

        fun withoutParagraphBackground(): ResolvedTextSpanStyle =
            copy(
                backgroundColor = null,
                backgroundBorderColor = null,
                backgroundBorderWidth = 0f,
                backgroundRadius = 0f,
                backgroundPaddingHorizontal = 0f,
                backgroundPaddingVertical = 0f
            )
    }

    private data class StyledCluster(
        val text: String,
        val start: Int,
        val end: Int,
        val style: ResolvedTextSpanStyle,
        val width: Float,
        val useParagraphFallback: Boolean
    )

    private data class StyledLineFragment(
        val text: String,
        val style: ResolvedTextSpanStyle,
        val xOffset: Float,
        val width: Float,
        val useParagraphFallback: Boolean
    )

    private data class StyledLine(
        val fragments: List<StyledLineFragment>,
        val width: Float
    )

    private data class StyledSpanRange(
        val start: Int,
        val end: Int,
        val style: ResolvedTextSpanStyle
    )

    private data class ClusterTypefaceResolution(
        val typefaceOverride: Typeface?,
        val useParagraphFallback: Boolean
    )

    private data class StyledBreakUnit(
        val clusters: List<StyledCluster>,
        val hardBreakAfter: Boolean = false
    )

    private data class ShapedGlyphInfo(
        val line: TextLine,
        val glyphs: ShortArray,
        val positions: FloatArray,
        val bounds: Array<Rect>
    )

    private data class ShapedText(
        val line: TextLine,
        val drawOffsetX: Float,
        val width: Float
    )

    private fun applyModifiers() {
        val modifierStyle = textModifier.fold(null as TextStyle?) { acc, m ->
            val textStyle = (m as? TextStyleModifier)?.style
            when {
                textStyle == null -> acc
                acc == null -> textStyle
                else -> acc.merge(textStyle)
            }
        }
        val finalStyle = modifierStyle?.let { styleOverride -> styleOverride.merge(style ?: TextStyle()) } ?: style
        val finalColor = textColor ?: finalStyle?.textColor ?: Color.WHITE
        val finalSize = fontSize ?: finalStyle?.fontSize ?: 24f
        val finalFamily = fontFamily ?: finalStyle?.fontFamily ?: FontManager.defaultFamily
        val finalFontWeight = finalStyle?.fontWeight
        val finalItalic = finalStyle?.italic ?: false
        val finalScaleX = finalStyle?.scaleX?.coerceAtLeast(0.01f) ?: 1f
        overflow = textOverflow ?: TextOverflow.Wrap
        overflowPlaceholder = textOverflowPlaceholder ?: TextDefaults.OVERFLOW_PLACEHOLDER
        underlineStyle = underline ?: finalStyle?.underline
        effectiveLineHeight = finalStyle?.lineHeight?.coerceAtLeast(0f) ?: 0f
        effectiveLetterSpacing = finalStyle?.letterSpacing ?: 0f
        effectiveScaleX = finalScaleX
        effectiveFontFamily = finalFamily
        effectiveFontSize = finalSize
        effectiveFontWeight = finalFontWeight
        effectiveItalic = finalItalic
        val antiAlias = modifier.fold(AntiAlias()) { acc, m -> m as? AntiAlias ?: acc }
        font = Font(FontManager.resolve(finalFamily), finalSize).apply {
            if (finalFontWeight != null) isEmboldened = finalFontWeight >= 600
            if (finalItalic) skewX = -0.25f
            scaleX = finalScaleX
        }
        paint = Paint().apply { color = finalColor; isAntiAlias = antiAlias.enabled }
    }

    private fun resolveSpanStyle(style: TextSpanStyle): ResolvedTextSpanStyle = ResolvedTextSpanStyle(
        fontSize = style.fontSize ?: effectiveFontSize,
        textColor = style.textColor ?: paint.color,
        fontFamily = style.fontFamily ?: effectiveFontFamily,
        typefaceOverride = null,
        backgroundColor = style.backgroundColor,
        backgroundBorderColor = style.backgroundBorderColor,
        backgroundBorderWidth = style.backgroundBorderWidth?.coerceAtLeast(0f) ?: 0f,
        backgroundRadius = style.backgroundRadius?.coerceAtLeast(0f) ?: 0f,
        backgroundPaddingHorizontal = style.backgroundPaddingHorizontal?.coerceAtLeast(0f) ?: 0f,
        backgroundPaddingVertical = style.backgroundPaddingVertical?.coerceAtLeast(0f) ?: 0f,
        fontWeight = style.fontWeight ?: effectiveFontWeight,
        italic = style.italic ?: effectiveItalic,
        letterSpacing = style.letterSpacing ?: effectiveLetterSpacing
    )

    private fun mergedSpanStyleForCluster(cluster: GraphemeCluster): TextSpanStyle {
        var result = TextSpanStyle()
        for (range in annotatedText.spanStyles) {
            if (range.start < cluster.end && range.end > cluster.start) {
                result = result.merge(range.item)
            }
        }
        return result
    }

    private fun resolvedSpanStyleForCluster(cluster: GraphemeCluster): ResolvedTextSpanStyle =
        resolveSpanStyle(mergedSpanStyleForCluster(cluster))

    private fun resolvedClusterStyle(cluster: GraphemeCluster): ResolvedTextSpanStyle {
        val style = resolvedSpanStyleForCluster(cluster)
        if (!enableClusterTypefaceFallback) return style
        return style.withTypefaceOverride(clusterTypefaceResolution(cluster, style).typefaceOverride)
    }

    private fun hasClusterTypefaceOverrides(): Boolean =
        segmentGraphemeClusters(annotatedText.text).any { cluster ->
            cluster.needsClusterFontChoice() &&
                resolvedSpanStyleForCluster(cluster).let { style ->
                    clusterTypefaceResolution(cluster, style).typefaceOverride != null
                }
        }

    private fun paragraphStyleRuns(): List<StyledSpanRange> {
        val clusters = segmentGraphemeClusters(annotatedText.text)
        if (clusters.isEmpty()) return emptyList()
        val runs = mutableListOf<StyledSpanRange>()
        var runStart = clusters.first().start
        var runEnd = clusters.first().end
        var runStyle = resolvedClusterStyle(clusters.first())

        fun flushRun() {
            if (runStart < runEnd) runs += StyledSpanRange(runStart, runEnd, runStyle)
        }

        for (cluster in clusters.drop(1)) {
            val style = resolvedClusterStyle(cluster)
            if (style == runStyle && cluster.start == runEnd) {
                runEnd = cluster.end
            } else {
                flushRun()
                runStart = cluster.start
                runEnd = cluster.end
                runStyle = style
            }
        }
        flushRun()
        return runs
    }

    private fun clusterTypefaceResolution(
        cluster: GraphemeCluster,
        style: ResolvedTextSpanStyle
    ): ClusterTypefaceResolution {
        val primary = style.typefaceOverride ?: FontManager.resolve(style.fontFamily)
        val needsClusterShaping = cluster.needsClusterFontChoice()
        if (typefaceSupportsCluster(primary, cluster, needsClusterShaping)) {
            return ClusterTypefaceResolution(null, useParagraphFallback = false)
        }
        if (!needsClusterShaping) {
            return ClusterTypefaceResolution(null, useParagraphFallback = true)
        }
        val candidates = clusterTypefaceCandidates(style.fontFamily, cluster.hasEnclosingMark())
        val fallback = candidates.firstOrNull { typefaceSupportsCluster(it, cluster, needsClusterShaping) }
            ?: systemTypefaceForCluster(cluster, style, needsClusterShaping)
        return if (fallback != null) {
            ClusterTypefaceResolution(fallback, useParagraphFallback = false)
        } else {
            ClusterTypefaceResolution(null, useParagraphFallback = true)
        }
    }

    private fun clusterTypefaceCandidates(primaryFamily: String, preferEnclosingMarkFont: Boolean): List<Typeface> {
        val names = linkedSetOf<String>()
        fun add(name: String?) {
            val family = name?.takeIf { it.isNotBlank() } ?: return
            names += family
        }
        add(primaryFamily)
        FontManager.graphemeClusterFallbackFamilies.forEach(::add)
        FontManager.registeredFamilies().forEach(::add)
        add(FontManager.defaultFamily)
        enclosingMarkFallbackNames.forEach(::add)

        val candidates = names.mapNotNull { family ->
            FontManager.findTypeface(family)?.let { family to it }
        }.distinctBy { it.second.uniqueId }

        val ordered = if (preferEnclosingMarkFont) {
            candidates.sortedBy { enclosingMarkFallbackRank(it.first, it.second) }
        } else {
            candidates
        }
        return ordered.map { it.second }
    }

    private fun systemTypefaceForCluster(
        cluster: GraphemeCluster,
        style: ResolvedTextSpanStyle,
        needsClusterShaping: Boolean
    ): Typeface? {
        val fontStyle = FontStyle(
            style.fontWeight ?: FontWeight.NORMAL,
            FontWidth.NORMAL,
            if (style.italic) FontSlant.OBLIQUE else FontSlant.UPRIGHT
        )
        val families = arrayOf<String?>(style.fontFamily, null)
        return cluster.codePoints
            .filter(::codePointNeedsGlyph)
            .asSequence()
            .mapNotNull { codePoint ->
                FontManager.fontMgr.matchFamiliesStyleCharacter(families, fontStyle, emptyArray(), codePoint)
            }
            .distinctBy { it.uniqueId }
            .firstOrNull { typefaceSupportsCluster(it, cluster, needsClusterShaping) }
    }

    private fun typefaceSupportsCluster(
        typeface: Typeface,
        cluster: GraphemeCluster,
        needsClusterShaping: Boolean
    ): Boolean {
        val hasGlyphs = cluster.codePoints.all { codePoint ->
            !codePointNeedsGlyph(codePoint) || typeface.getUTF32Glyph(codePoint).toInt() != 0
        }
        if (!hasGlyphs) return false
        if (!needsClusterShaping) return true
        return when {
            cluster.hasEnclosingMark() -> typefaceShapesEnclosingMark(typeface, cluster)
            cluster.hasEmojiSequenceControl() -> typefaceShapesEmojiSequence(typeface, cluster)
            cluster.hasCombiningMark() -> typefaceShapesCombiningMark(typeface, cluster)
            else -> true
        }
    }

    private fun typefaceShapesEnclosingMark(typeface: Typeface, cluster: GraphemeCluster): Boolean {
        val shaped = shapedGlyphInfo(typeface, cluster) ?: return false
        val glyphs = shaped.glyphs
        val positions = shaped.positions
        val bounds = shaped.bounds

        val markGlyphs = cluster.codePoints
            .filter { Character.getType(it) == Character.ENCLOSING_MARK.toInt() }
            .map { typeface.getUTF32Glyph(it) }
            .toSet()
        if (markGlyphs.isEmpty()) return true
        if (glyphs.size == 1) return true

        val markIndexes = glyphs.indices.filter { glyphs[it] in markGlyphs }
        val baseIndexes = glyphs.indices.filterNot { glyphs[it] in markGlyphs }
        if (markIndexes.isEmpty() || baseIndexes.isEmpty()) return false

        fun glyphLeft(index: Int): Float = positions[index * 2] + bounds[index].left
        fun glyphRight(index: Int): Float = positions[index * 2] + bounds[index].right

        val baseLeft = baseIndexes.minOf { glyphLeft(it) }
        val baseRight = baseIndexes.maxOf { glyphRight(it) }
        val baseWidth = baseRight - baseLeft
        if (baseWidth <= 0f) return false

        return markIndexes.all { index ->
            val markLeft = glyphLeft(index)
            val markRight = glyphRight(index)
            val markWidth = markRight - markLeft
            if (markWidth <= 0f) {
                false
            } else {
                val overlap = minOf(baseRight, markRight) - maxOf(baseLeft, markLeft)
                overlap >= minOf(baseWidth, markWidth) * ENCLOSING_MARK_MIN_OVERLAP_RATIO
            }
        }
    }

    private fun typefaceShapesCombiningMark(typeface: Typeface, cluster: GraphemeCluster): Boolean {
        val shaped = shapedGlyphInfo(typeface, cluster) ?: return false
        val glyphs = shaped.glyphs
        val positions = shaped.positions
        val bounds = shaped.bounds

        val markGlyphs = cluster.codePoints
            .filter {
                when (Character.getType(it)) {
                    Character.NON_SPACING_MARK.toInt(),
                    Character.COMBINING_SPACING_MARK.toInt() -> true
                    else -> false
                }
            }
            .map { typeface.getUTF32Glyph(it) }
            .toSet()
        if (markGlyphs.isEmpty()) return true
        if (glyphs.size == 1) return true

        val markIndexes = glyphs.indices.filter { glyphs[it] in markGlyphs }
        val baseIndexes = glyphs.indices.filterNot { glyphs[it] in markGlyphs }
        if (markIndexes.isEmpty() || baseIndexes.isEmpty()) return false

        fun glyphLeft(index: Int): Float = positions[index * 2] + bounds[index].left
        fun glyphRight(index: Int): Float = positions[index * 2] + bounds[index].right

        val baseLeft = baseIndexes.minOf { glyphLeft(it) }
        val baseRight = baseIndexes.maxOf { glyphRight(it) }
        val baseWidth = baseRight - baseLeft
        if (baseWidth <= 0f) return false

        return markIndexes.all { index ->
            val markLeft = glyphLeft(index)
            val markRight = glyphRight(index)
            val markWidth = markRight - markLeft
            markWidth > 0f &&
                minOf(baseRight, markRight) - maxOf(baseLeft, markLeft) >=
                minOf(baseWidth, markWidth) * ENCLOSING_MARK_MIN_OVERLAP_RATIO
        }
    }

    private fun typefaceShapesEmojiSequence(typeface: Typeface, cluster: GraphemeCluster): Boolean {
        val shaped = shapedGlyphInfo(typeface, cluster) ?: return false
        val visibleGlyphCount = cluster.codePoints.count(::codePointNeedsGlyph)
        if (visibleGlyphCount <= 0) return true

        val requiresLigatureLikeShape =
            cluster.hasZeroWidthJoiner() || cluster.hasRegionalIndicator() || cluster.hasEmojiModifier()
        if (requiresLigatureLikeShape && shaped.glyphs.size >= visibleGlyphCount) return false

        if (cluster.hasVariationSelector() && !typefaceShapesVariationSelector(typeface, cluster, shaped)) {
            return false
        }
        return true
    }

    private fun typefaceShapesVariationSelector(
        typeface: Typeface,
        cluster: GraphemeCluster,
        shaped: ShapedGlyphInfo
    ): Boolean {
        if (cluster.hasEmojiPresentationSelector() && typefaceLooksLikeEmoji(typeface)) return true
        val baseText = buildString {
            cluster.codePoints
                .filterNot(::codePointIsVariationSelector)
                .forEach { appendCodePoint(it) }
        }
        if (baseText == cluster.text) return true
        val baseLine = TextLine.make(baseText, Font(typeface, effectiveFontSize))
        return !baseLine.glyphs.contentEquals(shaped.glyphs) ||
            abs(baseLine.width - shaped.line.width) > 0.01f
    }

    private fun typefaceLooksLikeEmoji(typeface: Typeface): Boolean {
        val name = typeface.familyName.lowercase()
        return "emoji" in name || "color" in name || "twemoji" in name
    }

    private fun shapedGlyphInfo(typeface: Typeface, cluster: GraphemeCluster): ShapedGlyphInfo? {
        val font = Font(typeface, effectiveFontSize)
        val line = TextLine.make(cluster.text, font)
        val glyphs = line.glyphs
        val positions = line.positions
        val bounds = font.getBounds(glyphs)
        if (glyphs.isEmpty() || positions.size < glyphs.size * 2 || bounds.size < glyphs.size) return null
        return ShapedGlyphInfo(line, glyphs, positions, bounds)
    }

    private fun enclosingMarkFallbackRank(familyName: String, typeface: Typeface): Int {
        val text = "$familyName ${typeface.familyName}".lowercase()
        return when {
            "freemono" in text || "free mono" in text -> 0
            "gnu-unifont" in text || "gnu unifont" in text || "unifont" in text -> 1
            else -> 2
        }
    }

    private fun styledFont(style: ResolvedTextSpanStyle): Font =
        Font(style.typefaceOverride ?: FontManager.resolve(style.fontFamily), style.fontSize).apply {
            if (style.fontWeight != null) isEmboldened = style.fontWeight >= 600
            if (style.italic) skewX = -0.25f
            scaleX = effectiveScaleX
        }

    private fun styledPaint(style: ResolvedTextSpanStyle): Paint =
        Paint().apply { color = style.textColor; isAntiAlias = paint.isAntiAlias }

    private fun drawSpanBackground(
        context: DrawContext,
        style: ResolvedTextSpanStyle,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ) {
        if (!style.hasBackground || width <= 0f || height <= 0f) return
        val backgroundX = x - style.backgroundPaddingHorizontal
        val backgroundY = y - style.backgroundPaddingVertical
        val backgroundWidth = width + style.backgroundPaddingHorizontal * 2f
        val backgroundHeight = height + style.backgroundPaddingVertical * 2f
        if (backgroundWidth <= 0f || backgroundHeight <= 0f) return
        val radius = style.backgroundRadius.coerceAtMost(minOf(backgroundWidth, backgroundHeight) / 2f)
        val rect = Rect.makeXYWH(backgroundX, backgroundY, backgroundWidth, backgroundHeight)
        val path = if (radius > 0f) Shape.RoundedRect(radius).createPath(backgroundWidth, backgroundHeight)
            .apply { transform(Matrix33.makeTranslate(backgroundX, backgroundY)) }
        else null

        fun drawWith(paint: Paint) {
            if (path != null) context.canvas.drawPath(path, paint) else context.canvas.drawRect(rect, paint)
        }

        style.backgroundColor?.let { color ->
            drawWith(Paint().apply {
                this.color = color
                mode = PaintMode.FILL
                isAntiAlias = paint.isAntiAlias
            })
        }

        if (style.backgroundBorderColor != null && style.backgroundBorderWidth > 0f) {
            drawWith(Paint().apply {
                color = style.backgroundBorderColor
                mode = PaintMode.STROKE
                strokeWidth = style.backgroundBorderWidth
                isAntiAlias = paint.isAntiAlias
            })
        }
    }

    private fun measureStyledTextWidth(
        text: String,
        style: ResolvedTextSpanStyle,
        measurer: TextMeasurer,
        useParagraphFallback: Boolean = false
    ): Float =
        when {
            text.isEmpty() -> 0f
            useParagraphFallback && measurer === SkiaTextMeasurer ->
                measureParagraphFragmentWidth(text, style)
            measurer === SkiaTextMeasurer && style.typefaceOverride != null ->
                shapedText(text, styledFont(style)).width
            else -> measurer.measureTextWidth(text, styledFont(style), styledPaint(style))
        }

    private fun shapedText(text: String, font: Font): ShapedText {
        val line = TextLine.make(text, font)
        val bounds = line.textBlob?.tightBounds ?: Rect.makeWH(line.width, line.height)
        val left = minOf(0f, bounds.left)
        val right = maxOf(line.width, bounds.right)
        return ShapedText(
            line = line,
            drawOffsetX = -left,
            width = right - left
        )
    }

    private fun measureParagraphFragmentWidth(text: String, style: ResolvedTextSpanStyle): Float {
        val paragraph = buildParagraphFragment(text, style)
        val metrics = paragraph.lineMetrics.toList()
        val width = when {
            metrics.isNotEmpty() -> metrics.maxOf { it.width.toFloat() }
            else -> paragraph.maxIntrinsicWidth
        }
        return width * effectiveScaleX
    }

    private fun buildParagraphFragment(text: String, style: ResolvedTextSpanStyle): Paragraph =
        ParagraphBuilder(ParagraphStyle().apply {
            this.textStyle = paragraphTextStyle(effectiveLineHeight, style.withoutParagraphBackground())
        }, FontManager.fonts)
            .also { builder ->
                builder.pushStyle(paragraphTextStyle(effectiveLineHeight, style.withoutParagraphBackground()))
                builder.addText(text)
                builder.popStyle()
            }
            .build()
            .layout(1_000_000f)

    private fun styledCluster(cluster: GraphemeCluster, measurer: TextMeasurer): StyledCluster {
        val baseStyle = resolvedSpanStyleForCluster(cluster)
        val resolution = if (enableClusterTypefaceFallback) {
            clusterTypefaceResolution(cluster, baseStyle)
        } else {
            ClusterTypefaceResolution(null, useParagraphFallback = false)
        }
        val style = baseStyle.withTypefaceOverride(resolution.typefaceOverride)
        return StyledCluster(
            text = cluster.text,
            start = cluster.start,
            end = cluster.end,
            style = style,
            width = measureStyledTextWidth(
                text = cluster.text,
                style = style,
                measurer = measurer,
                useParagraphFallback = resolution.useParagraphFallback
            ),
            useParagraphFallback = resolution.useParagraphFallback
        )
    }

    private fun styledLine(clusters: List<StyledCluster>): StyledLine {
        if (clusters.isEmpty()) return StyledLine(emptyList(), 0f)
        val fragments = mutableListOf<StyledLineFragment>()
        var xOffset = 0f
        var fragmentText = StringBuilder()
        var fragmentStyle = clusters.first().style
        var fragmentUseParagraphFallback = clusters.first().useParagraphFallback
        var fragmentX = 0f
        var fragmentWidth = 0f
        var previousStyle: ResolvedTextSpanStyle? = null

        fun flushFragment() {
            if (fragmentText.isNotEmpty()) {
                fragments += StyledLineFragment(
                    text = fragmentText.toString(),
                    style = fragmentStyle,
                    xOffset = fragmentX,
                    width = fragmentWidth,
                    useParagraphFallback = fragmentUseParagraphFallback
                )
                fragmentText = StringBuilder()
                fragmentWidth = 0f
            }
        }

        for (cluster in clusters) {
            previousStyle?.let { previous ->
                if (previous.letterSpacing != 0f) {
                    flushFragment()
                    xOffset += previous.letterSpacing
                }
            }
            if (fragmentText.isEmpty()) {
                fragmentStyle = cluster.style
                fragmentUseParagraphFallback = cluster.useParagraphFallback
                fragmentX = xOffset
            } else if (fragmentStyle != cluster.style || fragmentUseParagraphFallback != cluster.useParagraphFallback) {
                flushFragment()
                fragmentStyle = cluster.style
                fragmentUseParagraphFallback = cluster.useParagraphFallback
                fragmentX = xOffset
            }
            fragmentText.append(cluster.text)
            fragmentWidth += cluster.width
            xOffset += cluster.width
            previousStyle = cluster.style
        }
        flushFragment()
        return StyledLine(fragments, xOffset)
    }

    private fun measureLineWidth(text: String, measurer: TextMeasurer): Float {
        if (text.isEmpty()) return 0f
        return styledLine(segmentGraphemeClusters(text).map { styledCluster(it, measurer) }).width
    }

    private fun clusterOffsets(text: String, measurer: TextMeasurer): List<Float> {
        var cursor = 0f
        return segmentGraphemeClusters(text).map { cluster ->
            val styled = styledCluster(cluster, measurer)
            val offset = cursor
            cursor += styled.width + styled.style.letterSpacing
            offset
        }
    }

    private fun overflowPlaceholderCluster(measurer: TextMeasurer): StyledCluster {
        val style = resolveSpanStyle(TextSpanStyle())
        return StyledCluster(
            text = overflowPlaceholder,
            start = -1,
            end = -1,
            style = style,
            width = measureStyledTextWidth(overflowPlaceholder, style, measurer),
            useParagraphFallback = false
        )
    }

    private fun appendOverflowPlaceholder(
        clusters: List<StyledCluster>,
        maxWidth: Float,
        measurer: TextMeasurer
    ): List<StyledCluster> {
        val placeholder = overflowPlaceholderCluster(measurer)
        if (!maxWidth.isFinite()) return clusters + placeholder
        val visible = clusters.toMutableList()
        while (visible.isNotEmpty() && styledLine(visible + placeholder).width > maxWidth) {
            visible.removeAt(visible.lastIndex)
        }
        return visible + placeholder
    }

    private fun wrapStyledClusters(
        clusters: List<StyledCluster>,
        maxWidth: Float,
        sourceText: String
    ): List<List<StyledCluster>> {
        if (clusters.isEmpty()) return listOf(emptyList())
        val result = mutableListOf<List<StyledCluster>>()
        var current = mutableListOf<StyledCluster>()

        fun commitLine(allowEmpty: Boolean = false) {
            if (current.isNotEmpty() || allowEmpty) {
                result += current
                current = mutableListOf()
            }
        }

        fun appendClusterFallback(unit: List<StyledCluster>) {
            for (cluster in unit) {
                val candidate = current + cluster
                if (current.isNotEmpty() && styledLine(candidate).width > maxWidth) {
                    commitLine()
                    current += cluster
                } else {
                    current += cluster
                }
            }
        }

        fun appendUnit(unit: List<StyledCluster>) {
            if (unit.isEmpty()) return
            val unitWidth = styledLine(unit).width
            val candidate = current + unit
            when {
                current.isEmpty() && (unitWidth > maxWidth && unit.size > 1) ->
                    appendClusterFallback(unit)
                current.isEmpty() -> current += unit
                styledLine(candidate).width <= maxWidth -> current += unit
                else -> {
                    commitLine()
                    appendUnit(unit)
                }
            }
        }

        for (unit in styledBreakUnits(clusters, sourceText)) {
            appendUnit(unit.clusters)
            if (unit.hardBreakAfter) {
                commitLine(allowEmpty = true)
            }
        }
        commitLine()
        return result.ifEmpty { listOf(emptyList()) }
    }

    private fun styledBreakUnits(
        clusters: List<StyledCluster>,
        sourceText: String
    ): List<StyledBreakUnit> {
        val units = mutableListOf<StyledBreakUnit>()
        val lineBreaks = segmentLineBreaks(sourceText)
        var clusterIndex = 0

        fun addSegment(end: Int) {
            var unit = mutableListOf<StyledCluster>()
            fun flushUnit(hardBreakAfter: Boolean = false) {
                if (unit.isNotEmpty() || hardBreakAfter) {
                    units += StyledBreakUnit(unit, hardBreakAfter)
                    unit = mutableListOf()
                }
            }

            while (clusterIndex < clusters.size && clusters[clusterIndex].end <= end) {
                val cluster = clusters[clusterIndex++]
                if (cluster.text == "\n") {
                    flushUnit(hardBreakAfter = true)
                } else {
                    unit += cluster
                }
            }
            flushUnit()
        }

        for (breakSegment in lineBreaks) {
            addSegment(breakSegment.end)
        }
        if (clusterIndex < clusters.size) {
            addSegment(Int.MAX_VALUE)
        }
        if (units.isEmpty()) {
            val fallback = clusters.filter { it.text != "\n" }
            if (fallback.isNotEmpty()) units += StyledBreakUnit(fallback)
        }
        return units
    }

    private fun measureAnnotatedContent(sizeIn: SizeIn, lineHeight: Float, measurer: TextMeasurer) {
        val clusters = segmentGraphemeClusters(annotatedText.text).map { styledCluster(it, measurer) }
        val maxWidth = sizeIn.maxWidth
        var rawLines = when {
            overflow == TextOverflow.Wrap && maxWidth.isFinite() -> wrapStyledClusters(clusters, maxWidth, annotatedText.text)
            overflow == TextOverflow.Ellipsis && maxWidth.isFinite() && styledLine(clusters).width > maxWidth ->
                listOf(appendOverflowPlaceholder(clusters, maxWidth, measurer))
            else -> listOf(clusters)
        }

        if (overflow == TextOverflow.Wrap && sizeIn.maxHeight.isFinite()) {
            val maxLines = (sizeIn.maxHeight / lineHeight).toInt().coerceAtLeast(1)
            if (rawLines.size > maxLines) {
                val visible = rawLines.take(maxLines).toMutableList()
                val lastIndex = visible.lastIndex
                visible[lastIndex] = appendOverflowPlaceholder(
                    clusters = visible[lastIndex],
                    maxWidth = maxWidth,
                    measurer = measurer
                )
                rawLines = visible
            }
        }

        styledLines = rawLines.map { styledLine(it) }
        lines = emptyList()
        lineWidths = styledLines.map { it.width }
        clusterOffsets = emptyList()
        contentWidth = if (styledLines.isEmpty()) 0f else styledLines.maxOf { it.width }
        if (maxWidth.isFinite()) contentWidth = contentWidth.coerceAtMost(maxWidth)
        contentHeight = styledLines.size * lineHeight
        if (sizeIn.maxHeight.isFinite()) {
            contentHeight = contentHeight.coerceAtMost(sizeIn.maxHeight)
        }
    }

    override fun measureContent(context: MeasureContext) {
        applyModifiers()
        paragraphLayout = null
        styledLines = emptyList()
        enableClusterTypefaceFallback = false

        val sizeIn = sizeIn()
        val measurer = context.textMeasurer
        val metrics = measurer.metrics(font)
        textMetrics = metrics
        val lineHeight = effectiveLineHeight.takeIf { it > 0f } ?: metrics.lineHeight
        effectiveLineHeight = lineHeight

        val useSkiaTextMeasurer = context.textMeasurer === SkiaTextMeasurer
        val useClusterControlledLayout = useSkiaTextMeasurer && hasClusterTypefaceOverrides()
        enableClusterTypefaceFallback = useClusterControlledLayout
        if (useSkiaTextMeasurer && !useClusterControlledLayout) {
            measureParagraphContent(sizeIn, lineHeight)
            return
        }

        if (annotatedText.spanStyles.isNotEmpty() || useClusterControlledLayout) {
            measureAnnotatedContent(sizeIn, lineHeight, measurer)
            return
        }

        if (overflow == TextOverflow.Wrap && sizeIn.maxWidth.isFinite()) {
            val words = text.split(Regex("\\s+"))
            val builtLines = mutableListOf<String>()
            var current = StringBuilder()

            fun commitLine() {
                if (current.isNotEmpty()) {
                    builtLines.add(current.toString())
                    current = StringBuilder()
                }
            }

            for (w in words) {
                if (w.isEmpty()) continue
                val attempt = if (current.isEmpty()) w else "$current $w"
                val widthAttempt = measureLineWidth(attempt, measurer)
                if (widthAttempt <= sizeIn.maxWidth) {
                    if (current.isEmpty()) current.append(w) else current.append(" ").append(w)
                } else {
                    if (current.isEmpty()) {
                        var acc = ""
                        for (cluster in textClusters(w)) {
                            val tryAcc = acc + cluster
                            if (measureLineWidth(tryAcc, measurer) <= sizeIn.maxWidth) {
                                acc = tryAcc
                            } else {
                                if (acc.isNotEmpty()) builtLines.add(acc)
                                acc = cluster
                            }
                        }
                        if (acc.isNotEmpty()) current.append(acc)
                    } else {
                        commitLine()
                        current.append(w)
                    }
                }
            }
            commitLine()

            // 受 maxHeight 限制：计算可显示最大行数
            if (sizeIn.maxHeight.isFinite()) {
                val maxLines = (sizeIn.maxHeight / lineHeight).toInt().coerceAtLeast(1)
                if (builtLines.size > maxLines) {
                    // 截断尾行并添加省略号
                    val visible = builtLines.subList(0, maxLines).toMutableList()
                    // 对最后一行做 Ellipsis 处理
                    val lastIndex = visible.lastIndex
                    val last = visible[lastIndex]
                    var truncated = last
                    if (measureLineWidth("$truncated$overflowPlaceholder", measurer) <= sizeIn.maxWidth) {
                        truncated += overflowPlaceholder
                    } else {
                        // 省略号截断按 cluster 删除，避免切开组合符号或 emoji 序列。
                        val visibleClusters = textClusters(truncated).toMutableList()
                        while (
                            visibleClusters.isNotEmpty() &&
                            measureLineWidth("${visibleClusters.joinToString("")}$overflowPlaceholder", measurer) > sizeIn.maxWidth
                        ) {
                            visibleClusters.removeAt(visibleClusters.lastIndex)
                        }
                        val visibleText = visibleClusters.joinToString("")
                        truncated = if (visibleText.isEmpty()) overflowPlaceholder else visibleText + overflowPlaceholder
                    }
                    visible[lastIndex] = truncated
                    lines = visible
                } else {
                    lines = builtLines
                }
            } else {
                lines = builtLines
            }

            // 计算内容宽高
            contentWidth = if (lines.isEmpty()) 0f else lines.maxOf { measureLineWidth(it, measurer) }
            contentHeight = (lines.size * lineHeight)
        } else {
            // Ellipsis 或者无 maxWidth 的情况：默认单行测量，必要时截断
            val measuredWidth = measureLineWidth(text, measurer)
            if (overflow == TextOverflow.Ellipsis && sizeIn.maxWidth.isFinite() && measuredWidth > sizeIn.maxWidth) {
                // 需要截断为能放下省略号的最长子串
                var lo = 0
                val clusters = textClusters(text)
                var hi = clusters.size
                var best = ""
                while (lo <= hi) {
                    val mid = (lo + hi) / 2
                    val candidate = clusters.take(mid).joinToString("")
                    if (measureLineWidth("$candidate$overflowPlaceholder", measurer) <= sizeIn.maxWidth) {
                        best = candidate; lo = mid + 1
                    } else {
                        hi = mid - 1
                    }
                }
                val finalLine = if (best.isEmpty()) overflowPlaceholder else best + overflowPlaceholder
                lines = listOf(finalLine)
                contentWidth = measureLineWidth(finalLine, measurer)
            } else {
                lines = listOf(text)
                contentWidth = measuredWidth
            }

            // maxHeight 限制：如果有限且比单行高小则裁剪高度为 maxHeight（外部布局会看到该高度）
            val lineHeightSingle = lineHeight
            contentHeight = lineHeightSingle
            if (sizeIn.maxHeight.isFinite()) {
                contentHeight = contentHeight.coerceAtMost(sizeIn.maxHeight)
                // 如果 maxHeight 小于单行高度可以强制置为 maxHeight ，显示可能被裁剪
            }
        }
        lineWidths = lines.map { measureLineWidth(it, measurer) }
        clusterOffsets = lines.map { clusterOffsets(it, measurer) }
    }

    private fun measureParagraphContent(sizeIn: SizeIn, lineHeight: Float) {
        val layoutWidth = when {
            sizeIn.maxWidth.isFinite() -> (sizeIn.maxWidth / effectiveScaleX).coerceAtLeast(1f)
            else -> 1_000_000f
        }
        val paragraph = buildParagraph(sizeIn, lineHeight).layout(layoutWidth)
        val metrics = paragraph.lineMetrics.toList()
        paragraphLayout = ParagraphTextLayout(paragraph, metrics)
        lines = emptyList()
        lineWidths = metrics.map { it.width.toFloat() * effectiveScaleX }
        val paragraphWidth = when {
            metrics.isNotEmpty() -> metrics.maxOf { it.width.toFloat() } * effectiveScaleX
            else -> paragraph.maxIntrinsicWidth * effectiveScaleX
        }
        contentWidth = if (sizeIn.maxWidth.isFinite()) paragraphWidth.coerceAtMost(sizeIn.maxWidth) else paragraphWidth
        contentHeight = paragraph.height
        if (sizeIn.maxHeight.isFinite()) {
            contentHeight = contentHeight.coerceAtMost(sizeIn.maxHeight)
        }
    }

    private fun buildParagraph(sizeIn: SizeIn, lineHeight: Float): Paragraph {
        val maxLines = when {
            overflow == TextOverflow.Ellipsis -> 1
            overflow == TextOverflow.Wrap && sizeIn.maxHeight.isFinite() ->
                (sizeIn.maxHeight / lineHeight).toInt().coerceAtLeast(1)
            else -> 0
        }
        val paragraphStyle = ParagraphStyle().apply {
            if (maxLines > 0) {
                maxLinesCount = maxLines
                ellipsis = overflowPlaceholder
            }
            textStyle = paragraphTextStyle(lineHeight)
        }
        return ParagraphBuilder(paragraphStyle, FontManager.fonts)
            .also { builder ->
                for (run in paragraphStyleRuns()) {
                    builder.pushStyle(paragraphTextStyle(lineHeight, run.style))
                    builder.addText(annotatedText.text.substring(run.start, run.end))
                    builder.popStyle()
                }
            }
            .build()
    }

    private fun paragraphTextStyle(lineHeight: Float): ParagraphTextStyle =
        paragraphTextStyle(lineHeight, resolveSpanStyle(TextSpanStyle()))

    private fun paragraphTextStyle(lineHeight: Float, style: ResolvedTextSpanStyle): ParagraphTextStyle =
        ParagraphTextStyle()
            .setColor(style.textColor)
            .setFontSize(style.fontSize)
            .setFontFamilies(arrayOf(style.fontFamily))
            .setFontStyle(
                FontStyle(
                    style.fontWeight ?: FontWeight.NORMAL,
                    FontWidth.NORMAL,
                    if (style.italic) FontSlant.OBLIQUE else FontSlant.UPRIGHT
                )
            )
            .setLetterSpacing(style.letterSpacing)
            .apply {
                style.typefaceOverride?.let { setTypeface(it) }
                if (!style.needsCustomBackground) style.backgroundColor?.let { color ->
                    setBackground(Paint().apply { this.color = color })
                }
                if (effectiveLineHeight > 0f) {
                    height = (lineHeight / style.fontSize).coerceAtLeast(0.01f)
                }
            }

    override fun layoutChildren(content: Bounds) {}

    override fun drawContent(context: DrawContext) {
        val content = contentBounds()
        val drawX = content.x
        val drawY = content.y

        paragraphLayout?.let { layout ->
            drawParagraphContent(context, layout, drawX, drawY)
            return
        }

        if (styledLines.isNotEmpty()) {
            drawStyledContent(context, drawX, drawY)
            return
        }

        val lineHeight = effectiveLineHeight
        var yCursor = drawY - textMetrics.ascent // 第一行基线
        // 绘制每一行（可能已经包括了省略号）
        for ((index, line) in lines.withIndex()) {
            val underline = underlineStyle
            val lineWidth = lineWidths.getOrElse(index) { 0f }
            if (underline != null && underline.mode == TextUnderlineMode.Block) {
                drawUnderline(context, underline, drawX, yCursor, lineWidth)
            }
            if (effectiveLetterSpacing == 0f) {
                context.canvas.drawString(line, drawX, yCursor, font, paint)
            } else {
                val offsets = clusterOffsets.getOrElse(index) { emptyList() }
                val clusters = segmentGraphemeClusters(line).map {
                    styledCluster(it, context.measureContext.textMeasurer)
                }
                clusters.forEachIndexed { clusterIndex, cluster ->
                    context.canvas.drawString(
                        cluster.text,
                        drawX + offsets.getOrElse(clusterIndex) { 0f },
                        yCursor,
                        styledFont(cluster.style),
                        styledPaint(cluster.style)
                    )
                }
            }
            if (underline != null && underline.mode == TextUnderlineMode.Line) {
                drawUnderline(context, underline, drawX, yCursor, lineWidth)
            }
            yCursor += lineHeight
        }
    }

    private fun drawStyledContent(context: DrawContext, drawX: Float, drawY: Float) {
        val lineHeight = effectiveLineHeight
        var yCursor = drawY - textMetrics.ascent // 第一行基线
        for (line in styledLines) {
            val underline = underlineStyle
            if (underline != null && underline.mode == TextUnderlineMode.Block) {
                drawUnderline(context, underline, drawX, yCursor, line.width)
            }
            for (fragment in line.fragments) {
                drawSpanBackground(
                    context = context,
                    style = fragment.style,
                    x = drawX + fragment.xOffset,
                    y = yCursor + textMetrics.ascent,
                    width = fragment.width,
                    height = lineHeight
                )
            }
            for (fragment in line.fragments) {
                drawStyledFragment(context, fragment, drawX + fragment.xOffset, yCursor)
            }
            if (underline != null && underline.mode == TextUnderlineMode.Line) {
                drawUnderline(context, underline, drawX, yCursor, line.width)
            }
            yCursor += lineHeight
        }
    }

    private fun drawStyledFragment(
        context: DrawContext,
        fragment: StyledLineFragment,
        x: Float,
        y: Float
    ) {
        if (fragment.useParagraphFallback) {
            drawParagraphFragment(context, fragment, x, y)
            return
        }
        val font = styledFont(fragment.style)
        val paint = styledPaint(fragment.style)
        if (fragment.style.typefaceOverride != null) {
            val shaped = shapedText(fragment.text, font)
            context.canvas.drawTextLine(shaped.line, x + shaped.drawOffsetX, y, paint)
        } else {
            context.canvas.drawString(fragment.text, x, y, font, paint)
        }
    }

    private fun drawParagraphFragment(
        context: DrawContext,
        fragment: StyledLineFragment,
        x: Float,
        baselineY: Float
    ) {
        val paragraph = buildParagraphFragment(fragment.text, fragment.style)
        val baseline = paragraph.lineMetrics.firstOrNull()?.baseline?.toFloat() ?: -textMetrics.ascent
        val top = baselineY - baseline
        if (effectiveScaleX == 1f) {
            context.canvas.drawParagraph(paragraph, x, top)
            return
        }
        context.canvas.save()
        try {
            context.canvas.translate(x, top)
            context.canvas.scale(effectiveScaleX, 1f)
            context.canvas.drawParagraph(paragraph, 0f, 0f)
        } finally {
            context.canvas.restore()
        }
    }

    private fun drawParagraphContent(
        context: DrawContext,
        layout: ParagraphTextLayout,
        drawX: Float,
        drawY: Float
    ) {
        if (effectiveScaleX == 1f) {
            drawParagraphSpanBackgrounds(context, layout, drawX, drawY)
            drawParagraphUnderlines(context, layout, drawX, drawY)
            context.canvas.drawParagraph(layout.paragraph, drawX, drawY)
            return
        }
        context.canvas.save()
        try {
            context.canvas.translate(drawX, drawY)
            context.canvas.scale(effectiveScaleX, 1f)
            drawParagraphSpanBackgrounds(context, layout, 0f, 0f)
            drawParagraphUnderlines(context, layout, 0f, 0f)
            context.canvas.drawParagraph(layout.paragraph, 0f, 0f)
        } finally {
            context.canvas.restore()
        }
    }

    private fun drawParagraphSpanBackgrounds(
        context: DrawContext,
        layout: ParagraphTextLayout,
        drawX: Float,
        drawY: Float
    ) {
        for (run in paragraphStyleRuns()) {
            if (!run.style.needsCustomBackground) continue
            val boxes = layout.paragraph.getRectsForRange(
                run.start,
                run.end,
                RectHeightMode.TIGHT,
                RectWidthMode.TIGHT
            )
            for (box in boxes) {
                val rect = box.rect
                drawSpanBackground(
                    context = context,
                    style = run.style,
                    x = drawX + rect.left,
                    y = drawY + rect.top,
                    width = rect.width,
                    height = rect.height
                )
            }
        }
    }

    private fun drawParagraphUnderlines(
        context: DrawContext,
        layout: ParagraphTextLayout,
        drawX: Float,
        drawY: Float
    ) {
        val underline = underlineStyle ?: return
        for (line in layout.lines) {
            val baselineY = drawY + line.baseline.toFloat()
            val lineX = drawX + line.left.toFloat()
            drawUnderline(context, underline, lineX, baselineY, line.width.toFloat())
        }
    }

    private fun drawUnderline(
        context: DrawContext,
        underline: TextUnderline,
        x: Float,
        baselineY: Float,
        textWidth: Float
    ) {
        val thickness = underline.thickness ?: maxOf(font.size * 0.08f, 1f)
        val offset = underline.offset ?: maxOf(thickness, 1f)
        val underlineX = x - underline.startPadding
        val underlineWidth = textWidth + underline.startPadding + underline.endPadding
        if (underlineWidth <= 0f || thickness <= 0f) return
        val underlinePaint = Paint().apply {
            color = underline.color ?: paint.color
            isAntiAlias = paint.isAntiAlias
        }
        when (underline.mode) {
            TextUnderlineMode.Block -> {
                context.canvas.drawRect(
                    Rect.makeXYWH(underlineX, baselineY + offset - thickness, underlineWidth, thickness),
                    underlinePaint
                )
            }
            TextUnderlineMode.Line -> {
                underlinePaint.apply {
                    mode = PaintMode.STROKE
                    strokeWidth = thickness
                    applyStrokeStyle(underline.strokeStyle)
                }
                val lineY = baselineY + offset
                context.canvas.drawLine(underlineX, lineY, underlineX + underlineWidth, lineY, underlinePaint)
            }
        }
    }
}

class ImageElement(
    private val image: Image,
    private val overflow: ImageOverflow = ImageOverflow.Scale
) : BaseElement() {
    // 记录测量后的目标绘制尺寸与源裁剪 Rect（如果需要）
    private var targetWidth: Float = 0f
    private var targetHeight: Float = 0f
    private var srcRect: Rect? = null

    override fun measureContent(context: MeasureContext) {
        // 默认原图尺寸
        val iw = image.width.toFloat()
        val ih = image.height.toFloat()

        val sizeIn = sizeIn()

        if (sizeIn.maxWidth.isFinite() || sizeIn.maxHeight.isFinite()) {
            if (overflow == ImageOverflow.Scale) {
                // 按比例缩放以适配 sizeIn（保持纵横比）
                val wLimit = if (sizeIn.maxWidth.isFinite()) sizeIn.maxWidth else iw
                val hLimit = if (sizeIn.maxHeight.isFinite()) sizeIn.maxHeight else ih
                val scale = minOf(wLimit / iw, hLimit / ih, 1f)
                targetWidth = iw * scale
                targetHeight = ih * scale
                contentWidth = targetWidth
                contentHeight = targetHeight
                srcRect = null
            } else if (overflow == ImageOverflow.Crop) {
                // Crop：目标尺寸受限于 sizeIn，但不放大图片；从中心裁剪源图
                val dstW = if (sizeIn.maxWidth.isFinite()) minOf(sizeIn.maxWidth, iw) else iw
                val dstH = if (sizeIn.maxHeight.isFinite()) minOf(sizeIn.maxHeight, ih) else ih
                // 计算源裁剪区域（中心裁剪）
                val srcLeft = ((iw - dstW) / 2f).coerceAtLeast(0f)
                val srcTop = ((ih - dstH) / 2f).coerceAtLeast(0f)
                srcRect = Rect.makeXYWH(srcLeft, srcTop, dstW, dstH)
                targetWidth = dstW
                targetHeight = dstH
                contentWidth = targetWidth
                contentHeight = targetHeight
            } else {
                targetWidth = if (sizeIn.maxWidth.isFinite()) sizeIn.maxWidth else iw
                targetHeight = if (sizeIn.maxHeight.isFinite()) sizeIn.maxHeight else ih
                contentWidth = targetWidth
                contentHeight = targetHeight
                srcRect = null
            }
        } else {
            // 无限制
            targetWidth = iw
            targetHeight = ih
            contentWidth = iw
            contentHeight = ih
            srcRect = null
        }
    }

    override fun layoutChildren(content: Bounds) {}

    override fun drawContent(context: DrawContext) {
        val content = contentBounds()
        val drawX = content.x
        val drawY = content.y

        val dstRect = Rect.makeXYWH(drawX, drawY, targetWidth, targetHeight)
        if (srcRect != null) {
            // 裁剪显示源图的一部分到目标区域（crop）
            context.canvas.drawImageRect(image, srcRect!!, dstRect, Paint())
        } else {
            // 直接缩放绘制或原尺寸绘制（scale）
            val fullSrc = Rect.makeXYWH(0f, 0f, image.width.toFloat(), image.height.toFloat())
            context.canvas.drawImageRect(image, fullSrc, dstRect, Paint())
        }
    }
}
