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
import kotlin.math.max
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
 * 关系图节点绘制器。
 *
 * 调用方可以通过主题设置全局节点绘制器，也可以在单个节点上覆盖。
 */
fun interface RelationNodeDrawer {
    fun draw(scope: RelationNodeDrawScope)
}

/**
 * 关系图边绘制器。
 *
 * 普通边和自环边都通过同一个绘制器扩展，scope 中的 isSelfLoop 用于区分场景。
 */
fun interface RelationEdgeDrawer {
    fun draw(scope: RelationEdgeDrawScope)
}

/**
 * 关系图节点。
 *
 * @param id 节点唯一标识，边会通过它引用节点。
 * @param label 节点展示文本。
 * @param color 节点填充色，不传时使用主题默认色。
 * @param radius 节点半径，不传时使用主题默认半径。
 * @param drawer 节点专属绘制器，不传时使用主题节点绘制器。
 */
data class RelationNode(
    val id: String,
    val label: String = id,
    val color: Int? = null,
    val radius: Float? = null,
    val drawer: RelationNodeDrawer? = null
)

/**
 * 关系图边。
 *
 * @param from 起点节点 id。
 * @param to 终点节点 id。
 * @param label 边标签，空值表示不绘制标签。
 * @param directed 是否绘制箭头。
 * @param drawer 边专属绘制器，不传时使用主题边绘制器。
 */
data class RelationEdge(
    val from: String,
    val to: String,
    val label: String? = null,
    val directed: Boolean = true,
    val color: Int? = null,
    val width: Float? = null,
    val style: StrokeStyle? = null,
    val drawer: RelationEdgeDrawer? = null
)

/**
 * 关系图布局策略。
 *
 * 调用方处理布局类型时建议保留 else 分支，以便后续新增布局策略时源码仍可平滑升级。
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

    /**
     * 力导向布局，适合社交关系、人物关系等有环、多中心的复杂网络。
     *
     * @param iterations 迭代次数，越大越稳定但耗时越高。
     * @param linkDistance 关系边倾向保持的节点中心距离。
     * @param repulsion 节点之间的排斥强度。
     * @param centerStrength 节点向画布中心收拢的强度，过大会让图过密。
     * @param collisionPadding 节点半径之外额外保留的碰撞间距。
     * @param initialRadiusRatio 初始环形半径占画布短边的比例。
     */
    data class Force(
        val iterations: Int = 520,
        val linkDistance: Float = 260f,
        val repulsion: Float = 18000f,
        val centerStrength: Float = 0.0024f,
        val collisionPadding: Float = 80f,
        val initialRadiusRatio: Float = 0.28f
    ) : RelationGraphLayout
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
    val arrowSize: Float = 12f,
    val nodeDrawer: RelationNodeDrawer = RelationNodeDrawer { it.drawDefault() },
    val edgeDrawer: RelationEdgeDrawer = RelationEdgeDrawer { it.drawDefault() }
) {
    val nodeFill: ChartFill get() = ChartFill(nodeFillColor)
    val nodeStroke: ChartStroke get() = ChartStroke(nodeStrokeColor, nodeStrokeWidth)
    val edgeStroke: ChartStroke get() = ChartStroke(edgeColor, edgeWidth, edgeLineStyle)
}

/**
 * 节点绘制上下文。
 *
 * 坐标为最终画布坐标，调用方可以先绘制背景效果，再调用 drawDefault() 复用默认节点。
 */
class RelationNodeDrawScope(
    val canvas: DrawCanvas,
    val measureContext: MeasureContext,
    val node: RelationNode,
    val theme: RelationGraphTheme,
    val centerX: Float,
    val centerY: Float,
    val radius: Float
) {
    fun drawDefault() {
        drawDefaultRelationNode(this)
    }
}

/**
 * 边绘制上下文。
 *
 * 普通边的 start/end 是扣除节点半径后的线段端点；自环边的 start/end 等于节点中心。
 */
