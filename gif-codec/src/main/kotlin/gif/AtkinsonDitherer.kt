package top.e404.tavolo.gif

import org.jetbrains.skia.*
import top.e404.tavolo.util.rgb
import top.e404.tavolo.util.limit

/**
 * 抖动器
 */
object AtkinsonDitherer {
    private val DISTRIBUTION = listOf(
        ErrorComponent(1, 0, 1 / 8.0),
        ErrorComponent(2, 0, 1 / 8.0),

        ErrorComponent(-1, 1, 1 / 8.0),
        ErrorComponent(0, 1, 1 / 8.0),
        ErrorComponent(1, 1, 1 / 8.0),

        ErrorComponent(0, 2, 1 / 8.0),
    )

    private data class ErrorComponent(
        val deltaX: Int,
        val deltaY: Int,
        val power: Double,
    )

    private data class Color(val red: Int, val green: Int, val blue: Int) {
        constructor(rgb: Int) : this(
            red = rgb and 0xFF0000 shr 16,
            green = rgb and 0x00FF00 shr 8,
            blue = rgb and 0x0000FF
        )
    }

    private operator fun Color.minus(other: Color) = Color(
        red = this.red - other.red,
        green = this.green - other.green,
        blue = this.blue - other.blue
    )

    private operator fun Color.plus(other: Color) = Color(
        red = this.red + other.red,
        green = this.green + other.green,
        blue = this.blue + other.blue
    )

    private operator fun Color.times(power: Double) = Color(
        red = (this.red * power).toInt(),
        green = (this.green * power).toInt(),
        blue = (this.blue * power).toInt()
    )

    private fun Color.nearest() = red * red + green * green + blue * blue
    private fun Color.clamped() = Color(red.limit(), green.limit(), blue.limit())
    private fun Color.cacheKey() = clamped().run { rgb(red, green, blue) }

    fun dither(bitmap: Bitmap, table: IntArray, transparencyIndex: Int? = null): IntArray {
        val width = bitmap.width
        val height = bitmap.height
        val colors = Array(height) { y -> Array(width) { x -> Color(rgb = bitmap.getColor(x, y)) } }
        val tableColors = List(table.size) { index -> Color(rgb = table[index]) }
        val searchableIndices = table.indices.filter { it != transparencyIndex }
        require(searchableIndices.isNotEmpty() || transparencyIndex != null) { "GIF色表不能为空" }
        val nearestCache = HashMap<Int, Int>()
        val indices = IntArray(width * height)

        for (y in 0 until height) for (x in 0 until width) {
            val offsetIndex = y * width + x
            if (bitmap.getAlphaf(x, y) < 0.5F) {
                indices[offsetIndex] = requireNotNull(transparencyIndex) { "透明GIF帧缺少透明色索引" }
                continue
            }

            val original = colors[y][x]
            require(searchableIndices.isNotEmpty()) { "GIF色表缺少非透明颜色" }
            val normalized = original.clamped()
            val replacementIndex = nearestCache.getOrPut(normalized.cacheKey()) {
                searchableIndices.minBy { (tableColors[it] - normalized).nearest() }
            }
            val replacement = tableColors[replacementIndex]
            indices[offsetIndex] = replacementIndex
            colors[y][x] = replacement
            val error = original - replacement
            for (component in DISTRIBUTION) {
                val siblingX = x + component.deltaX
                val siblingY = y + component.deltaY
                if (
                    siblingX in 0 until width &&
                    siblingY in 0 until height &&
                    bitmap.getAlphaf(siblingX, siblingY) >= 0.5F
                ) {
                    val offset = error * component.power
                    colors[siblingY][siblingX] = colors[siblingY][siblingX] + offset
                }
            }
        }

        return indices
    }
}
