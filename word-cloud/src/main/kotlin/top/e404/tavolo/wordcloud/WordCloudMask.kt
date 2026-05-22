package top.e404.tavolo.wordcloud

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import top.e404.tavolo.util.toBitmap
import top.e404.tavolo.util.toImage
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

class WordCloudMask(
    val width: Int,
    val height: Int,
    private val allowedPixels: BooleanArray,
) {
    init {
        require(width > 0) { "蒙版宽度必须大于 0" }
        require(height > 0) { "蒙版高度必须大于 0" }
        require(allowedPixels.size == width * height) { "蒙版像素数量和尺寸不匹配" }
    }

    fun isAllowed(x: Int, y: Int): Boolean =
        x in 0 until width && y in 0 until height && allowedPixels[y * width + x]

    fun allowedCount(): Int = allowedPixels.count { it }

    internal fun randomAllowedPoint(random: Random): Pair<Float, Float> {
        repeat(1000) {
            val x = random.nextInt(width)
            val y = random.nextInt(height)
            if (isAllowed(x, y)) return x.toFloat() to y.toFloat()
        }
        for (y in 0 until height) for (x in 0 until width) {
            if (isAllowed(x, y)) return x.toFloat() to y.toFloat()
        }
        error("词云蒙版没有可绘制像素")
    }

    fun toDebugImage(allowedColor: Int = Color.BLACK, blockedColor: Int = Color.WHITE): Image {
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32Premul(width, height))
        for (y in 0 until height) for (x in 0 until width) {
            bitmap.erase(if (isAllowed(x, y)) allowedColor else blockedColor, org.jetbrains.skia.IRect.makeXYWH(x, y, 1, 1))
        }
        return bitmap.toImage()
    }

    companion object {
        fun rectangle(width: Int, height: Int): WordCloudMask =
            WordCloudMask(width, height, BooleanArray(width * height) { true })
    }
}

data class OtsuMaskOptions(
    val maxSide: Int = 1000,
    val darkAsAllowed: Boolean = true,
    val alphaThreshold: Int = 8,
)

data class EdgeMaskOptions(
    val maxSide: Int = 1000,
    val threshold: Int? = null,
    val dilateRadius: Int = 2,
    val fillBetweenEdges: Boolean = false,
    val alphaThreshold: Int = 8,
)

object WordCloudMaskFactory {
    /**
     * 按最大边等比缩放模板图，避免大图拖慢后续逐像素处理。
     */
    fun resizeByMaxSide(image: Image, maxSide: Int): Image {
        require(maxSide > 0) { "最大边长必须大于 0" }
        val longest = max(image.width, image.height)
        if (longest <= maxSide) return image
        val scale = maxSide.toFloat() / longest
        val width = (image.width * scale).roundToInt().coerceAtLeast(1)
        val height = (image.height * scale).roundToInt().coerceAtLeast(1)
        return Surface.makeRasterN32Premul(width, height).use { surface ->
            surface.canvas.drawImageRect(
                image,
                Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
                Rect.makeWH(width.toFloat(), height.toFloat()),
                FilterMipmap(FilterMode.LINEAR, MipmapMode.NEAREST),
                null,
                false,
            )
            surface.makeImageSnapshot()
        }
    }

    /**
     * 使用 OTSU 阈值把明暗区域拆成词云可绘制蒙版。
     */
    fun fromOtsu(image: Image, options: OtsuMaskOptions = OtsuMaskOptions()): WordCloudMask {
        val resized = resizeByMaxSide(image, options.maxSide)
        val bitmap = resized.toBitmap()
        val gray = grayPixels(bitmap, options.alphaThreshold)
        val threshold = otsuThreshold(gray)
        val allowed = BooleanArray(bitmap.width * bitmap.height)
        for (index in allowed.indices) {
            val value = gray[index]
            allowed[index] = value >= 0 && if (options.darkAsAllowed) value <= threshold else value > threshold
        }
        return WordCloudMask(bitmap.width, bitmap.height, allowed)
    }

