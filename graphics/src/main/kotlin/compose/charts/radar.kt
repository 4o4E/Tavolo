package top.e404.tavolo.draw.compose.charts

import org.jetbrains.skia.Color
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Path
import org.jetbrains.skia.Rect
import org.jetbrains.skia.TextBlob
import org.jetbrains.skia.TextLine
import top.e404.tavolo.draw.compose.BaseElement
import top.e404.tavolo.draw.compose.DrawCanvas
import top.e404.tavolo.draw.compose.DrawContext
import top.e404.tavolo.draw.compose.MeasureContext
import top.e404.tavolo.draw.compose.StrokeStyle
import top.e404.tavolo.draw.compose.UiElement
import top.e404.tavolo.util.Colors
import top.e404.tavolo.util.FontManager
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 将雷达图添加到 UiElement。
 *
 * @param theme 雷达图主题
 * @param data 数据列表，second 是 0..1 的归一化半径比例；0 位于圆心，1 位于外圈。
 *   旧版合法比例迁移为 `gridUnit / radius + (1 - gridUnit / radius) * oldRate`。
 *   五层对数计数按 `if (count == 0) 0f else minOf((1f + log10(count.toFloat())) / 5f, 1f)` 转换；
 *   五层对应 1、10、100、1k、10k，10k 以上截断到外圈。
 */
fun UiElement.radar(theme: RadarTheme, data: List<Pair<String, Float>>) = add(
    RadarElement(data, theme)
)

private class RadarElement(
    private val data: List<Pair<String, Float>>,
    private val theme: RadarTheme
) : BaseElement() {
    private var measuredLayout: RadarLayout? = null
    private var originX = 0f
    private var originY = 0f

    override fun measureContent(context: MeasureContext) {
        if (theme.sizeMode == RadarSizeMode.FIXED) {
            contentWidth = theme.width
            contentHeight = theme.height
            return
        }
        val layout = radarLayout(data, theme, context)
        measuredLayout = layout
        contentWidth = layout.width
        contentHeight = layout.height
    }

    override fun layoutChildren(content: Bounds) {
        originX = content.x
        originY = content.y
    }

    override fun drawContent(context: DrawContext) {
        val layout = measuredLayout ?: radarLayout(data, theme, context.measureContext, originX, originY)
        drawRadarLayout(context.canvas, theme, layout, originX, originY)
    }
}

/**
 * Theme 数据类，用于定义雷达图外观。
 *
 * 主题字段只暴露稳定配置，不要求调用方直接创建 Skia Paint 或 Font。
 */
data class RadarTheme(
    /** 雷达图宽度 */
    val width: Float,
    /** 雷达图高度 */
    val height: Float,
    /** 网格最外圈半径，不受尺寸模式影响。 */
    val radius: Float = 200f,

    /** 数据填充轮廓 */
    val fillOutlineColor: Int = Colors.LIGHT_BLUE.argb,
    val fillOutlineWidth: Float = 2f,

    /** 数据填充 */
    val fillColor: Int = fillOutlineColor and 0xFFFFFF or (0x66 shl 24),

    /** 背景填充，用于网格之间的空白部分 */
    val bgColor: Int = Color.TRANSPARENT,

    /** 网格 */
    val gridCount: Int = 5,
    val gridLineColor: Int = 0xFFCCCCCC.toInt(),
    val gridLineWidth: Float = 1f,
    val gridLineStyle: StrokeStyle = StrokeStyle.Dashed(listOf(10f, 10f)),
    /** 网格坐标文案生成逻辑 */
    val gridFontProvider: (Int) -> String? = { it.toString() },
    val gridFontSize: Float = 12f,
    val gridFontFamily: String = FontManager.defaultFamily,
    val gridFontColor: Int = Color.WHITE,
    val gridFontWeight: Int? = null,
    val gridItalic: Boolean = false,
    val gridScaleX: Float? = null,
    val gridTextAntiAlias: Boolean = true,

    val labelOuterLength: Float = 10F,
    /** 标签位置修正，处理标签覆盖坐标的问题 */
    val labelFixPolicy: RadarFixPolicy = RadarFixPolicy.RATED_FIX,
    val labelFontSize: Float = 25f,
    val labelFontFamily: String = FontManager.defaultFamily,
    val labelFontColor: Int = Color.WHITE,
    val labelFontWeight: Int? = null,
    val labelItalic: Boolean = false,
    val labelScaleX: Float? = null,
    val labelTextAntiAlias: Boolean = true,
    /** 固定模式沿用 width/height；自然模式根据半径、描边和文字外接框计算尺寸。 */
    val sizeMode: RadarSizeMode = RadarSizeMode.FIXED,
    /** 自然模式中图表边缘与外框间的等距留白；不缩放半径、字体或轴标签距离。 */
    val naturalPadding: Float = 0f
) {
    /** 每个网格的单位距离。gridCount 非法时返回 0，避免产生 Infinity。 */
    val gridUnit: Float get() = if (gridCount > 0) radius / gridCount else 0f

    val fillOutline: ChartStroke get() = ChartStroke(fillOutlineColor, fillOutlineWidth)
    val fill: ChartFill get() = ChartFill(fillColor)
    val background: ChartFill get() = ChartFill(bgColor)
    val gridLine: ChartStroke get() = ChartStroke(gridLineColor, gridLineWidth, gridLineStyle)
    val gridTextStyle: ChartTextStyle
        get() = ChartTextStyle(
            fontSize = gridFontSize,
            color = gridFontColor,
            fontFamily = gridFontFamily,
            fontWeight = gridFontWeight,
            italic = gridItalic,
            scaleX = gridScaleX,
            antiAlias = gridTextAntiAlias
        )
    val labelTextStyle: ChartTextStyle
        get() = ChartTextStyle(
            fontSize = labelFontSize,
            color = labelFontColor,
            fontFamily = labelFontFamily,
            fontWeight = labelFontWeight,
            italic = labelItalic,
            scaleX = labelScaleX,
            antiAlias = labelTextAntiAlias
        )
}

