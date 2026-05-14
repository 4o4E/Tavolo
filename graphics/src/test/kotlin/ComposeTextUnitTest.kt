package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Typeface
import org.junit.Test
import top.e404.tavolo.draw.compose.*
import top.e404.tavolo.util.FontManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposeTextUnitTest {
    @Test
    fun fontFamilyUsesRegisteredTypefaceName() {
        val typeface = Typeface.makeEmpty()
        val fontName = FontManager.register("unit-text-empty", typeface)
        val measurer = CapturingTypefaceTextMeasurer()

        renderCommands(measureContext = MeasureContext(measurer)) {
            text("abc", fontSize = 12f, fontFamily = fontName)
        }

        assertEquals(typeface.uniqueId, measurer.metricsTypefaceId)
    }

    @Test
    fun fontManagerResolvesSystemFamilyAndFallback() {
        val families = FontManager.systemFamilies()
        if (families.isNotEmpty()) {
            FontManager.registerSystem("unit-system-font", families.first())
            assertTrue(FontManager.resolve("unit-system-font").uniqueId >= 0)
        }

        assertTrue(FontManager.resolve("unit-missing-font").uniqueId >= 0)
    }

    @Test
    fun ellipsisTruncatesTextToFitMaxWidth() {
        val commands = renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            text(
                "abcdef",
                Modifier
                    .sizeIn(maxWidth = 30f),
                fontSize = 20f,
                textOverflow = TextOverflow.Ellipsis
            )
        }

        val command = commands.filterIsInstance<DrawCommand.Text>().single()
        assertEquals("ab…", command.text)
        assertFloatEquals(8f, command.baselineY)
        assertFloatEquals(20f, command.font.size)
    }

    @Test
    fun wrapSplitsWordsAndAppliesMaxHeightEllipsis() {
        val commands = renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            text(
                "aa bbbb c",
                Modifier
                    .sizeIn(maxWidth = 40f, maxHeight = 20f)
            )
        }

        val texts = commands.filterIsInstance<DrawCommand.Text>()
        assertEquals(listOf("aa", "bbb…"), texts.map { it.text })
        assertFloatEquals(8f, texts[0].baselineY)
        assertFloatEquals(18f, texts[1].baselineY)
    }

    @Test
    fun textDrawPositionIncludesPaddingAndBorder() {
        val commands = renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            text(
                "hi",
                Modifier
                    .padding(left = 3f, top = 4f)
                    .border(left = 2f, top = 1f, color = Color.RED),
                textColor = Color.GREEN
            )
        }

        val command = commands.filterIsInstance<DrawCommand.Text>().single()
        assertFloatEquals(5f, command.x)
        assertFloatEquals(13f, command.baselineY)
        assertEquals(Color.GREEN, command.paint.color)
    }

    @Test
    fun textSupportsBlockUnderlineStyle() {
        val commands = renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            text(
                "hi",
                fontSize = 20f,
                textColor = Color.WHITE,
                underline = TextUnderline(
                    color = Color.YELLOW,
                    thickness = 4f,
                    offset = 1f,
                    mode = TextUnderlineMode.Block,
                    startPadding = 2f,
                    endPadding = 3f
                )
            )
        }

        val rect = commands.filterIsInstance<DrawCommand.Rect>().single()
        val text = commands.filterIsInstance<DrawCommand.Text>().single()
        assertTrue(commands.indexOf(rect) < commands.indexOf(text))
        assertEquals(Color.YELLOW, rect.paint.color)
        assertFloatEquals(-2f, rect.rect.left)
        assertFloatEquals(5f, rect.rect.top)
        assertFloatEquals(25f, rect.rect.width)
        assertFloatEquals(4f, rect.rect.height)
        assertEquals("hi", text.text)
    }

    @Test
    fun textStyleSupportsDashedUnderline() {
        val commands = renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            text(
                "ok",
                style = TextStyle(
                    fontSize = 20f,
                    textColor = Color.WHITE,
                    underline = TextUnderline(
                        color = Color.RED,
                        thickness = 2f,
                        offset = 3f,
                        strokeStyle = StrokeStyle.Dashed(listOf(4f, 2f))
                    )
                )
            )
        }

        val line = commands.filterIsInstance<DrawCommand.Line>().single()
        assertEquals(Color.RED, line.paint.color)
        assertEquals(PaintMode.STROKE, line.paint.mode)
        assertFloatEquals(2f, line.paint.strokeWidth)
        assertTrue(line.paint.hasPathEffect)
    }

    @Test
    fun textModifierAppliesReusableFontAndUnderline() {
        val commands = renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            val reusable = TextModifier
                .font(
                    fontSize = 18f,
                    textColor = Color.BLUE,
                    underline = TextUnderline(
                        color = Color.YELLOW,
                        thickness = 5f,
                        offset = 2f,
                        mode = TextUnderlineMode.Block
                    )
                )

            text("hi", modifier = Modifier.padding(left = 3f), textModifier = reusable)
        }

        val underline = commands.filterIsInstance<DrawCommand.Rect>().single()
        val text = commands.filterIsInstance<DrawCommand.Text>().single()
        assertEquals(Color.YELLOW, underline.paint.color)
        assertEquals(Color.BLUE, text.paint.color)
        assertFloatEquals(18f, text.font.size)
        assertFloatEquals(3f, text.x)
    }

    @Test
    fun explicitTextArgumentsOverrideTextStyleModifier() {
        val commands = renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            text(
                "hi",
                textModifier = TextModifier.textStyle(TextStyle(fontSize = 12f, textColor = Color.BLUE)),
                fontSize = 20f,
                textColor = Color.RED
            )
        }

        val text = commands.filterIsInstance<DrawCommand.Text>().single()
        assertFloatEquals(20f, text.font.size)
        assertEquals(Color.RED, text.paint.color)
    }

    @Test
    fun textModifierSupportsBoldItalicScaleAndLetterSpacing() {
        val commands = renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            text(
                "ab",
                textModifier = TextModifier
                    .font(fontSize = 20f, fontWeight = 700, italic = true, scaleX = 1.2f)
                    .letterSpacing(3f)
            )
        }

        val texts = commands.filterIsInstance<DrawCommand.Text>()
        assertEquals(listOf("a", "b"), texts.map { it.text })
        assertFloatEquals(0f, texts[0].x)
        assertFloatEquals(13f, texts[1].x)
        assertEquals(true, texts[0].font.emboldened)
        assertFloatEquals(-0.25f, texts[0].font.skewX)
        assertFloatEquals(1.2f, texts[0].font.scaleX)
    }

    @Test
    fun textModifierLineHeightControlsWrappedBaselineSpacing() {
        val commands = renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            text(
                "aa aa",
                modifier = Modifier.sizeIn(maxWidth = 20f),
                textModifier = TextModifier.lineHeight(16f)
            )
        }

        val texts = commands.filterIsInstance<DrawCommand.Text>()
        assertEquals(listOf("aa", "aa"), texts.map { it.text })
        assertFloatEquals(8f, texts[0].baselineY)
        assertFloatEquals(24f, texts[1].baselineY)
    }
}