    /**
     * 使用 Sobel 边缘检测生成轮廓蒙版，可选择按边缘层级交替填充。
     */
    fun fromEdges(image: Image, options: EdgeMaskOptions = EdgeMaskOptions()): WordCloudMask {
        val resized = resizeByMaxSide(image, options.maxSide)
        val bitmap = resized.toBitmap()
        val gray = grayPixels(bitmap, options.alphaThreshold)
        val magnitudes = sobelMagnitudes(gray, bitmap.width, bitmap.height)
        if (magnitudes.maxOrNull() == 0) {
            return WordCloudMask(bitmap.width, bitmap.height, BooleanArray(magnitudes.size))
        }
        val threshold = options.threshold ?: otsuThreshold(magnitudes)
        val edgePixels = BooleanArray(magnitudes.size) { magnitudes[it] > 0 && magnitudes[it] >= threshold.coerceAtLeast(1) }
        val edges = if (options.dilateRadius > 0) {
            dilate(edgePixels, bitmap.width, bitmap.height, options.dilateRadius)
        } else {
            edgePixels
        }
        val allowed = if (options.fillBetweenEdges) fillBetweenEdges(edges, bitmap.width, bitmap.height) else edges
        return WordCloudMask(bitmap.width, bitmap.height, allowed)
    }

    fun otsuThreshold(values: IntArray): Int {
        val histogram = IntArray(256)
        var total = 0
        for (value in values) {
            if (value !in 0..255) continue
            histogram[value] += 1
            total += 1
        }
        require(total > 0) { "无法从空像素数据计算 OTSU 阈值" }

        var sum = 0L
        for (i in histogram.indices) sum += i.toLong() * histogram[i]

        var backgroundWeight = 0
        var backgroundSum = 0L
        var maxVariance = -1.0
        var threshold = 0
        for (i in histogram.indices) {
            backgroundWeight += histogram[i]
            if (backgroundWeight == 0) continue
            val foregroundWeight = total - backgroundWeight
            if (foregroundWeight == 0) break

            backgroundSum += i.toLong() * histogram[i]
            val backgroundMean = backgroundSum.toDouble() / backgroundWeight
            val foregroundMean = (sum - backgroundSum).toDouble() / foregroundWeight
            val variance = backgroundWeight.toDouble() * foregroundWeight * (backgroundMean - foregroundMean) * (backgroundMean - foregroundMean)
            if (variance > maxVariance) {
                maxVariance = variance
                threshold = i
            }
        }
        return threshold
    }

    fun encodeDebugPng(mask: WordCloudMask): ByteArray =
        requireNotNull(mask.toDebugImage().encodeToData(EncodedImageFormat.PNG)) {
            "蒙版调试图编码失败"
        }.bytes

