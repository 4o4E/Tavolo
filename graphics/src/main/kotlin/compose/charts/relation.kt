package top.e404.tavolo.draw.compose.charts

import org.jetbrains.skia.Color
import org.jetbrains.skia.Path
import org.jetbrains.skia.TextLine
import top.e404.tavolo.draw.compose.CanvasElement
import top.e404.tavolo.draw.compose.DrawCanvas
import top.e404.tavolo.draw.compose.MeasureContext
import top.e404.tavolo.draw.compose.StrokeStyle
import top.e404.tavolo.draw.compose.UiElement
import top.e404.tavolo.util.FontManager
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 在 UiElement 中添加静态关系图。
 *
 * 坐标类布局都使用图表内部局部坐标，组件会在绘制时自动叠加父级位置。
 */
fun UiElement.relationGraph(
    theme: RelationGraphTheme,
    nodes: List<RelationNode>,
    edges: List<RelationEdge>
) = add(
    CanvasElement(theme.width, theme.height) { canvas, measureContext ->
        drawRelationGraph(canvas, parentX, parentY, nodes, edges, theme, measureContext)
    }
)

/**
 * 关系图节点。
 *
 * @param id 节点唯一标识，边会通过它引用节点。
 * @param label 节点展示文本。
 * @param color 节点填充色，不传时使用主题默认色。
 * @param radius 节点半径，不传时使用主题默认半径。
 */
data class RelationNode(
    val id: String,
    val label: String = id,
    val color: Int? = null,
    val radius: Float? = null
)

/**
 * 关系图边。
 *
 * @param from 起点节点 id。
 * @param to 终点节点 id。
 * @param label 边标签，空值表示不绘制标签。
 * @param directed 是否绘制箭头。
 */
data class RelationEdge(
    val from: String,
    val to: String,
    val label: String? = null,
    val directed: Boolean = true,
    val color: Int? = null,
    val width: Float? = null,
    val style: StrokeStyle? = null
)

/**
 * 关系图布局策略。
 */
sealed interface RelationGraphLayout {
    /**
     * 固定坐标布局，坐标为图表内部局部坐标。
     */
    data class Fixed(val positions: Map<String, Pair<Float, Float>>) : RelationGraphLayout

    /**
     * 环形布局，适合小规模中心扩散或人物关系图。
     */
    data object Circular : RelationGraphLayout

    /**
     * 分层布局，适合依赖关系、调用链和流程关系。
     */
    data class Layered(val roots: List<String> = emptyList()) : RelationGraphLayout
}

/**
 * 关系图主题。
 *
 * 主题只暴露稳定配置，内部再转换为 Skia Paint 和 Font。
 */
data class RelationGraphTheme(
    val width: Float,
    val height: Float,
    val layout: RelationGraphLayout = RelationGraphLayout.Circular,
    val padding: Float = 36f,
    val nodeRadius: Float = 30f,
    val nodeFillColor: Int = Color.makeRGB(72, 149, 239),
    val nodeStrokeColor: Int = Color.WHITE,
    val nodeStrokeWidth: Float = 2f,
    val nodeTextStyle: ChartTextStyle = ChartTextStyle(
        fontSize = 14f,
        color = Color.WHITE,
        fontFamily = FontManager.defaultFamily
    ),
    val edgeColor: Int = Color.makeRGB(138, 150, 168),
    val edgeWidth: Float = 2f,
    val edgeLineStyle: StrokeStyle = StrokeStyle.Solid,
    val edgeTextStyle: ChartTextStyle = ChartTextStyle(
        fontSize = 12f,
        color = Color.makeRGB(96, 108, 128),
        fontFamily = FontManager.defaultFamily
    ),
    val arrowSize: Float = 12f
) {
    val nodeFill: ChartFill get() = ChartFill(nodeFillColor)
    val nodeStroke: ChartStroke get() = ChartStroke(nodeStrokeColor, nodeStrokeWidth)
    val edgeStroke: ChartStroke get() = ChartStroke(edgeColor, edgeWidth, edgeLineStyle)
}

private data class RelationPoint(val x: Float, val y: Float)

