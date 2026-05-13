package top.e404.tavolo.util

import org.jetbrains.skia.Typeface
import top.e404.tavolo.assets.Assets
import top.e404.tavolo.assets.AssetsConfig
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

class AssetsAndFontManagerTest {
    private val originalAssetsConfig = Assets.config

    @AfterTest
    fun resetGlobalState() {
        Assets.configure(originalAssetsConfig)
        FontManager.clearRegistered()
    }

    @Test
    fun assetsResolveRejectsUnsafePaths() {
        val root = tempDir()
        Assets.configure(AssetsConfig(root))

        assertFailsWith<IllegalArgumentException> {
            Assets.resolve("")
        }
        assertFailsWith<IllegalArgumentException> {
            Assets.resolve(root.resolve("x").toString())
        }
        assertFailsWith<IllegalArgumentException> {
            Assets.resolve("../outside.txt")
        }
    }

    @Test
    fun assetsConfigureClearsCachedBytes() {
        val root = tempDir()
        val file = root.resolve("data.txt")
        file.writeText("one")
        Assets.configure(AssetsConfig(root))

        assertContentEquals("one".encodeToByteArray(), Assets.bytes("data.txt"))
        file.writeText("two")
        assertContentEquals("one".encodeToByteArray(), Assets.bytes("data.txt"))

        Assets.configure(AssetsConfig(root))

        assertContentEquals("two".encodeToByteArray(), Assets.bytes("data.txt"))
    }

    @Test
    fun assetsReportsMissingFilesAndInvalidImages() {
        val root = tempDir()
        root.resolve("bad.bin").writeBytes(byteArrayOf(1, 2, 3))
        Assets.configure(AssetsConfig(root))

        val missing = assertFailsWith<IllegalStateException> {
            Assets.text("missing.txt")
        }
        assertEquals(true, missing.message?.contains("资源文件不存在"))

        val invalidImage = assertFailsWith<RuntimeException> {
            Assets.image("bad.bin")
        }
        assertEquals(true, invalidImage.message?.isNotBlank())
    }

    @Test
    fun fontManagerRegistersResolvesAndClearsTypefaceNames() {
        val typeface = Typeface.makeEmpty()

        val name = FontManager.register("unit-empty", typeface)

        assertEquals("unit-empty", name)
        assertEquals(true, FontManager.isRegistered("unit-empty"))
        assertSame(typeface, FontManager.resolve("unit-empty"))

        FontManager.clearRegistered()

        assertFalse(FontManager.isRegistered("unit-empty"))
        assertSame(FontManager.fallbackTypeface, FontManager.resolve("unit-empty"))
    }

    @Test
    fun fontManagerRejectsBlankFontName() {
        assertFailsWith<IllegalArgumentException> {
            FontManager.register(" ", Typeface.makeEmpty())
        }
    }

    private fun tempDir(): Path =
        Files.createTempDirectory("tavolo-common-test-").apply {
            createDirectories()
        }
}
