package top.e404.tavolo.handler.face

import top.e404.tavolo.assets.Assets
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import top.e404.tavolo.annotation.ImageHandler
import top.e404.tavolo.frame.*
import top.e404.tavolo.frame.HandleResult.Companion.result
import top.e404.tavolo.handler.DrawData
import top.e404.tavolo.util.pmapIndexed
import top.e404.tavolo.util.round
import top.e404.tavolo.util.withCanvas

/**
 * 嫌弃
 */
@ImageHandler("fondle")
object FondleHandler : FramesHandler {
    private const val w = 100
    private const val h = 110
    private const val count = 28
    private val range = 0..count
    private val bgList = range.map { Assets.image("handlers/fondle/assets/fondle/$it.png") }
    private val ddList = DrawData.loadFromAssets("handlers/fondle/assets/fondle/fondle.yml")

    override val name = "fondle"
    override val regex = Regex("(?i)fondle")

    override suspend fun handleFrames(
        frames: MutableList<Frame>,
        args: MutableMap<String, String>,
    ) = frames.handle { it.round() }.common(args).replenish(count + 1).result {
        pmapIndexed { index ->
            duration(80)
            handleImage {
                val src = Rect.makeWH(it.width.toFloat(), it.height.toFloat())
                Surface.makeRasterN32Premul(w, h).withCanvas {
                    drawImage(bgList[index], 0F, 0F)
                    ddList[index].draw(this, image, src)
                }
            }
        }
    }
}
