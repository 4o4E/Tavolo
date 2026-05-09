package top.e404.tavolo.generator.list

import top.e404.tavolo.assets.Assets
import top.e404.tavolo.annotation.ImageGenerator
import top.e404.tavolo.frame.Frame
import top.e404.tavolo.generator.FramesGenerator
import top.e404.tavolo.handler.face.WriteHandler

/**
 * 悄悄话图片生成
 */
@ImageGenerator("whisper")
object WhisperGenerator : FramesGenerator {
    private val bg = Assets.image("generators/whisper/assets/whisper.png")

    override suspend fun generate(args: MutableMap<String, String>): MutableList<Frame> {
        args["location"] = "OUTSIDE_BOTTOM"
        return WriteHandler.handleFrames(
            mutableListOf(Frame(0, bg)), args
        ).getOrThrow().toMutableList()
    }
}
