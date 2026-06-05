package top.e404.tavolo.handler.face

import top.e404.tavolo.assets.Assets
import top.e404.tavolo.annotation.ImageHandler
import top.e404.tavolo.frame.Frame
import top.e404.tavolo.frame.FramesHandler
import top.e404.tavolo.frame.HandleResult.Companion.result
import top.e404.tavolo.frame.common
import top.e404.tavolo.util.newSurface
import top.e404.tavolo.util.round
import top.e404.tavolo.util.withCanvas

/**
 * 白嫖
 */
@ImageHandler("demand")
object DemandHandler : FramesHandler {
    private val bg = Assets.image("handlers/demand/assets/demand.jpg")

    override val name = "白嫖"
    override val regex = Regex("(?i)白嫖|bp")

    override suspend fun handleFrames(
        frames: MutableList<Frame>,
        args: MutableMap<String, String>,
    ) = frames.result {
        common(args).onEach { frame ->
            frame.handleImage {
                bg.newSurface().withCanvas {
                    drawImage(bg, 0F, 0F)
                    drawImage(it.round(90), 107F, 37F)
                }
            }
        }
    }
}
