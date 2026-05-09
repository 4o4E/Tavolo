package top.e404.tavolo.generator.list

import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import org.jetbrains.skia.TextLine
import top.e404.tavolo.BdfType
import top.e404.tavolo.TavoloFonts
import top.e404.tavolo.dot.generator
import top.e404.tavolo.dot.toBitMatrix
import top.e404.tavolo.frame.Frame
import top.e404.tavolo.generator.ImageGenerator
import top.e404.tavolo.util.*

object DotMatrixCharImageGenerator : ImageGenerator {
    private val font = TavoloFonts.font(TavoloFonts.HEI, 20F)
    private val fullHeight = font.height()
    override suspend fun generate(args: MutableMap<String, String>): MutableList<Frame> {
        val text = args["text"]!!
        val color = args["color"]?.asColor() ?: Colors.WHITE.argb
        val bg = args["bg"]?.asColor() ?: Colors.BG.argb
        val paint = Paint().also { it.color = color }

        val matrix = text.toBitMatrix(BdfType.UNI_FONT.font)
        val generator = matrix.generator(
            args["ud"]?.toIntOrNull() ?: 0,
            args["lr"]?.toIntOrNull() ?: 0
        )
        val lines = generator.lines().map { TextLine.make(it, font) }

        return mutableListOf(
            Frame(0, Surface.makeRasterN32Premul(lines[0].width.toInt(), (fullHeight * lines.size).toInt()).fill(bg).withCanvas {
                lines.forEachIndexed { index, line ->
                    drawTextLine(line, 0F, index * fullHeight - font.metrics.ascent, paint)
                }
            })
        )
    }
}
