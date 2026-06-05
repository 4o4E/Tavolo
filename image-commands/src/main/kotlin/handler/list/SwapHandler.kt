package top.e404.tavolo.handler.list

import top.e404.tavolo.annotation.ImageHandler
import top.e404.tavolo.util.argb
import top.e404.tavolo.frame.Frame
import top.e404.tavolo.frame.FramesHandler
import top.e404.tavolo.frame.HandleResult.Companion.result
import top.e404.tavolo.frame.common
import top.e404.tavolo.frame.handle
import top.e404.tavolo.util.handlePixel

/**
 * 交换色相
 */
@ImageHandler("swap")
object SwapHandler : FramesHandler {
    override val name = "交换色相"
    override val regex = Regex("(?i)swap|交换色相")
    override suspend fun handleFrames(
        frames: MutableList<Frame>,
        args: MutableMap<String, String>,
    ) = frames.result {
        common(args).handle { it.handlePixel(handler) }
    }

    private val handler = fun(pixel: Int): Int {
        val (a, r, g, b) = pixel.argb()
        if (a == 0) return 0
        return argb(a, g, b, r)
    }
}
