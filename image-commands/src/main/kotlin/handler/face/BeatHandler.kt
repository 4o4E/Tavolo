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

/**
 * 铁咩
 */
@ImageHandler("beat")
object BeatHandler : FramesHandler {
    private val bg = Assets.image("handlers/beat/assets/beat.png")
    private val bgRect = Rect.makeWH(bg.width.toFloat(), bg.height.toFloat())
    private val rect = Rect.makeXYWH(5F, 60F, 100F, 100F)
    private val paint = Paint().apply { color = Colors.WHITE.argb }

    override val name = "tm"
    override val regex = Regex("(?i)铁咩|tm")

    override suspend fun handleFrames(
        frames: MutableList<Frame>,
        args: MutableMap<String, String>,
    ) = frames.result {
        common(args).handle {
            val image = it.subCenter()
            val src = Rect.makeWH(image.width.toFloat(), image.height.toFloat())
            bg.newSurface().withCanvas {
                drawRect(bgRect, paint)
                drawImageRectNearest(image, src, rect)
                drawImage(bg, 0F, 0F)
            }
        }
    }
}
