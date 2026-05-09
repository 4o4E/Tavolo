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

@ImageHandler("hold2")
object Hold2Handler : FramesHandler {
    private val cover = Assets.image("handlers/hold2/assets/hold/2.png")
    private const val size = 220
    private val faceRect = Rect.makeXYWH(148F, 296F, 220F, 220F)
    private val imgRect = Rect.makeWH(cover.width.toFloat(), cover.height.toFloat())
    private val paint = Paint().apply {
        color = Colors.WHITE.argb
    }

    override val name = "Hold2"
    override val regex = Regex("(?i)Hold2")

    override suspend fun handleFrames(
        frames: MutableList<Frame>,
        args: MutableMap<String, String>,
    ) = frames.result {
        common(args).handle {
            val image = it.subCenter(this@Hold2Handler.size)
            val src = Rect.makeWH(image.width.toFloat(), image.height.toFloat())
            cover.newSurface().withCanvas {
                drawRect(imgRect, paint)
                drawImageRectNearest(image, src, faceRect)
                drawImage(cover, 0F, 0F)
            }
        }
    }
}
