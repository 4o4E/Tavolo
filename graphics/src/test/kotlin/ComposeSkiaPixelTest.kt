package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.junit.Test
import top.e404.tavolo.draw.compose.Box
import top.e404.tavolo.draw.compose.ImageOverflow
import top.e404.tavolo.draw.compose.Modifier
import top.e404.tavolo.draw.compose.Shape
import top.e404.tavolo.draw.compose.antiAlias
import top.e404.tavolo.draw.compose.background
import top.e404.tavolo.draw.compose.border
import top.e404.tavolo.draw.compose.box
import top.e404.tavolo.draw.compose.clip
import top.e404.tavolo.draw.compose.image
import top.e404.tavolo.draw.compose.padding
import top.e404.tavolo.draw.compose.render
import top.e404.tavolo.draw.compose.size
import top.e404.tavolo.draw.compose.svg
import top.e404.tavolo.util.toBitmap
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val COLOR_TOLERANCE = 2

class ComposeSkiaPixelTest {
    @Test
    fun renderSnapsFractionalRootSizeUpByDefault() {
        val image = render(
            backgroundColor = Color.TRANSPARENT,
            root = Box().apply {
                modifier = Modifier
                    .size(10.5f, 12.5f)
                    .border(.5f, Color.WHITE)
            }
        ) {}
        val bitmap = image.toBitmap()

        assertEquals(11, image.width)
        assertEquals(13, image.height)
        assertTrue(
            Color.getA(bitmap.getColor(5, 12)) > 0,
            "根容器小数高度向上取整后，底部边框不应被最终 Surface 裁掉"
        )
    }

    @Test
    fun renderCanKeepLegacyFractionalRootFlooring() {
        val image = render(
            backgroundColor = Color.TRANSPARENT,
            root = Box().apply {
                modifier = Modifier.size(10.5f, 12.5f)
            },
            snapRootSizeToPixel = false
        ) {}

        assertEquals(10, image.width)
        assertEquals(12, image.height)
    }

    @Test
    fun skiaRenderKeepsBackgroundAndSolidModifierPixels() {
        val image = render(
            backgroundColor = Color.WHITE,
            root = Box().apply { modifier = Modifier.size(8f) }
        ) {
            box(Modifier.size(3f, 2f).background(Color.RED))
        }
        val bitmap = image.toBitmap()

        assertEquals(8, image.width)
        assertEquals(8, image.height)
        assertColorNear(Color.RED, bitmap.getColor(1, 1))
        assertColorNear(Color.WHITE, bitmap.getColor(4, 4))
    }

    @Test
    fun skiaRenderAppliesClipToRealPixels() {
        val image = render(
            backgroundColor = Color.TRANSPARENT,
            root = Box().apply { modifier = Modifier.size(12f) }
        ) {
            box(
                Modifier
                    .size(12f)
                    .clip(Shape.Circle)
                    .background(Color.GREEN)
            )
        }
        val bitmap = image.toBitmap()

        assertAlphaNear(0, bitmap.getColor(0, 0))
        assertColorNear(Color.GREEN, bitmap.getColor(6, 6))
    }

    @Test
    fun skiaRenderClipAntiAliasIsEnabledByDefaultAndCanBeDisabled() {
        val smooth = renderCircleClip(antiAlias = true).toBitmap()
        val nearest = renderCircleClip(antiAlias = false).toBitmap()

        assertTrue(
            pixelColors(smooth, 12, 12).any { Color.getA(it) in 1..254 },
            "默认圆形裁剪应包含部分透明的平滑边缘像素"
        )
        assertTrue(
            pixelColors(nearest, 12, 12).none { Color.getA(it) in 1..254 },
            "关闭抗锯齿后圆形裁剪不应生成部分透明像素"
        )
    }

    @Test
    fun skiaRenderKeepsBorderPaddingAndBackgroundOrder() {
        val image = render(
            backgroundColor = Color.TRANSPARENT,
            root = Box().apply { modifier = Modifier.size(8f) }
        ) {
            box(
                Modifier
                    .size(6f)
                    .border(1f, Color.RED)
                    .padding(1f)
                    .background(Color.BLUE)
            )
        }
        val bitmap = image.toBitmap()

        assertColorNear(Color.RED, bitmap.getColor(0, 0))
        assertColorNear(Color.BLUE, bitmap.getColor(2, 2))
        assertAlphaNear(0, bitmap.getColor(7, 7))
    }

