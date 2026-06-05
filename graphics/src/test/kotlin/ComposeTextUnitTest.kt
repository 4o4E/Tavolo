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
    fun defaultTextRenderingUsesParagraphCommand() {
        val commands = renderCommands {
            text("emoji \uD83D\uDE00", fontSize = 20f)
        }

        val paragraph = commands.filterIsInstance<DrawCommand.ParagraphText>().single()
        assertEquals("emoji \uD83D\uDE00", paragraph.text)
        assertTrue(paragraph.width > 0f)
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
    fun letterSpacingKeepsEmojiSurrogatePairTogether() {
        val emoji = "\uD83D\uDE00"
        val commands = renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            text(
                "${emoji}a",
                textModifier = TextModifier.letterSpacing(3f)
            )
        }

        val texts = commands.filterIsInstance<DrawCommand.Text>()
        assertEquals(listOf(emoji, "a"), texts.map { it.text })
        assertFloatEquals(0f, texts[0].x)
        assertFloatEquals(23f, texts[1].x)
    }

    @Test
    fun graphemeSegmentationUsesExtendedClusterRules() {
        val family = "👨‍👩‍👧‍👦"
        val flag = "🇨🇳"

        assertEquals(listOf("୧", "⍤⃝"), segmentGraphemeClusters("୧⍤⃝").map { it.text })
        assertEquals(listOf("A⃝"), segmentGraphemeClusters("A⃝").map { it.text })
        assertEquals(listOf(family), segmentGraphemeClusters(family).map { it.text })
        assertEquals(listOf(flag), segmentGraphemeClusters(flag).map { it.text })
        assertEquals(listOf("é"), segmentGraphemeClusters("é").map { it.text })
    }

    @Test
    fun graphemeRiskDetectionCoversEmojiSequences() {
        listOf("👨‍👩‍👧‍👦", "🇨🇳", "👍🏽", "❤️").forEach { sample ->
            val cluster = segmentGraphemeClusters(sample).single()
            assertTrue(cluster.needsClusterFontChoice(), "$sample 应进入 cluster 字体风险判断")
            assertTrue(cluster.hasEmojiSequenceControl(), "$sample 应识别为 emoji 序列风险 cluster")
        }
    }

    @Test
    fun annotatedWrapPrefersLineBreaksBeforeClusterFallback() {
        val commands = renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            text(
                AnnotatedText(
                    text = "hello world",
                    spanStyles = listOf(TextRange(TextSpanStyle(textColor = Color.RED), 0, "hello world".length))
                ),
                modifier = Modifier.sizeIn(maxWidth = 80f),
                textColor = Color.WHITE
            )
        }

        val texts = commands.filterIsInstance<DrawCommand.Text>()
        assertEquals(listOf("hello ", "world"), texts.map { it.text })
    }

    @Test
    fun annotatedWrapSplitsLongWordByGraphemeClusterOnlyWhenNeeded() {
        val commands = renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            text(
                AnnotatedText(
                    text = "abcdefghij",
                    spanStyles = listOf(TextRange(TextSpanStyle(textColor = Color.RED), 0, "abcdefghij".length))
                ),
                modifier = Modifier.sizeIn(maxWidth = 40f),
                textColor = Color.WHITE
            )
        }

        val texts = commands.filterIsInstance<DrawCommand.Text>()
        assertEquals(listOf("abcd", "efgh", "ij"), texts.map { it.text })
    }

    @Test
    fun ellipsisKeepsEnclosingMarkClusterTogether() {
        val commands = renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            text(
                "A⃝BC",
                Modifier.sizeIn(maxWidth = 30f),
                textOverflow = TextOverflow.Ellipsis
            )
        }

        val command = commands.filterIsInstance<DrawCommand.Text>().single()
        assertEquals("A⃝…", command.text)
    }

    @Test
    fun annotatedTextStyleRangeExpandsToWholeGraphemeCluster() {
        val commands = renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            text(
                AnnotatedText(
                    text = "A⃝B",
                    spanStyles = listOf(TextRange(TextSpanStyle(textColor = Color.RED), 1, 2))
                ),
                textColor = Color.WHITE
            )
        }

        val texts = commands.filterIsInstance<DrawCommand.Text>()
        assertEquals(listOf("A⃝", "B"), texts.map { it.text })
        assertEquals(Color.RED, texts[0].paint.color)
        assertEquals(Color.WHITE, texts[1].paint.color)
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

    @Test
    fun annotatedTextDrawsInlineRunsWithDifferentStyles() {
        val commands = renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            text(
                buildAnnotatedText {
                    append("use ")
                    inlineCode(
                        "code",
                        TextSpanStyle(
                            fontSize = 18f,
                            textColor = Color.RED,
                            backgroundColor = Color.YELLOW,
                            fontWeight = 700,
                            italic = true
                        )
                    )
                    append(" now")
                },
                fontSize = 20f,
                textColor = Color.WHITE
            )
        }

        val background = commands.filterIsInstance<DrawCommand.Rect>().single()
        assertEquals(Color.YELLOW, background.paint.color)
        assertFloatEquals(40f, background.rect.left)
        assertFloatEquals(0f, background.rect.top)
        assertFloatEquals(40f, background.rect.width)
        assertFloatEquals(10f, background.rect.height)

        val texts = commands.filterIsInstance<DrawCommand.Text>()
        assertEquals(listOf("use ", "code", " now"), texts.map { it.text })
        assertEquals(Color.WHITE, texts[0].paint.color)
        assertEquals(Color.RED, texts[1].paint.color)
        assertEquals(Color.WHITE, texts[2].paint.color)
        assertFloatEquals(40f, texts[1].x)
        assertFloatEquals(18f, texts[1].font.size)
        assertEquals(true, texts[1].font.emboldened)
        assertFloatEquals(-0.25f, texts[1].font.skewX)
    }

    @Test
    fun annotatedTextDrawsRoundedInlineBackgroundAndBorder() {
        val commands = renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            text(
                buildAnnotatedText {
                    append("run ")
                    inlineCode(
                        "check",
                        TextSpanStyle(
                            textColor = Color.WHITE,
                            backgroundColor = Color.makeRGB(45, 51, 59),
                            backgroundBorderColor = Color.makeRGB(139, 148, 158),
                            backgroundBorderWidth = 1.5f,
                            backgroundRadius = 5f,
                            backgroundPaddingHorizontal = 4f,
                            backgroundPaddingVertical = 2f
                        )
                    )
                },
                fontSize = 20f
            )
        }

        val backgrounds = commands.filterIsInstance<DrawCommand.Path>()
        assertEquals(2, backgrounds.size)
        assertEquals(Color.makeRGB(45, 51, 59), backgrounds[0].paint.color)
        assertEquals(PaintMode.FILL, backgrounds[0].paint.mode)
        assertEquals(Color.makeRGB(139, 148, 158), backgrounds[1].paint.color)
        assertEquals(PaintMode.STROKE, backgrounds[1].paint.mode)
        assertFloatEquals(1.5f, backgrounds[1].paint.strokeWidth)

        val texts = commands.filterIsInstance<DrawCommand.Text>()
        assertEquals(listOf("run ", "check"), texts.map { it.text })
        assertTrue(commands.indexOf(backgrounds[0]) < commands.indexOf(texts[0]))
    }

    @Test
    fun annotatedTextDrawsBackgroundForEachWrappedInlineRunLine() {
        val backgroundColor = Color.makeRGB(45, 51, 59)
        val borderColor = Color.makeRGB(139, 148, 158)
        val commands = renderCommands {
            text(
                buildAnnotatedText {
                    append("run ")
                    inlineCode(
                        "alpha beta gamma delta epsilon",
                        TextSpanStyle(
                            fontSize = 20f,
                            textColor = Color.WHITE,
                            backgroundColor = backgroundColor,
                            backgroundBorderColor = borderColor,
                            backgroundBorderWidth = 1f,
                            backgroundRadius = 4f,
                            backgroundPaddingHorizontal = 4f,
                            backgroundPaddingVertical = 2f
                        )
                    )
                },
                modifier = Modifier.sizeIn(maxWidth = 120f),
                fontSize = 20f
            )
        }

        val fills = commands.filterIsInstance<DrawCommand.Path>()
            .filter { it.paint.color == backgroundColor && it.paint.mode == PaintMode.FILL }
        val strokes = commands.filterIsInstance<DrawCommand.Path>()
            .filter { it.paint.color == borderColor && it.paint.mode == PaintMode.STROKE }
        assertTrue(fills.size >= 2, "行内代码块换行后每一行都应绘制独立背景")
        assertEquals(fills.size, strokes.size)

        val paragraph = commands.filterIsInstance<DrawCommand.ParagraphText>().single()
        assertTrue(paragraph.height > 20f, "测试文本应在宽度约束下换行")
        assertTrue(commands.indexOf(fills.first()) < commands.indexOf(paragraph))
    }

    @Test
    fun annotatedTextNestedStylesOverrideOuterStyle() {
        val commands = renderCommands(
            measureContext = MeasureContext(FixedTextMeasurer())
        ) {
            text(
                buildAnnotatedText {
                    withStyle(TextSpanStyle(textColor = Color.RED)) {
                        append("a")
                        withStyle(TextSpanStyle(textColor = Color.BLUE)) {
                            append("b")
                        }
                        append("c")
                    }
                }
            )
        }

        val texts = commands.filterIsInstance<DrawCommand.Text>()
        assertEquals(listOf("a", "b", "c"), texts.map { it.text })
        assertEquals(Color.RED, texts[0].paint.color)
        assertEquals(Color.BLUE, texts[1].paint.color)
        assertEquals(Color.RED, texts[2].paint.color)
    }

    @Test
    fun defaultAnnotatedTextRenderingUsesParagraphCommand() {
        val commands = renderCommands {
            text(
                buildAnnotatedText {
                    append("run ")
                    withStyle(TextSpanStyle(textColor = Color.CYAN)) {
                        append("code")
                    }
                },
                fontSize = 20f
            )
        }

        val paragraph = commands.filterIsInstance<DrawCommand.ParagraphText>().single()
        assertEquals("run code", paragraph.text)
        assertTrue(paragraph.width > 0f)
    }
}

