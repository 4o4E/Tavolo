package top.e404.tavolo.handler.list

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.IRect
import top.e404.tavolo.annotation.ImageHandler
import top.e404.tavolo.util.argb
import top.e404.tavolo.frame.Frame
import top.e404.tavolo.frame.FramesHandler
import top.e404.tavolo.frame.HandleResult.Companion.result
import top.e404.tavolo.frame.common
import top.e404.tavolo.frame.handle
import top.e404.tavolo.util.limit
import top.e404.tavolo.util.rgb
import top.e404.tavolo.util.toBitmap
import top.e404.tavolo.util.toImage

/**
 * 浮雕效果
 */
@ImageHandler("emboss")
object EmbossHandler : FramesHandler {
    override val name = "浮雕"
    override val regex = Regex("(?i)浮雕|fd|emboss")

    override suspend fun handleFrames(
        frames: MutableList<Frame>,
        args: MutableMap<String, String>,
    ) = frames.result {
        common(args).handle {
            val old = it.toBitmap()
            val new = it.toBitmap()
            for (x in 1 until old.width - 1) for (y in 1 until old.height - 1) {
                new.erase(old.fd(x, y), IRect.makeXYWH(x, y, 1, 1))
            }
            new.toImage()
        }
    }

    private fun Bitmap.fd(x: Int, y: Int): Int {
        val c = getColor(x, y)
        val a = c shr 24
        if (a == 0) return c
        val (pr, pg, pb) = getColor(x - 1, y - 1).rgb()
        val (nr, ng, nb) = getColor(x + 1, y + 1).rgb()
        return argb(
            a,
            (pr - nr + 128).limit(),
            (pg - ng + 128).limit(),
            (pb - nb + 128).limit(),
        )
    }
}
