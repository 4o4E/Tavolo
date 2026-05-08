package top.e404.tavolo.handler.face

import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import top.e404.tavolo.util.Colors
import top.e404.tavolo.annotation.ImageHandler
import top.e404.tavolo.frame.Frame
import top.e404.tavolo.frame.FramesHandler
import top.e404.tavolo.frame.HandleResult.Companion.result
import top.e404.tavolo.frame.common
import top.e404.tavolo.frame.handle
import top.e404.tavolo.util.*

@ImageHandler
object ShinyHandler : FramesHandler {
    private val cover = getJarImage(this::class.java, "statistic/shiny.png")
    private const val size = 170
    private val faceRect = Rect.makeXYWH(157F, 114F, size.toFloat(), size.toFloat())
    private val imgRect = Rect.makeWH(cover.width.toFloat(), cover.height.toFloat())
    private val paint = Paint().apply {
        color = Colors.WHITE.argb
    }

    override val name = "shiny"
    override val regex = Regex("(?i)shiny")

    override suspend fun handleFrames(
        frames: MutableList<Frame>,
        args: MutableMap<String, String>,
    ) = frames.result {
        common(args).handle {
            val image = it.round()
            val src = Rect.makeWH(image.width.toFloat(), image.height.toFloat())
            cover.newSurface().withCanvas {
                drawRect(imgRect, paint)
                drawImage(cover, 0F, 0F)
                drawImageRectNearest(image, src, faceRect)
            }
        }
    }
}
