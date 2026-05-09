package top.e404.tavolo.frame

import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import top.e404.tavolo.util.newSurface
import top.e404.tavolo.util.resize
import top.e404.tavolo.util.withCanvas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FrameCodecTest {
    @Test
    fun singleFrameEncodesAsPngAndDecodesWithDefaultDuration() {
        val bytes = listOf(Frame(123, imageOf(2, 1, Color.RED, Color.BLUE))).encodeToBytes()
        val frames = bytes.decodeToFrames()

        assertEquals(1, frames.size)
        assertEquals(50, frames.single().duration)
        assertEquals(2, frames.single().image.width)
        assertTrue(bytes.isNotEmpty())
        assertTrue(bytes[0].toInt() and 0xFF == 0x89, "单帧应编码为PNG")
    }

    @Test
    fun multiFrameEncodesAsGifAndCanBeDecoded() {
        val bytes = listOf(
            Frame(80, imageOf(2, 1, Color.RED, Color.BLUE)),
            Frame(90, imageOf(2, 1, Color.BLUE, Color.RED))
        ).encodeToBytes()
        val frames = bytes.decodeToFrames()

        assertEquals('G'.code, bytes[0].toInt() and 0xFF)
        assertEquals(2, frames.size)
        assertEquals(80, frames[0].duration)
        assertEquals(90, frames[1].duration)
    }

    @Test
    fun emptyFrameListRejectsEncoding() {
        val error = assertFailsWith<IllegalArgumentException> {
            emptyList<Frame>().encodeToBytes()
        }

        assertEquals("gif帧数必须大于0", error.message)
    }

    @Test
    fun commonUpdatesDurationAndResizesFrames() {
        val frames = mutableListOf(
            Frame(10, imageOf(4, 2, Color.RED)),
            Frame(20, imageOf(4, 2, Color.BLUE))
        )

        val returned = frames.common(mapOf("d" to "75", "w" to "2"))

        assertSame(frames, returned)
        frames.forEach {
            assertEquals(75, it.duration)
            assertEquals(2, it.image.width)
            assertEquals(2, it.image.height)
        }
    }

    @Test
    fun frameHelpersMutateImageAndExposeEncodedBytes() {
        val original = imageOf(2, 2, Color.RED)
        val replacement = imageOf(1, 1, Color.BLUE)
        val frame = original.toFrame(12)
            .duration(34)
            .image(replacement)

        assertEquals(34, frame.duration)
        assertEquals(1, frame.toBitmap().width)
        assertTrue(frame.bytes(EncodedImageFormat.PNG).isNotEmpty())

        val clone = frame.clone()
        frame.handleImage { it.resize(2, 2) }

        assertEquals(2, frame.image.width)
        assertEquals(1, clone.image.width)
        assertEquals(1, replacement.toFrames().size)
    }

    @Test
    fun handleIndexedAndCanvasHelpersProcessEachFrame() = runBlocking {
        val frames = mutableListOf(
            Frame(10, imageOf(3, 3, Color.RED)),
            Frame(20, imageOf(3, 3, Color.BLUE))
        )

        val handled = frames
            .handle { it.resize(2, 2) }
            .handleIndexed { index, image -> image.resize(index + 1, image.height) }
            .withCanvas { image ->
                drawImage(image, 0F, 0F)
                drawRect(Rect.makeXYWH(0F, 0F, 1F, 1F), Paint().apply { color = Color.GREEN })
            }

        assertEquals(listOf(1, 2), handled.map { it.image.width })
        assertEquals(listOf(2, 2), handled.map { it.image.height })
    }

    @Test
    fun handlerStylePipelineCanBeEncodedAndDecoded() {
        runBlocking {
            val frames = mutableListOf(Frame(10, imageOf(4, 4, Color.RED)))
            val result = HandleResult.run {
                frames
                    .common(mapOf("d" to "40", "w" to "2", "h" to "2"))
                    .replenish(3, Frame::limitAsGif)
                    .result {
                        handleIndexed { index, image ->
                            image.newSurface().withCanvas {
                                drawImage(image, 0F, 0F)
                                drawRect(
                                    Rect.makeXYWH(index.toFloat(), 0F, 1F, 1F),
                                    Paint().apply { color = Color.BLUE }
                                )
                            }
                        }
                    }
            }

            val encoded = result.getOrThrow().encodeToBytes()
            val decoded = encoded.decodeToFrames()

            assertTrue(result.success)
            assertTrue(result.gif)
            assertEquals(3, decoded.size)
            assertEquals(listOf(40, 40, 40), decoded.map { it.duration })
            assertEquals(listOf(2, 2, 2), decoded.map { it.image.width })
        }
    }

    @Test
    fun replenishValidatesInputAndReturnsOriginalWhenEnoughFrames() {
        runBlocking {
            val frames = mutableListOf(
                Frame(10, imageOf(1, 1, Color.RED)),
                Frame(20, imageOf(1, 1, Color.BLUE))
            )

            val same = frames.replenish(2) { duration += 1 }

            assertSame(frames, same)
            assertEquals(listOf(11, 21), frames.map { it.duration })
            assertFailsWith<IllegalArgumentException> {
                mutableListOf<Frame>().replenish(1)
            }
            assertFailsWith<IllegalArgumentException> {
                frames.replenish(0)
            }
        }
    }

    @Test
    fun limitAsGifOnlyShrinksLargeFrames() {
        val small = Frame(10, imageOf(2, 1, Color.RED))
        val large = Frame(10, imageOf(4, 2, Color.RED))

        small.limitAsGif(10F)
        large.limitAsGif(2F)

        assertEquals(2, small.image.width)
        assertEquals(2, large.image.width)
        assertEquals(1, large.image.height)
    }

    @Test
    fun handleResultReportsSuccessFailureAndThrowsOriginalException() = runBlocking {
        val success = HandleResult.run {
            mutableListOf(Frame(10, imageOf(1, 1, Color.RED))).result { this }
        }
        val fail = HandleResult.fail("失败")
        val error = RuntimeException("boom")
        val thrown = HandleResult.run {
            mutableListOf(Frame(10, imageOf(1, 1, Color.BLUE))).result {
                throw error
            }
        }

        assertTrue(success.success)
        assertFalse(success.gif)
        assertNotNull(success.getOrNull())
        assertEquals(1, success.getOrThrow().size)

        assertFalse(fail.success)
        assertTrue(fail.gif)
        assertNull(fail.getOrNull())
        assertFailsWith<Exception> { fail.getOrThrow() }

        assertFalse(thrown.success)
        assertEquals("处理时出现异常(RuntimeException: boom)", thrown.failMsg)
        assertSame(error, assertFailsWith<RuntimeException> { thrown.getOrThrow() })
    }

    @Test
    fun framesHandlerHandlesBytesAndWrapsDecodeFailures() {
        runBlocking {
            val handler = object : FramesHandler {
                override val name = "unit"
                override val regex = Regex("unit")

                override suspend fun handleFrames(
                    frames: MutableList<Frame>,
                    args: MutableMap<String, String>
                ): HandleResult {
                    frames.common(args)
                    return HandleResult(frames, null, null)
                }
            }

            val success = handler.handleBytes(
                listOf(Frame(10, imageOf(1, 1, Color.RED))).encodeToBytes(),
                mutableMapOf("d" to "66")
            )
            val failure = handler.handleBytes(byteArrayOf(1, 2, 3), mutableMapOf())

            assertTrue(success.success)
            assertEquals(66, success.getOrThrow().single().duration)
            assertFalse(failure.success)
            assertNotNull(failure.throwable)
        }
    }

    private fun imageOf(width: Int, height: Int, vararg colors: Int): Image {
        val surface = Surface.makeRasterN32Premul(width, height)
        val bitmapColors = if (colors.isEmpty()) intArrayOf(Color.TRANSPARENT) else colors
        surface.canvas.clear(bitmapColors.first())
        bitmapColors.forEachIndexed { index, color ->
            surface.canvas.drawRect(
                Rect.makeXYWH((index % width).toFloat(), (index / width).toFloat(), 1F, 1F),
                Paint().apply { this.color = color }
            )
        }
        return surface.makeImageSnapshot()
    }
}