private data class RelationVector(val x: Float, val y: Float) {
    val length: Float get() = sqrt(x * x + y * y)
    fun normalized(): RelationVector {
        val len = length
        return if (len <= 0.0001f) RelationVector(0f, 0f) else RelationVector(x / len, y / len)
    }
}

private data class MeasuredRelationText(
    val line: TextLine,
    val box: ChartTextBox
)

private fun measureRelationText(text: String, style: ChartTextStyle, measureContext: MeasureContext): MeasuredRelationText {
    val font = style.toFont()
    val paint = style.toPaint()
    val metrics = measureContext.textMeasurer.metrics(font)
    val width = measureContext.textMeasurer.measureTextWidth(text, font, paint)
    return MeasuredRelationText(
        line = TextLine.make(text, font),
        box = ChartTextBox(
            width = width,
            height = metrics.lineHeight,
            ascent = metrics.ascent,
            descent = metrics.descent
        )
    )
}

private fun layoutRelationGraph(
    nodes: List<RelationNode>,
    edges: List<RelationEdge>,
    theme: RelationGraphTheme
): Map<String, RelationPoint> {
    val ids = nodes.map { it.id }
    if (ids.isEmpty()) return emptyMap()
    val idSet = ids.toSet()
    return when (val layout = theme.layout) {
        is RelationGraphLayout.Fixed -> fixedLayout(nodes, layout, theme)
        RelationGraphLayout.Circular -> circularLayout(nodes, theme)
        is RelationGraphLayout.Layered -> layeredLayout(nodes, edges, idSet, theme, layout)
    }
}

private fun fixedLayout(
    nodes: List<RelationNode>,
    layout: RelationGraphLayout.Fixed,
    theme: RelationGraphTheme
): Map<String, RelationPoint> {
    val fallback = circularLayout(nodes, theme)
    return nodes.associate { node ->
        val point = layout.positions[node.id]?.let { (x, y) -> RelationPoint(x, y) }
            ?: fallback.getValue(node.id)
        node.id to point
    }
}

private fun circularLayout(nodes: List<RelationNode>, theme: RelationGraphTheme): Map<String, RelationPoint> {
    val centerX = theme.width / 2f
    val centerY = theme.height / 2f
    if (nodes.size == 1) return mapOf(nodes.single().id to RelationPoint(centerX, centerY))

    val maxRadius = nodes.maxOf { it.radius ?: theme.nodeRadius }
    val availableWidth = (theme.width - theme.padding * 2f).coerceAtLeast(0f)
    val availableHeight = (theme.height - theme.padding * 2f).coerceAtLeast(0f)
    val radius = (min(availableWidth, availableHeight) / 2f - maxRadius).coerceAtLeast(maxRadius)

    return nodes.mapIndexed { index, node ->
        val angle = 2.0 * PI * index / nodes.size - PI / 2.0
        node.id to RelationPoint(
            centerX + radius * cos(angle).toFloat(),
            centerY + radius * sin(angle).toFloat()
        )
    }.toMap()
}

