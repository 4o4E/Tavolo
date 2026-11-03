package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import org.junit.Test
import top.e404.tavolo.draw.compose.Column
import top.e404.tavolo.draw.compose.Modifier
import top.e404.tavolo.draw.compose.padding
import top.e404.tavolo.draw.compose.render
import top.e404.tavolo.draw.compose.charts.RadarFixPolicy
import top.e404.tavolo.draw.compose.charts.RadarSizeMode
import top.e404.tavolo.draw.compose.charts.RadarTheme
import top.e404.tavolo.draw.compose.charts.radar
import top.e404.tavolo.util.FontManager
import top.e404.tavolo.util.toBitmap
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposeRadarZeroMarkerTest {
    private val zeroData = List(5) { "" to 0f }
    private val outlineColor = Color.makeARGB(128, 255, 0, 0)
    private val fontFamily = FontManager.systemFamilies().first()

    @Test
    fun allZeroDataDrawsOneOutlineColoredDotWithoutInnerRingArea() {
        for (width in listOf(0f, .5f, 2f, 6f)) {
            val theme = theme().copy(fillOutlineWidth = width)
            val actual = renderRadar(theme, zeroData).toBitmap()
            val expected = Surface.makeRasterN32Premul(120, 120).use { surface ->
                surface.canvas.clear(Color.TRANSPARENT)
                Paint().use { paint ->
                    paint.color = outlineColor
                    paint.isAntiAlias = true
                    surface.canvas.drawCircle(60f, 60f, maxOf(width, 1f) / 2f, paint)
                }
                surface.makeImageSnapshot().use { it.toBitmap() }
            }
            // 半透明颜色和边缘覆盖率能区分单个圆点与多个重合圆点。
            assertTrue(Color.getA(actual.getColor(60, 60)) > 0, "零值圆心必须存在可见标记")
            for (y in 56..63) for (x in 56..63) {
                assertEquals(expected.getColor(x, y), actual.getColor(x, y), "圆点颜色、尺寸和单次覆盖率应正确：width=$width pixel=($x,$y)")
            }
            for (y in 0 until 120) for (x in 0 until 120) {
                val color = actual.getColor(x, y)
                assertEquals(0, Color.getG(color), "全零数据不能产生绿色数据填充面积")
                if (x !in 56..63 || y !in 56..63) {
                    assertEquals(0, Color.getR(color), "数据色不能出现在圆心局部范围之外")
                }
            }
        }
    }

    @Test
    fun emptyAndNonzeroDataDoNotAcquireZeroMarkers() {
        val theme = theme().copy(gridCount = 0, gridLineColor = Color.TRANSPARENT, fillColor = Color.TRANSPARENT)
        val empty = renderRadar(theme, emptyList()).toBitmap()
        for (y in 0 until 120) for (x in 0 until 120) {
            assertEquals(0, Color.getA(empty.getColor(x, y)), "空列表不能生成圆心数据点")
        }
        val positive = renderRadar(theme, zeroData.map { it.first to .2f }).toBitmap()
        assertEquals(0, Color.getA(positive.getColor(60, 60)), "正数轮廓不能额外出现圆心标记")
        assertTrue(Color.getA(positive.getColor(60, 52)) > 0, "一次贡献应仍位于正数最内圈")
        val mixed = renderRadar(theme, listOf("" to 0f, "" to 1f, "" to 0f, "" to 1f)).toBitmap()
        assertEquals(128, Color.getA(mixed.getColor(60, 60)), "混合数据的原有轮廓不能被额外圆点重复叠色")
    }

    @Test
    fun fixedAndNaturalMarkersFollowParentOffsetsAndNaturalBoundsContainTheWholeDot() {
        for (mode in RadarSizeMode.entries) {
            // 标记半径大于网格半径，确保自然测量真正覆盖标记，而不是依赖网格留白。
            val theme = theme().copy(sizeMode = mode, radius = 1f, gridCount = 0, gridLineColor = Color.TRANSPARENT, fillOutlineWidth = 8f)
            val base = renderRadar(theme, zeroData)
            val shifted = renderRadar(theme, zeroData, left = 7f, top = 11f)
            assertEquals(base.width + 7, shifted.width)
            assertEquals(base.height + 11, shifted.height)
            val bitmap = base.toBitmap()
            val shiftedBitmap = shifted.toBitmap()
            assertTrue(Color.getA(bitmap.getColor(base.width / 2, base.height / 2)) > 0)
            for (y in 0 until shifted.height) for (x in 0 until shifted.width) {
                val expected = if (x >= 7 && y >= 11) bitmap.getColor(x - 7, y - 11) else Color.TRANSPARENT
                assertEquals(expected, shiftedBitmap.getColor(x, y), "父容器偏移只应应用一次：mode=$mode pixel=($x,$y)")
            }
            if (mode == RadarSizeMode.NATURAL) {
                assertTrue(base.width >= 8 && base.height >= 8, "自然尺寸不能截断 8px 圆点")
                val reference = renderRadar(theme.copy(naturalPadding = 12f), zeroData)
                assertEquals(base.width + 24, reference.width)
                assertEquals(base.height + 24, reference.height)
                val referenceBitmap = reference.toBitmap()
                for (y in 0 until reference.height) for (x in 0 until reference.width) {
                    val expected = if (x in 12 until base.width + 12 && y in 12 until base.height + 12) {
                        bitmap.getColor(x - 12, y - 12)
                    } else Color.TRANSPARENT
                    assertEquals(expected, referenceBitmap.getColor(x, y), "扩大画布后不能出现原自然边界之外的标记像素")
                }
            }
        }
    }

    private fun theme() = RadarTheme(
        width = 120f,
        height = 120f,
        radius = 40f,
        fillOutlineColor = outlineColor,
        fillOutlineWidth = 2f,
        fillColor = Color.GREEN,
        gridLineColor = Color.BLUE,
        gridFontProvider = { null },
        labelFontFamily = fontFamily,
        labelFixPolicy = RadarFixPolicy.NONE
    )

    private fun renderRadar(theme: RadarTheme, data: List<Pair<String, Float>>, left: Float = 0f, top: Float = 0f) = render(
        root = Column().apply { modifier = Modifier.padding(left = left, top = top) }
    ) {
        radar(theme, data)
    }
}
