package top.e404.tavolo.gif

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.IRect
import top.e404.tavolo.util.limit
import top.e404.tavolo.util.rgb
import java.util.BitSet
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class GifAlgorithmRegressionTest {
    @Test
    fun rollingAtkinsonMatchesLegacyObjectMatrix() {
        val bitmap = Bitmap().apply { allocN32Pixels(12, 8) }
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val alpha = if ((x + y) % 7 == 0) 0 else 255
                val color = Color.makeARGB(alpha, x * 21 % 256, y * 37 % 256, (x * 13 + y * 17) % 256)
                bitmap.erase(color, IRect.makeXYWH(x, y, 1, 1))
            }
        }
        val table = intArrayOf(
            Color.TRANSPARENT,
            Color.BLACK,
            Color.WHITE,
            Color.RED,
            Color.GREEN,
            Color.BLUE,
            Color.CYAN,
            Color.MAGENTA,
        )

        val expected = legacyDither(bitmap, table, transparencyIndex = 0)
        val actual = AtkinsonDitherer.dither(bitmap, table, transparencyIndex = 0)

        assertContentEquals(expected, actual)
    }

    @Test
    fun primitiveLzwMatchesLegacySequenceDictionary() {
        val table = ColorTable(
            colors = IntArray(256) { index -> index shl 16 or (255 - index) shl 8 or index },
            sort = false,
        )
        val indices = IntArray(30_000) { index ->
            (index * 31 + index / 7 + (index % 23) * 11) and 0xFF
        }

        val expected = LegacyLzwEncoder(table, indices).encode()
        val actual = LZWEncoder(table, indices).encode()

        assertEquals(expected.first, actual.first)
        assertContentEquals(expected.second, actual.second)
    }

    private fun legacyDither(bitmap: Bitmap, table: IntArray, transparencyIndex: Int?): IntArray {
        val width = bitmap.width
        val height = bitmap.height
        val colors = Array(height) { y -> Array(width) { x -> LegacyColor(bitmap.getColor(x, y)) } }
        val tableColors = List(table.size) { index -> LegacyColor(table[index]) }
        val searchableIndices = table.indices.filter { it != transparencyIndex }
        val nearestCache = HashMap<Int, Int>()
        val indices = IntArray(width * height)
        val distribution = arrayOf(
            1 to 0,
            2 to 0,
            -1 to 1,
            0 to 1,
            1 to 1,
            0 to 2,
        )

        for (y in 0 until height) for (x in 0 until width) {
            val offsetIndex = y * width + x
            if (bitmap.getAlphaf(x, y) < 0.5F) {
                indices[offsetIndex] = requireNotNull(transparencyIndex)
                continue
            }
            val original = colors[y][x]
            val normalized = original.clamped()
            val replacementIndex = nearestCache.getOrPut(normalized.cacheKey()) {
                searchableIndices.minBy { (tableColors[it] - normalized).distance() }
            }
            val replacement = tableColors[replacementIndex]
            indices[offsetIndex] = replacementIndex
            colors[y][x] = replacement
            val error = original - replacement
            for ((deltaX, deltaY) in distribution) {
                val siblingX = x + deltaX
                val siblingY = y + deltaY
                if (
                    siblingX in 0 until width &&
                    siblingY in 0 until height &&
                    bitmap.getAlphaf(siblingX, siblingY) >= 0.5F
                ) {
                    colors[siblingY][siblingX] = colors[siblingY][siblingX] + error.dividedByEight()
                }
            }
        }
        return indices
    }

    private data class LegacyColor(val red: Int, val green: Int, val blue: Int) {
        constructor(argb: Int) : this(argb ushr 16 and 0xFF, argb ushr 8 and 0xFF, argb and 0xFF)

        operator fun minus(other: LegacyColor) = LegacyColor(red - other.red, green - other.green, blue - other.blue)
        operator fun plus(other: LegacyColor) = LegacyColor(red + other.red, green + other.green, blue + other.blue)
        fun dividedByEight() = LegacyColor(red / 8, green / 8, blue / 8)
        fun distance() = red * red + green * green + blue * blue
        fun clamped() = LegacyColor(red.limit(), green.limit(), blue.limit())
        fun cacheKey() = clamped().run { rgb(red, green, blue) }
    }

    private class LegacyLzwEncoder(
        private val colors: ColorTable,
        private val indices: IntArray,
    ) {
        private val clearCode = listOf(-1)
        private val endOfInfo = listOf(-2)
        private val minimumCodeSize = maxOf(2, colors.size() + 1)
        private val outputBits = BitSet()
        private var position = 0
        private val table = HashMap<List<Int>, Int>()
        private var codeSize = 0
        private var indexBuffer: List<Int> = emptyList()

        init {
            reset()
        }

        fun encode(): Pair<Int, ByteArray> {
            writeCode(table.getValue(clearCode))
            for (index in indices) processIndex(index)
            writeCode(table.getValue(indexBuffer))
            writeCode(table.getValue(endOfInfo))
            val bytes = ByteArray((position + 7) / 8)
            for (bitIndex in 0 until position) {
                if (!outputBits.get(bitIndex)) continue
                val byteIndex = bitIndex / 8
                bytes[byteIndex] = (bytes[byteIndex].toInt() or (1 shl (bitIndex % 8))).toByte()
            }
            return minimumCodeSize to bytes
        }

        private fun processIndex(index: Int) {
            val extended = indexBuffer + index
            indexBuffer = if (extended in table) {
                extended
            } else {
                writeCode(table.getValue(indexBuffer))
                if (table.size == LZWEncoder.MAX_CODE_TABLE_SIZE) {
                    writeCode(table.getValue(clearCode))
                    reset()
                } else {
                    val newCode = table.size
                    table[extended] = newCode
                    if (newCode == 1 shl codeSize) codeSize++
                }
                listOf(index)
            }
        }

        private fun writeCode(code: Int) {
            for (shift in 0 until codeSize) {
                outputBits.set(position++, code ushr shift and 1 != 0)
            }
        }

        private fun reset() {
            table.clear()
            for (index in 0 until (1 shl minimumCodeSize)) table[listOf(index)] = index
            table[clearCode] = table.size
            table[endOfInfo] = table.size
            codeSize = minimumCodeSize + 1
        }
    }
}
