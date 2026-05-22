package top.e404.tavolo.wordcloud

import org.jetbrains.skia.Color
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import top.e404.tavolo.util.FontManager
import top.e404.tavolo.util.toBitmap
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.PI
import kotlin.random.Random

internal class WordCloudLayoutEngine {
    fun layout(entries: List<WordCloudEntry>, mask: WordCloudMask, options: WordCloudOptions): List<WordCloudPlacedEntry> {
        require(options.minFontSize > 0) { "最小字号必须大于 0" }
        require(options.maxFontSize >= options.minFontSize) { "最大字号不能小于最小字号" }
        require(options.maxWords > 0) { "最大词数必须大于 0" }
        require(options.rotations.isNotEmpty()) { "旋转角度列表不能为空" }
        require(mask.allowedCount() > 0) { "词云蒙版没有可绘制区域" }

        val normalized = normalizeEntries(entries, options.maxWords)
        require(normalized.isNotEmpty()) { "词云词频列表为空或全部被过滤" }

        val random = Random(options.randomSeed)
        val typeface = FontManager.resolve(options.fontFamily)
        val paint = Paint().apply { isAntiAlias = true }
        val occupancy = PixelOccupancy(mask.width, mask.height)
        val minWeight = normalized.minOf { it.weight }
        val maxWeight = normalized.maxOf { it.weight }

        return normalized.mapIndexedNotNull { index, entry ->
            val fontSize = scaleFontSize(entry.weight, minWeight, maxWeight, options)
            val rotation = options.rotations[index % options.rotations.size]
            val font = Font(typeface, fontSize)
            val sprite = WordSprite.fromText(entry.text, font, paint, options.padding, rotation)
            findPlacement(sprite, mask, occupancy, options, random)
                ?.let { (left, top) ->
                    occupancy.occupy(left, top, sprite)
                    WordCloudPlacedEntry(
                        text = entry.text,
                        weight = entry.weight,
                        fontSize = fontSize,
                        color = options.palette.pick(index, random),
                        left = left,
                        top = top,
                        width = sprite.width,
                        height = sprite.height,
                        baselineY = top + sprite.drawBaselineY,
                        rotation = rotation,
                        drawWidth = sprite.drawWidth,
                        drawHeight = sprite.drawHeight,
                        drawX = sprite.drawX,
                        drawBaselineY = sprite.drawBaselineY,
                    )
                }
        }
    }

    private fun normalizeEntries(entries: List<WordCloudEntry>, maxWords: Int): List<WordCloudEntry> =
        entries.asSequence()
            .map { WordCloudEntry(it.text.trim(), it.weight) }
            .filter { it.text.isNotBlank() && it.weight > 0 }
            .sortedByDescending { it.weight }
            .take(maxWords)
            .toList()

    private fun scaleFontSize(weight: Int, minWeight: Int, maxWeight: Int, options: WordCloudOptions): Float {
        if (minWeight == maxWeight) return (options.minFontSize + options.maxFontSize) / 2
        val ratio = (weight - minWeight).toFloat() / (maxWeight - minWeight)
        return options.minFontSize + (options.maxFontSize - options.minFontSize) * kotlin.math.sqrt(ratio)
    }

    private fun findPlacement(
        sprite: WordSprite,
        mask: WordCloudMask,
        occupancy: PixelOccupancy,
        options: WordCloudOptions,
        random: Random,
    ): Pair<Int, Int>? {
        val centerX = mask.width / 2f
        val centerY = mask.height / 2f
        val restartCount = 5
        val attemptsPerRestart = max(1, options.maxPlacementAttempts / restartCount)
        for (restart in 0 until restartCount) {
            val (originX, originY) = if (restart == 0 && mask.isAllowed(centerX.roundToInt(), centerY.roundToInt())) {
                centerX to centerY
            } else {
                mask.randomAllowedPoint(random)
            }
            var angle = random.nextFloat() * 6.2831855f
            for (attempt in 0 until attemptsPerRestart) {
                val radius = options.spiralStep * attempt / 6f
                val left = (originX + cos(angle) * radius - sprite.width / 2f).roundToInt()
                val top = (originY + sin(angle) * radius - sprite.height / 2f).roundToInt()
                if (occupancy.canPlace(left, top, sprite, mask)) {
                    return left to top
                }
                angle += options.angleStep
            }
        }
        return null
    }
}

