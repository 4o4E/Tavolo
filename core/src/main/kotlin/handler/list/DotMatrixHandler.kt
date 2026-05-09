package top.e404.tavolo.handler.list

import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import org.jetbrains.skia.TextLine
import top.e404.tavolo.TavoloFonts
import top.e404.tavolo.annotation.ImageHandler
import top.e404.tavolo.dot.binary
import top.e404.tavolo.dot.generator
import top.e404.tavolo.dot.gray
import top.e404.tavolo.frame.Frame
import top.e404.tavolo.frame.FramesHandler
import top.e404.tavolo.frame.HandleResult.Companion.result
import top.e404.tavolo.frame.common
import top.e404.tavolo.frame.handle
import top.e404.tavolo.util.*

@ImageHandler("dot_matrix")
object DotMatrixHandler : FramesHandler {
    private val font = TavoloFonts.font(TavoloFonts.HEI, 20F)
    private val fullHeight = font.height()

    override val name = "点阵字符画"
    override val regex = Regex("(?i)点阵字符画|dot")

    override suspend fun handleFrames(
        frames: MutableList<Frame>,
        args: MutableMap<String, String>,
    ) = frames.result {
        val color = args["color"]?.asColor() ?: Colors.WHITE.argb
        val bg = args["bg"]?.asColor() ?: Colors.BG.argb
        val paint = Paint().also { it.color = color }
        common(args).handle { src ->
            val bitImage = binary(gray(src))
            val text = bitImage.generator(
                args["ud"]?.toIntOrNull() ?: 2,
                args["lr"]?.toIntOrNull() ?: 2,
            )
            val lines = text.lines().map {
                TextLine.make(it, font)
            }

            Surface.makeRasterN32Premul(lines[0].width.toInt(), (fullHeight * lines.size).toInt()).fill(bg).withCanvas {
                lines.forEachIndexed { index, line ->
                    drawTextLine(line, 0F, index * fullHeight - font.metrics.ascent, paint)
                }
            }
        }
    }
}
