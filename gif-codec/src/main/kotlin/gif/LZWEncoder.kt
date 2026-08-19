package top.e404.tavolo.gif

class LZWEncoder(private val colors: ColorTable, private val indices: IntArray) {
    internal companion object {
        // 保留旧版内部符号，避免已编译的同模块测试或工具发生二进制链接变化。
        val CLEAR_CODE = listOf(-1)
        val END_OF_INFO = listOf(-2)

        const val MAX_CODE_TABLE_SIZE = 1 shl 12
    }

    private val minimumCodeSize = maxOf(2, colors.size() + 1)
    private val clearCode = 1 shl minimumCodeSize
    private val endOfInfoCode = clearCode + 1
    private val dictionaryKeys = IntArray(MAX_CODE_TABLE_SIZE * 2)
    private val dictionaryValues = IntArray(dictionaryKeys.size)
    private var nextCode = endOfInfoCode + 1
    private var codeSize = minimumCodeSize + 1
    private var output = ByteArray(maxOf(32, indices.size / 2))
    private var outputBitCount = 0

    fun encode(): Pair<Int, ByteArray> {
        require(indices.isNotEmpty()) { "GIF帧像素不能为空" }
        resetDictionary()
        writeCode(clearCode)
        var prefix = indices[0]
        require(prefix in colors.colors.indices) { "GIF像素索引超出色表范围: $prefix" }
        for (offset in 1 until indices.size) {
            val index = indices[offset]
            require(index in colors.colors.indices) { "GIF像素索引超出色表范围: $index" }
            val existingCode = findCode(prefix, index)
            if (existingCode >= 0) {
                prefix = existingCode
                continue
            }

            writeCode(prefix)
            if (nextCode == MAX_CODE_TABLE_SIZE) {
                writeCode(clearCode)
                resetDictionary()
            } else {
                addCode(prefix, index, nextCode)
                if (nextCode == 1 shl codeSize) codeSize++
                nextCode++
            }
            prefix = index
        }
        writeCode(prefix)
        writeCode(endOfInfoCode)
        return minimumCodeSize to output.copyOf((outputBitCount + 7) / 8)
    }

    private fun writeCode(code: Int) {
        for (shift in 0 until codeSize) {
            ensureOutputCapacity(outputBitCount + 1)
            if (code ushr shift and 1 != 0) {
                val byteIndex = outputBitCount ushr 3
                output[byteIndex] = (output[byteIndex].toInt() or (1 shl (outputBitCount and 7))).toByte()
            }
            outputBitCount++
        }
    }

    private fun ensureOutputCapacity(requiredBits: Int) {
        val requiredBytes = (requiredBits + 7) ushr 3
        if (requiredBytes <= output.size) return
        output = output.copyOf(output.size shl 1)
    }

    private fun findCode(prefix: Int, index: Int): Int {
        val key = pairKey(prefix, index)
        var slot = hash(key) and (dictionaryKeys.size - 1)
        while (true) {
            val storedKey = dictionaryKeys[slot]
            if (storedKey == 0) return -1
            if (storedKey == key + 1) return dictionaryValues[slot]
            slot = (slot + 1) and (dictionaryKeys.size - 1)
        }
    }

    private fun addCode(prefix: Int, index: Int, code: Int) {
        val key = pairKey(prefix, index)
        var slot = hash(key) and (dictionaryKeys.size - 1)
        while (dictionaryKeys[slot] != 0) slot = (slot + 1) and (dictionaryKeys.size - 1)
        dictionaryKeys[slot] = key + 1
        dictionaryValues[slot] = code
    }

    private fun resetDictionary() {
        dictionaryKeys.fill(0)
        nextCode = endOfInfoCode + 1
        codeSize = minimumCodeSize + 1
    }

    private fun pairKey(prefix: Int, index: Int): Int = prefix shl 8 or index

    private fun hash(value: Int): Int {
        var result = value * -0x61C88647
        result = result xor (result ushr 16)
        return result
    }
}
