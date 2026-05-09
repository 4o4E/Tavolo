package top.e404.tavolo.gif

import java.util.*

class LZWEncoder(private val colors: ColorTable, private val indices: IntArray) {
    internal companion object {
        val CLEAR_CODE = listOf(-1)
        val END_OF_INFO = listOf(-2)

        const val MAX_CODE_TABLE_SIZE = 1 shl 12
    }

    private val minimumCodeSize = maxOf(2, colors.size() + 1)
    private val outputBits = BitSet()
    private var position = 0
    private val table: MutableMap<List<Int>, Int> = HashMap()
    private var codeSize = 0
    private var indexBuffer: List<Int> = emptyList()

    init {
        resetCodeTableAndCodeSize()
    }

    fun encode(): Pair<Int, ByteArray> {
        require(indices.isNotEmpty()) { "GIF帧像素不能为空" }
        writeCode(table.getValue(CLEAR_CODE))
        for (index in indices) {
            require(index in colors.colors.indices) { "GIF像素索引超出色表范围: $index" }
            processIndex(index)
        }
        writeCode(table.getValue(indexBuffer))
        writeCode(table.getValue(END_OF_INFO))
        return minimumCodeSize to toBytes()
    }

    private fun processIndex(index: Int) {
        val extendedIndexBuffer = indexBuffer + index
        indexBuffer = if (extendedIndexBuffer in table) {
            extendedIndexBuffer
        } else {
            writeCode(table.getValue(indexBuffer))
            if (table.size == MAX_CODE_TABLE_SIZE) {
                writeCode(table.getValue(CLEAR_CODE))
                resetCodeTableAndCodeSize()
            } else {
                addCodeToTable(extendedIndexBuffer)
            }
            listOf(index)
        }
    }

    private fun writeCode(code: Int) {
        for (shift in 0 until codeSize) {
            val bit = code ushr shift and 1 != 0
            outputBits.set(position++, bit)
        }
    }

    private fun toBytes(): ByteArray {
        val bitCount: Int = position
        val result = ByteArray((bitCount + 7) / 8)
        for (i in 0 until bitCount) {
            val byteIndex = i / 8
            val bitIndex = i % 8
            result[byteIndex] = ((if (outputBits.get(i)) 1 else 0) shl bitIndex or result[byteIndex].toInt()).toByte()
        }
        return result
    }

    private fun addCodeToTable(indices: List<Int>) {
        val newCode: Int = table.size
        table[indices] = newCode
        if (newCode == 1 shl codeSize) {
            ++codeSize
        }
    }

    private fun resetCodeTableAndCodeSize() {
        table.clear()

        val colorsInCodeTable = 1 shl minimumCodeSize
        for (i in 0 until colorsInCodeTable) {
            table[listOf(i)] = i
        }
        table[CLEAR_CODE] = table.size
        table[END_OF_INFO] = table.size

        codeSize = minimumCodeSize + 1
    }
}
