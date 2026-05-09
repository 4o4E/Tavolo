package top.e404.tavolo.generator.list

import top.e404.tavolo.assets.Assets
import top.e404.tavolo.annotation.ImageGenerator
import org.jetbrains.skia.Paint
import org.jetbrains.skia.TextLine
import top.e404.tavolo.TavoloFonts
import top.e404.tavolo.frame.toFrames
import top.e404.tavolo.generator.FramesGenerator
import top.e404.tavolo.util.newSurface
import top.e404.tavolo.util.withCanvas

@ImageGenerator("graduation_record")
object GraduationRecordGenerator : FramesGenerator {
    private val bg by lazy { Assets.image("generators/graduation_record/assets/record.png") }
    private const val fontSize = 42F
    private val font = TavoloFonts.font(TavoloFonts.HEI, fontSize)
    val paint = Paint().apply { color = 0xFF979797.toInt() }
    override suspend fun generate(
        args: MutableMap<String, String>,
    ) = bg.newSurface().withCanvas {
        drawImage(bg, 0f, 0f)
        args["text"]!!.lines().forEachIndexed { index, s ->
            val line = TextLine.make(s, font)
            val w = line.width
            drawTextLine(line, bg.width - w - 80F, 490F + index * 138, paint)
        }
    }.toFrames()
}
