package top.e404.skiko.draw.compose.charts

import org.jetbrains.skia.*
import org.jetbrains.skia.paragraph.*
import top.e404.skiko.draw.compose.*
import top.e404.skiko.util.Colors
import kotlin.math.*

fun UiElement.radar(theme: RadarTheme, data: List<Pair<String, Int>>) = add(
    CanvasElement(
        theme.width,
        theme.height
    ) { canvas ->
        drawRadarChart(canvas, parentX, parentY, data, theme)
    }
)

/**
 * Theme 数据类，用于定义图表外观
 *
 * @param radius 雷达图半径
 * @param gridCount 网格数量
 * @param backgroundColor 背景颜色
 * @param lineColor 网格线颜色
 * @param fillOutlineColor 数据填充轮廓颜色
 * @param fillColor 数据填充颜色
 * @param fontColor 字体颜色
 * @param fontSize 字体大小
 */
data class RadarTheme(
    val width: Float,
    val height: Float,
    val radius: Float = 200f,
    val gridCount: Int = 5,
    val backgroundColor: Int = Color.TRANSPARENT,
    val lineColor: Int = 0xFFCCCCCC.toInt(),
    val fillOutlineColor: Int = Colors.LIGHT_BLUE.argb,
    val fillColor: Int = fillOutlineColor and 0xFFFFFF or (0x66 shl 24), // 半透明
    val fontColor: Int = Color.WHITE,
    val fontSize: Float = 24f,
)

enum class Location { LEFT, TOP, RIGHT, BOTTOM }

fun drawRadarChart(canvas: Canvas, parentX: Float, parentY: Float, data: List<Pair<String, Int>>, theme: RadarTheme) {
    val centerX = parentX + theme.width / 2f
    val centerY = parentY + theme.height / 2f
    val n = data.size
    val angleStep = 2 * Math.PI / n

    // 绘制雷达图背景
    val path = Path()
    for (i in 0 until n) {
        val angle = i * angleStep - Math.PI / 2
        val x = centerX + theme.radius * cos(angle).toFloat()
        val y = centerY + theme.radius * sin(angle).toFloat()
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.closePath()

    // 填充颜色
    val paint = Paint().apply { color = theme.backgroundColor; isAntiAlias = true }
    canvas.drawPath(path, paint)

    // 绘制网格线
    val gridPaint = Paint().apply {
        color = theme.lineColor
        strokeWidth = 1f
        isAntiAlias = true
        mode = PaintMode.STROKE
        pathEffect = PathEffect.makeDash(floatArrayOf(10f, 10f), 0f)
    }
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
        canvas.drawPath(gridPath, gridPaint)
    }

    for (i in 0 until n) {
        val angle = i * angleStep - Math.PI / 2
        val x = centerX + theme.radius * cos(angle).toFloat()
        val y = centerY + theme.radius * sin(angle).toFloat()
        canvas.drawLine(centerX, centerY, x, y, gridPaint)
    }

    // 绘制数据
    val dataPath = Path()
    val maxValue = data.maxOfOrNull { it.second } ?: 1
    data.forEachIndexed { i, (_, value) ->
        val r = theme.radius * value / maxValue
        val angle = i * angleStep - Math.PI / 2
        val x = centerX + r * cos(angle).toFloat()
        val y = centerY + r * sin(angle).toFloat()
        if (i == 0) {
            dataPath.moveTo(x, y)
        } else {
            dataPath.lineTo(x, y)
        }
    }
    dataPath.closePath()

    val dataPaint = Paint().apply {
        color = theme.fillColor // Blue with alpha
        isAntiAlias = true
    }
    canvas.drawPath(dataPath, dataPaint)

    val dataStrokePaint = Paint().apply {
        color = theme.fillOutlineColor
        strokeWidth = 2f
        mode = PaintMode.STROKE
        isAntiAlias = true
    }
    canvas.drawPath(dataPath, dataStrokePaint)


    // 绘制标签
    val fontCollection = FontCollection()
    fontCollection.setDefaultFontManager(FontMgr.default)
    val paragraphStyle = ParagraphStyle()
    // **不要改typeface获取方式！！！**
    val typeface = FontMgr.default.makeFromFile("JetBrainsMono-Bold.ttf") ?: Typeface.makeEmpty()

    val labelOuterLength = 20F
    data.forEachIndexed { i, (label) ->
        val angle = (i * angleStep + Math.PI / 2 * 3) % (2 * Math.PI)
        val x = centerX + (theme.radius + labelOuterLength) * cos(angle).toFloat()
        val y = centerY + (theme.radius + labelOuterLength) * sin(angle).toFloat()

        val paragraphBuilder = ParagraphBuilder(paragraphStyle, fontCollection)
        paragraphBuilder.pushStyle(
            TextStyle()
                .setColor(theme.fontColor)
                .setFontSize(theme.fontSize).setTypeface(typeface)
                .setColor(theme.fontColor)
        )
        paragraphBuilder.addText(label)
        val paragraph = paragraphBuilder.build()

        val a = angle / Math.PI
        val location = when {
            abs(a - 1.5) < 0.1 -> Location.TOP
            abs(a - 0.5) < 0.1 -> Location.BOTTOM
            a in 0.5..1.5 -> Location.LEFT
            else -> Location.RIGHT
        }
        val gridX = centerX + theme.radius * cos(angle).toFloat()
        val offset = theme.fontSize
        val overflow = when {
            location == Location.LEFT -> x + paragraph.minIntrinsicWidth / 2 + offset > gridX
            location == Location.RIGHT -> x - paragraph.minIntrinsicWidth / 2 - offset < gridX
            else -> false
        }
        println("$label, angle: ${angle / Math.PI}, location: $location, overflow: $overflow")

        paragraph.layout(Float.POSITIVE_INFINITY)
        val px = when {
            location == Location.LEFT && overflow -> x - paragraph.maxIntrinsicWidth
            location == Location.RIGHT && overflow -> x
            else -> x - paragraph.maxIntrinsicWidth / 2
        }
        val py = y - paragraph.height / 2
        paragraph.paint(canvas, px, py)
    }
}