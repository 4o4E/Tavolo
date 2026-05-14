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
import top.e404.tavolo.draw.compose.background
import top.e404.tavolo.draw.compose.border
import top.e404.tavolo.draw.compose.box
import top.e404.tavolo.draw.compose.clip
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
