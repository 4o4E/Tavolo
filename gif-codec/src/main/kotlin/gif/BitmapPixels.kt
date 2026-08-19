package top.e404.tavolo.gif

import org.jetbrains.skia.Bitmap

/**
 * GIF 编码阶段使用的 JVM 像素快照，避免量化和抖动反复跨 JNI 读取同一张位图。
 */
internal class BitmapPixels(
    val width: Int,
    val height: Int,
    val argb: IntArray,
    val hasTransparency: Boolean,
) {
    companion object {
        fun from(bitmap: Bitmap): BitmapPixels {
            val width = bitmap.width
            val height = bitmap.height
            val argb = IntArray(width * height)
            var hasTransparency = false
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val color = bitmap.getColor(x, y)
                    argb[y * width + x] = color
                    if (color ushr 24 != 0xFF) hasTransparency = true
                }
            }
            return BitmapPixels(width, height, argb, hasTransparency)
        }
    }
}
