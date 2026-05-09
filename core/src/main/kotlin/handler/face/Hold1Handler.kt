package top.e404.tavolo.handler.face

import top.e404.tavolo.assets.Assets
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

@ImageHandler("hold1")
object Hold1Handler : FramesHandler {
    private val cover = Assets.image("handlers/hold1/assets/hold/1.png")
    private const val size = 160
    private val faceRect = Rect.makeXYWH(28F, 215F, 160F, 160F)
    private val imgRect = Rect.makeWH(cover.width.toFloat(), cover.height.toFloat())
    private val paint = Paint().apply {
        color = Colors.WHITE.argb
    }

    override val name = "Hold1"
    override val regex = Regex("(?i)Hold1")

    override suspend fun handleFrames(
        frames: MutableList<Frame>,
        args: MutableMap<String, String>,
    ) = frames.result {
        common(args).handle {
            val image = it.subCenter(this@Hold1Handler.size)
            val src = Rect.makeWH(image.width.toFloat(), image.height.toFloat())
            cover.newSurface().withCanvas {
                drawRect(imgRect, paint)
                drawImageRectNearest(image, src, faceRect)
                drawImage(cover, 0F, 0F)
            }
        }
    }
}
