package top.e404.tavolo.wordcloud

import org.jetbrains.skia.Color
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WordCloudMaskFactoryTest {
    @Test
    fun otsuThresholdSplitsBlackAndWhitePixels() {
        val values = IntArray(100) { if (it < 50) 0 else 255 }

        val threshold = WordCloudMaskFactory.otsuThreshold(values)

        assertTrue(threshold in 0..254)
    }

    @Test
    fun otsuMaskUsesDarkPixelsAsAllowedArea() {
        val image = testTemplateImage()

        val mask = WordCloudMaskFactory.fromOtsu(image)

        assertEquals(80, mask.width)
        assertEquals(60, mask.height)
        assertTrue(mask.isAllowed(25, 25), "深色模板区域应该允许绘制")
        assertEquals(false, mask.isAllowed(2, 2), "浅色背景区域不应该允许绘制")
    }

    @Test
    fun otsuMaskCanUseLightPixelsAsAllowedArea() {
        val image = testTemplateImage()

        val mask = WordCloudMaskFactory.fromOtsu(image, OtsuMaskOptions(darkAsAllowed = false))

        assertEquals(false, mask.isAllowed(25, 25), "反向 OTSU 不应该允许深色模板区域")
        assertTrue(mask.isAllowed(2, 2), "反向 OTSU 应该允许浅色背景区域")
    }

    @Test
    fun resizeByMaxSideKeepsAspectRatio() {
        val resized = WordCloudMaskFactory.resizeByMaxSide(testTemplateImage(), maxSide = 40)

        assertEquals(40, resized.width)
        assertEquals(30, resized.height)
    }

    @Test
    fun edgeMaskMarksTemplateBoundary() {
        val image = testTemplateImage()

        val mask = WordCloudMaskFactory.fromEdges(image, EdgeMaskOptions(threshold = 40, dilateRadius = 1))

        assertTrue(mask.allowedCount() > 0, "边缘检测应该生成非空二值图")
        assertTrue(mask.isAllowed(20, 30) || mask.isAllowed(59, 30), "矩形边缘附近应该允许绘制")
    }

    @Test
    fun edgeMaskKeepsBlankImageEmpty() {
        val image = Surface.makeRasterN32Premul(40, 30).use { surface ->
            surface.canvas.clear(Color.WHITE)
            surface.makeImageSnapshot()
        }

        val mask = WordCloudMaskFactory.fromEdges(image, EdgeMaskOptions(threshold = null, dilateRadius = 2))

        assertEquals(0, mask.allowedCount(), "空白图不应该被识别为整张边缘")
    }

    @Test
    fun edgeMaskKeepsTransparentImageEmpty() {
        val image = Surface.makeRasterN32Premul(40, 30).use { surface ->
            surface.canvas.clear(Color.TRANSPARENT)
            surface.makeImageSnapshot()
        }

        val mask = WordCloudMaskFactory.fromEdges(image, EdgeMaskOptions(threshold = 0, dilateRadius = 0))

        assertEquals(0, mask.allowedCount(), "透明图不应该生成边缘")
    }

    @Test
    fun edgeMaskCanFillInsideClosedBoundary() {
        val image = testTemplateImage()

        val mask = WordCloudMaskFactory.fromEdges(
            image,
            EdgeMaskOptions(threshold = 24, dilateRadius = 2, fillBetweenEdges = true)
        )

        assertEquals(false, mask.isAllowed(2, 2), "闭合边缘外部不应该被填充")
        assertTrue(mask.isAllowed(40, 30), "闭合边缘内部应该整体填充")
    }

    @Test
    fun edgeMaskAlternatesNestedBoundaryFill() {
        val image = nestedBoundaryImage()

        val mask = WordCloudMaskFactory.fromEdges(
            image,
            EdgeMaskOptions(threshold = 24, dilateRadius = 1, fillBetweenEdges = true)
        )

        assertEquals(false, mask.isAllowed(10, 50), "最外层边缘外部不应该被填充")
        assertTrue(mask.isAllowed(25, 50), "第一圈和第二圈边缘之间应该被填充")
        assertEquals(false, mask.isAllowed(35, 50), "第二圈和第三圈边缘之间不应该被填充")
        assertTrue(mask.isAllowed(50, 50), "第三圈边缘内部应该继续按交替规则填充")
    }

    @Test
    fun emptyOtsuInputFailsClearly() {
        assertFailsWith<IllegalArgumentException> {
            WordCloudMaskFactory.otsuThreshold(intArrayOf(-1, -1))
        }
    }

    @Test
    fun debugMaskCanEncodePng() {
        val bytes = WordCloudMaskFactory.encodeDebugPng(WordCloudMask.rectangle(8, 6))

        assertTrue(bytes.size > 8)
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals('P'.code.toByte(), bytes[1])
        assertEquals('N'.code.toByte(), bytes[2])
        assertEquals('G'.code.toByte(), bytes[3])
    }

    private fun testTemplateImage() =
        Surface.makeRasterN32Premul(80, 60).use { surface ->
            val canvas = surface.canvas
            canvas.clear(Color.WHITE)
            val paint = Paint().apply {
                color = Color.BLACK
                isAntiAlias = false
            }
            canvas.drawRect(Rect.makeXYWH(20f, 15f, 40f, 30f), paint)
            surface.makeImageSnapshot()
        }

    private fun nestedBoundaryImage() =
        Surface.makeRasterN32Premul(100, 100).use { surface ->
            val canvas = surface.canvas
            canvas.clear(Color.WHITE)
            val paint = Paint().apply {
                color = Color.BLACK
                isAntiAlias = false
            }
            canvas.drawRect(Rect.makeXYWH(20f, 20f, 60f, 60f), paint)
            paint.color = Color.WHITE
            canvas.drawRect(Rect.makeXYWH(30f, 30f, 40f, 40f), paint)
            paint.color = Color.BLACK
            canvas.drawRect(Rect.makeXYWH(42f, 42f, 16f, 16f), paint)
            surface.makeImageSnapshot()
        }
}
