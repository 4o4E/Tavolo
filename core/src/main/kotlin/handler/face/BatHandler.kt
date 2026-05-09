package top.e404.tavolo.handler.face

import top.e404.tavolo.assets.Assets
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import top.e404.tavolo.annotation.ImageHandler
import top.e404.tavolo.frame.*
import top.e404.tavolo.frame.HandleResult.Companion.result
import top.e404.tavolo.handler.DrawData
import top.e404.tavolo.util.round
import top.e404.tavolo.util.withCanvas

/**
 * 球拍
 */
@ImageHandler("bat")
object BatHandler : FramesHandler {
    private const val w = 500
    private const val h = 377
    private const val count = 7
    private val range = 0..count
    private val bgList = range.map { Assets.image("handlers/bat/assets/bat/$it.png") }
    private val ddList = DrawData.loadFromAssets("handlers/bat/assets/bat/bat.yml")
    private val bgRect = Rect.makeWH(w.toFloat(), h.toFloat())

    override val name = "bat"
    override val regex = Regex("(?i)bat")

    override suspend fun handleFrames(
        frames: MutableList<Frame>,
        args: MutableMap<String, String>,
    ): HandleResult {
        return frames.handle { it.round() }.common(args).replenish(count + 1, Frame::limitAsGif).result {
            handleIndexed { index, image ->
                val src = Rect.makeWH(image.width.toFloat(), image.height.toFloat())
                Surface.makeRasterN32Premul(w, h).withCanvas {
                    drawImageRect(bgList[index % 8], bgRect)
                    ddList[index % 8].draw(this, image, src)
                }
            }
        }
    }
}
