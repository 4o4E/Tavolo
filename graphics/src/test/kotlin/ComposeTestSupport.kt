package top.e404.tavolo.draw.test

import org.jetbrains.skia.Font
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import top.e404.tavolo.draw.compose.TextMeasurer
import top.e404.tavolo.draw.compose.TextMetrics
import top.e404.tavolo.draw.compose.UiElement
import top.e404.tavolo.draw.compose.charts.ChartTextBox
import kotlin.math.abs
import kotlin.test.assertTrue

internal const val EPSILON = 0.001f

internal class FixedTextMeasurer(
    private val charWidth: Float = 10f,
    private val ascent: Float = -8f,
    private val descent: Float = 2f
) : TextMeasurer {
    override fun measureTextWidth(text: String, font: Font, paint: Paint): Float =
        text.length * charWidth

    override fun metrics(font: Font): TextMetrics =
        TextMetrics(ascent, descent)
}

internal class CapturingTypefaceTextMeasurer : TextMeasurer {
    var metricsTypefaceId: Int? = null

    override fun measureTextWidth(text: String, font: Font, paint: Paint): Float =
        text.length * 10f

    override fun metrics(font: Font): TextMetrics {
        metricsTypefaceId = font.typeface!!.uniqueId
        return TextMetrics(-8f, 2f)
    }
}

internal fun assertFloatEquals(expected: Float, actual: Float) {
    assertTrue(
        abs(expected - actual) <= EPSILON,
        "期望 $expected，实际 $actual"
    )
}

internal fun fixedTextBox(text: String, charWidth: Float = 10f): ChartTextBox =
    ChartTextBox(
        width = text.length * charWidth,
        height = 10f,
        ascent = -8f,
        descent = 2f
    )

internal fun assertElementBounds(element: UiElement, x: Float, y: Float, width: Float, height: Float) {
    assertFloatEquals(x, element.x)
    assertFloatEquals(y, element.y)
    assertFloatEquals(width, element.width)
    assertFloatEquals(height, element.height)
}

internal fun testImage(width: Int, height: Int): Image =
    Surface.makeRasterN32Premul(width, height).use { it.makeImageSnapshot() }
