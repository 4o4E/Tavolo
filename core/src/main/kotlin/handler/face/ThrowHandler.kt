package top.e404.tavolo.handler.face

import top.e404.tavolo.assets.Assets
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import top.e404.tavolo.annotation.ImageHandler
import top.e404.tavolo.frame.Frame
import top.e404.tavolo.frame.FramesHandler
import top.e404.tavolo.frame.HandleResult.Companion.result
import top.e404.tavolo.frame.common
import top.e404.tavolo.frame.handle
import top.e404.tavolo.util.*

@ImageHandler("throw")
object ThrowHandler : FramesHandler {
    private const val size = 448
    private val bg = Assets.image("handlers/throw/assets/throw.png")

    override val name = "丢"
    override val regex = Regex("(?i)丢|diu|throw")

    override suspend fun handleFrames(
        frames: MutableList<Frame>,
        args: MutableMap<String, String>,
    ) = frames.result {
        common(args).handle {
            val img = it.round().rotateKeepSize(270F)
            val src = Rect.makeWH(img.width.toFloat(), img.height.toFloat())
            Surface.makeRasterN32Premul(
                this@ThrowHandler.size,
                this@ThrowHandler.size
            ).withCanvas {
                drawImage(bg, 0F, 0F)
                drawImageRectNearest(
                    image = img,
                    src = src,
                    dst = Rect.makeXYWH(10F, 175F, 150F, 150F)
                )
            }
        }
    }
}
