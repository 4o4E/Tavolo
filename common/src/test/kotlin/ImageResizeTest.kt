package top.e404.tavolo.util

import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import kotlin.test.Test
import kotlin.test.assertTrue

class ImageResizeTest {
    @Test
    fun resizeCanSmoothUpscalingAndUsesNearestNeighborByDefault() {
        val source = stripeImage()
        val smoothReds = source.resize(8, 2, smooth = true).redChannels()
        val nearestReds = source.resize(8, 2).redChannels()

        assertTrue(smoothReds.any { it in 1..254 }, "开启平滑后放大应生成过渡像素")
        assertTrue(nearestReds.all { it == 0 || it == 255 }, "关闭抗锯齿后放大应使用最近邻像素")
    }

    @Test
    fun resizeCanSmoothDownscalingAndUsesNearestNeighborByDefault() {
        val source = checkerImage(16)
        val smoothReds = source.resize(2, 2, smooth = true).redChannels()
        val nearestReds = source.resize(2, 2).redChannels()

        assertTrue(smoothReds.all { it in 1..254 }, "开启平滑后缩小应混合高频像素以抑制锯齿")
        assertTrue(nearestReds.all { it == 0 || it == 255 }, "关闭抗锯齿后缩小应使用最近邻像素")
    }

    private fun stripeImage(): Image = Surface.makeRasterN32Premul(2, 1).use { surface ->
        val paint = Paint().apply { isAntiAlias = false }
        paint.color = Color.BLACK
        surface.canvas.drawRect(Rect.makeXYWH(0f, 0f, 1f, 1f), paint)
        paint.color = Color.WHITE
        surface.canvas.drawRect(Rect.makeXYWH(1f, 0f, 1f, 1f), paint)
        surface.makeImageSnapshot()
    }

    private fun checkerImage(size: Int): Image = Surface.makeRasterN32Premul(size, size).use { surface ->
        val paint = Paint().apply { isAntiAlias = false }
        for (y in 0 until size) {
            for (x in 0 until size) {
                paint.color = if ((x + y) % 2 == 0) Color.BLACK else Color.WHITE
                surface.canvas.drawRect(Rect.makeXYWH(x.toFloat(), y.toFloat(), 1f, 1f), paint)
            }
        }
        surface.makeImageSnapshot()
    }

    private fun Image.redChannels(): List<Int> {
        val bitmap = toBitmap()
        return buildList {
            for (y in 0 until height) {
                for (x in 0 until width) add(Color.getR(bitmap.getColor(x, y)))
            }
        }
    }
}