private fun layeredLayout(
    nodes: List<RelationNode>,
    edges: List<RelationEdge>,
    idSet: Set<String>,
    theme: RelationGraphTheme,
    layout: RelationGraphLayout.Layered
): Map<String, RelationPoint> {
    val outgoing = nodes.associate { it.id to mutableListOf<String>() }.toMutableMap()
    val incomingCount = nodes.associate { it.id to 0 }.toMutableMap()

    edges.forEach { edge ->
        if (edge.from in idSet && edge.to in idSet) {
            outgoing.getValue(edge.from).add(edge.to)
            incomingCount[edge.to] = incomingCount.getValue(edge.to) + 1
        }
    }

    val rootIds = layout.roots.filter { it in idSet }
        .ifEmpty { nodes.map { it.id }.filter { incomingCount.getValue(it) == 0 } }
        .ifEmpty { listOf(nodes.first().id) }

    val explicitRootIds = layout.roots.filter { it in idSet }.toSet()
    val layerById = mutableMapOf<String, Int>()

    fun visit(id: String, layer: Int, path: Set<String>) {
        val oldLayer = layerById[id]
        if (oldLayer == null || layer > oldLayer) {
            layerById[id] = layer
        }

        val nextPath = path + id
        outgoing.getValue(id).forEach { next ->
            if (next in nextPath) return@forEach
            // 显式指定的根节点保持在第 0 层，反向或回流边只参与绘制，不影响分层。
            if (explicitRootIds.isNotEmpty() && next in explicitRootIds) return@forEach
            visit(next, layer + 1, nextPath)
        }
    }

    rootIds.forEach { visit(it, 0, emptySet()) }

    // 对环或孤立节点给出稳定兜底层，避免布局结果依赖 Map 遍历顺序。
    var fallbackLayer = (layerById.values.maxOrNull() ?: 0) + 1
    nodes.forEach { node ->
        if (node.id !in layerById) {
            layerById[node.id] = fallbackLayer
            fallbackLayer += 1
        }
    }

    val layers = nodes.groupBy { layerById.getValue(it.id) }.toSortedMap()
    val layerCount = layers.size
    val availableWidth = (theme.width - theme.padding * 2f).coerceAtLeast(0f)
    val availableHeight = (theme.height - theme.padding * 2f).coerceAtLeast(0f)
    val layerKeys = layers.keys.toList()

    return layers.flatMap { (layerIndex, layerNodes) ->
        val keyPosition = layerKeys.indexOf(layerIndex)
        val x = theme.padding + if (layerCount <= 1) availableWidth / 2f else availableWidth * keyPosition / (layerCount - 1)
        layerNodes.mapIndexed { index, node ->
            val y = theme.padding + availableHeight * (index + 1) / (layerNodes.size + 1)
            node.id to RelationPoint(x, y)
        }
    }.toMap()
}

private fun nodeRadius(node: RelationNode, theme: RelationGraphTheme): Float =
    (node.radius ?: theme.nodeRadius).coerceAtLeast(1f)

private fun drawRelationArrow(
    canvas: DrawCanvas,
    end: RelationPoint,
    unit: RelationVector,
    color: Int,
    size: Float
) {
    if (unit.length <= 0f || size <= 0f) return

    val baseX = end.x - unit.x * size
    val baseY = end.y - unit.y * size
    val perpX = -unit.y * size * 0.45f
    val perpY = unit.x * size * 0.45f
    val arrowPaint = ChartFill(color).toPaint()
    val path = Path().apply {
        moveTo(end.x, end.y)
        lineTo(baseX + perpX, baseY + perpY)
        lineTo(baseX - perpX, baseY - perpY)
        closePath()
    }
    canvas.drawPath(path, arrowPaint)
}

private fun drawRelationSelfLoop(
    canvas: DrawCanvas,
    point: RelationPoint,
    radius: Float,
    edge: RelationEdge,
    theme: RelationGraphTheme
) {
    val color = edge.color ?: theme.edgeColor
    val stroke = ChartStroke(
        color = color,
        width = edge.width ?: theme.edgeWidth,
        style = edge.style ?: theme.edgeLineStyle
    ).toPaint()
    val loopRadius = radius * 0.95f
    val loopStartAngle = 135f
    val loopSweepAngle = 300f
    val arrowSweepGap = if (edge.directed && theme.arrowSize > 0f) {
        Math.toDegrees((theme.arrowSize / loopRadius).toDouble()).toFloat()
    } else {
        0f
    }.coerceIn(0f, loopSweepAngle - 1f)
    canvas.drawArc(
        point.x + radius * 0.25f,
        point.y - radius * 2.25f,
        point.x + radius * 0.25f + loopRadius * 2f,
        point.y - radius * 0.25f,
        loopStartAngle,
        loopSweepAngle - arrowSweepGap,
        false,
        stroke
    )
    if (edge.directed) {
        val angle = Math.toRadians((loopStartAngle + loopSweepAngle).toDouble())
        val tangent = RelationVector(-sin(angle).toFloat(), cos(angle).toFloat()).normalized()
        val end = RelationPoint(
            point.x + radius * 0.25f + loopRadius + loopRadius * cos(angle).toFloat(),
            point.y - radius * 1.25f + loopRadius * sin(angle).toFloat()
        )
        drawRelationArrow(canvas, end, tangent, color, theme.arrowSize)
    }
}

