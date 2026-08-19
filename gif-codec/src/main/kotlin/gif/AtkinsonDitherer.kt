package top.e404.tavolo.gif

import org.jetbrains.skia.Bitmap
import top.e404.tavolo.util.limit

/**
 * 使用三行滚动误差缓冲执行 Atkinson 抖动，避免为整帧创建颜色对象矩阵。
 */
object AtkinsonDitherer {
    fun dither(bitmap: Bitmap, table: IntArray, transparencyIndex: Int? = null): IntArray =
        dither(BitmapPixels.from(bitmap), table, transparencyIndex)

    internal fun dither(pixels: BitmapPixels, table: IntArray, transparencyIndex: Int? = null): IntArray {
        val width = pixels.width
        val height = pixels.height
        val tableRed = IntArray(table.size) { table[it] ushr 16 and 0xFF }
        val tableGreen = IntArray(table.size) { table[it] ushr 8 and 0xFF }
        val tableBlue = IntArray(table.size) { table[it] and 0xFF }
        val searchableIndices = IntArray(table.size - if (transparencyIndex == null) 0 else 1)
        var searchableCount = 0
        for (index in table.indices) {
            if (index != transparencyIndex) searchableIndices[searchableCount++] = index
        }
        require(searchableCount > 0 || transparencyIndex != null) { "GIF色表不能为空" }

        val errorRed = Array(3) { IntArray(width) }
        val errorGreen = Array(3) { IntArray(width) }
        val errorBlue = Array(3) { IntArray(width) }
        val nearestCache = IntIntCache()
        val indices = IntArray(width * height)

        for (y in 0 until height) {
            val currentRow = y % 3
            val nextRow = (y + 1) % 3
            val secondNextRow = (y + 2) % 3
            for (x in 0 until width) {
                val offsetIndex = y * width + x
                val color = pixels.argb[offsetIndex]
                if (color ushr 24 < 0x80) {
                    indices[offsetIndex] = requireNotNull(transparencyIndex) { "透明GIF帧缺少透明颜色索引" }
                    continue
                }

                require(searchableCount > 0) { "GIF色表缺少非透明颜色" }
                val originalRed = (color ushr 16 and 0xFF) + errorRed[currentRow][x]
                val originalGreen = (color ushr 8 and 0xFF) + errorGreen[currentRow][x]
                val originalBlue = (color and 0xFF) + errorBlue[currentRow][x]
                val normalizedRed = originalRed.limit()
                val normalizedGreen = originalGreen.limit()
                val normalizedBlue = originalBlue.limit()
                val cacheKey = normalizedRed shl 16 or (normalizedGreen shl 8) or normalizedBlue
                var replacementIndex = nearestCache[cacheKey]
                if (replacementIndex < 0) {
                    replacementIndex = nearestColorIndex(
                        normalizedRed,
                        normalizedGreen,
                        normalizedBlue,
                        searchableIndices,
                        searchableCount,
                        tableRed,
                        tableGreen,
                        tableBlue,
                    )
                    nearestCache[cacheKey] = replacementIndex
                }
                indices[offsetIndex] = replacementIndex

                val errorOffsetRed = (originalRed - tableRed[replacementIndex]) / 8
                val errorOffsetGreen = (originalGreen - tableGreen[replacementIndex]) / 8
                val errorOffsetBlue = (originalBlue - tableBlue[replacementIndex]) / 8
                addError(pixels, x + 1, y, currentRow, errorOffsetRed, errorOffsetGreen, errorOffsetBlue, errorRed, errorGreen, errorBlue)
                addError(pixels, x + 2, y, currentRow, errorOffsetRed, errorOffsetGreen, errorOffsetBlue, errorRed, errorGreen, errorBlue)
                addError(pixels, x - 1, y + 1, nextRow, errorOffsetRed, errorOffsetGreen, errorOffsetBlue, errorRed, errorGreen, errorBlue)
                addError(pixels, x, y + 1, nextRow, errorOffsetRed, errorOffsetGreen, errorOffsetBlue, errorRed, errorGreen, errorBlue)
                addError(pixels, x + 1, y + 1, nextRow, errorOffsetRed, errorOffsetGreen, errorOffsetBlue, errorRed, errorGreen, errorBlue)
                addError(pixels, x, y + 2, secondNextRow, errorOffsetRed, errorOffsetGreen, errorOffsetBlue, errorRed, errorGreen, errorBlue)
            }
            errorRed[currentRow].fill(0)
            errorGreen[currentRow].fill(0)
            errorBlue[currentRow].fill(0)
        }

        return indices
    }

    private fun nearestColorIndex(
        red: Int,
        green: Int,
        blue: Int,
        searchableIndices: IntArray,
        searchableCount: Int,
        tableRed: IntArray,
        tableGreen: IntArray,
        tableBlue: IntArray,
    ): Int {
        var nearestIndex = searchableIndices[0]
        var nearestDistance = Int.MAX_VALUE
        for (searchableIndex in 0 until searchableCount) {
            val index = searchableIndices[searchableIndex]
            val deltaRed = tableRed[index] - red
            val deltaGreen = tableGreen[index] - green
            val deltaBlue = tableBlue[index] - blue
            val distance = deltaRed * deltaRed + deltaGreen * deltaGreen + deltaBlue * deltaBlue
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestIndex = index
            }
        }
        return nearestIndex
    }

    private fun addError(
        pixels: BitmapPixels,
        x: Int,
        y: Int,
        row: Int,
        red: Int,
        green: Int,
        blue: Int,
        errorRed: Array<IntArray>,
        errorGreen: Array<IntArray>,
        errorBlue: Array<IntArray>,
    ) {
        if (x !in 0 until pixels.width || y !in 0 until pixels.height) return
        if (pixels.argb[y * pixels.width + x] ushr 24 < 0x80) return
        errorRed[row][x] += red
        errorGreen[row][x] += green
        errorBlue[row][x] += blue
    }

    /**
     * 仅保存实际出现过的颜色，避免 HashMap 装箱和固定 24 位直查表占用。
     */
    private class IntIntCache {
        private var keys = IntArray(4096)
        private var values = IntArray(4096)
        private var size = 0
        private var resizeThreshold = keys.size * 2 / 3

        operator fun get(key: Int): Int {
            var index = hash(key) and (keys.size - 1)
            val storedKey = key + 1
            while (true) {
                val candidate = keys[index]
                if (candidate == storedKey) return values[index]
                if (candidate == 0) return -1
                index = (index + 1) and (keys.size - 1)
            }
        }

        operator fun set(key: Int, value: Int) {
            if (size >= resizeThreshold) resize()
            val storedKey = key + 1
            var index = hash(key) and (keys.size - 1)
            while (keys[index] != 0) index = (index + 1) and (keys.size - 1)
            keys[index] = storedKey
            values[index] = value
            size++
        }

        private fun resize() {
            val previousKeys = keys
            val previousValues = values
            keys = IntArray(previousKeys.size shl 1)
            values = IntArray(keys.size)
            resizeThreshold = keys.size * 2 / 3
            size = 0
            for (index in previousKeys.indices) {
                val storedKey = previousKeys[index]
                if (storedKey == 0) continue
                putExisting(storedKey, previousValues[index])
            }
        }

        private fun putExisting(storedKey: Int, value: Int) {
            var index = hash(storedKey - 1) and (keys.size - 1)
            while (keys[index] != 0) index = (index + 1) and (keys.size - 1)
            keys[index] = storedKey
            values[index] = value
            size++
        }

        private fun hash(value: Int): Int {
            var result = value * -0x61C88647
            result = result xor (result ushr 16)
            return result
        }
    }
}