enum class RadarSizeMode { FIXED, NATURAL }

private data class RadarPoint(val x: Float, val y: Float)

private data class RadarText(val measured: MeasuredChartText, val x: Float, val y: Float)

private data class RadarLayout(
    val width: Float,
    val height: Float,
    val vertices: List<RadarPoint>,
    val rings: List<List<RadarPoint>>,
    val axisStarts: List<RadarPoint>,
    val dataPoints: List<RadarPoint>,
    val gridTexts: List<RadarText>,
    val labels: List<RadarText>
)

private fun radarLayout(
    data: List<Pair<String, Float>>,
    theme: RadarTheme,
    measureContext: MeasureContext,
    parentX: Float = 0f,
    parentY: Float = 0f
): RadarLayout {
    val axisCount = data.size
    val angleStep = if (axisCount > 0) 2 * Math.PI / axisCount else 0.0
    val gridCount = theme.gridCount.coerceAtLeast(0)
    fun point(radius: Float, index: Int): RadarPoint {
        val angle = index * angleStep - Math.PI / 2
        return RadarPoint(radius * cos(angle).toFloat(), radius * sin(angle).toFloat())
    }

    val vertices = data.indices.map { point(theme.radius, it) }
    val rings = (1..gridCount).map { ringIndex ->
        val radius = theme.radius * ringIndex / gridCount
        data.indices.map { point(radius, it) }
    }
    val axisStarts = data.indices.map { point(if (gridCount > 0) theme.radius / gridCount else 0f, it) }
    val dataPoints = data.mapIndexed { index, (_, rate) ->
        point(theme.radius * rate.coerceIn(0f, 1f), index)
    }
    val gridTextStyle = theme.gridTextStyle
    val labelTextStyle = theme.labelTextStyle
    val measureInk = theme.sizeMode == RadarSizeMode.NATURAL
    val gridTexts = (1..gridCount).mapNotNull { ringIndex ->
        theme.gridFontProvider(ringIndex - 1)?.let { text ->
            val measured = measureChartText(text, gridTextStyle, measureContext, measureInk)
            val radius = theme.radius * ringIndex / gridCount
            RadarText(measured, 3f, -radius - 3f)
        }
    }
    // 固定画布沿用绝对圆心的标签策略；自然布局在局部原点测量，不依赖固定画布尺寸。
    val labelCenterX = if (theme.sizeMode == RadarSizeMode.FIXED) parentX + theme.width / 2f else 0f
    val labelCenterY = if (theme.sizeMode == RadarSizeMode.FIXED) parentY + theme.height / 2f else 0f
    val labels = data.mapIndexed { index, (label) ->
        val angle = (index * angleStep + Math.PI * 1.5) % (2 * Math.PI)
        val measured = measureChartText(label, labelTextStyle, measureContext, measureInk)
        val (x, y) = theme.labelFixPolicy.fix(angle, angle / Math.PI, measured.box, labelCenterX, labelCenterY, theme)
        RadarText(measured, x - labelCenterX, y - labelCenterY)
    }

    val natural = theme.sizeMode == RadarSizeMode.NATURAL
    var minX = 0f
    var minY = 0f
    var maxX = 0f
    var maxY = 0f
    if (natural) {
        minX = Float.POSITIVE_INFINITY
        minY = Float.POSITIVE_INFINITY
        maxX = Float.NEGATIVE_INFINITY
        maxY = Float.NEGATIVE_INFINITY
        fun include(points: List<RadarPoint>, extent: Float) {
            for (point in points) {
                minX = minOf(minX, point.x - extent)
                maxX = maxOf(maxX, point.x + extent)
                minY = minOf(minY, point.y - extent)
                maxY = maxOf(maxY, point.y + extent)
            }
        }
        // Skia 的零宽描边是一个设备像素的细线，仍需要纳入测量。
        val halfGridStroke = maxOf(theme.gridLineWidth, 1f) / 2f
        val halfOutline = maxOf(theme.fillOutlineWidth, 1f) / 2f
        val ringMiterFactor = if (axisCount >= 3) (1.0 / cos(Math.PI / axisCount)).toFloat() else 1f
        rings.forEach { include(it, halfGridStroke * ringMiterFactor) }
        include(axisStarts, halfGridStroke)
        include(vertices, halfGridStroke)
        // 不等半径的数据轮廓包含锐角，以 Skia 默认 miter limit 4 的包络保留描边。
        include(dataPoints, halfOutline * 4f)
        fun includeText(text: RadarText) {
            val ink = text.measured.inkBounds ?: return
            if (ink.isEmpty) return
            // 字形度量不包含栅格化时的抗锯齿覆盖率，额外保留一个像素。
            minX = minOf(minX, text.x + ink.left - 1f)
            maxX = maxOf(maxX, text.x + ink.right + 1f)
            minY = minOf(minY, text.y + ink.top - 1f)
            maxY = maxOf(maxY, text.y + ink.bottom + 1f)
        }
        gridTexts.forEach(::includeText)
        labels.forEach(::includeText)
        if (!minX.isFinite()) {
            minX = 0f
            maxX = 0f
            minY = 0f
            maxY = 0f
        }
    }
    val padding = if (theme.sizeMode == RadarSizeMode.NATURAL) theme.naturalPadding.coerceAtLeast(0f) else 0f
    val shiftX = if (theme.sizeMode == RadarSizeMode.NATURAL) padding - minX else theme.width / 2f
    val shiftY = if (theme.sizeMode == RadarSizeMode.NATURAL) padding - minY else theme.height / 2f
    fun shifted(point: RadarPoint) = RadarPoint(point.x + shiftX, point.y + shiftY)
    fun shifted(text: RadarText) = text.copy(x = text.x + shiftX, y = text.y + shiftY)
    return RadarLayout(
        width = if (theme.sizeMode == RadarSizeMode.NATURAL) maxX - minX + 2 * padding else theme.width,
        height = if (theme.sizeMode == RadarSizeMode.NATURAL) maxY - minY + 2 * padding else theme.height,
        vertices = vertices.map(::shifted),
        rings = rings.map { ring -> ring.map(::shifted) },
        axisStarts = axisStarts.map(::shifted),
        dataPoints = dataPoints.map(::shifted),
        gridTexts = gridTexts.map(::shifted),
        labels = labels.map(::shifted)
    )
}

