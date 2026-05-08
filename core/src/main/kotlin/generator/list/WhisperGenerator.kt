package top.e404.tavolo.generator.list

import top.e404.tavolo.frame.Frame
import top.e404.tavolo.generator.ImageGenerator
import top.e404.tavolo.handler.face.WriteHandler
import top.e404.tavolo.util.getJarImage

/**
 * 悄悄话图片生成
 */
object WhisperGenerator : ImageGenerator {
    private val bg = getJarImage(this::class.java, "statistic/whisper.png")

    override suspend fun generate(args: MutableMap<String, String>): MutableList<Frame> {
        args["location"] = "OUTSIDE_BOTTOM"
        return WriteHandler.handleFrames(
            mutableListOf(Frame(0, bg)), args
        ).getOrThrow().toMutableList()
    }
}
