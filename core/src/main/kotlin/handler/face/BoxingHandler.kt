package top.e404.tavolo.handler.face

import top.e404.tavolo.assets.Assets
import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import top.e404.tavolo.annotation.ImageHandler
import top.e404.tavolo.frame.*
import top.e404.tavolo.frame.HandleResult.Companion.result
import top.e404.tavolo.handler.DrawData
import top.e404.tavolo.util.*

/**
 * 拳击
 */
@ImageHandler("boxing")
object BoxingHandler : FramesHandler {
    private const val size = 500
    private const val count = 8
    private val list by lazy { Yaml.default.decodeFromString<List<BoxingData>>(Assets.text("handlers/boxing/assets/boxing/boxing.yml")) }
    private val hand by lazy { Assets.image("handlers/boxing/assets/boxing/fisted-hand.png") }
    private val handSrc by lazy { Rect.makeWH(hand.width.toFloat(), hand.height.toFloat()) }

    @Serializable
    private data class BoxingData(
        val head: DrawData,
        val left: DrawData,
        val right: DrawData,
    )

    override val name = "boxing"
    override val regex = Regex("(?i)boxing")

    override suspend fun handleFrames(
        frames: MutableList<Frame>,
        args: MutableMap<String, String>,
    ) = frames.handle { it.round() }.common(args).replenish(count, Frame::limitAsGif).result {
        pmapIndexed { index ->
            duration = 60
            handleImage {
                val headSrc = Rect.makeWH(it.width.toFloat(), it.height.toFloat())
                Surface.makeRasterN32Premul(
                    BoxingHandler.size,
                    BoxingHandler.size
                ).withCanvas {
                    list[index % 8].head.draw(this, image, headSrc)
                    list[index % 8].left.draw(this, hand, handSrc)
                    list[index % 8].right.draw(this, hand, handSrc)
                }
            }
        }
    }
}