fun drawRadarChart(
    canvas: DrawCanvas,
    parentX: Float,
    parentY: Float,
    data: List<Pair<String, Float>>,
    theme: RadarTheme,
    measureContext: MeasureContext = MeasureContext()
) = drawRadarLayout(canvas, theme, radarLayout(data, theme, measureContext, parentX, parentY), parentX, parentY)

private fun drawRadarLayout(
    canvas: DrawCanvas,
    theme: RadarTheme,
    layout: RadarLayout,
    parentX: Float = 0f,
    parentY: Float = 0f
) {
    fun path(points: List<RadarPoint>) = Path().apply {
        points.forEachIndexed { index, point ->
            if (index == 0) moveTo(parentX + point.x, parentY + point.y)
            else lineTo(parentX + point.x, parentY + point.y)
        }
        closePath()
    }
    val backgroundPaint = theme.background.toPaint()
    val gridLinePaint = theme.gridLine.toPaint()
    val fillPaint = theme.fill.toPaint()
    val fillOutlinePaint = theme.fillOutline.toPaint()
    val gridTextPaint = theme.gridTextStyle.toPaint()
    val labelTextPaint = theme.labelTextStyle.toPaint()
    canvas.drawPath(path(layout.vertices), backgroundPaint)
    layout.rings.forEach { canvas.drawPath(path(it), gridLinePaint) }
    layout.gridTexts.forEach { text ->
        canvas.drawTextLine(text.measured.line, parentX + text.x, parentY + text.y, gridTextPaint)
    }
    for (index in layout.vertices.indices) {
        val start = layout.axisStarts[index]
        val end = layout.vertices[index]
        canvas.drawLine(parentX + start.x, parentY + start.y, parentX + end.x, parentY + end.y, gridLinePaint)
    }
    val dataPath = path(layout.dataPoints)
    canvas.drawPath(dataPath, fillPaint)
    canvas.drawPath(dataPath, fillOutlinePaint)
    layout.labels.forEach { text ->
        canvas.drawTextLine(text.measured.line, parentX + text.x, parentY + text.y, labelTextPaint)
    }
}
@Suppress("UNUSED")
enum class RadarFixPolicy(val fix: (
    angle: Double,
    a: Double,
    box: ChartTextBox,
    centerX: Float,
    centerY: Float,
    theme: RadarTheme
) -> Pair<Float, Float>) {
    /** 不进行修正，标签起点始终在图表外侧固定位置 */
    NONE({ angle, _, box, centerX, centerY, theme ->
        val x = centerX + (theme.radius + theme.labelOuterLength) * cos(angle).toFloat()
        val y = centerY + (theme.radius + theme.labelOuterLength) * sin(angle).toFloat()
        x - box.width / 2 to y
    }),
    /** 对于两边的标签向外移动 */
    MOVE_OUTSIDE({ angle, a, box, centerX, centerY, theme ->
        val r = theme.radius + theme.labelOuterLength

        val x = centerX + r * cos(angle).toFloat()
        val y = centerY + r * sin(angle).toFloat()

        val location = when {
            abs(a - 1.5) < 1e-6 -> Location.TOP
            abs(a - 0.5) < 1e-6 -> Location.BOTTOM
            a in 0.5..1.5 -> Location.LEFT
            else -> Location.RIGHT
        }

        val px = when (location) {
            Location.LEFT -> x - box.width
            Location.RIGHT -> x
            else -> x - box.width / 2
        }
        px to y
    }),
    /** 按比例修正，标签仍然除了顶部和底部都向外偏移 */
    RATED_FIX({ angle, a, box, centerX, centerY, theme ->
        val v = abs((a % 1) - 0.5)
        val fix = (1 - v).toFloat() * theme.labelOuterLength * 1.5f
        val r = theme.radius + theme.labelOuterLength + fix

        val x = centerX + r * cos(angle).toFloat()
        val y = centerY + r * sin(angle).toFloat()

        val location = when {
            abs(a - 1.5) < 1e-6 -> Location.TOP
            abs(a - 0.5) < 1e-6 -> Location.BOTTOM
            a in 0.5..1.5 -> Location.LEFT
            else -> Location.RIGHT
        }

        val px = when (location) {
            Location.LEFT -> x - box.width
            Location.RIGHT -> x
            else -> x - box.width / 2
        }
        px to y
    }),
    /** 倾斜修正 */
    TILT({ angle, _, box, centerX, centerY, theme ->
        fun computeTextCenterToRadarInsideFontBoxLength(
            tx: Float,
            ty: Float,
            cx: Float,
            cy: Float,
            left: Float,
            top: Float,
            right: Float,
            bottom: Float
        ): Float {
            val dx = cx - tx
            val dy = cy - ty
            val eps = 1e-6f

            if (abs(dx) < eps && abs(dy) < eps) return 0f

            val candidates = mutableListOf<Float>()

            if (abs(dx) > eps) {
                val tLeft = (left - tx) / dx
                if (tLeft > 0f) {
                    val yAtT = ty + tLeft * dy
                    if (yAtT >= top - eps && yAtT <= bottom + eps) candidates.add(tLeft)
                }
                val tRight = (right - tx) / dx
                if (tRight > 0f) {
                    val yAtT = ty + tRight * dy
                    if (yAtT >= top - eps && yAtT <= bottom + eps) candidates.add(tRight)
                }
            }

            if (abs(dy) > eps) {
                val tTop = (top - ty) / dy
                if (tTop > 0f) {
                    val xAtT = tx + tTop * dx
                    if (xAtT >= left - eps && xAtT <= right + eps) candidates.add(tTop)
                }
                val tBottom = (bottom - ty) / dy
                if (tBottom > 0f) {
                    val xAtT = tx + tBottom * dx
                    if (xAtT >= left - eps && xAtT <= right + eps) candidates.add(tBottom)
                }
            }

            if (candidates.isEmpty()) return 0f

            val tMin = candidates.min()
            return sqrt(dx * dx + dy * dy) * tMin
        }

        val x = centerX + (theme.radius + theme.labelOuterLength) * cos(angle).toFloat()
        val y = centerY + (theme.radius + theme.labelOuterLength) * sin(angle).toFloat()

        val boxLeft = box.width / 2
        val boxTop = box.ascent
        val boxRight = box.width / 2
        val boxBottom = box.descent
        val distance = computeTextCenterToRadarInsideFontBoxLength(
            x,
            y,
            centerX,
            centerY,
            boxLeft,
            boxTop,
            boxRight,
            boxBottom
        )

        val r = theme.radius + theme.labelOuterLength - distance + sqrt(box.width * box.width + box.height * box.height) / 2

        val dx = centerX + r * cos(angle).toFloat()
        val dy = centerY + r * sin(angle).toFloat()

        val dpx = dx - box.width / 2
        val dpy = dy - box.descent / 2
        dpx to dpy
    });
}

