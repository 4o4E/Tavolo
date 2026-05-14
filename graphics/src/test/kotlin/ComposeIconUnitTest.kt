package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.jetbrains.skia.Path
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Typeface
import org.junit.Test
import top.e404.tavolo.draw.compose.*
import top.e404.tavolo.util.FontManager
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ComposeIconUnitTest {
    @Test
    fun drawIconRecordsTransformAndPathCommands() {
        val recorder = RecordingDrawCanvas()
        val path = Path().apply { moveTo(0f, 0f); lineTo(10f, 0f) }

        drawIcon(
            canvas = recorder,
            path = path,
            viewBox = Rect.makeXYWH(0f, 0f, 10f, 20f),
            theme = IconTheme(size = 40f, scale = 1f, color = Color.RED),
            parentX = 3f,
            parentY = 4f
        )

        assertTrue(recorder.commands[0] is DrawCommand.Save)
        assertEquals(DrawCommand.Translate(13f, 4f), recorder.commands[1])
        assertEquals(DrawCommand.Scale(2f, 2f), recorder.commands[2])
        val originTranslate = assertIs<DrawCommand.Translate>(recorder.commands[3])
        assertFloatEquals(0f, originTranslate.dx)
        assertFloatEquals(0f, originTranslate.dy)
        val drawPath = assertIs<DrawCommand.Path>(recorder.commands[4])
        assertEquals(Color.RED, drawPath.paint.color)
        assertTrue(recorder.commands[5] is DrawCommand.Restore)
    }

    @Test
    fun iconDslParsesSvgAndDrawsPath() {
        val svg = """<svg viewBox="0 0 10 10"><path d="M 0 0 L 10 0 L 10 10 Z"/></svg>"""
        val commands = renderCommands {
            icon(IconTheme(size = 20f), svg)
        }

        assertTrue(commands.any { it is DrawCommand.Path })
        assertTrue(commands.any { it is DrawCommand.Scale })
    }

    @Test
    fun githubFooterStyleIconAndTextRowRecordsIconPathAndText() {
        val svg = """<svg viewBox="0 0 10 10"><path d="M 0 0 L 10 0 L 10 10 Z"/></svg>"""
        val fontName = FontManager.register("unit-footer-empty", Typeface.makeEmpty())
        val commands = renderCommands(measureContext = MeasureContext(FixedTextMeasurer())) {
            row(Modifier.padding(horizontal = 50f), VerticalAlignment.Center) {
                icon(IconTheme(40f, color = Color.WHITE), svg)
                text(
                    "42",
                    Modifier
                        .padding(left = 20f, right = 50f),
                    fontSize = 40f,
                    textColor = Color.WHITE,
                    fontFamily = fontName
                )
            }
        }

        assertTrue(commands.any { it is DrawCommand.Path })
        val text = commands.filterIsInstance<DrawCommand.Text>().single()
        assertEquals("42", text.text)
        assertFloatEquals(110f, text.x)
    }
}