class RelationEdgeDrawScope(
    val canvas: DrawCanvas,
    val measureContext: MeasureContext,
    val edge: RelationEdge,
    val fromNode: RelationNode,
    val toNode: RelationNode,
    val theme: RelationGraphTheme,
    val fromCenterX: Float,
    val fromCenterY: Float,
    val toCenterX: Float,
    val toCenterY: Float,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val fromRadius: Float,
    val toRadius: Float,
    val isSelfLoop: Boolean
) {
    fun drawDefault() {
        drawDefaultRelationEdge(this)
    }
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
        is RelationGraphLayout.Force -> forceLayout(nodes, edges, idSet, theme, layout)
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

private data class ForceNodeState(
    val order: Int,
    val id: String,
    val radius: Float,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f
)

private fun forceLayout(
    nodes: List<RelationNode>,
    edges: List<RelationEdge>,
    idSet: Set<String>,
    theme: RelationGraphTheme,
    layout: RelationGraphLayout.Force
): Map<String, RelationPoint> {
    if (nodes.size == 1) {
        return mapOf(nodes.single().id to RelationPoint(theme.width / 2f, theme.height / 2f))
    }

    val maxRadius = nodes.maxOf { nodeRadius(it, theme) }
    val padding = max(theme.padding, maxRadius + 8f)
    val minX = padding
    val maxX = (theme.width - padding).coerceAtLeast(minX)
    val minY = padding
    val maxY = (theme.height - padding).coerceAtLeast(minY)
    val centerX = theme.width / 2f
    val centerY = theme.height / 2f
    val initialRadius = min(theme.width, theme.height) * layout.initialRadiusRatio.coerceAtLeast(0f)

    val states = nodes.mapIndexed { index, node ->
        val angle = 2.0 * PI * index / nodes.size - PI / 2.0
        ForceNodeState(
            order = index,
            id = node.id,
            radius = nodeRadius(node, theme),
            x = (centerX + cos(angle).toFloat() * initialRadius).coerceIn(minX, maxX),
            y = (centerY + sin(angle).toFloat() * initialRadius).coerceIn(minY, maxY)
        )
    }
    val stateById = states.associateBy { it.id }
    val forceEdges = edges.mapNotNull { edge ->
        val from = stateById[edge.from]
        val to = stateById[edge.to]
        if (from == null || to == null || edge.from !in idSet || edge.to !in idSet || edge.from == edge.to) {
            null
        } else {
            from to to
        }
    }

    val iterations = layout.iterations.coerceAtLeast(0)
    repeat(iterations) { iteration ->
        states.forEach {
            it.vx = 0f
            it.vy = 0f
        }

        for (i in states.indices) {
            for (j in i + 1 until states.size) {
                val a = states[i]
                val b = states[j]
                val dx = b.x - a.x
                val dy = b.y - a.y
                val distance = sqrt(dx * dx + dy * dy)
                val (ux, uy) = forceDirection(a, b, distance, dx, dy)
                val safeDistance = distance.coerceAtLeast(1f)
                val force = layout.repulsion.coerceAtLeast(0f) / (safeDistance * safeDistance)
                val fx = ux * force
                val fy = uy * force
                a.vx -= fx
                a.vy -= fy
                b.vx += fx
                b.vy += fy
            }
        }

        for ((from, to) in forceEdges) {
            val dx = to.x - from.x
            val dy = to.y - from.y
            val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val target = layout.linkDistance.coerceAtLeast(from.radius + to.radius + layout.collisionPadding)
            val force = (distance - target) * 0.035f
            val fx = dx / distance * force
            val fy = dy / distance * force
            from.vx += fx
            from.vy += fy
            to.vx -= fx
            to.vy -= fy
        }

        val centerStrength = layout.centerStrength.coerceAtLeast(0f)
        for (node in states) {
            node.vx += (centerX - node.x) * centerStrength
            node.vy += (centerY - node.y) * centerStrength
        }

        val temperature = if (iterations == 0) 0f else (1f - iteration.toFloat() / iterations).coerceAtLeast(0.08f)
        for (node in states) {
            node.x = (node.x + node.vx * temperature).coerceIn(minX, maxX)
            node.y = (node.y + node.vy * temperature).coerceIn(minY, maxY)
        }

        separateForceNodes(states, layout.collisionPadding.coerceAtLeast(0f), minX, maxX, minY, maxY)
    }

    if (iterations == 0) {
        separateForceNodes(states, layout.collisionPadding.coerceAtLeast(0f), minX, maxX, minY, maxY)
    }

    return states.associate { it.id to RelationPoint(it.x, it.y) }
}

private fun separateForceNodes(
    nodes: List<ForceNodeState>,
    collisionPadding: Float,
    minX: Float,
    maxX: Float,
    minY: Float,
    maxY: Float
) {
    for (i in nodes.indices) {
        for (j in i + 1 until nodes.size) {
            val a = nodes[i]
            val b = nodes[j]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val distance = sqrt(dx * dx + dy * dy)
            val minDistance = a.radius + b.radius + collisionPadding
            if (distance >= minDistance) continue
            val offset = (minDistance - distance) / 2f
            val (ux, uy) = forceDirection(a, b, distance, dx, dy)
            val ox = ux * offset
            val oy = uy * offset
            a.x = (a.x - ox).coerceIn(minX, maxX)
            a.y = (a.y - oy).coerceIn(minY, maxY)
            b.x = (b.x + ox).coerceIn(minX, maxX)
            b.y = (b.y + oy).coerceIn(minY, maxY)
        }
    }
}

private fun forceDirection(
    a: ForceNodeState,
    b: ForceNodeState,
    distance: Float,
    dx: Float,
    dy: Float
): Pair<Float, Float> {
    if (distance > 0.0001f) return dx / distance to dy / distance

    // 节点完全重合时使用稳定方向拆开，避免零向量导致力和碰撞分离失效。
    val angle = 2.399963229728653 * (a.order + b.order + 1)
    return cos(angle).toFloat() to sin(angle).toFloat()
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

private fun drawRelationEdgeLabel(
    canvas: DrawCanvas,
    measureContext: MeasureContext,
    edge: RelationEdge,
    theme: RelationGraphTheme,
    textX: Float,
    textY: Float
) {
    edge.label?.takeIf { it.isNotBlank() }?.let { label ->
        val measured = measureRelationText(label, theme.edgeTextStyle, measureContext)
        canvas.drawTextLine(measured.line, textX, textY, theme.edgeTextStyle.toPaint())
    }
}

private fun drawDefaultRelationEdge(scope: RelationEdgeDrawScope) {
    val edgeColor = scope.edge.color ?: scope.theme.edgeColor
    if (scope.isSelfLoop) {
        val point = RelationPoint(scope.fromCenterX, scope.fromCenterY)
        drawRelationSelfLoop(scope.canvas, point, scope.fromRadius, scope.edge, scope.theme)
        drawRelationEdgeLabel(
            canvas = scope.canvas,
            measureContext = scope.measureContext,
            edge = scope.edge,
            theme = scope.theme,
            textX = scope.fromCenterX + scope.fromRadius * 1.25f,
            textY = scope.fromCenterY - scope.fromRadius * 1.65f
        )
        return
    }

    val edgePaint = ChartStroke(
        color = edgeColor,
        width = scope.edge.width ?: scope.theme.edgeWidth,
        style = scope.edge.style ?: scope.theme.edgeLineStyle
    ).toPaint()
    scope.canvas.drawLine(scope.startX, scope.startY, scope.endX, scope.endY, edgePaint)

    val unit = RelationVector(scope.endX - scope.startX, scope.endY - scope.startY).normalized()
    if (scope.edge.directed) {
        drawRelationArrow(scope.canvas, RelationPoint(scope.endX, scope.endY), unit, edgeColor, scope.theme.arrowSize)
    }

    scope.edge.label?.takeIf { it.isNotBlank() }?.let { label ->
        val measured = measureRelationText(label, scope.theme.edgeTextStyle, scope.measureContext)
        val angle = atan2(unit.y, unit.x)
        val normalX = -sin(angle) * 8f
        val normalY = cos(angle) * 8f
        val centerX = (scope.startX + scope.endX) / 2f + normalX
        val centerY = (scope.startY + scope.endY) / 2f + normalY
        val textX = centerX - measured.box.width / 2f
        val textY = centerY - (measured.box.ascent + measured.box.descent) / 2f
        scope.canvas.drawTextLine(measured.line, textX, textY, scope.theme.edgeTextStyle.toPaint())
    }
}

private fun drawDefaultRelationNode(scope: RelationNodeDrawScope) {
    val fillPaint = ChartFill(scope.node.color ?: scope.theme.nodeFillColor).toPaint()
    val strokePaint = scope.theme.nodeStroke.toPaint()
    scope.canvas.drawCircle(scope.centerX, scope.centerY, scope.radius, fillPaint)
    if (scope.theme.nodeStrokeWidth > 0f) {
        scope.canvas.drawCircle(scope.centerX, scope.centerY, scope.radius, strokePaint)
    }

    val measured = measureRelationText(scope.node.label, scope.theme.nodeTextStyle, scope.measureContext)
    val textX = scope.centerX - measured.box.width / 2f
    val textY = scope.centerY - (measured.box.ascent + measured.box.descent) / 2f
    scope.canvas.drawTextLine(measured.line, textX, textY, scope.theme.nodeTextStyle.toPaint())
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

    edges.forEach { edge ->
        val from = positions[edge.from]
        val to = positions[edge.to]
        if (from == null || to == null || edge.from !in idSet || edge.to !in idSet) return@forEach

        val fromNode = nodeById.getValue(edge.from)
        val toNode = nodeById.getValue(edge.to)
        val fromRadius = nodeRadius(fromNode, theme)
        val toRadius = nodeRadius(toNode, theme)

        if (edge.from == edge.to) {
            (edge.drawer ?: theme.edgeDrawer).draw(
                RelationEdgeDrawScope(
                    canvas = canvas,
                    measureContext = measureContext,
                    edge = edge,
                    fromNode = fromNode,
                    toNode = toNode,
                    theme = theme,
                    fromCenterX = from.x,
                    fromCenterY = from.y,
                    toCenterX = to.x,
                    toCenterY = to.y,
                    startX = from.x,
                    startY = from.y,
                    endX = to.x,
                    endY = to.y,
                    fromRadius = fromRadius,
                    toRadius = toRadius,
                    isSelfLoop = true
                )
            )
            return@forEach
        }

        val vector = RelationVector(to.x - from.x, to.y - from.y)
        val unit = vector.normalized()
        if (unit.length <= 0f) return@forEach

        val start = RelationPoint(from.x + unit.x * fromRadius, from.y + unit.y * fromRadius)
        val end = RelationPoint(to.x - unit.x * toRadius, to.y - unit.y * toRadius)
        (edge.drawer ?: theme.edgeDrawer).draw(
            RelationEdgeDrawScope(
                canvas = canvas,
                measureContext = measureContext,
                edge = edge,
                fromNode = fromNode,
                toNode = toNode,
                theme = theme,
                fromCenterX = from.x,
                fromCenterY = from.y,
                toCenterX = to.x,
                toCenterY = to.y,
                startX = start.x,
                startY = start.y,
                endX = end.x,
                endY = end.y,
                fromRadius = fromRadius,
                toRadius = toRadius,
                isSelfLoop = false
            )
        )
    }

    nodes.forEach { node ->
        val point = positions.getValue(node.id)
        val radius = nodeRadius(node, theme)
        (node.drawer ?: theme.nodeDrawer).draw(
            RelationNodeDrawScope(
                canvas = canvas,
                measureContext = measureContext,
                node = node,
                theme = theme,
                centerX = point.x,
                centerY = point.y,
                radius = radius
            )
        )
    }
}
