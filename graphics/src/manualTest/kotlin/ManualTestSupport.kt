package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import top.e404.tavolo.draw.compose.Column
import top.e404.tavolo.draw.compose.Composable
import top.e404.tavolo.draw.compose.render
import top.e404.tavolo.util.FontManager
import java.io.File

object ManualTestSupport {
    val uiFont: String = FontManager.registerSystem("manual-compose-ui", "Noto Sans SC")
    val monoFont: String = FontManager.registerSystem("manual-compose-mono", "Consolas")

    fun saveCompose(name: String, content: Composable): Column {
        val root = Column()
        val image = render(Color.TRANSPARENT, root, content)
        saveImage("compose/$name.png", image)
        return root
    }

    fun saveImage(path: String, image: Image) {
        val file = File("out/$path").also { it.parentFile.mkdirs() }
        file.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        println("已输出: ${file.absolutePath}")
    }

    private fun hsv(hue: Float, saturation: Float, value: Float): Int {
        fun channel(offset: Float): Int {
            val k = (offset + hue / 60f) % 6f
            val component = value * (1f - saturation * maxOf(0f, minOf(k, 4f - k, 1f)))
            return (component * 255f + 0.5f).toInt()
        }
        return Color.makeRGB(channel(5f), channel(3f), channel(1f))
    }

    fun drawnAvatar(index: Int, width: Int = 64, height: Int = 64): Image {
        return Surface.makeRasterN32Premul(width, height).use { surface ->
            val canvas = surface.canvas
            val hue = (index * 37) % 360
            val bg = hsv(hue.toFloat(), 0.18f, 0.95f)
            val primary = hsv(hue.toFloat(), 0.72f, 0.78f)
            val secondary = hsv(((hue + 32) % 360).toFloat(), 0.62f, 0.9f)

            canvas.clear(bg)
            val paint = Paint().apply {
                isAntiAlias = true
                mode = PaintMode.FILL
            }

            paint.color = primary
            canvas.drawCircle(width * 0.5f, height * 0.38f, minOf(width, height) * 0.22f, paint)

            paint.color = secondary
            canvas.drawOval(
                Rect.makeXYWH(width * 0.22f, height * 0.58f, width * 0.56f, height * 0.26f),
                paint
            )

            paint.color = Color.WHITE
            canvas.drawCircle(width * 0.42f, height * 0.35f, minOf(width, height) * 0.035f, paint)
            canvas.drawCircle(width * 0.58f, height * 0.35f, minOf(width, height) * 0.035f, paint)

            surface.makeImageSnapshot()
        }
    }

    fun drawnAvatars(count: Int, width: Int = 64, height: Int = 64): List<Image> {
        return (0 until count).map { drawnAvatar(it, width, height) }
    }
}
