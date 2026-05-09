package top.e404.tavolo.handler.face

import top.e404.tavolo.assets.Assets
import org.jetbrains.skia.Rect
import top.e404.tavolo.annotation.ImageHandler
import top.e404.tavolo.frame.Frame
import top.e404.tavolo.frame.FramesHandler
import top.e404.tavolo.frame.HandleResult.Companion.result
import top.e404.tavolo.frame.common
import top.e404.tavolo.frame.handle
import top.e404.tavolo.util.*

@ImageHandler("jszz")
object JszzHandler : FramesHandler {
    private val bg = Assets.image("handlers/jszz/assets/zz.png")

    override val name = "精神支柱"
    override val regex = Regex("(?i)精神支柱|精神|支柱|jszz|js|zz")

    override suspend fun handleFrames(
        frames: MutableList<Frame>,
        args: MutableMap<String, String>,
    ) = frames.result {
        common(args).handle {
            bg.newSurface().withCanvas {
                drawImage(bg, 0F, 0F)
                val face = it.subCenter().rotate(337F)
                drawImageRect(
                    face,
                    Rect.makeWH(face.width.toFloat(), face.height.toFloat()),
                    Rect.makeXYWH(-174F, -22F, 1075F, 1075F)
                )
            }
        }
    }
}
