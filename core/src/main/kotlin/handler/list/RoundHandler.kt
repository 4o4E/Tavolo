package top.e404.tavolo.handler.list

import top.e404.tavolo.annotation.ImageHandler
import top.e404.tavolo.frame.Frame
import top.e404.tavolo.frame.FramesHandler
import top.e404.tavolo.frame.HandleResult.Companion.result
import top.e404.tavolo.frame.common
import top.e404.tavolo.util.round

@ImageHandler
object RoundHandler : FramesHandler {
    override val name = "Round"
    override val regex = Regex("(?i)round")
    override suspend fun handleFrames(
        frames: MutableList<Frame>,
        args: MutableMap<String, String>,
    ) = frames.result {
        common(args).onEach { frame ->
            frame.handleImage { image -> image.round() }
        }
    }
}
