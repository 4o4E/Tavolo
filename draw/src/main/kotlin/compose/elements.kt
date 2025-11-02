package top.e404.skiko.draw.compose

import org.jetbrains.skia.*

object DefaultTypefaceProvider {
    var default: Typeface = Typeface.makeEmpty()
}

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
        measureContent(surface)

        // 步骤 2: 检查是否存在 Modifier.size()。如果存在，用它来覆盖内容的自然尺寸。
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
            }
        }

        this.width = finalWidth
        this.height = finalHeight
    }

    abstract fun measureContent(surface: Surface)

    override fun layout(parentX: Float, parentY: Float) {
        // parentX, parentY 是父容器为我们指定的 margin box 的起始坐标
        val margin = modifier.fold(Margin()) { acc, m -> m as? Margin ?: acc }

        // 计算并设置我们自己的 border box 的坐标 (this.x, this.y)
        this.x = parentX + margin.left
        this.y = parentY + margin.top

        // 计算我们子元素的布局起点，也就是我们 content box 的左上角
        val border = modifier.fold(Border(color = Color.TRANSPARENT)) { acc, m -> m as? Border ?: acc }
        val padding = modifier.fold(Padding()) { acc, m -> m as? Padding ?: acc }

        val childStartX = this.x + border.left + padding.left
        val childStartY = this.y + border.top + padding.top

        layoutChildren(childStartX, childStartY)
    }

    abstract fun layoutChildren(parentX: Float, parentY: Float)

    final override fun draw(canvas: Canvas) {
        var clipPath: Path? = null
        // 应用Clip
        modifier.fold(Unit) { _, mod ->
            if (mod is Clip) {
                // 裁剪是基于 border box 的
                clipPath = mod.shape.createPath(width, height)
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
        // this.x, y, width, height 直接就是 border box，不再需要计算 margin
        val antiAlias = modifier.fold(AntiAlias()) { acc, m -> m as? AntiAlias ?: acc }

        modifier.fold(Unit) { _, mod ->
            when (mod) {
                is Background -> {
                    val paint = Paint().apply { color = mod.color; isAntiAlias = antiAlias.enabled }
                    // 背景直接绘制在整个 border box 区域
                    canvas.drawRect(Rect.makeXYWH(x, y, width, height), paint)
                }

                is Border -> if (mod.color != Color.TRANSPARENT) {
                    val paint = Paint().apply {
                        color = mod.color
                        mode = PaintMode.FILL
                        isAntiAlias = antiAlias.enabled
                    }
                    // 边框绘制在 border box 的边缘
                    if (mod.top > 0) canvas.drawRect(Rect.makeXYWH(x, y, width, mod.top), paint)
                    if (mod.bottom > 0) canvas.drawRect(Rect.makeXYWH(x, y + height - mod.bottom, width, mod.bottom), paint)
                    if (mod.left > 0) canvas.drawRect(Rect.makeXYWH(x, y, mod.left, height), paint)
                    if (mod.right > 0) canvas.drawRect(Rect.makeXYWH(x + width - mod.right, y, mod.right, height), paint)
                }
            }
        }
    }

    abstract fun drawContent(canvas: Canvas)
}

class Column(private val horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Left) : BaseElement() {
    override fun measureContent(surface: Surface) {
        children.forEach { it.measure(surface) }
        contentWidth = children.maxOfOrNull {
            val margin = it.modifier.fold(Margin()) { acc, m -> m as? Margin ?: acc }
            it.width + margin.left + margin.right
        } ?: 0f
        contentHeight = children.sumOf {
            val margin = it.modifier.fold(Margin()) { acc, m -> m as? Margin ?: acc }
            (it.height + margin.top + margin.bottom).toDouble()
        }.toFloat()
    }

    override fun layoutChildren(parentX: Float, parentY: Float) {
        val padding = modifier.fold(Padding()) { acc, m -> m as? Padding ?: acc }
        val border = modifier.fold(Border(color = Color.TRANSPARENT)) { acc, m -> m as? Border ?: acc }
        val availableWidth = this.width - (border.left + border.right + padding.left + padding.right)

        var currentY = parentY
        children.forEach { child ->
            val margin = child.modifier.fold(Margin()) { acc, m -> m as? Margin ?: acc }
            val childOccupiedWidth = child.width + margin.left + margin.right

            val childStartX = when (horizontalAlignment) {
                HorizontalAlignment.Left -> parentX
                HorizontalAlignment.Center -> parentX + (availableWidth - childOccupiedWidth) / 2
                HorizontalAlignment.Right -> parentX + (availableWidth - childOccupiedWidth)
            }

            child.layout(childStartX, currentY)

            currentY += child.height + margin.top + margin.bottom
        }
    }

    override fun drawContent(canvas: Canvas) { /* Column 本身无内容 */ }
}

class Row(private val verticalAlignment: VerticalAlignment = VerticalAlignment.Top) : BaseElement() {
    override fun measureContent(surface: Surface) {
        children.forEach { it.measure(surface) }
        contentWidth = children.sumOf {
            val margin = it.modifier.fold(Margin()) { acc, m -> m as? Margin ?: acc }
            (it.width + margin.left + margin.right).toDouble()
        }.toFloat()
        contentHeight = children.maxOfOrNull {
            val margin = it.modifier.fold(Margin()) { acc, m -> m as? Margin ?: acc }
            it.height + margin.top + margin.bottom
        } ?: 0f
    }

    override fun layoutChildren(parentX: Float, parentY: Float) {
        val padding = modifier.fold(Padding()) { acc, m -> m as? Padding ?: acc }
        val border = modifier.fold(Border(color = Color.TRANSPARENT)) { acc, m -> m as? Border ?: acc }
        val availableHeight = this.height - (border.top + border.bottom + padding.top + padding.bottom)

        var currentX = parentX
        children.forEach { child ->
            val margin = child.modifier.fold(Margin()) { acc, m -> m as? Margin ?: acc }
            val childOccupiedHeight = child.height + margin.top + margin.bottom

            val childStartY = when (verticalAlignment) {
                VerticalAlignment.Top -> parentY
                VerticalAlignment.Center -> parentY + (availableHeight - childOccupiedHeight) / 2
                VerticalAlignment.Bottom -> parentY + (availableHeight - childOccupiedHeight)
            }

            child.layout(currentX, childStartY)

            currentX += child.width + margin.left + margin.right
        }
    }

    override fun drawContent(canvas: Canvas) { /* Row 本身无内容 */ }
}

class Box(
    private val horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Left,
    private val verticalAlignment: VerticalAlignment = VerticalAlignment.Top
) : BaseElement() {
    override fun measureContent(surface: Surface) {
        children.forEach { it.measure(surface) }
        contentWidth = children.maxOfOrNull {
            val margin = it.modifier.fold(Margin()) { acc, m -> m as? Margin ?: acc }
            it.width + margin.left + margin.right
        } ?: 0f
        contentHeight = children.maxOfOrNull {
            val margin = it.modifier.fold(Margin()) { acc, m -> m as? Margin ?: acc }
            it.height + margin.top + margin.bottom
        } ?: 0f
    }

    override fun layoutChildren(parentX: Float, parentY: Float) {
        val padding = modifier.fold(Padding()) { acc, m -> m as? Padding ?: acc }
        val border = modifier.fold(Border(color = Color.TRANSPARENT)) { acc, m -> m as? Border ?: acc }

        val availableWidth = this.width - (border.left + border.right + padding.left + padding.right)
        val availableHeight = this.height - (border.top + border.bottom + padding.top + padding.bottom)

        children.forEach { child ->
            val margin = child.modifier.fold(Margin()) { acc, m -> m as? Margin ?: acc }
            val childOccupiedWidth = child.width + margin.left + margin.right
            val childOccupiedHeight = child.height + margin.top + margin.bottom

            val childStartX = when (horizontalAlignment) {
                HorizontalAlignment.Left -> parentX
                HorizontalAlignment.Center -> parentX + (availableWidth - childOccupiedWidth) / 2
                HorizontalAlignment.Right -> parentX + (availableWidth - childOccupiedWidth)
            }
            val childStartY = when (verticalAlignment) {
                VerticalAlignment.Top -> parentY
                VerticalAlignment.Center -> parentY + (availableHeight - childOccupiedHeight) / 2
                VerticalAlignment.Bottom -> parentY + (availableHeight - childOccupiedHeight)
            }
            child.layout(childStartX, childStartY)
        }
    }

    override fun drawContent(canvas: Canvas) { /* Box 本身无内容 */ }
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

    override fun measureContent(surface: Surface) {
        content?.measure(surface)
        contentWidth = content?.width ?: 0f
        contentHeight = content?.height ?: 0f
    }

    // 关键修改：像 Box 一样，在自己的可用空间内对齐子元素
    override fun layoutChildren(parentX: Float, parentY: Float) {
        val padding = modifier.fold(Padding()) { acc, m -> m as? Padding ?: acc }
        val border = modifier.fold(Border(color = Color.TRANSPARENT)) { acc, m -> m as? Border ?: acc }

        val availableWidth = this.width - (border.left + border.right + padding.left + padding.right)
        val availableHeight = this.height - (border.top + border.bottom + padding.top + padding.bottom)

        content?.let { child ->
            val margin = child.modifier.fold(Margin()) { acc, m -> m as? Margin ?: acc }
            val childOccupiedWidth = child.width + margin.left + margin.right
            val childOccupiedHeight = child.height + margin.top + margin.bottom

            val childStartX = when (horizontalAlignment) {
                HorizontalAlignment.Left -> parentX
                HorizontalAlignment.Center -> parentX + (availableWidth - childOccupiedWidth) / 2
                HorizontalAlignment.Right -> parentX + (availableWidth - childOccupiedWidth)
            }
            val childStartY = when (verticalAlignment) {
                VerticalAlignment.Top -> parentY
                VerticalAlignment.Center -> parentY + (availableHeight - childOccupiedHeight) / 2
                VerticalAlignment.Bottom -> parentY + (availableHeight - childOccupiedHeight)
            }
            child.layout(childStartX, childStartY)
        }
    }

    override fun drawContent(canvas: Canvas) {
        // TableCell 本身不绘制，只作为容器
    }
}

/**
 * 表格行元素，管理一行内的单元格。
 */
class TableRow : BaseElement() {
    val cells: List<TableCell>
        get() = children.filterIsInstance<TableCell>()

    override fun measureContent(surface: Surface) {
        // 在 Table 的协调下进行测量，这里只是一个备用实现
        children.forEach { it.measure(surface) }
        contentWidth = children.sumOf { it.width.toDouble() }.toFloat()
        contentHeight = children.maxOfOrNull { it.height } ?: 0f
    }

    override fun layoutChildren(parentX: Float, parentY: Float) {
        // 布局也由 Table 统一处理
    }

    override fun drawContent(canvas: Canvas) {
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

    override fun measureContent(surface: Surface) {
        // --- 阶段一：发现列宽 ---
        val naturalColumnWidths = mutableMapOf<Int, Float>()
        rows.forEach { row ->
            row.cells.forEachIndexed { index, cell ->
                cell.measure(surface)
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
                    content.modifier = content.modifier.maxSize(maxWidth = constrainedWidth)
                    content.measure(surface)
                    content.modifier = originalModifier
                }

                cell.measure(surface)
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

    override fun layoutChildren(parentX: Float, parentY: Float) {
        var currentY = parentY
        rows.forEach { row ->
            var currentX = parentX
            row.cells.forEachIndexed { index, cell ->
                // 使用最终的列宽来布局单元格
                cell.layout(currentX, currentY)
                currentX += columnWidths[index] + columnSpacing
            }
            currentY += row.height + rowSpacing
        }
    }

    override fun drawContent(canvas: Canvas) {
        // Table 本身不绘制
    }
}

class Text(private val text: String) : BaseElement() {
    private lateinit var font: Font
    private lateinit var paint: Paint
    private var lines: List<String> = listOf()

    private fun applyModifiers() {
        var finalColor = Color.WHITE
        var finalSize = 24f
        var finalTypeface = DefaultTypefaceProvider.default
        modifier.fold(Unit) { _, mod ->
            when (mod) {
                is TextColor -> finalColor = mod.color
                is FontTypeface -> finalTypeface = mod.typeface
                is FontSize -> finalSize = mod.size
            }
        }
        val antiAlias = modifier.fold(AntiAlias()) { acc, m -> m as? AntiAlias ?: acc }
        font = Font(finalTypeface, finalSize)
        paint = Paint().apply { color = finalColor; isAntiAlias = antiAlias.enabled }
    }

    override fun measureContent(surface: Surface) {
        applyModifiers()

        val maxSize = modifier.fold(MaxSize()) { acc, m -> m as? MaxSize ?: acc }
        val overflow = modifier.fold(TextOverflowStrategy()) { acc, m -> m as? TextOverflowStrategy ?: acc }
        val lineHeight = font.metrics.descent - font.metrics.ascent

        if (overflow.strategy == TextOverflow.Wrap && maxSize.maxWidth.isFinite()) {
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
                val widthAttempt = font.measureTextWidth(attempt, paint)
                if (widthAttempt <= maxSize.maxWidth) {
                    if (current.isEmpty()) current.append(w) else current.append(" ").append(w)
                } else {
                    if (current.isEmpty()) {
                        var acc = ""
                        for (ch in w) {
                            val tryAcc = acc + ch
                            if (font.measureTextWidth(tryAcc, paint) <= maxSize.maxWidth) {
                                acc = tryAcc
                            } else {
                                if (acc.isNotEmpty()) builtLines.add(acc)
                                acc = ch.toString()
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
            if (maxSize.maxHeight.isFinite()) {
                val maxLines = (maxSize.maxHeight / lineHeight).toInt().coerceAtLeast(1)
                if (builtLines.size > maxLines) {
                    // 截断尾行并添加省略号
                    val visible = builtLines.subList(0, maxLines).toMutableList()
                    // 对最后一行做 Ellipsis 处理
                    val lastIndex = visible.lastIndex
                    val last = visible[lastIndex]
                    var truncated = last
                    if (font.measureTextWidth("$truncated${overflow.placeholder}", paint) <= maxSize.maxWidth) {
                        truncated += overflow.placeholder
                    } else {
                        // 逐字符去掉直到放得下
                        var t = truncated
                        while (t.isNotEmpty() && font.measureTextWidth("${t}${overflow.placeholder}", paint) > maxSize.maxWidth) {
                            t = t.dropLast(1)
                        }
                        truncated = if (t.isEmpty()) overflow.placeholder else t + overflow.placeholder
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
            contentWidth = if (lines.isEmpty()) 0f else lines.maxOf { font.measureTextWidth(it, paint) }
            contentHeight = (lines.size * lineHeight)
        } else {
            // Ellipsis 或者无 maxWidth 的情况：默认单行测量，必要时截断
            val measuredWidth = font.measureTextWidth(text, paint)
            if (overflow.strategy == TextOverflow.Ellipsis && maxSize.maxWidth.isFinite() && measuredWidth > maxSize.maxWidth) {
                // 需要截断为能放下省略号的最长子串
                var lo = 0
                var hi = text.length
                var best = ""
                while (lo <= hi) {
                    val mid = (lo + hi) / 2
                    val candidate = text.substring(0, mid)
                    if (font.measureTextWidth("$candidate${overflow.placeholder}", paint) <= maxSize.maxWidth) {
                        best = candidate; lo = mid + 1
                    } else {
                        hi = mid - 1
                    }
                }
                val finalLine = if (best.isEmpty()) overflow.placeholder else best + overflow.placeholder
                lines = listOf(finalLine)
                contentWidth = font.measureTextWidth(finalLine, paint)
            } else {
                lines = listOf(text)
                contentWidth = measuredWidth
            }

            // maxHeight 限制：如果有限且比单行高小则裁剪高度为 maxHeight（外部布局会看到该高度）
            val lineHeightSingle = font.metrics.descent - font.metrics.ascent
            contentHeight = lineHeightSingle
            if (maxSize.maxHeight.isFinite()) {
                contentHeight = contentHeight.coerceAtMost(maxSize.maxHeight)
                // 如果 maxHeight 小于单行高度可以强制置为 maxHeight ，显示可能被裁剪
            }
        }
    }

    override fun layoutChildren(parentX: Float, parentY: Float) {}

    override fun drawContent(canvas: Canvas) {
        // val margin = modifier.fold(Margin()) { acc, m -> m as? Margin ?: acc }
        val border = modifier.fold(Border(color = Color.TRANSPARENT)) { acc, m -> m as? Border ?: acc }
        val padding = modifier.fold(Padding()) { acc, m -> m as? Padding ?: acc }

        val drawX = this.x + border.left + padding.left
        val drawY = this.y + border.top + padding.top

        val lineHeight = font.metrics.descent - font.metrics.ascent
        var yCursor = drawY - font.metrics.ascent // baseline for first line
        // 绘制每一行（可能已经包括了省略号）
        for (line in lines) {
            canvas.drawString(line, drawX, yCursor, font, paint)
            yCursor += lineHeight
        }
    }
}

class ImageElement(private val image: Image) : BaseElement() {
    // 记录测量后的目标绘制尺寸与源裁剪 Rect（如果需要）
    private var targetWidth: Float = 0f
    private var targetHeight: Float = 0f
    private var srcRect: Rect? = null

    override fun measureContent(surface: Surface) {
        // 默认原图尺寸
        val iw = image.width.toFloat()
        val ih = image.height.toFloat()

        val maxSize = modifier.fold(MaxSize()) { acc, m -> m as? MaxSize ?: acc }
        val overflow = modifier.fold(ImageOverflowStrategy()) { acc, m -> m as? ImageOverflowStrategy ?: acc }

        if (maxSize.maxWidth.isFinite() || maxSize.maxHeight.isFinite()) {
            if (overflow.strategy == ImageOverflow.Scale) {
                // 按比例缩放以适配 maxSize（保持纵横比）
                val wLimit = if (maxSize.maxWidth.isFinite()) maxSize.maxWidth else iw
                val hLimit = if (maxSize.maxHeight.isFinite()) maxSize.maxHeight else ih
                val scale = minOf(wLimit / iw, hLimit / ih, 1f)
                targetWidth = iw * scale
                targetHeight = ih * scale
                contentWidth = targetWidth
                contentHeight = targetHeight
                srcRect = null
            } else {
                // Crop：目标尺寸受限于 maxSize，但不放大图片；从中心裁剪源图
                val dstW = if (maxSize.maxWidth.isFinite()) minOf(maxSize.maxWidth, iw) else iw
                val dstH = if (maxSize.maxHeight.isFinite()) minOf(maxSize.maxHeight, ih) else ih
                // 计算源裁剪区域（中心裁剪）
                val srcLeft = ((iw - dstW) / 2f).coerceAtLeast(0f)
                val srcTop = ((ih - dstH) / 2f).coerceAtLeast(0f)
                srcRect = Rect.makeXYWH(srcLeft, srcTop, dstW, dstH)
                targetWidth = dstW
                targetHeight = dstH
                contentWidth = targetWidth
                contentHeight = targetHeight
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

    override fun layoutChildren(parentX: Float, parentY: Float) {}

    override fun drawContent(canvas: Canvas) {
        val border = modifier.fold(Border(color = Color.TRANSPARENT)) { acc, m -> m as? Border ?: acc }
        val padding = modifier.fold(Padding()) { acc, m -> m as? Padding ?: acc }

        val drawX = this.x + border.left + padding.left
        val drawY = this.y + border.top + padding.top

        val dstRect = Rect.makeXYWH(drawX, drawY, targetWidth, targetHeight)
        if (srcRect != null) {
            // 裁剪显示源图的一部分到目标区域（crop）
            canvas.drawImageRect(image, srcRect!!, dstRect, Paint())
        } else {
            // 直接缩放绘制或原尺寸绘制（scale）
            val fullSrc = Rect.makeXYWH(0f, 0f, image.width.toFloat(), image.height.toFloat())
            canvas.drawImageRect(image, fullSrc, dstRect, Paint())
        }
    }
}
