package top.e404.tavolo.wordcloud

import org.jetbrains.skia.Color
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import top.e404.tavolo.util.FontManager
import top.e404.tavolo.util.toBitmap
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TavoloWordCloudTest {
    private val fontFamily = FontManager.registerSystem("word-cloud-test-ui", testSystemFontFamily())

    @AfterTest
    fun resetFonts() {
        FontManager.clearRegistered()
    }

    @Test
    fun renderRejectsEmptyEntriesAfterFiltering() {
        val error = assertFailsWith<IllegalArgumentException> {
            TavoloWordCloud.render(
                listOf(
                    WordCloudEntry("", 10),
                    WordCloudEntry("无效", 0),
                ),
                WordCloudOptions(width = 200, height = 120, fontFamily = fontFamily)
            )
        }

        assertEquals(true, error.message?.contains("词频列表为空"))
    }

    @Test
    fun renderRectangleWordCloudProducesNonBlankImage() {
        val image = TavoloWordCloud.render(sampleEntries(), WordCloudOptions(width = 320, height = 220, fontFamily = fontFamily))
        val bitmap = image.toBitmap()
        var changed = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (bitmap.getColor(x, y) != 0xFFDDDDDD.toInt()) changed += 1
        }

        assertEquals(320, image.width)
        assertEquals(220, image.height)
        assertTrue(changed > 0, "词云应该绘制出非背景像素")
    }

    @Test
    fun renderWithMaskUsesMaskSize() {
        val mask = WordCloudMaskFactory.fromOtsu(templateImage(), OtsuMaskOptions(maxSide = 160))

        val image = TavoloWordCloud.render(
            sampleEntries(),
            mask,
            WordCloudOptions(width = 1, height = 1, fontFamily = fontFamily, minFontSize = 8f, maxFontSize = 34f)
        )

        assertEquals(mask.width, image.width)
        assertEquals(mask.height, image.height)
    }

    @Test
    fun renderPngReturnsPngBytes() {
        val bytes = TavoloWordCloud.renderPng(
            sampleEntries().take(2),
            options = WordCloudOptions(width = 180, height = 120, fontFamily = fontFamily, minFontSize = 12f, maxFontSize = 28f)
        )

        assertTrue(bytes.size > 8)
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals('P'.code.toByte(), bytes[1])
        assertEquals('N'.code.toByte(), bytes[2])
        assertEquals('G'.code.toByte(), bytes[3])
    }

    @Test
    fun rotatedTextStaysInsideLayoutBounds() {
        val layout = TavoloWordCloud.layout(
            sampleEntries().take(4),
            WordCloudMask.rectangle(360, 260),
            WordCloudOptions(
                width = 360,
                height = 260,
                fontFamily = fontFamily,
                minFontSize = 18f,
                maxFontSize = 52f,
                rotations = listOf(90f),
                maxPlacementAttempts = 8000,
            )
        )

        assertTrue(layout.isNotEmpty(), "旋转词云应该至少放下一个词")
        assertTrue(layout.all { it.rotation == 90f }, "布局应该保留调用方指定的旋转角度")
        assertTrue(layout.all { it.left >= 0 && it.top >= 0 }, "旋转文字不应该越过画布左上边界")
        assertTrue(layout.all { it.left + it.width <= 360 && it.top + it.height <= 260 }, "旋转文字不应该越过画布右下边界")
    }

    @Test
    fun renderRotatedWordCloudProducesNonBlankImage() {
        val image = TavoloWordCloud.render(
            sampleEntries().take(4),
            WordCloudOptions(
                width = 360,
                height = 260,
                fontFamily = fontFamily,
                minFontSize = 18f,
                maxFontSize = 52f,
                rotations = listOf(90f),
                maxPlacementAttempts = 8000,
            )
        )
        val bitmap = image.toBitmap()
        var changed = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (bitmap.getColor(x, y) != 0xFFDDDDDD.toInt()) changed += 1
        }

        assertTrue(changed > 0, "旋转词云应该绘制出非背景像素")
    }

    @Test
    fun invalidOptionsFailClearly() {
        assertFailsWith<IllegalArgumentException> {
            TavoloWordCloud.layout(sampleEntries(), WordCloudMask.rectangle(120, 80), WordCloudOptions(minFontSize = 0f, fontFamily = fontFamily))
        }
        assertFailsWith<IllegalArgumentException> {
            TavoloWordCloud.layout(sampleEntries(), WordCloudMask.rectangle(120, 80), WordCloudOptions(minFontSize = 30f, maxFontSize = 20f, fontFamily = fontFamily))
        }
        assertFailsWith<IllegalArgumentException> {
            TavoloWordCloud.layout(sampleEntries(), WordCloudMask.rectangle(120, 80), WordCloudOptions(maxWords = 0, fontFamily = fontFamily))
        }
        assertFailsWith<IllegalArgumentException> {
            TavoloWordCloud.layout(sampleEntries(), WordCloudMask.rectangle(120, 80), WordCloudOptions(rotations = emptyList(), fontFamily = fontFamily))
        }
    }

    @Test
    fun emptyMaskFailsClearly() {
        val mask = WordCloudMask(20, 20, BooleanArray(400))

        assertFailsWith<IllegalArgumentException> {
            TavoloWordCloud.layout(sampleEntries(), mask, WordCloudOptions(fontFamily = fontFamily, minFontSize = 8f, maxFontSize = 12f))
        }
    }

    @Test
    fun paletteRejectsEmptyColors() {
        assertFailsWith<IllegalArgumentException> {
            WordCloudPalette(emptyList())
        }
    }

    private fun sampleEntries(): List<WordCloudEntry> =
        listOf(
            WordCloudEntry("Tavolo", 100),
            WordCloudEntry("词云", 90),
            WordCloudEntry("Skiko", 80),
            WordCloudEntry("模板", 70),
            WordCloudEntry("渲染", 65),
            WordCloudEntry("二值图", 55),
            WordCloudEntry("边缘检测", 45),
            WordCloudEntry("蒙版", 35),
        )

    private fun templateImage() =
        Surface.makeRasterN32Premul(180, 140).use { surface ->
            val canvas = surface.canvas
            canvas.clear(Color.WHITE)
            val paint = Paint().apply {
                color = Color.BLACK
                isAntiAlias = true
            }
            canvas.drawOval(Rect.makeXYWH(30f, 20f, 120f, 90f), paint)
            surface.makeImageSnapshot()
        }

    private fun testSystemFontFamily(): String {
        val families = FontManager.systemFamilies().toSet()
        return listOf("Microsoft YaHei", "DejaVu Sans", "Liberation Sans", "Arial")
            .firstOrNull { it in families }
            ?: families.firstOrNull()
            ?: "sans"
    }
}
