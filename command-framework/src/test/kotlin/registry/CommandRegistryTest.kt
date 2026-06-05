package top.e404.tavolo.registry

import top.e404.tavolo.assets.Assets
import top.e404.tavolo.assets.AssetsConfig
import top.e404.tavolo.frame.Frame
import top.e404.tavolo.frame.FramesHandler
import top.e404.tavolo.frame.HandleResult
import top.e404.tavolo.frame.HandleResult.Companion.result
import top.e404.tavolo.generator.FramesGenerator
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CommandRegistryTest {
    private val originalAssetsConfig = Assets.config

    @AfterTest
    fun resetAssets() {
        Assets.configure(originalAssetsConfig)
    }

    @Test
    fun loaderReadsVersionHandlersAndGeneratorsFromAssets() {
        val root = tempAssetsRoot()
        writeVersion(root)
        writeHandler(root, "round", name = "Round Handler", regex = "\"^round$\"", version = 7)
        writeGenerator(root, "card", name = "Card Generator", regex = "card|卡片", version = 3)
        Assets.configure(AssetsConfig(root))

        val registry = ResourceCommandLoader(
            handlers = mapOf("round" to { NoopHandler("round") }),
            generators = mapOf("card" to { NoopGenerator })
        ).load()

        assertEquals(AssetsVersion("1.2.3", "2026-05-13"), registry.assetsVersion)
        assertEquals(listOf("round"), registry.handlers.map { it.descriptor.id })
        assertEquals(listOf("card"), registry.generators.map { it.descriptor.id })
        val handler = registry.handlers.single().descriptor
        assertEquals(CommandCategory.HANDLER, handler.category)
        assertEquals(CommandType.KOTLIN, handler.type)
        assertEquals("Round Handler", handler.name)
        assertEquals(7, handler.version)
        assertNotNull(registry.matchHandler("round"))
        assertEquals(null, registry.matchHandler("not-round"))

        val generator = registry.generators.single().descriptor
        assertEquals(CommandCategory.GENERATOR, generator.category)
        assertEquals(CommandType.KOTLIN, generator.type)
        assertEquals("Card Generator", generator.name)
        assertEquals(3, generator.version)
        assertTrue(generator.regex.matches("卡片"))
    }

    @Test
    fun loaderRejectsMissingVersionFields() {
        val root = tempAssetsRoot()
        root.resolve("version.yml").writeText("version: 1.2.3\n")
        Assets.configure(AssetsConfig(root))

        val error = assertFailsWith<IllegalStateException> {
            ResourceCommandLoader(emptyMap(), emptyMap()).load()
        }

        assertEquals(true, error.message?.contains("version.yml 缺少 time"))
    }

    @Test
    fun loaderRejectsHandlerIdMismatch() {
        val root = tempAssetsRoot()
        writeVersion(root)
        writeHandler(root, directory = "actual", id = "declared")
        Assets.configure(AssetsConfig(root))

        val error = assertFailsWith<IllegalArgumentException> {
            ResourceCommandLoader(
                handlers = mapOf("declared" to { NoopHandler("declared") }),
                generators = emptyMap()
            ).load()
        }

        assertEquals(true, error.message?.contains("id 与目录名不一致"))
    }

    @Test
    fun loaderRejectsMissingKspRegistration() {
        val root = tempAssetsRoot()
        writeVersion(root)
        writeHandler(root, "round")
        Assets.configure(AssetsConfig(root))

        val error = assertFailsWith<IllegalStateException> {
            ResourceCommandLoader(emptyMap(), emptyMap()).load()
        }

        assertEquals(true, error.message?.contains("handler 缺少 KSP 注册项: round"))
    }

    @Test
    fun loaderRejectsTemplateHandlersUntilRuntimeSupportExists() {
        val root = tempAssetsRoot()
        writeVersion(root)
        writeHandler(root, "template_handler", type = "template")
        Assets.configure(AssetsConfig(root))

        val error = assertFailsWith<IllegalStateException> {
            ResourceCommandLoader(emptyMap(), emptyMap()).load()
        }

        assertEquals(true, error.message?.contains("模板 handler 运行时尚未接入"))
    }

    @Test
    fun loaderRejectsDuplicateCommandIdsAcrossCategories() {
        val root = tempAssetsRoot()
        writeVersion(root)
        writeHandler(root, "same")
        writeGenerator(root, "same")
        Assets.configure(AssetsConfig(root))

        val error = assertFailsWith<IllegalArgumentException> {
            ResourceCommandLoader(
                handlers = mapOf("same" to { NoopHandler("same") }),
                generators = mapOf("same" to { NoopGenerator })
            ).load()
        }

        assertEquals(true, error.message?.contains("指令 id 重复: same"))
    }

    @Test
    fun loaderRejectsGeneratorTypeFieldAndMissingRegistration() {
        val rootWithType = tempAssetsRoot()
        writeVersion(rootWithType)
        writeGenerator(rootWithType, "bad", extra = "type: kotlin\n")
        Assets.configure(AssetsConfig(rootWithType))

        val typeError = assertFailsWith<IllegalArgumentException> {
            ResourceCommandLoader(emptyMap(), mapOf("bad" to { NoopGenerator })).load()
        }
        assertEquals(true, typeError.message?.contains("generator.yml 不允许声明 type"))

        val rootMissingProvider = tempAssetsRoot()
        writeVersion(rootMissingProvider)
        writeGenerator(rootMissingProvider, "card")
        Assets.configure(AssetsConfig(rootMissingProvider))

        val registrationError = assertFailsWith<IllegalStateException> {
            ResourceCommandLoader(emptyMap(), emptyMap()).load()
        }
        assertEquals(true, registrationError.message?.contains("generator 缺少 KSP 注册项: card"))
    }

    private class NoopHandler(
        override val name: String,
    ) : FramesHandler {
        override val regex: Regex = Regex(name)

        override suspend fun handleFrames(
            frames: MutableList<Frame>,
            args: MutableMap<String, String>,
        ): HandleResult = frames.result { this }
    }

    private object NoopGenerator : FramesGenerator {
        override suspend fun generate(args: MutableMap<String, String>): MutableList<Frame> =
            mutableListOf()
    }

    private fun tempAssetsRoot(): Path =
        Files.createTempDirectory("tavolo-registry-test-").also {
            it.resolve("handlers").createDirectories()
            it.resolve("generators").createDirectories()
        }

    private fun writeVersion(root: Path) {
        root.resolve("version.yml").writeText(
            """
            version: 1.2.3
            time: 2026-05-13
            """.trimIndent()
        )
    }

    private fun writeHandler(
        root: Path,
        directory: String,
        id: String = directory,
        type: String = "kotlin",
        name: String = id,
        regex: String = id,
        version: Int = 1,
    ) {
        val dir = root.resolve("handlers").resolve(directory).also { it.createDirectories() }
        dir.resolve("handler.yml").writeText(
            """
            id: $id
            type: $type
            version: $version
            name: $name
            regex: $regex
            """.trimIndent()
        )
    }

    private fun writeGenerator(
        root: Path,
        directory: String,
        id: String = directory,
        name: String = id,
        regex: String = id,
        version: Int = 1,
        extra: String = "",
    ) {
        val dir = root.resolve("generators").resolve(directory).also { it.createDirectories() }
        dir.resolve("generator.yml").writeText(
            """
            id: $id
            version: $version
            name: $name
            regex: $regex
            $extra
            """.trimIndent()
        )
    }
}
