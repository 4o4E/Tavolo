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
 * 龙鸣
 */
@ImageHandler("long_ming")
object LongMingHandler : FramesHandler {
    private val bg = Assets.image("handlers/long_ming/assets/lm.png")
    private val bgRect = Rect.makeWH(bg.width.toFloat(), bg.height.toFloat())
    private val paint = Paint().apply {
        color = Colors.WHITE.argb
    }

    override val name = "龙鸣"
    override val regex = Regex("龙鸣|(?i)lm")

    override suspend fun handleFrames(
        frames: MutableList<Frame>,
        args: MutableMap<String, String>,
    ) = frames.result {
        common(args).handle {
            bg.newSurface().withCanvas {
                drawRect(bgRect, paint)
                val face = it.subCenter()
                drawImageRectNearest(
                    face,
                    Rect.makeWH(face.width.toFloat(), face.height.toFloat()),
                    Rect.makeXYWH(228F, 126F, 234F, 234F)
                )
                drawImage(bg, 0F, 0F)
            }
        }
    }
}
