package top.e404.tavolo.draw.compose.charts

import org.jetbrains.skia.*
import top.e404.tavolo.draw.compose.*

fun UiElement.bar(theme: BarTheme, data: List<Pair<Int, Float>>) = add(
    CanvasElement(
        theme.outerRadius * 2,
        theme.outerRadius * 2
    ) { canvas ->
        drawDonutChart(canvas, parentX, parentY, data, theme)
    }
)

/**
 * Theme 数据类，用于定义图表的颜色方案
 * @param outerRadius 图表的外半径
 * @param innerRadius 图表中心空洞的半径
 * @param backgroundColor 画布的背景颜色
 * @param strokeColor 描边的颜色
 * @param strokeWidth 描边的宽度，默认为 2f
 * @param start 起始角度，默认为 -90f（从顶部开始绘制）
 */
data class BarTheme(
    val outerRadius: Float,
    val innerRadius: Float = outerRadius * 2 / 3,
    val backgroundColor: Int = Color.TRANSPARENT,
    val strokeColor: Int = Color.WHITE,
    val strokeWidth: Float = 2f,
    val start: Float = -90f,
) {
    val stroke: ChartStroke get() = ChartStroke(strokeColor, strokeWidth)
}

/**
 * 绘制一个空心饼图（甜甜圈图）到指定的 Canvas 上
 *
 * @param canvas Skia 画布对象
 * @param data 颜色和占比
 * @param theme 包含颜色配置的 Theme 对象
 */
fun drawDonutChart(
    canvas: DrawCanvas,
    left: Float,
    top: Float,
    data: List<Pair<Int, Float>>,
    theme: BarTheme,
) {
    val centerX = left + theme.outerRadius
    val centerY = top + theme.outerRadius
    val positiveData = data.filter { it.second > 0f }
    val total = positiveData.sumOf { it.second.toDouble() }.toFloat()

    // 计算弧形的边界
    val right = left + theme.outerRadius * 2
    val bottom = top + theme.outerRadius * 2
    val strokeWidth = theme.stroke.width

    // 定义用于填充和描边的 Paint
    val fillPaint = ChartFill(Color.TRANSPARENT).toPaint()
    val strokePaint = theme.stroke.toPaint()

    var startAngle = theme.start

    canvas.save()
    // 设置clip
    val clipPath = Path().apply {
        addCircle(centerX, centerY, theme.outerRadius)
        addCircle(centerX, centerY, theme.innerRadius, PathDirection.COUNTER_CLOCKWISE)
    }
    canvas.clipPath(clipPath)

    // 遍历数据并绘制每个扇形
    val l = left + strokeWidth
    val t = top + strokeWidth
    val r = right - strokeWidth
    val b = bottom - strokeWidth
    for ((color, value) in positiveData) {
        val sweepAngle = 360 * (value / total)
        fillPaint.color = color
        canvas.drawArc(l, t, r, b, startAngle, sweepAngle, true, fillPaint)

        // 2. 绘制描边部分
        canvas.drawArc(l, t, r, b, startAngle, sweepAngle, true, strokePaint)

        startAngle += sweepAngle
    }
    canvas.restore()

    // 描边
    canvas.drawCircle(centerX, centerY, theme.outerRadius - strokeWidth, strokePaint)
    canvas.drawCircle(centerX, centerY, theme.innerRadius, strokePaint)
}
