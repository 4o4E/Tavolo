package top.e404.tavolo.handler.list

import org.jetbrains.skia.*
import top.e404.tavolo.annotation.ImageHandler
import top.e404.tavolo.frame.*
import top.e404.tavolo.frame.HandleResult.Companion.result
import top.e404.tavolo.util.intOrPercentage
import top.e404.tavolo.util.newBitmap
import top.e404.tavolo.util.toBitmap
import top.e404.tavolo.util.toImage
import kotlin.math.min

/**
 * 方格化1
 */
@ImageHandler
object PaneHandler : FramesHandler {
    override val name = "Pane"
    override val regex = Regex("(?i)pane")

    override suspend fun handleFrames(
        frames: MutableList<Frame>,
        args: MutableMap<String, String>,
    ): HandleResult {
        val rate = args["text"].intOrPercentage(-10)
        return frames.common(args).result {
            handle {
                val src = it.toBitmap()
                val r = if (rate > 0) rate else rate * min(src.width, src.height) / -100
                val dst = it.newBitmap(-2 * r)
                for (x in 0 until dst.width) for (y in 0 until dst.height) {
                    val vx = r + x + x % r
                    val vy = r + y + y % r
                    dst.erase(src.getColor(vx, vy), IRect.makeXYWH(x, y, 1, 1))
                }
                dst.toImage()
            }
        }
    }
}