    @Test
    fun skiaRenderDrawsBackgroundImagePixels() {
        val source = quadrantImage()
        val image = render(
            backgroundColor = Color.TRANSPARENT,
            root = Box().apply { modifier = Modifier.size(4f) }
        ) {
            box(Modifier.size(4f).background(source, ImageOverflow.Stretch))
        }
        val bitmap = image.toBitmap()

        assertColorNear(Color.RED, bitmap.getColor(0, 0))
        assertColorNear(Color.GREEN, bitmap.getColor(3, 0))
        assertColorNear(Color.BLUE, bitmap.getColor(0, 3))
        assertColorNear(Color.YELLOW, bitmap.getColor(3, 3))
    }

    @Test
    fun skiaRenderSmoothsImageUpscalingByDefaultAndCanUseNearestNeighbor() {
        val source = stripeImage()
        val smooth = renderScaledImage(source, 8, 2, antiAlias = true).toBitmap()
        val nearest = renderScaledImage(source, 8, 2, antiAlias = false).toBitmap()
        val smoothReds = (0 until 8).map { Color.getR(smooth.getColor(it, 0)) }
        val nearestReds = (0 until 8).map { Color.getR(nearest.getColor(it, 0)) }

        assertTrue(smoothReds.any { it in 1..254 }, "默认放大应生成平滑过渡像素")
        assertTrue(nearestReds.all { it == 0 || it == 255 }, "关闭抗锯齿后放大应使用最近邻像素")
    }

    @Test
    fun skiaRenderSmoothsImageDownscalingByDefaultAndCanUseNearestNeighbor() {
        val source = checkerImage(16)
        val smooth = renderScaledImage(source, 2, 2, antiAlias = true).toBitmap()
        val nearest = renderScaledImage(source, 2, 2, antiAlias = false).toBitmap()
        val smoothReds = pixelColors(smooth, 2, 2).map(Color::getR)
        val nearestReds = pixelColors(nearest, 2, 2).map(Color::getR)

        assertTrue(smoothReds.all { it in 1..254 }, "默认缩小应混合高频像素以抑制锯齿")
        assertTrue(nearestReds.all { it == 0 || it == 255 }, "关闭抗锯齿后缩小应使用最近邻像素")
    }

    @Test
    fun skiaRenderDrawsFullSvgDomPixels() {
        val image = render(
            backgroundColor = Color.TRANSPARENT,
            root = Box().apply { modifier = Modifier.size(10f, 10f) }
        ) {
            svg(
                """
                <svg viewBox="0 0 10 10" xmlns="http://www.w3.org/2000/svg">
                  <g transform="translate(2 2)">
                    <polygon points="0,0 6,0 6,6 0,6" style="fill:#ff0000"/>
                  </g>
                </svg>
                """.trimIndent()
            )
        }
        val bitmap = image.toBitmap()

        assertColorNear(Color.RED, bitmap.getColor(5, 5))
        assertAlphaNear(0, bitmap.getColor(0, 0))
    }

    @Test
    fun skiaRenderResizesSvgRootWithFixedWidthAndHeight() {
        val image = render(
            backgroundColor = Color.TRANSPARENT,
            root = Box().apply { modifier = Modifier.size(20f, 20f) }
        ) {
            svg(
                """
                <svg width="10" height="10" viewBox="0 0 10 10" xmlns="http://www.w3.org/2000/svg">
                  <rect width="10" height="10" style="fill:#ff0000"/>
                </svg>
                """.trimIndent(),
                Modifier.size(20f)
            )
        }
        val bitmap = image.toBitmap()

        assertColorNear(Color.RED, bitmap.getColor(15, 15))
    }

    @Test
    fun skiaRenderDrawsSvgDefsClipPathPixels() {
        val image = render(
            backgroundColor = Color.TRANSPARENT,
            root = Box().apply { modifier = Modifier.size(10f, 10f) }
        ) {
            svg(
                """
                <svg viewBox="0 0 10 10" xmlns="http://www.w3.org/2000/svg">
                  <defs>
                    <clipPath id="clip">
                      <circle cx="5" cy="5" r="3"/>
                    </clipPath>
                  </defs>
                  <rect width="10" height="10" clip-path="url(#clip)" style="fill:#00ff00"/>
                </svg>
                """.trimIndent()
            )
        }
        val bitmap = image.toBitmap()

        assertColorNear(Color.GREEN, bitmap.getColor(5, 5))
        assertAlphaNear(0, bitmap.getColor(0, 0))
    }

