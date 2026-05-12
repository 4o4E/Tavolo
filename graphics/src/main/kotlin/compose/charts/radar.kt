package top.e404.tavolo.draw.compose.charts

import org.jetbrains.skia.Color
import org.jetbrains.skia.Path
import org.jetbrains.skia.TextLine
import top.e404.tavolo.draw.compose.CanvasElement
import top.e404.tavolo.draw.compose.DrawCanvas
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
 * 在 UiElement 上添加雷达图。
 *
 * @param theme 雷达图主题
 * @param data 数据列表，second 表示当前轴上的比例
 */
fun UiElement.radar(theme: RadarTheme, data: List<Pair<String, Float>>) = add(
    CanvasElement(
        theme.width,
        theme.height
    ) { canvas, measureContext ->
        drawRadarChart(canvas, parentX, parentY, data, theme, measureContext)
    }
)

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
    /** 雷达图半径，即使设置了 gridStart 尺寸依然不变 */
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
    val labelTextAntiAlias: Boolean = true
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
    val box: ChartTextBox
)

private fun measureChartText(text: String, style: ChartTextStyle, measureContext: MeasureContext): MeasuredChartText {
    val font = style.toFont()
    val paint = style.toPaint()
    val metrics = measureContext.textMeasurer.metrics(font)
    val width = measureContext.textMeasurer.measureTextWidth(text, font, paint)
    return MeasuredChartText(
        line = TextLine.make(text, font),
        box = ChartTextBox(
            width = width,
            height = metrics.lineHeight,
            ascent = metrics.ascent,
            descent = metrics.descent
        )
    )
}

fun drawRadarChart(
    canvas: DrawCanvas,
    parentX: Float,
    parentY: Float,
    data: List<Pair<String, Float>>,
    theme: RadarTheme,
    measureContext: MeasureContext = MeasureContext()
) {
    val centerX = parentX + theme.width / 2f
    val centerY = parentY + theme.height / 2f
    val n = data.size
    val angleStep = if (n > 0) 2 * Math.PI / n else 0.0
    val gridCount = theme.gridCount.coerceAtLeast(0)
    val backgroundPaint = theme.background.toPaint()
    val gridLinePaint = theme.gridLine.toPaint()
    val fillPaint = theme.fill.toPaint()
    val fillOutlinePaint = theme.fillOutline.toPaint()
    val gridTextPaint = theme.gridTextStyle.toPaint()
    val labelTextPaint = theme.labelTextStyle.toPaint()

    val path = Path().run {
        for (i in 0 until n) {
            val angle = i * angleStep - Math.PI / 2
            val x = centerX + theme.radius * cos(angle).toFloat()
            val y = centerY + theme.radius * sin(angle).toFloat()
            if (i == 0) {
                moveTo(x, y)
            } else {
                lineTo(x, y)
            }
        }
        closePath()
    }
    canvas.drawPath(path, backgroundPaint)

    for (ringIndex in 1..gridCount) {
        val r = theme.radius * ringIndex / gridCount
        val gridPath = Path()
        for (j in 0 until n) {
            val angle = j * angleStep - Math.PI / 2
            val x = centerX + r * cos(angle).toFloat()
            val y = centerY + r * sin(angle).toFloat()
            if (j == 0) {
                gridPath.moveTo(x, y)
            } else {
                gridPath.lineTo(x, y)
            }
        }
        gridPath.closePath()
        canvas.drawPath(gridPath, gridLinePaint)
        theme.gridFontProvider.invoke(ringIndex - 1)?.let { gridText ->
            val measured = measureChartText(gridText, theme.gridTextStyle, measureContext)
            canvas.drawTextLine(
                measured.line,
                centerX + 3f,
                centerY - r - 3f,
                gridTextPaint
            )
        }
    }

    for (i in 0 until n) {
        val angle = i * angleStep - Math.PI / 2
        val tx = centerX + theme.radius * cos(angle).toFloat()
        val ty = centerY + theme.radius * sin(angle).toFloat()
        val fx = centerX + theme.gridUnit * cos(angle).toFloat()
        val fy = centerY + theme.gridUnit * sin(angle).toFloat()
        canvas.drawLine(fx, fy, tx, ty, gridLinePaint)
    }

    val dataPath = Path().run {
        data.forEachIndexed { i, (_, rate) ->
            val safeRate = rate.coerceIn(0f, 1f)
            val r = (theme.radius - theme.gridUnit) * safeRate + theme.gridUnit
            val angle = i * angleStep - Math.PI / 2
            val x = centerX + r * cos(angle).toFloat()
            val y = centerY + r * sin(angle).toFloat()
            if (i == 0) {
                moveTo(x, y)
            } else {
                lineTo(x, y)
            }
        }
        closePath()
    }

    canvas.drawPath(dataPath, fillPaint)
    canvas.drawPath(dataPath, fillOutlinePaint)

    data.forEachIndexed { i, (label) ->
        val angle = (i * angleStep + Math.PI / 2 * 3) % (2 * Math.PI)
        val measured = measureChartText(label, theme.labelTextStyle, measureContext)
        val a = angle / Math.PI

        theme.labelFixPolicy.fix(angle, a, measured.box, centerX, centerY, theme).let { (px, py) ->
            canvas.drawTextLine(measured.line, px, py, labelTextPaint)
        }
    }
}