enum class Location { LEFT, TOP, RIGHT, BOTTOM }


private data class MeasuredChartText(
    val line: TextLine,
    val box: ChartTextBox,
    val inkBounds: Rect?
)

private fun measureChartText(
    text: String,
    style: ChartTextStyle,
    measureContext: MeasureContext,
    measureInk: Boolean
): MeasuredChartText {
    val font = style.toFont()
    val paint = style.toPaint()
    val metrics = measureContext.textMeasurer.metrics(font)
    val width = measureContext.textMeasurer.measureTextWidth(text, font, paint)
    val line = TextLine.make(text, font)
    val ink = if (measureInk) measureTextInk(line, font, paint) else null
    return MeasuredChartText(
        line = line,
        box = ChartTextBox(
            width = width,
            height = metrics.lineHeight,
            ascent = metrics.ascent,
            descent = metrics.descent
        ),
        inkBounds = ink
    )
}

/**
 * 从已成形的字形及实际字体计算墨迹边界，保留回退字体、合成斜体、字重和横向缩放。
 * 字体总外框会包含未使用字形的空间；逐字形测量可避免这些空间进入自然尺寸。
 */
private fun measureTextInk(line: TextLine, font: Font, paint: Paint): Rect? {
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    line.textBlob?.use { blob ->
        val positions = blob.positions
        var glyphOffset = 0
        TextBlob.TextBlobIter(blob).use { runs ->
            while (runs.hasNext()) {
                val run = runs.next()
                run.typeface.use { typeface ->
                    font.setTypeface(typeface)
                    val bounds = font.getBounds(run.glyphs, paint)
                    for (index in bounds.indices) {
                        val ink = bounds[index]
                        if (ink.isEmpty) continue
                        val position = (glyphOffset + index) * 2
                        val x = positions[position]
                        val y = positions[position + 1]
                        left = minOf(left, x + ink.left)
                        top = minOf(top, y + ink.top)
                        right = maxOf(right, x + ink.right)
                        bottom = maxOf(bottom, y + ink.bottom)
                    }
                }
                glyphOffset += run.glyphs.size
            }
        }
    }
    return if (left.isFinite()) Rect.makeLTRB(left, top, right, bottom) else null
}

