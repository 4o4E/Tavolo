@file:Suppress("unused")

package top.e404.tavolo.dbf

import java.io.BufferedReader
import java.io.File

private val whitespaceRegex = Regex("\\s+")

object BdfParser {
    /**
     * 文件开始, `BDF`标准版本号, 如`2.1`
     */
    const val START_FONT = "STARTFONT"

    /**
     * 文件结束
     */
    const val END_FONT = "ENDFONT"

    /**
     * 注释
     */
    const val COMMENT = "COMMENT"

    /**
     * 字体名
     */
    const val FONT = "FONT"

    /**
     * 字形尺寸(以`PT`计) 分辨率x(以`DPI`计) 分辨率y(以`DPI`计)
     */
    const val SIZE = "SIZE"

    /**
     * 字体包围盒宽度 字体包围盒高度 分辨率x 分辨率y, 单位像素
     */
    const val FONT_BOUNDING_BOX = "FONTBOUNDINGBOX"

    /**
     * 整型值, 缺省值`0`, 三种可选值
     *
     * - `0` - 从左向右
     * - `1` - 自上而下
     * - `2` - 兼有
     *
     * 若该参数取1, 则`DWIDTH`和`SWIDTH`两关键字可选。
     */
    const val METRICS_SET = "METRICSSET"

    /**
     * 可选属性
     */
    const val START_PROPERTIES = "STARTPROPERTIES"

    /**
     * 字符数量
     */
    const val CHARS = "CHARS"

    /**
     * 开始字符
     */
    const val START_CHAR = "STARTCHAR"

    /**
     * 开始字符点阵
     */
    const val BITMAP = "BITMAP"

    /**
     * 结束字符
     */
    const val END_CHAR = "ENDCHAR"

    /**
     * 结束可选属性
     */
    const val END_PROPERTIES = "ENDPROPERTIES"

    fun parse(file: File) = file.bufferedReader().use(::parse)

    fun parse(text: String) = text.reader().buffered().use(::parse)

    fun parse(reader: BufferedReader): BdfFont {
        val source = BdfSource(reader)
        val header = parseHeader(source)
        val map = HashMap<Int, BdfChar>(header.count)
        repeat(header.count) {
            val bdfChar = parseFont(source)
            map[bdfChar.encoding] = bdfChar
        }
        val endLine = source.readContentLine("文件结束")
        source.requireLine(endLine == END_FONT) { "期望 $END_FONT, 实际为 $endLine" }
        return BdfFont(header, map)
    }

    private fun parseHeader(reader: BdfSource): BdfHeader {
        val (startKey, version) = reader.readKeyValue("文件开始")
        reader.requireLine(startKey == START_FONT) { "期望 $START_FONT, 实际为 $startKey" }
        val map = mutableMapOf<String, String>()
        var properties = mutableMapOf<String, String>()
        val count: Int
        while (true) {
            val (key, value) = reader.readKeyValue("字体头")
            if (key == START_PROPERTIES) {
                val size = reader.parseInt(value, START_PROPERTIES)
                properties = HashMap(size)
                repeat(size) {
                    val (propertyKey, propertyValue) = reader.readKeyValue("字体属性")
                    properties[propertyKey] = propertyValue
                }
                val endProperties = reader.readContentLine("属性结束")
                reader.requireLine(endProperties == END_PROPERTIES) {
                    "期望 $END_PROPERTIES, 实际为 $endProperties"
                }
                continue
            }
            if (key == CHARS) {
                count = reader.parseInt(value, CHARS)
                break
            }
            map[key] = value
        }
        return BdfHeader(
            version = version,
            font = reader.requireField(map, FONT),
            size = BdfSize(reader.requireField(map, SIZE)),
            boundingBox = FontBoundingBox(reader.requireField(map, FONT_BOUNDING_BOX)),
            properties = properties,
            count = count
        )
    }

