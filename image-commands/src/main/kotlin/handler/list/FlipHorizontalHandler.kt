package top.e404.tavolo.handler.list

import org.jetbrains.skia.Image
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Surface
import top.e404.tavolo.annotation.ImageHandler
import top.e404.tavolo.frame.Frame
import top.e404.tavolo.frame.FramesHandler
import top.e404.tavolo.frame.HandleResult.Companion.result
import top.e404.tavolo.frame.common
import top.e404.tavolo.frame.handle
import top.e404.tavolo.util.withCanvas

/**
 * 水平翻转 `b -> p`
 */
@ImageHandler("flip_horizontal")
object FlipHorizontalHandler : FramesHandler {
    override val name = "水平翻转"
    override val regex = Regex("(?i)(水平|上下)翻转|spfz|sxfz")
    override suspend fun handleFrames(
        frames: MutableList<Frame>,
        args: MutableMap<String, String>,
    ) = frames.result {
        common(args).handle { it.flipHorizontal() }
    }

    fun Image.flipHorizontal() = Surface.makeRaster(imageInfo).withCanvas {
        setMatrix(
            Matrix33(
                1F, 0F, 0F,
                0F, -1F, height.toFloat(),
                0F, 0F, 1F
            )
        )
        drawImage(this@flipHorizontal, 0F, 0F)
    }
}
