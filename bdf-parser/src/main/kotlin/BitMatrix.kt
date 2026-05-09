package top.e404.tavolo.dbf

private fun createBitMatrixBytes(width: Int, height: Int): ByteArray {
    require(width >= 0) { "点阵宽度不能小于 0" }
    require(height >= 0) { "点阵高度不能小于 0" }
    return ByteArray(((width + 7) / 8) * height)
}

/**
 * 代表二值的点阵图, 坐标轴原点在左上方, 横向x轴, 竖向y轴
 *
 * @property width 点阵图宽度
 * @property height 点阵图高度
 * @property bytes 点阵图数据
 */
class BitMatrix(
    val width: Int,
    val height: Int,
    private val bytes: ByteArray = createBitMatrixBytes(width, height)
) {
    companion object {
        operator fun invoke(width: Int, height: Int, string: String): BitMatrix {
            require(width > 0) { "点阵行宽必须大于 0" }
            require(height >= 0) { "点阵高度不能小于 0" }
            require(string.length == width * height) { "点阵数据长度和尺寸不匹配" }
            require(string.length % 2 == 0) { "点阵数据必须是完整的十六进制字节" }
            val pixelWidth = width * 4
            val bytes = ByteArray(((pixelWidth + 7) / 8) * height)
            for (i in string.indices step 2) {
                bytes[i / 2] = string.substring(i, i + 2).toUByte(16).toByte()
            }
            return BitMatrix(pixelWidth, height, bytes)
        }

        operator fun invoke(lines: List<String>): BitMatrix {
            require(lines.isNotEmpty()) { "点阵行不能为空" }
            return invoke(lines[0].length, lines.size, lines.joinToString(""))
        }
    }

    private val bytesPerRow = (width + 7) / 8

    init {
        require(width >= 0) { "点阵宽度不能小于 0" }
        require(height >= 0) { "点阵高度不能小于 0" }
        require(bytes.size >= bytesPerRow * height) { "点阵字节数组长度不足" }
    }

    val xRange get() = 0 until width
    val yRange get() = 0 until height

    /**
     * 获取对应位置的bit
     */
    operator fun get(x: Int, y: Int): Boolean {
        val offset = 7 - x % 8
        val get = y * bytesPerRow + x / 8
        return bytes[get].toInt() shr offset and 1 != 0
    }

    /**
     * 设置对应位置的bit
     */
    operator fun set(x: Int, y: Int, value: Boolean) {
        val offset = 7 - x % 8
        val get = y * bytesPerRow + x / 8
        val old = bytes[get].toInt()
        val new =
            if (value) old or (1 shl offset)
            else old and (1 shl offset).inv()
        bytes[get] = new.toUByte().toByte()

    }

    /**
     * 遍历每个bit
     *
     * @param block 处理
     */
    fun forEachBit(block: (x: Int, y: Int, bit: Boolean) -> Unit) {
        for (y in yRange) for (x in xRange) {
            block(x, y, get(x, y))
        }
    }
}

/**
 * 横向叠加
 */
operator fun BitMatrix.plus(other: BitMatrix): BitMatrix {
    require(height == other.height) { "只能拼接高度相同的点阵" }
    val new = BitMatrix(width + other.width, height)
    forEachBit { x, y, bit ->
        new[x, y] = bit
    }
    other.forEachBit { x, y, bit ->
        new[x + width, y] = bit
    }
    return new
}