private fun requireUniqueRelationNodeIds(nodes: List<RelationNode>) {
    val duplicateId = nodes.groupingBy { it.id }
        .eachCount()
        .entries
        .firstOrNull { it.value > 1 }
        ?.key
    require(duplicateId == null) { "关系图节点 id 必须唯一，重复 id: $duplicateId" }
}

/**
 * 绘制静态关系图。
 */
fun drawRelationGraph(
    canvas: DrawCanvas,
    parentX: Float,
    parentY: Float,
    nodes: List<RelationNode>,
    edges: List<RelationEdge>,
    theme: RelationGraphTheme,
    measureContext: MeasureContext = MeasureContext()
) {
    if (nodes.isEmpty()) return
    requireUniqueRelationNodeIds(nodes)

    val idSet = nodes.map { it.id }.toSet()
    val localPositions = layoutRelationGraph(nodes, edges, theme)
    val positions = localPositions.mapValues { (_, point) ->
        RelationPoint(parentX + point.x, parentY + point.y)
    }
    val nodeById = nodes.associateBy { it.id }
    val edgeTextPaint = theme.edgeTextStyle.toPaint()
    val nodeTextPaint = theme.nodeTextStyle.toPaint()

    edges.forEach { edge ->
        val from = positions[edge.from]
        val to = positions[edge.to]
        if (from == null || to == null || edge.from !in idSet || edge.to !in idSet) return@forEach

        val fromRadius = nodeRadius(nodeById.getValue(edge.from), theme)
        val toRadius = nodeRadius(nodeById.getValue(edge.to), theme)

        if (edge.from == edge.to) {
            drawRelationSelfLoop(canvas, from, fromRadius, edge, theme)
            edge.label?.takeIf { it.isNotBlank() }?.let { label ->
                val measured = measureRelationText(label, theme.edgeTextStyle, measureContext)
                val textX = from.x + fromRadius * 1.25f
                val textY = from.y - fromRadius * 1.65f
                canvas.drawTextLine(measured.line, textX, textY, edgeTextPaint)
            }
            return@forEach
        }

        val vector = RelationVector(to.x - from.x, to.y - from.y)
        val unit = vector.normalized()
        if (unit.length <= 0f) return@forEach

        val start = RelationPoint(from.x + unit.x * fromRadius, from.y + unit.y * fromRadius)
        val end = RelationPoint(to.x - unit.x * toRadius, to.y - unit.y * toRadius)
        val edgeColor = edge.color ?: theme.edgeColor
        val edgePaint = ChartStroke(
            color = edgeColor,
            width = edge.width ?: theme.edgeWidth,
            style = edge.style ?: theme.edgeLineStyle
        ).toPaint()
        canvas.drawLine(start.x, start.y, end.x, end.y, edgePaint)

        if (edge.directed) {
            drawRelationArrow(canvas, end, unit, edgeColor, theme.arrowSize)
        }

        edge.label?.takeIf { it.isNotBlank() }?.let { label ->
            val measured = measureRelationText(label, theme.edgeTextStyle, measureContext)
            val angle = atan2(unit.y, unit.x)
            val normalX = -sin(angle) * 8f
            val normalY = cos(angle) * 8f
            val centerX = (start.x + end.x) / 2f + normalX
            val centerY = (start.y + end.y) / 2f + normalY
            val textX = centerX - measured.box.width / 2f
            val textY = centerY - (measured.box.ascent + measured.box.descent) / 2f
            canvas.drawTextLine(measured.line, textX, textY, edgeTextPaint)
        }
    }

    nodes.forEach { node ->
        val point = positions.getValue(node.id)
        val radius = nodeRadius(node, theme)
        val fillPaint = ChartFill(node.color ?: theme.nodeFillColor).toPaint()
        val strokePaint = theme.nodeStroke.toPaint()
        canvas.drawCircle(point.x, point.y, radius, fillPaint)
        if (theme.nodeStrokeWidth > 0f) {
            canvas.drawCircle(point.x, point.y, radius, strokePaint)
        }

        val measured = measureRelationText(node.label, theme.nodeTextStyle, measureContext)
        val textX = point.x - measured.box.width / 2f
        val textY = point.y - (measured.box.ascent + measured.box.descent) / 2f
        canvas.drawTextLine(measured.line, textX, textY, nodeTextPaint)
    }
}
