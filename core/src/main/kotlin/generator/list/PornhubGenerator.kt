package top.e404.tavolo.generator.list

import top.e404.tavolo.annotation.ImageGenerator
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.TextLine
import top.e404.tavolo.util.Colors
import top.e404.tavolo.TavoloFonts
import top.e404.tavolo.frame.Frame
import top.e404.tavolo.frame.toFrames
import top.e404.tavolo.generator.FramesGenerator
import top.e404.tavolo.util.withCanvas

@ImageGenerator("pornhub")
object PornhubGenerator : FramesGenerator {
    private const val space = 30F
    private const val height = 170
    private const val radius = 20F
    private val font = TavoloFonts.font(TavoloFonts.MI_BOLD, 70F)
    override suspend fun generate(args: MutableMap<String, String>): MutableList<Frame> {
        val s1 = args["s1"]!!
        val s2 = args["s2"]!!
        val lineLeft = TextLine.make(s1, font)
        val lineRight = TextLine.make(s2, font)
        val lenLeft = lineLeft.width
        val lenRight = lineRight.width
        val width = space * 3.5 + lenLeft + lenRight
        return Surface.makeRasterN32Premul(width.toInt(), height).withCanvas {
            val paint = Paint()
            // bg
            drawRRect(
                RRect.makeXYWH(0f, 0f, width.toFloat(), height.toFloat(), radius),
                paint.apply { color = Colors.BLACK.argb }
            )
            // left
            paint.color = 0xffffffff.toInt()
            drawTextLine(lineLeft, space, 108F, paint)
            // right bg
            paint.color = 0xffff9000.toInt()
            drawRRect(
                RRect.makeXYWH(45 + lenLeft, 40F, lenRight + 30, 90F, radius),
                paint
            )
            // right line
            paint.color = Colors.BLACK.argb
            drawTextLine(lineRight, space * 2 + lenLeft, 108F, paint)
        }.toFrames()
    }
}
