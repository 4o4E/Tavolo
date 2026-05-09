package top.e404.tavolo.dbf

import java.io.BufferedReader
import java.io.File
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BdfParserUnitTest {
    @Test
    fun bitMatrixParsesHexRowsAndAllowsMutation() {
        val matrix = BitMatrix(listOf("80", "40"))

        assertEquals(8, matrix.width)
        assertEquals(2, matrix.height)
        assertTrue(matrix[0, 0])
        assertFalse(matrix[1, 0])
        assertTrue(matrix[1, 1])

        matrix[2, 1] = true
        matrix[1, 1] = false

        assertTrue(matrix[2, 1])
        assertFalse(matrix[1, 1])
        assertEquals(16, matrix.xRange.count() * matrix.yRange.count())
    }

    @Test
    fun bitMatrixForEachBitAndPlusKeepBitPositions() {
        val left = BitMatrix(listOf("80"))
        val right = BitMatrix(listOf("40"))
        val combined = left + right
        val enabled = mutableListOf<Pair<Int, Int>>()

        combined.forEachBit { x, y, bit ->
            if (bit) enabled += x to y
        }

        assertEquals(16, combined.width)
        assertEquals(1, combined.height)
        assertEquals(listOf(0 to 0, 9 to 0), enabled)
    }

    @Test
    fun bitMatrixForEachBitHandlesEmptyRanges() {
        var visited = false

        BitMatrix(0, 1).forEachBit { _, _, _ -> visited = true }
        assertFalse(visited)

        BitMatrix(1, 0).forEachBit { _, _, _ -> visited = true }
        assertFalse(visited)
    }

    @Test
    fun bitMatrixSupportsNonByteAlignedWidthAndRejectsInvalidMerge() {
        val matrix = BitMatrix(5, 2)

        matrix[4, 0] = true
        matrix[0, 1] = true

        assertTrue(matrix[4, 0])
        assertTrue(matrix[0, 1])
        assertFalse(matrix[3, 0])
        assertFailsWith<IllegalArgumentException> {
            matrix + BitMatrix(5, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            BitMatrix(emptyList())
        }
    }

    @Test
    fun bitMatrixRejectsInvalidConstructorArguments() {
        assertFailsWith<IllegalArgumentException> {
            BitMatrix(-1, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            BitMatrix(1, -1)
        }
        assertFailsWith<IllegalArgumentException> {
            BitMatrix(-1, 1, ByteArray(0))
        }
        assertFailsWith<IllegalArgumentException> {
            BitMatrix(1, -1, ByteArray(0))
        }
        assertFailsWith<IllegalArgumentException> {
            BitMatrix(9, 1, ByteArray(1))
        }
        assertFailsWith<IllegalArgumentException> {
            BitMatrix(0, 1, "")
        }
        assertFailsWith<IllegalArgumentException> {
            BitMatrix(1, -1, "")
        }
        assertFailsWith<IllegalArgumentException> {
            BitMatrix(1, 1, "F")
        }

        val emptyHeight = BitMatrix(1, 0, "")
        assertEquals(4, emptyHeight.width)
        assertEquals(0, emptyHeight.height)
    }

    @Test
    fun parserParsesHeaderPropertiesAndCharsFromText() {
        val font = BdfParser.parse(sampleBdf())

        assertEquals("2.1", font.header.version)
        assertEquals("-tavolo-test-medium-r-normal--8-80-75-75-c-80-test", font.header.font)
        assertEquals(BdfSize(8, 75, 75), font.header.size)
        assertEquals(FontBoundingBox(8, 8, 0, -2), font.header.boundingBox)
        assertEquals(2, font.header.count)
        assertEquals(8, font.header.boundingBox.w)
        assertEquals(8, font.header.boundingBox.h)
        assertEquals(0, font.header.boundingBox.x)
        assertEquals(-2, font.header.boundingBox.y)
        assertEquals(8, font.header.size.size)
        assertEquals(75, font.header.size.x)
        assertEquals(75, font.header.size.y)
        assertEquals("\"Test\"", font.header.properties["FAMILY_NAME"])
        assertEquals("7", font.header.properties["FONT_ASCENT"])

        val a = assertNotNull(font.getBitmap("A"))
        assertEquals("U+0041", a.unicode)
        assertEquals(65, a.encoding)
        assertEquals(500 to 0, a.sWidth)
        assertEquals(8 to 0, a.dWidth)
        assertEquals(FontBoundingBox(8, 2, 0, 0), a.bbx)
        assertTrue(a.bitMatrix[0, 0])
        assertTrue(a.bitMatrix[1, 1])
        assertFalse(a.bitMatrix[7, 1])

        val bitmaps = font.getBitmaps("AZ")
        assertNotNull(bitmaps[0])
        assertNull(bitmaps[1])
        assertFailsWith<IllegalArgumentException> {
            font.getBitmap("")
        }
    }

    @Test
    fun parserAcceptsFlexibleWhitespaceAndBitmapWithoutTrailingSpace() {
        val font = BdfParser.parse(
            sampleBdf()
                .replace("STARTFONT 2.1", "STARTFONT\t2.1")
                .replace("SIZE 8 75 75", "SIZE    8   75   75")
                .replace("FONTBOUNDINGBOX 8 8 0 -2", "FONTBOUNDINGBOX   8  8   0  -2")
                .replace("FONT -tavolo-test", "\n\nCOMMENT 空行和注释应被跳过\nFONT -tavolo-test")
                .replace("BITMAP ", "BITMAP")
        )

        assertEquals(BdfSize(8, 75, 75), font.header.size)
        assertTrue(assertNotNull(font.getBitmap("A")).bitMatrix[0, 0])
    }

    @Test
    fun parserSupportsFileAndReaderEntrances() {
        val file = File.createTempFile("tavolo-bdf-parser", ".bdf")
        try {
            file.writeText(sampleBdf())

            assertEquals(2, BdfParser.parse(file).header.count)
            BufferedReader(StringReader(sampleBdf())).use {
                assertEquals(2, BdfParser.parse(it).chars.size)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun valueObjectsRejectMalformedInput() {
        assertFailsWith<IllegalArgumentException> {
            BdfSize("8 75")
        }
        assertEquals(BdfSize(8, 75, 75), BdfSize(" 8  75   75 "))
        assertFailsWith<IllegalArgumentException> {
            BdfSize("8 x 75")
        }
        assertFailsWith<IllegalArgumentException> {
            FontBoundingBox("8 8 0")
        }
        assertEquals(FontBoundingBox(8, 8, 0, -2), FontBoundingBox(" 8  8  0  -2 "))
        assertFailsWith<IllegalArgumentException> {
            FontBoundingBox("8 8 x -2")
        }
    }

    @Test
    fun parserRejectsMalformedCharacterLine() {
        val malformed = sampleBdf().replace("SWIDTH 500 0", "SWIDTH")

        assertFailsWith<IllegalArgumentException> {
            BdfParser.parse(malformed)
        }
    }

    @Test
    fun parserRejectsInvalidStartFontAndMissingEndFont() {
        assertFailsWith<IllegalArgumentException> {
            BdfParser.parse(sampleBdf().replace("STARTFONT 2.1", "FONTSTART 2.1"))
        }
        assertFailsWith<IllegalArgumentException> {
            BdfParser.parse("STARTFONT")
        }
        assertFailsWith<IllegalArgumentException> {
            BdfParser.parse("")
        }
        assertFailsWith<IllegalArgumentException> {
            BdfParser.parse(sampleBdf().removeSuffix("\nENDFONT"))
        }
    }

    @Test
    fun parserRejectsMalformedHeaderAndCharTerminators() {
        assertFailsWith<IllegalArgumentException> {
            BdfParser.parse(sampleBdf().replace("ENDPROPERTIES", "ENDPROPS"))
        }
        assertFailsWith<IllegalArgumentException> {
            BdfParser.parse(sampleBdf().replace("STARTCHAR U+0041", "BEGINCHAR U+0041"))
        }
        assertFailsWith<IllegalArgumentException> {
            BdfParser.parse(sampleBdf().replace("ENDCHAR", "ENDGLYPH"))
        }
        assertFailsWith<IllegalArgumentException> {
            BdfParser.parse(sampleBdf().replace("FONT -tavolo-test-medium-r-normal--8-80-75-75-c-80-test\n", ""))
        }
        assertFailsWith<IllegalArgumentException> {
            BdfParser.parse(sampleBdf().replace("CHARS 2", "CHARS x"))
        }
        assertFailsWith<IllegalArgumentException> {
            BdfParser.parse(sampleBdf().replace("ENCODING 65", "ENCODING x"))
        }
    }

    @Test
    fun parserRejectsInvalidBitMatrixData() {
        assertFailsWith<IllegalArgumentException> {
            BitMatrix(2, 2, "80")
        }
        assertFailsWith<NumberFormatException> {
            BitMatrix(2, 1, "GG")
        }
    }

    @Test
    fun parserKeepsExistingLargeResourceSmokeTestWithoutPrinting() {
        val url = BdfParserUnitTest::class.java.classLoader.getResource("unifont-15.0.03.bdf")!!
        val bdfFont = BdfParser.parse(url.readText())

        assertEquals(57086, bdfFont.header.count)
        assertNotNull(bdfFont.getBitmap("疯"))
        assertNotNull(bdfFont.getBitmap("狂"))
        assertNotNull(bdfFont.getBitmap("四"))
    }

    private fun sampleBdf() = """
        STARTFONT 2.1
        COMMENT 顶层注释应该被跳过
        FONT -tavolo-test-medium-r-normal--8-80-75-75-c-80-test
        SIZE 8 75 75
        FONTBOUNDINGBOX 8 8 0 -2
        STARTPROPERTIES 3
        COMMENT 属性注释应该被跳过
        FAMILY_NAME "Test"
        FONT_ASCENT 7
        FONT_DESCENT 1
        ENDPROPERTIES
        CHARS 2
        STARTCHAR U+0041
        ENCODING 65
        SWIDTH 500 0
        DWIDTH 8 0
        BBX 8 2 0 0
        BITMAP 
        80
        40
        ENDCHAR
        STARTCHAR U+0042
        ENCODING 66
        SWIDTH 500 0
        DWIDTH 8 0
        BBX 8 2 0 0
        BITMAP 
        C0
        20
        ENDCHAR
        ENDFONT
    """.trimIndent()
}