    private fun grayPixels(bitmap: Bitmap, alphaThreshold: Int): IntArray {
        val result = IntArray(bitmap.width * bitmap.height)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val color = bitmap.getColor(x, y)
            val index = y * bitmap.width + x
            result[index] = if (Color.getA(color) < alphaThreshold) {
                -1
            } else {
                (0.299 * Color.getR(color) + 0.587 * Color.getG(color) + 0.114 * Color.getB(color)).roundToInt()
                    .coerceIn(0, 255)
            }
        }
        return result
    }

    private fun sobelMagnitudes(gray: IntArray, width: Int, height: Int): IntArray {
        val result = IntArray(gray.size)
        fun at(x: Int, y: Int): Int {
            val cx = x.coerceIn(0, width - 1)
            val cy = y.coerceIn(0, height - 1)
            return gray[cy * width + cx].coerceAtLeast(0)
        }
        for (y in 0 until height) for (x in 0 until width) {
            val gx = -at(x - 1, y - 1) + at(x + 1, y - 1) -
                2 * at(x - 1, y) + 2 * at(x + 1, y) -
                at(x - 1, y + 1) + at(x + 1, y + 1)
            val gy = -at(x - 1, y - 1) - 2 * at(x, y - 1) - at(x + 1, y - 1) +
                at(x - 1, y + 1) + 2 * at(x, y + 1) + at(x + 1, y + 1)
            result[y * width + x] = hypot(gx.toDouble(), gy.toDouble()).roundToInt().coerceIn(0, 255)
        }
        return result
    }

    private fun dilate(source: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray {
        val result = BooleanArray(source.size)
        for (y in 0 until height) for (x in 0 until width) {
            var hit = false
            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 0 until width && ny in 0 until height && source[ny * width + nx]) {
                        hit = true
                        break
                    }
                }
                if (hit) break
            }
            result[y * width + x] = hit
        }
        return result
    }

    private fun fillBetweenEdges(edges: BooleanArray, width: Int, height: Int): BooleanArray {
        val regions = labelRegions(edges, width, height)
        val edgeComponents = labelEdgeComponents(edges, width, height)
        val edgeToRegions = Array(edgeComponents.count) { mutableSetOf<Int>() }

        for (y in 0 until height) for (x in 0 until width) {
            val edgeId = edgeComponents.ids[y * width + x]
            if (edgeId < 0) continue
            forEachNeighbor(x, y, width, height) { nx, ny ->
                val regionId = regions.ids[ny * width + nx]
                if (regionId >= 0) edgeToRegions[edgeId] += regionId
            }
        }

        val adjacency = Array(regions.count) { mutableSetOf<Int>() }
        for (regionSet in edgeToRegions) {
            if (regionSet.size < 2) continue
            val ids = regionSet.toList()
            for (i in ids.indices) for (j in i + 1 until ids.size) {
                adjacency[ids[i]] += ids[j]
                adjacency[ids[j]] += ids[i]
            }
        }

        val depth = IntArray(regions.count) { -1 }
        val queue = IntArray(regions.count.coerceAtLeast(1))
        var head = 0
        var tail = 0
        for (regionId in 0 until regions.count) {
            if (!regions.touchesBorder[regionId]) continue
            depth[regionId] = 0
            queue[tail++] = regionId
        }

        while (head < tail) {
            val regionId = queue[head++]
            for (next in adjacency[regionId]) {
                if (depth[next] >= 0) continue
                depth[next] = depth[regionId] + 1
                queue[tail++] = next
            }
        }

        return BooleanArray(edges.size) { index ->
            if (edges[index]) {
                true
            } else {
                val regionId = regions.ids[index]
                regionId >= 0 && depth.getOrElse(regionId) { -1 } % 2 == 1
            }
        }
    }

    private data class ComponentLabels(
        val ids: IntArray,
        val count: Int,
        val touchesBorder: BooleanArray,
    )

    private fun labelRegions(edges: BooleanArray, width: Int, height: Int): ComponentLabels {
        val ids = IntArray(edges.size) { -1 }
        val borderFlags = mutableListOf<Boolean>()
        val queue = IntArray(edges.size)
        var count = 0
        for (start in edges.indices) {
            if (edges[start] || ids[start] >= 0) continue
            val componentId = count++
            var touchesBorder = false
            var head = 0
            var tail = 0
            ids[start] = componentId
            queue[tail++] = start
            while (head < tail) {
                val index = queue[head++]
                val x = index % width
                val y = index / width
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) touchesBorder = true
                forEachNeighbor(x, y, width, height) { nx, ny ->
                    val next = ny * width + nx
                    if (edges[next] || ids[next] >= 0) return@forEachNeighbor
                    ids[next] = componentId
                    queue[tail++] = next
                }
            }
            borderFlags += touchesBorder
        }
        return ComponentLabels(ids, count, borderFlags.toBooleanArray())
    }

    private fun labelEdgeComponents(edges: BooleanArray, width: Int, height: Int): ComponentLabels {
        val ids = IntArray(edges.size) { -1 }
        val borderFlags = mutableListOf<Boolean>()
        val queue = IntArray(edges.size)
        var count = 0
        for (start in edges.indices) {
            if (!edges[start] || ids[start] >= 0) continue
            val componentId = count++
            var touchesBorder = false
            var head = 0
            var tail = 0
            ids[start] = componentId
            queue[tail++] = start
            while (head < tail) {
                val index = queue[head++]
                val x = index % width
                val y = index / width
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) touchesBorder = true
                forEachNeighbor(x, y, width, height) { nx, ny ->
                    val next = ny * width + nx
                    if (!edges[next] || ids[next] >= 0) return@forEachNeighbor
                    ids[next] = componentId
                    queue[tail++] = next
                }
            }
            borderFlags += touchesBorder
        }
        return ComponentLabels(ids, count, borderFlags.toBooleanArray())
    }

    private inline fun forEachNeighbor(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        block: (Int, Int) -> Unit,
    ) {
        if (x > 0) block(x - 1, y)
        if (x < width - 1) block(x + 1, y)
        if (y > 0) block(x, y - 1)
        if (y < height - 1) block(x, y + 1)
    }
}
