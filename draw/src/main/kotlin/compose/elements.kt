package top.e404.skiko.draw.compose

import org.jetbrains.skia.*

object DefaultTypefaceProvider {
    var default: Typeface = Typeface.makeEmpty()
}

class Column(private val horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Left) : BaseElement() {
    override fun measureContent(surface: Surface) {
        children.forEach { it.measure(surface) }
        contentWidth = children.maxOfOrNull { it.width } ?: 0f
        contentHeight = children.sumOf { it.height.toDouble() }.toFloat()
    }

    override fun layoutChildren(parentX: Float, parentY: Float) {
        var currentY = parentY
        children.forEach { child ->
            val childX = when (horizontalAlignment) {
                HorizontalAlignment.Left -> parentX
                HorizontalAlignment.Center -> parentX + (this.contentWidth - child.width) / 2
                HorizontalAlignment.Right -> parentX + (this.contentWidth - child.width)
            }
            child.layout(childX, currentY)
            currentY += child.height
        }
    }

    override fun drawContent(canvas: Canvas) { /* Column 本身无内容 */
    }
}

class Row(private val verticalAlignment: VerticalAlignment = VerticalAlignment.Top) : BaseElement() {
    override fun measureContent(surface: Surface) {
        children.forEach { it.measure(surface) }
        contentWidth = children.sumOf { it.width.toDouble() }.toFloat()
        contentHeight = children.maxOfOrNull { it.height } ?: 0f
    }

    override fun layoutChildren(parentX: Float, parentY: Float) {
        var currentX = parentX
        children.forEach { child ->
            val childY = when (verticalAlignment) {
                VerticalAlignment.Top -> parentY
                VerticalAlignment.Center -> parentY + (this.contentHeight - child.height) / 2
                VerticalAlignment.Bottom -> parentY + (this.contentHeight - child.height)
            }
            child.layout(currentX, childY)
            currentX += child.width
        }
    }

    override fun drawContent(canvas: Canvas) { /* Row 本身无内容 */
    }
}

class Box : BaseElement() {
    override fun measureContent(surface: Surface) {
        children.forEach { it.measure(surface) }
        contentWidth = children.maxOfOrNull { it.width } ?: 0f
        contentHeight = children.maxOfOrNull { it.height } ?: 0f
    }

    override fun layoutChildren(parentX: Float, parentY: Float) {
        children.forEach { it.layout(parentX, parentY) } // 所有子元素都从同一点开始布局
    }

    override fun drawContent(canvas: Canvas) { /* Box 本身无内容，靠子元素和Modifier绘制 */
    }
}

class Text(private val text: String) : BaseElement() {
    private lateinit var font: Font
    private lateinit var paint: Paint

    // 新增：换行后的行集合（measure 后填充）
    private var lines: List<String> = listOf()

    private fun applyModifiers() {
        var finalColor = Color.BLACK
        var finalSize = 24f
        var finalTypeface = DefaultTypefaceProvider.default
        modifier.fold(Unit) { _, mod ->
            when (mod) {
                is TextColor -> finalColor = mod.color
                is FontTypeface -> finalTypeface = mod.typeface
                is FontSize -> {
                    finalSize = mod.size
                }
            }
        }
        val antiAlias = modifier.fold(AntiAlias()) { acc, m -> m as? AntiAlias ?: acc }
        font = Font(finalTypeface, finalSize)
        paint = Paint().apply { color = finalColor; isAntiAlias = antiAlias.enabled }
    }

    override fun measureContent(surface: Surface) {
        applyModifiers()

        // 读取策略和最大尺寸
        val maxSize = modifier.fold(MaxSize()) { acc, m -> if (m is MaxSize) m else acc }
        val overflow = modifier.fold(TextOverflowStrategy()) { acc, m -> m as? TextOverflowStrategy ?: acc }

        val lineHeight = font.metrics.descent - font.metrics.ascent

        if (overflow.strategy == TextOverflow.Wrap && maxSize.maxWidth.isFinite()) {
            // 简单按空格换行的实现，遇不到空格则按字符拆分
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
                val attempt = if (current.isEmpty()) w else current.toString() + " " + w
                val widthAttempt = font.measureTextWidth(attempt, paint)
                if (widthAttempt <= maxSize.maxWidth) {
                    if (current.isEmpty()) current.append(w) else {
                        current.append(" "); current.append(w)
                    }
                } else {
                    if (current.isEmpty()) {
                        // 单个词就超宽，按字符切分
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
                        while (t.isNotEmpty() && font.measureTextWidth(
                                "${t}${overflow.placeholder}",
                                paint
                            ) > maxSize.maxWidth
                        ) {
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
        val margin = modifier.fold(Margin()) { acc, m -> if (m is Margin) m else acc }
        val border = modifier.fold(Border(color = Color.TRANSPARENT)) { acc, m -> if (m is Border) m else acc }
        val padding = modifier.fold(Padding()) { acc, m -> if (m is Padding) m else acc }

        val drawX = this.x + border.left + padding.left
        val drawY = this.y + border.top + padding.top

        val lineHeight = font.metrics.descent - font.metrics.ascent
        var yCursor = drawY - font.metrics.ascent // baseline for first line
        // 绘制每一行（可能已经包括了省略号）
        for (line in lines) {
            // 如果超出 contentHeight 则停止绘制（以防因为 rounding）
            if (yCursor - drawY > contentHeight + 0.001f) break
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
        var iw = image.width.toFloat()
        var ih = image.height.toFloat()

        val maxSize = modifier.fold(MaxSize()) { acc, m -> if (m is MaxSize) m else acc }
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
                val srcR = Rect.makeXYWH(srcLeft, srcTop, dstW, dstH)
                srcRect = srcR
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
        val margin = modifier.fold(Margin()) { acc, m -> if (m is Margin) m else acc }
        val border = modifier.fold(Border(color = Color.TRANSPARENT)) { acc, m -> if (m is Border) m else acc }
        val padding = modifier.fold(Padding()) { acc, m -> if (m is Padding) m else acc }
        val drawX = this.x + border.left + padding.left + margin.left
        val drawY = this.y + border.top + padding.top + margin.top

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
