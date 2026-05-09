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
 * 垂直翻转 `b -> d`
 */
@ImageHandler("flip_vertical")
object FlipVerticalHandler : FramesHandler {
    override val name = "垂直翻转"
    override val regex = Regex("(?i)垂直翻转|左右翻转|czfz|sxfz|szfz")
    override suspend fun handleFrames(
        frames: MutableList<Frame>,
        args: MutableMap<String, String>,
    ) = frames.result {
        common(args).handle { it.flipVertical() }
    }

    fun Image.flipVertical() = Surface.makeRaster(imageInfo).withCanvas {
        setMatrix(
            Matrix33(
                -1F, 0F, width.toFloat(),
                0F, 1F, 0F,
                0F, 0F, 1F
            )
        )
        drawImage(this@flipVertical, 0F, 0F)

    }
}
