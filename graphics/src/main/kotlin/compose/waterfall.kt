package top.e404.tavolo.draw.compose

/**
 * 按列宽测量子元素，并把每个子元素放入当前高度最短的列。
 */
class WaterfallLayout(
    columns: Int,
    width: Float,
    columnSpacing: Float = 0f,
    rowSpacing: Float = 0f,
) : BaseElement() {
    private data class Placement(val x: Float, val y: Float)

    private val columns = columns.coerceAtLeast(1)
    private val layoutWidth = width.coerceAtLeast(0f)
    private val columnSpacing = columnSpacing.coerceAtLeast(0f)
    private val rowSpacing = rowSpacing.coerceAtLeast(0f)
    private val spacingWidth = this.columnSpacing * (this.columns - 1)
    private var placements: List<Placement> = emptyList()

    val columnWidth: Float =
        ((layoutWidth - spacingWidth) / this.columns).coerceAtLeast(1f)

    // 宽度不足时按最小列宽反推实际宽度，避免布局坐标超出组件边界。
    private val measuredLayoutWidth: Float = columnWidth * this.columns + spacingWidth

    override fun measureContent(context: MeasureContext) {
        val columnHeights = FloatArray(columns)
        val nextPlacements = ArrayList<Placement>(children.size)

        children.forEach { child ->
            measureChildInColumn(context, child)
            val columnIndex = columnHeights.indices.minBy { columnHeights[it] }
            val y = if (columnHeights[columnIndex] > 0f) columnHeights[columnIndex] + rowSpacing else 0f
            val x = columnIndex * (columnWidth + columnSpacing)
            nextPlacements += Placement(x, y)
            columnHeights[columnIndex] = y + child.height
        }

        placements = nextPlacements
        contentWidth = if (children.isEmpty()) 0f else measuredLayoutWidth
        contentHeight = columnHeights.maxOrNull() ?: 0f
    }

    private fun measureChildInColumn(context: MeasureContext, child: UiElement) {
        val originalModifier = child.modifier
        // 同时约束内容测量和最终外框，避免子元素自己的 sizeIn 或 padding 撑出列宽。
        child.modifier = Modifier.sizeIn(maxWidth = columnWidth)
            .then(originalModifier)
            .then(originalModifier.constrainedContentSizeIn(columnWidth))
        try {
            child.measure(context)
        } finally {
            child.modifier = originalModifier
        }
    }

    private fun Modifier.constrainedContentSizeIn(maxWidth: Float): SizeIn {
        val originalSizeIn = fold(SizeIn()) { acc, modifier -> modifier as? SizeIn ?: acc }
        val constrainedMaxWidth = minOf(originalSizeIn.maxWidth, maxWidth)
        return originalSizeIn.copy(
            minWidth = minOf(originalSizeIn.minWidth, constrainedMaxWidth),
            maxWidth = constrainedMaxWidth
        )
    }

    override fun layoutChildren(content: Bounds) {
        children.forEachIndexed { index, child ->
            val placement = placements.getOrNull(index) ?: return@forEachIndexed
            child.layout(content.x + placement.x, content.y + placement.y)
        }
    }

    override fun drawContent(context: DrawContext) {
        // 仅负责瀑布流测量和布局，具体绘制由子元素完成。
    }
}
