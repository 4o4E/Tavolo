package top.e404.tavolo.gif

import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Color
import org.jetbrains.skia.Data
import org.jetbrains.skia.IRect
import org.jetbrains.skia.Image
import java.nio.ByteOrder
import top.e404.tavolo.frame.Frame
import top.e404.tavolo.frame.replenish
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun builderWritesRatioBufferingAndGlobalTableFromBitmap() {
        val bytes = gif(2, 1) {
            ratio(7)
            buffering(true)
            table(bitmapOf(2, 1, Color.RED, Color.BLUE))
            frame(bitmapOf(2, 1, Color.RED, Color.BLUE)) { duration = 30 }
            frame(bitmapOf(2, 1, Color.BLUE, Color.RED)) { duration = 40 }
        }.bytes

        val codec = Codec.makeFromData(Data.makeFromBytes(bytes))

        assertEquals(7, bytes[12].toInt() and 0xFF)
        assertTrue(bytes.indexOf(byteArrayOf(0x01, 0x00, 0x01, 0x00, 0x00)) >= 0, "应写入buffering扩展")
        assertEquals(2, codec.frameCount)
    }

    @Test
    fun localColorTableCanBeUsedPerFrame() {
        val redBlue = ColorTable.create(intArrayOf(Color.RED, Color.BLUE), sort = true, hasTransparency = false)
        val greenWhite = ColorTable.create(intArrayOf(Color.GREEN, Color.WHITE), sort = true, hasTransparency = false)
        val bytes = gif(2, 1) {
            frame(bitmapOf(2, 1, Color.RED, Color.BLUE), redBlue) { duration = 50 }
            frame(bitmapOf(2, 1, Color.GREEN, Color.WHITE), greenWhite) { duration = 60 }
        }.bytes

        val codec = Codec.makeFromData(Data.makeFromBytes(bytes))

        assertEquals(2, codec.frameCount)
        assertEquals(';'.code, bytes.last().toInt() and 0xFF)
    }

    @Test
    fun validationRejectsInvalidLoopAndTransparentFullTable() {
        assertFailsWith<IllegalArgumentException> {
            gif(1, 1) {
                loop(0x1_0000)
                frame(bitmapOf(1, 1, Color.RED))
            }
        }
        assertFailsWith<IllegalArgumentException> {
            ColorTable.create(IntArray(256) { it }, sort = false, hasTransparency = true)
        }
    }

    @Test
    fun applicationProfileExtensionWritesIdentifierAndData() {
        val buffer = ByteBuffer.allocate(19)

        ApplicationExtension.profile(buffer, byteArrayOf(1, 2, 3))

        val bytes = buffer.array().copyOf(buffer.position())
        assertTrue(bytes.indexOf("ICCRGBG1".toByteArray(Charsets.US_ASCII)) >= 0)
        assertEquals(3, bytes[14].toInt() and 0xFF)
        assertEquals(1, bytes[15].toInt() and 0xFF)
        assertEquals(2, bytes[16].toInt() and 0xFF)
        assertEquals(3, bytes[17].toInt() and 0xFF)
    }

    @Test
    fun colorTableWritesPaddedRgbValuesAndReportsSizeBits() {
        val table = ColorTable.create(
            colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE),
            sort = true,
            hasTransparency = false,
            background = 2
        )
        val buffer = ByteBuffer.allocate(table.s())

        table.write(buffer)

        assertTrue(table.exists())
        assertEquals(1, table.size())
        assertEquals(12, table.s())
        assertEquals(12, buffer.position())
        assertEquals(2, table.background)
        assertEquals(0xFF, buffer.array()[0].toInt() and 0xFF)
        assertEquals(0, buffer.array()[11].toInt() and 0xFF)
    }

    @Test
    fun colorTableAndUnsignedHelpersValidateBounds() {
        assertFailsWith<IllegalStateException> {
            ColorTable(IntArray(257), sort = false)
        }
        assertFailsWith<IllegalStateException> {
            ColorTable(IntArray(1), sort = false, transparency = 2)
        }
        assertFailsWith<IllegalStateException> {
            (-1).asUnsignedByte()
        }
        assertFailsWith<IllegalStateException> {
            0x1_0000.asUnsignedShort()
        }
    }

    @Test
    fun imageDescriptorWriteSerializesLocalTableAndImageData() {
        val table = ColorTable.create(
            colors = intArrayOf(Color.RED, Color.BLUE),
            sort = true,
            hasTransparency = false
        )
        val buffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)

        ImageDescriptor.write(
            buffer = buffer,
            rect = IRect.makeXYWH(1, 2, 2, 1),
            table = table,
            local = true,
            image = intArrayOf(0, 1)
        )

        val bytes = buffer.array()
        assertEquals(0x2C, bytes[0].toInt() and 0xFF)
        assertEquals(1, bytes[1].toInt() and 0xFF)
        assertEquals(2, bytes[3].toInt() and 0xFF)
        assertTrue((bytes[9].toInt() and 0x90) == 0x90, "局部色表和排序标记应被写入")
        assertTrue(buffer.position() > 16)
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