    private fun quadrantImage(): Image =
        Surface.makeRasterN32Premul(4, 4).use { surface ->
            val canvas = surface.canvas
            canvas.clear(Color.TRANSPARENT)
            val paint = Paint()
            paint.color = Color.RED
            canvas.drawRect(Rect.makeXYWH(0f, 0f, 2f, 2f), paint)
            paint.color = Color.GREEN
            canvas.drawRect(Rect.makeXYWH(2f, 0f, 2f, 2f), paint)
            paint.color = Color.BLUE
            canvas.drawRect(Rect.makeXYWH(0f, 2f, 2f, 2f), paint)
            paint.color = Color.YELLOW
            canvas.drawRect(Rect.makeXYWH(2f, 2f, 2f, 2f), paint)
            surface.makeImageSnapshot()
        }

    private fun stripeImage(): Image =
        Surface.makeRasterN32Premul(2, 1).use { surface ->
            val paint = Paint().apply { isAntiAlias = false }
            paint.color = Color.BLACK
            surface.canvas.drawRect(Rect.makeXYWH(0f, 0f, 1f, 1f), paint)
            paint.color = Color.WHITE
            surface.canvas.drawRect(Rect.makeXYWH(1f, 0f, 1f, 1f), paint)
            surface.makeImageSnapshot()
        }

    private fun checkerImage(size: Int): Image =
        Surface.makeRasterN32Premul(size, size).use { surface ->
            val paint = Paint().apply { isAntiAlias = false }
            for (y in 0 until size) {
                for (x in 0 until size) {
                    paint.color = if ((x + y) % 2 == 0) Color.BLACK else Color.WHITE
                    surface.canvas.drawRect(Rect.makeXYWH(x.toFloat(), y.toFloat(), 1f, 1f), paint)
                }
            }
            surface.makeImageSnapshot()
        }

    private fun renderCircleClip(antiAlias: Boolean): Image = render(
        backgroundColor = Color.TRANSPARENT,
        root = Box().apply { modifier = Modifier.size(12f) }
    ) {
        box(
            Modifier
                .size(12f)
                .antiAlias(antiAlias)
                .clip(Shape.Circle)
                .background(Color.GREEN)
        )
    }

    private fun renderScaledImage(source: Image, width: Int, height: Int, antiAlias: Boolean): Image = render(
        backgroundColor = Color.TRANSPARENT,
        root = Box().apply { modifier = Modifier.size(width.toFloat(), height.toFloat()) }
    ) {
        image(
            source,
            Modifier
                .size(width.toFloat(), height.toFloat())
                .antiAlias(antiAlias),
            ImageOverflow.Stretch
        )
    }

    private fun pixelColors(bitmap: org.jetbrains.skia.Bitmap, width: Int, height: Int): List<Int> = buildList {
        for (y in 0 until height) {
            for (x in 0 until width) add(bitmap.getColor(x, y))
        }
    }

    private fun assertColorNear(expected: Int, actual: Int, tolerance: Int = COLOR_TOLERANCE) {
        assertChannelNear("alpha", Color.getA(expected), Color.getA(actual), tolerance)
        assertChannelNear("red", Color.getR(expected), Color.getR(actual), tolerance)
        assertChannelNear("green", Color.getG(expected), Color.getG(actual), tolerance)
        assertChannelNear("blue", Color.getB(expected), Color.getB(actual), tolerance)
    }

    private fun assertAlphaNear(expected: Int, actual: Int, tolerance: Int = COLOR_TOLERANCE) {
        assertChannelNear("alpha", expected, Color.getA(actual), tolerance)
    }

    private fun assertChannelNear(name: String, expected: Int, actual: Int, tolerance: Int) {
        assertTrue(
            abs(expected - actual) <= tolerance,
            "$name 期望 $expected，实际 $actual，容差 $tolerance"
        )
    }
}
