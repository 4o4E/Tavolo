package top.e404.tavolo.handler.list

import top.e404.tavolo.annotation.ImageHandler
import top.e404.tavolo.frame.Frame
import top.e404.tavolo.frame.FramesHandler
import top.e404.tavolo.frame.HandleResult
import top.e404.tavolo.frame.HandleResult.Companion.result
import top.e404.tavolo.frame.common
import top.e404.tavolo.util.resize

@ImageHandler
object PxHandler : FramesHandler {
    override val name = "像素画"
    override val regex = Regex("(?i)px|pixel")
    override suspend fun handleFrames(
        frames: MutableList<Frame>,
        args: MutableMap<String, String>,
    ): HandleResult {
        val scale = args["text"]?.toIntOrNull() ?: 10
        return frames.result {
            common(args).onEach { frame ->
                frame.image = frame.image.run {
                    resize(
                        width / scale,
                        height / scale,
                        true
                    ).resize(
                        width,
                        height,
                        true
                    )
                }
            }
        }
    }
}