    private fun parseFont(reader: BdfSource): BdfChar {
        val map = mutableMapOf<String, String>()
        val (startKey, unicode) = reader.readKeyValue("字符开始")
        reader.requireLine(startKey == START_CHAR) { "期望 $START_CHAR, 实际为 $startKey" }
        var line: String
        while (true) {
            line = reader.readContentLine("字符 $unicode")
            if (line == BITMAP) break
            val (key, value) = reader.parseKeyValue(line, "字符 $unicode")
            map[key] = value
        }
        val bbx = FontBoundingBox(reader.requireField(map, "BBX"))
        // bitmap
        val lines = (1..bbx.h).map {
            reader.readRequiredLine("字符 $unicode 的点阵").trim()
        }
        line = reader.readContentLine("字符结束")
        reader.requireLine(line == END_CHAR) { "期望 $END_CHAR, 实际为 $line, 字符: $unicode" }
        return BdfChar(
            unicode,
            reader.parseInt(reader.requireField(map, "ENCODING"), "ENCODING"),
            parseIntPair(reader.requireField(map, "SWIDTH"), "SWIDTH"),
            parseIntPair(reader.requireField(map, "DWIDTH"), "DWIDTH"),
            bbx,
            BitMatrix(lines)
        )
    }

    private class BdfSource(private val reader: BufferedReader) {
        private var lineNumber = 0

        fun readRequiredLine(context: String): String {
            val line = reader.readLine() ?: fail("读取 $context 时文件提前结束")
            lineNumber += 1
            return line
        }

        fun readContentLine(context: String): String {
            while (true) {
                val line = readRequiredLine(context).trim()
                if (line.isEmpty() || line.startsWith(COMMENT)) continue
                return line
            }
        }

        fun readKeyValue(context: String): Pair<String, String> =
            parseKeyValue(readContentLine(context), context)

        fun parseKeyValue(line: String, context: String): Pair<String, String> {
            val index = line.indexOfFirst { it.isWhitespace() }
            if (index == -1) fail("$context 缺少字段值: $line")
            val key = line.substring(0, index)
            val value = line.substring(index + 1).trim()
            return key to value
        }

        fun parseInt(value: String, field: String): Int =
            value.toIntOrNull() ?: fail("$field 不是整数: $value")

        fun requireField(map: Map<String, String>, field: String): String =
            map[field] ?: fail("缺少 $field 字段")

        fun requireLine(value: Boolean, message: () -> String) {
            if (!value) fail(message())
        }

        fun fail(message: String): Nothing {
            throw IllegalArgumentException("第 $lineNumber 行: $message")
        }
    }
}

data class BdfFont(
    val header: BdfHeader,
    val chars: Map<Int, BdfChar>
) {
    fun getBitmap(string: String): BdfChar? {
        require(string.isNotEmpty()) { "查询字符不能为空" }
        return chars[string[0].code]
    }

    fun getBitmaps(string: String) = string.toCharArray().map { chars[it.code] }
}

data class BdfHeader(
    val version: String,
    val font: String,
    val size: BdfSize,
    val boundingBox: FontBoundingBox,
    val properties: Map<String, String>,
    val count: Int,
)

/**
 * 尺寸数据
 *
 * @property size 字形尺寸(以PT计)
 * @property x 分辨率x(以DPI计)
 * @property y 分辨率y(以DPI计)
 */
data class BdfSize(
    val size: Int,
    val x: Int,
    val y: Int
) {
    companion object {
        operator fun invoke(string: String): BdfSize {
            val split = parseIntFields(string, 3, "SIZE")
            return BdfSize(split[0], split[1], split[2])
        }
    }
}

/**
 * 字体包围盒宽度 字体包围盒高度 分辨率x 分辨率y, 单位像素
 *
 * @property w 字体边界宽度
 * @property h 字体边界高度
 * @property x 字体边界x
 * @property y 字体边界y
 */
data class FontBoundingBox(
    val w: Int,
    val h: Int,
    val x: Int,
    val y: Int,
) {
    companion object {
        operator fun invoke(string: String): FontBoundingBox {
            val split = parseIntFields(string, 4, "FONTBOUNDINGBOX")
            return FontBoundingBox(split[0], split[1], split[2], split[3])
        }
    }
}

class BdfChar(
    val unicode: String,
    val encoding: Int,
    val sWidth: Pair<Int, Int>,
    val dWidth: Pair<Int, Int>,
    val bbx: FontBoundingBox,
    val bitMatrix: BitMatrix
)

private fun parseIntPair(value: String, field: String): Pair<Int, Int> {
    val split = parseIntFields(value, 2, field)
    return split[0] to split[1]
}

private fun parseIntFields(value: String, expectedSize: Int, field: String): List<Int> {
    val split = value.trim().split(whitespaceRegex).filter { it.isNotEmpty() }
    require(split.size == expectedSize) { "$field 需要 $expectedSize 个整数, 实际为 ${split.size} 个: $value" }
    return split.map {
        it.toIntOrNull() ?: throw IllegalArgumentException("$field 包含非整数值: $value")
    }
}
