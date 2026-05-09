package top.e404.tavolo.handler.list

import top.e404.tavolo.assets.Assets
import top.e404.tavolo.annotation.ImageHandler
import top.e404.tavolo.frame.Frame
import top.e404.tavolo.frame.FramesHandler
import top.e404.tavolo.frame.HandleResult.Companion.result
import top.e404.tavolo.frame.common
import top.e404.tavolo.frame.handle
import top.e404.tavolo.util.newSurface
import top.e404.tavolo.util.resize
import top.e404.tavolo.util.withCanvas
import kotlin.math.min

/**
 * x64
 */
@ImageHandler("x64")
object X64Handler : FramesHandler {
    private val x64 by lazy { Assets.image("handlers/x64/assets/64x.png") }

    override val name = "x64"
    override val regex = Regex("(?i)x64|64x")
    override suspend fun handleFrames(
        frames: MutableList<Frame>,
        args: MutableMap<String, String>,
    ) = frames.result {
        val first = first()
        val size = min(first.image.width, first.image.height)
        val resize = x64.resize(size / 2, size / 3, true)
        common(args).handle {
            it.newSurface().withCanvas {
                drawImage(it, 0f, 0f)
                drawImage(
                    resize,
                    it.width.toFloat() - resize.width,
                    it.height.toFloat() - resize.height
                )
            }
        }
    }
}