private class WordSprite(
    val width: Int,
    val height: Int,
    val textPoints: IntArray,
    val collisionPoints: IntArray,
    val drawWidth: Int,
    val drawHeight: Int,
    val drawX: Float,
    val drawBaselineY: Float,
) {
    companion object {
        fun fromText(text: String, font: Font, paint: Paint, padding: Int, rotation: Float): WordSprite {
            val metrics = font.metrics
            val textWidth = ceil(font.measureTextWidth(text, paint)).roundToInt().coerceAtLeast(1)
            val textHeight = ceil(metrics.descent - metrics.ascent).roundToInt().coerceAtLeast(1)
            val drawWidth = textWidth + padding * 2
            val drawHeight = textHeight + padding * 2
            val normalizedRotation = ((rotation % 360f) + 360f) % 360f
            val radians = normalizedRotation / 180.0 * PI
            val width = ceil(kotlin.math.abs(drawWidth * kotlin.math.cos(radians)) + kotlin.math.abs(drawHeight * kotlin.math.sin(radians)))
                .roundToInt()
                .coerceAtLeast(1)
            val height = ceil(kotlin.math.abs(drawWidth * kotlin.math.sin(radians)) + kotlin.math.abs(drawHeight * kotlin.math.cos(radians)))
                .roundToInt()
                .coerceAtLeast(1)
            val drawX = padding.toFloat()
            val drawBaselineY = padding - metrics.ascent
            val textPixels = BooleanArray(width * height)
            Surface.makeRasterN32Premul(width, height).use { surface ->
                val canvas = surface.canvas
                canvas.clear(Color.TRANSPARENT)
                val spritePaint = Paint().apply {
                    isAntiAlias = true
                    color = Color.BLACK
                }
                if (normalizedRotation == 0f) {
                    canvas.drawString(text, drawX, drawBaselineY, font, spritePaint)
                } else {
                    canvas.save()
                    try {
                        canvas.translate(width / 2f, height / 2f)
                        canvas.rotate(rotation)
                        canvas.translate(-drawWidth / 2f, -drawHeight / 2f)
                        canvas.drawString(text, drawX, drawBaselineY, font, spritePaint)
                    } finally {
                        canvas.restore()
                    }
                }
                val bitmap = surface.makeImageSnapshot().toBitmap()
                for (y in 0 until height) for (x in 0 until width) {
                    textPixels[y * width + x] = Color.getA(bitmap.getColor(x, y)) > 8
                }
            }
            val collisionPixels = if (padding > 0) dilate(textPixels, width, height, padding) else textPixels
            return WordSprite(
                width = width,
                height = height,
                textPoints = toPoints(textPixels, width, height),
                collisionPoints = toPoints(collisionPixels, width, height),
                drawWidth = drawWidth,
                drawHeight = drawHeight,
                drawX = drawX,
                drawBaselineY = drawBaselineY,
            )
        }

        private fun dilate(source: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray {
            val result = BooleanArray(source.size)
            for (y in 0 until height) for (x in 0 until width) {
                if (!source[y * width + x]) continue
                for (dy in -radius..radius) for (dx in -radius..radius) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 0 until width && ny in 0 until height) result[ny * width + nx] = true
                }
            }
            return result
        }

        private fun toPoints(pixels: BooleanArray, width: Int, height: Int): IntArray {
            val points = IntArray(pixels.count { it } * 2)
            var cursor = 0
            for (y in 0 until height) for (x in 0 until width) {
                if (!pixels[y * width + x]) continue
                points[cursor++] = x
                points[cursor++] = y
            }
            return points
        }
    }
}

private class PixelOccupancy(
    private val width: Int,
    private val height: Int,
) {
    private val occupied = BooleanArray(width * height)

    fun canPlace(left: Int, top: Int, sprite: WordSprite, mask: WordCloudMask): Boolean {
        if (left < 0 || top < 0 || left + sprite.width > width || top + sprite.height > height) return false
        var cursor = 0
        while (cursor < sprite.textPoints.size) {
            val x = left + sprite.textPoints[cursor]
            val y = top + sprite.textPoints[cursor + 1]
            if (!mask.isAllowed(x, y)) return false
            cursor += 2
        }
        cursor = 0
        while (cursor < sprite.collisionPoints.size) {
            val x = left + sprite.collisionPoints[cursor]
            val y = top + sprite.collisionPoints[cursor + 1]
            if (occupied[y * width + x]) return false
            cursor += 2
        }
        return true
    }

    fun occupy(left: Int, top: Int, sprite: WordSprite) {
        var cursor = 0
        while (cursor < sprite.collisionPoints.size) {
            val x = left + sprite.collisionPoints[cursor]
            val y = top + sprite.collisionPoints[cursor + 1]
            occupied[y * width + x] = true
            cursor += 2
        }
    }
}
