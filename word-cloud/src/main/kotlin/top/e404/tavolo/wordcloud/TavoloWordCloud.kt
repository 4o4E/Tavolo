package top.e404.tavolo.wordcloud

import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import top.e404.tavolo.util.FontManager

object TavoloWordCloud {
    private val layoutEngine = WordCloudLayoutEngine()

    fun render(entries: List<WordCloudEntry>, options: WordCloudOptions = WordCloudOptions()): Image =
        render(entries, WordCloudMask.rectangle(options.width, options.height), options)

    fun render(
        entries: List<WordCloudEntry>,
        mask: WordCloudMask,
        options: WordCloudOptions = WordCloudOptions(width = mask.width, height = mask.height),
    ): Image {
        val layout = layout(entries, mask, options)
        return renderLayout(layout, mask.width, mask.height, options)
    }

    fun layout(
        entries: List<WordCloudEntry>,
        mask: WordCloudMask,
        options: WordCloudOptions = WordCloudOptions(width = mask.width, height = mask.height),
    ): List<WordCloudPlacedEntry> =
        layoutEngine.layout(entries, mask, options)

    fun renderPng(
        entries: List<WordCloudEntry>,
        mask: WordCloudMask? = null,
        options: WordCloudOptions = WordCloudOptions(),
    ): ByteArray {
        val image = if (mask == null) render(entries, options) else render(entries, mask, options)
        return requireNotNull(image.encodeToData(EncodedImageFormat.PNG)) {
            "词云图片编码失败"
        }.bytes
    }

    private fun renderLayout(layout: List<WordCloudPlacedEntry>, width: Int, height: Int, options: WordCloudOptions): Image =
        Surface.makeRasterN32Premul(width, height).use { surface ->
            val canvas = surface.canvas
            canvas.clear(options.backgroundColor)
            val typeface = FontManager.resolve(options.fontFamily)
            val paint = Paint().apply {
                isAntiAlias = true
                color = Color.BLACK
            }
            for (entry in layout) {
                paint.color = entry.color
                val font = Font(typeface, entry.fontSize)
                if (entry.rotation == 0f) {
                    canvas.drawString(entry.text, entry.left + entry.drawX, entry.top + entry.drawBaselineY, font, paint)
                } else {
                    canvas.save()
                    try {
                        canvas.translate(entry.left + entry.width / 2f, entry.top + entry.height / 2f)
                        canvas.rotate(entry.rotation)
                        canvas.translate(-entry.drawWidth / 2f, -entry.drawHeight / 2f)
                        canvas.drawString(entry.text, entry.drawX, entry.drawBaselineY, font, paint)
                    } finally {
                        canvas.restore()
                    }
                }
            }
            surface.makeImageSnapshot()
        }
}
