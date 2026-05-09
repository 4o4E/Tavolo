package top.e404.tavolo.gif

import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Color
import org.jetbrains.skia.Data
import org.jetbrains.skia.IRect
import org.jetbrains.skia.Image
import top.e404.tavolo.frame.Frame
import top.e404.tavolo.frame.replenish
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GifCodecTest {
    @Test
    fun loopExtensionWritesLoopCountAsLittleEndian() {
        val bytes = gif(1, 1) {
            loop(1)
            frame(bitmapOf(1, 1, Color.RED))
        }.bytes

        val netscapeIndex = bytes.indexOf("NETSCAPE".toByteArray(Charsets.US_ASCII))

        assertTrue(netscapeIndex >= 0, "应写入NETSCAPE循环扩展")
        assertEquals(0x03, bytes[netscapeIndex + 11].toInt() and 0xFF)
        assertEquals(0x01, bytes[netscapeIndex + 12].toInt() and 0xFF)
        assertEquals(0x01, bytes[netscapeIndex + 13].toInt() and 0xFF)
        assertEquals(0x00, bytes[netscapeIndex + 14].toInt() and 0xFF)
    }

    @Test
    fun twoColorGifCanBeDecoded() {
        val bytes = gif(2, 1) {
            frame(bitmapOf(2, 1, Color.BLACK, Color.WHITE))
            frame(bitmapOf(2, 1, Color.WHITE, Color.BLACK))
        }.bytes

        val codec = Codec.makeFromData(Data.makeFromBytes(bytes))

        assertEquals(2, codec.frameCount)
    }

    @Test
    fun globalColorTableGifEndsAtTrailer() {
        val table = ColorTable.create(
            colors = intArrayOf(Color.RED, Color.BLUE),
            sort = false,
            hasTransparency = false
        )
        val bytes = gif(2, 1) {
            table(table)
            frame(bitmapOf(2, 1, Color.RED, Color.BLUE))
            frame(bitmapOf(2, 1, Color.BLUE, Color.RED))
        }.bytes

        assertEquals(';'.code, bytes.last().toInt() and 0xFF)
    }

    @Test
    fun transparentPixelKeepsAlphaAfterDecode() {
        val bytes = gif(2, 1) {
            frame(bitmapOf(2, 1, Color.RED, Color.TRANSPARENT))
            frame(bitmapOf(2, 1, Color.RED, Color.TRANSPARENT))
        }.bytes
        val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
        val decoded = Bitmap().apply {
            allocPixels(codec.imageInfo)
            codec.readPixels(this, 0)
        }

        assertTrue(decoded.getAlphaf(1, 0) < 0.5F, "透明像素解码后仍应保持透明")
    }

    @Test
    fun replenishKeepsOriginalFrameOrderBeforeLooping() = runBlocking {
        val frames = mutableListOf(
            Frame(10, imageOf(Color.RED)),
            Frame(20, imageOf(Color.GREEN)),
            Frame(30, imageOf(Color.BLUE))
        )

        val result = frames.replenish(5)

        assertEquals(listOf(10, 20, 30, 10, 20), result.map { it.duration })
    }

    private fun bitmapOf(width: Int, height: Int, vararg colors: Int): Bitmap {
        val bitmap = Bitmap()
        bitmap.allocN32Pixels(width, height)
        colors.forEachIndexed { index, color ->
            bitmap.erase(color, IRect.makeXYWH(index % width, index / width, 1, 1))
        }
        return bitmap
    }

    private fun imageOf(color: Int): Image = Image.makeFromBitmap(bitmapOf(1, 1, color))

    private fun ByteArray.indexOf(target: ByteArray): Int {
        for (index in 0..size - target.size) {
            var matched = true
            for (targetIndex in target.indices) {
                if (this[index + targetIndex] != target[targetIndex]) {
                    matched = false
                    break
                }
            }
            if (matched) return index
        }
        return -1
    }
}
