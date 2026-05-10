package top.e404.tavolo.draw.render3d

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.IRect
import org.jetbrains.skia.Paint

/**
 * 渲染器可采样的二维纹理。
 */
interface RenderTexture {
    val width: Int
    val height: Int

    fun getColor(x: Int, y: Int): Int
}

/**
 * 3D 光栅化输出目标。
 */
interface RenderTarget : RenderTexture {
    fun clear(color: Int)
    fun setPixel(x: Int, y: Int, color: Int)
    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, color: Int)
}

/**
 * 基于 Skia Bitmap 的纹理适配器。
 */
class BitmapRenderTexture(private val bitmap: Bitmap) : RenderTexture {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height

    override fun getColor(x: Int, y: Int): Int = bitmap.getColor(x, y)
}

/**
 * 基于 Skia Bitmap 的渲染目标适配器。
 */
class BitmapRenderTarget(val bitmap: Bitmap) : RenderTarget {
    private val canvas = Canvas(bitmap)

    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height

    override fun clear(color: Int) {
        canvas.clear(color)
    }

    override fun getColor(x: Int, y: Int): Int = bitmap.getColor(x, y)

    override fun setPixel(x: Int, y: Int, color: Int) {
        bitmap.erase(color, IRect.makeXYWH(x, y, 1, 1))
    }

    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, color: Int) {
        val paint = Paint().apply {
            this.color = color
            strokeWidth = 1f
            isAntiAlias = false
        }
        canvas.drawLine(x0, y0, x1, y1, paint)
    }
}

sealed interface RenderCommand {
    data class Clear(val color: Int) : RenderCommand
    data class Pixel(val x: Int, val y: Int, val color: Int) : RenderCommand
    data class Line(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val color: Int) : RenderCommand
}

/**
 * 记录式渲染目标，用于断言 3D 光栅化发出的绘制指令。
 */
class RecordingRenderTarget(
    override val width: Int,
    override val height: Int,
    private val defaultColor: Int = Color.TRANSPARENT
) : RenderTarget {
    val commands: MutableList<RenderCommand> = mutableListOf()
    private val pixels = IntArray(width * height) { defaultColor }

    override fun clear(color: Int) {
        pixels.fill(color)
        commands += RenderCommand.Clear(color)
    }

    override fun getColor(x: Int, y: Int): Int =
        if (x in 0 until width && y in 0 until height) pixels[y * width + x] else defaultColor

    override fun setPixel(x: Int, y: Int, color: Int) {
        if (x in 0 until width && y in 0 until height) {
            pixels[y * width + x] = color
        }
        commands += RenderCommand.Pixel(x, y, color)
    }

    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, color: Int) {
        commands += RenderCommand.Line(x0, y0, x1, y1, color)
    }
}
