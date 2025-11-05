package top.e404.skiko.draw.compose.charts

import org.jetbrains.skia.*
import top.e404.skiko.draw.compose.*
import top.e404.skiko.util.Colors
import kotlin.math.*

/**
 * 在 UiElement 上添加雷达图
 *
 * @param theme 雷达图主题
 * @param data 数据列表，包含标签和对应的数值, second是在整个轴上的占比
 */
fun UiElement.radar(theme: RadarTheme, data: List<Pair<String, Float>>) = add(
    CanvasElement(
        theme.width,
        theme.height
    ) { canvas ->
        drawRadarChart(canvas, parentX, parentY, data, theme)
    }
)

/**
 * Theme 数据类，用于定义图表外观
 */
data class RadarTheme(
    /** 雷达图的宽度 */
    val width: Float,
    /** 雷达图高度 */
    val height: Float,
    /** 雷达图半径 */
    val radius: Float = 200f,

    /** 数据填充轮廓颜色 */
    val fillOutlineColor: Int = Colors.LIGHT_BLUE.argb,
    val fillOutlinePaint: Paint = Paint().apply {
        color = fillOutlineColor
        strokeWidth = 2f
        mode = PaintMode.STROKE
        isAntiAlias = true
    },

    /** 数据填充颜色 */
    val fillColor: Int = fillOutlineColor and 0xFFFFFF or (0x66 shl 24), // 半透明
    val fillPaint: Paint = Paint().apply {
        color = fillColor
        isAntiAlias = true
    },

    /** 背景颜色 填充网格间的空白部分 */
    val bgColor: Int = Color.TRANSPARENT,
    val bgPaint: Paint = Paint().apply {
        color = bgColor
        isAntiAlias = true
    },

    /** 网格数量 */
    val gridCount: Int = 5,
    /** 网格线颜色 */
    val gridLineColor: Int = 0xFFCCCCCC.toInt(),
    val gridLinePaint: Paint = Paint().apply {
        color = gridLineColor
        strokeWidth = 1f
        isAntiAlias = true
        mode = PaintMode.STROKE
        pathEffect = PathEffect.makeDash(floatArrayOf(10f, 10f), 0f)
    },
    /** 网格坐标生成逻辑 */
    val gridFontProvider: (Int) -> String? = { it.toString() },
    /** 网格坐标字体 */
    val gridFont: Font = Font(DefaultTypefaceProvider.default, 12f),
    /** 网格坐标字体颜色 */
    val gridFontColor: Int = Color.WHITE,
    val gridFontPaint: Paint = Paint().apply {
        color = gridFontColor
        isAntiAlias = true
    },

    /** 标签字体 */
    val labelFont: Font = Font(DefaultTypefaceProvider.default, 25f),
    /** 标签字体颜色 */
    val labelFontColor: Int = Color.WHITE,
    val labelFontPaint: Paint = Paint().apply {
        color = labelFontColor
        isAntiAlias = true
    }
)

enum class Location { LEFT, TOP, RIGHT, BOTTOM }

fun drawRadarChart(canvas: Canvas, parentX: Float, parentY: Float, data: List<Pair<String, Float>>, theme: RadarTheme) {
    val centerX = parentX + theme.width / 2f
    val centerY = parentY + theme.height / 2f
    val n = data.size
    val angleStep = 2 * Math.PI / n

    // 绘制雷达图背景
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
    canvas.drawPath(path, theme.bgPaint)

    // 绘制网格线 和 网格坐标
    for (i in 1..theme.gridCount) {
        val r = theme.radius * i / theme.gridCount
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
        canvas.drawPath(gridPath, theme.gridLinePaint)
        theme.gridFontProvider.invoke(i)?.let { gridText ->
            canvas.drawTextLine(
                TextLine.make(gridText, theme.gridFont),
                centerX + 3f,
                centerY - r - 3f,
                theme.gridFontPaint
            )
        }
    }

    for (i in 0 until n) {
        val angle = i * angleStep - Math.PI / 2
        val x = centerX + theme.radius * cos(angle).toFloat()
        val y = centerY + theme.radius * sin(angle).toFloat()
        canvas.drawLine(centerX, centerY, x, y, theme.gridLinePaint)
    }

    // 绘制数据
    val dataPath = Path().run {
        data.forEachIndexed { i, (_, rate) ->
            val r = theme.radius * rate
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

    canvas.drawPath(dataPath, theme.fillPaint)
    canvas.drawPath(dataPath, theme.fillOutlinePaint)


    // 绘制标签

    val labelOuterLength = 40F
    data.forEachIndexed { i, (label) ->
        val angle = (i * angleStep + Math.PI / 2 * 3) % (2 * Math.PI)
        val x = centerX + (theme.radius + labelOuterLength) * cos(angle).toFloat()
        val y = centerY + (theme.radius + labelOuterLength) * sin(angle).toFloat()

        val line = TextLine.make(label, theme.labelFont)

        val a = angle / Math.PI
        val location = when {
            abs(a - 1.5) < 0.1 -> Location.TOP
            abs(a - 0.5) < 0.1 -> Location.BOTTOM
            a in 0.5..1.5 -> Location.LEFT
            else -> Location.RIGHT
        }
        val gridX = centerX + theme.radius * cos(angle).toFloat()
        val offset = theme.gridFont.size * 2
        val overflow = when (location) {
            Location.LEFT -> x + line.width / 2 + offset > gridX
            Location.RIGHT -> x - line.width / 2 - offset < gridX
            else -> false
        }

        val px = when (location) {
            Location.LEFT if overflow -> x - line.width
            Location.RIGHT if overflow -> x
            else -> x - line.width / 2
        }
        val py = y - line.height / 2
        canvas.drawTextLine(line, px, py, theme.labelFontPaint)
    }
}