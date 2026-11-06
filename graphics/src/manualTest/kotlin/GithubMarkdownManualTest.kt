package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.junit.Test
import top.e404.tavolo.draw.compose.*
import java.io.File
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 使用实际官方引擎和公开库入口，不通过 mock 验证图形结果。 */
class GithubMarkdownManualTest {
    @Test(timeout = 10000)
    fun timeoutStopsWorkerThatDoesNotReadLargeRequests() {
        val directory = java.nio.file.Files.createTempDirectory("tavolo-markdown-timeout")
        try {
            directory.resolve("worker.mjs").toFile().writeText("setInterval(() => {}, 1000);")
            NodeMarkdownRuntime(directory, timeout = java.time.Duration.ofMillis(500)).use { runtime ->
                val error = assertFailsWith<IllegalStateException> { runtime.highlight("x".repeat(16 * 1024 * 1024), "text") }
                assertTrue(error.message.orEmpty().contains("超时"))
            }
        } finally {
            directory.resolve("worker.mjs").toFile().delete()
            java.nio.file.Files.delete(directory)
        }
    }

    @Test
    fun renderGithubMarkdownWithOfficialEngines() {
        val source = requireNotNull(javaClass.getResource("/issue7/github.md")).readText()
        val inlineSource = requireNotNull(javaClass.getResource("/issue7/inline-layout.md")).readText()
        val style = MarkdownStyle(fontFamily = ManualTestSupport.uiFont, codeFontFamily = ManualTestSupport.monoFont)
        val context = MarkdownContext(referenceResolver = { reference -> when {
            reference.startsWith("@") -> "https://github.com/${reference.drop(1)}"
            reference.startsWith("#") -> "https://github.com/4o4E/Tavolo/issues/${reference.drop(1)}"
            else -> null
        } })
        NodeMarkdownRuntime(File("../graphics/markdown-runtime").toPath()).use { runtime ->
            ManualTestSupport.drawnAvatar(2, 160, 100).use { avatar ->
                val resolver: MarkdownImageResolver = { destination ->
                    require(destination in setOf("fixture:avatar", "fixture:light", "fixture:dark")) { "未知离线资源: $destination" }
                    avatar
                }
                for ((name, width, theme) in listOf(
                    Triple("github", 800f, style),
                    Triple("github-narrow", 480f, style.copy(pagePadding = 16f)),
                    Triple("github-dark", 800f, style.copy(darkTheme = true, backgroundColor = Color.makeRGB(13, 17, 23),
                        textColor = Color.makeRGB(240, 246, 252), linkColor = Color.makeRGB(68, 147, 248),
                        codeBackgroundColor = Color.makeRGB(21, 27, 35), quoteColor = Color.makeRGB(61, 68, 77),
                        tableBorderColor = Color.makeRGB(61, 68, 77)))
                )) {
                    val png = renderMarkdownPng(source, width, theme, resolver, runtime, context)
                    File("out/issue7/$name.png").apply { parentFile.mkdirs(); writeBytes(png) }
                    val detail = renderMarkdownPng(inlineSource, minOf(width, 640f), theme, resolver, runtime, context)
                    File("out/issue7/$name-inline.png").writeBytes(detail)
                }
                render(Color.WHITE, pixelRatio = 2f) {
                    column(Modifier.padding(12f)) {
                        text("已有 Compose 布局", fontFamily = style.fontFamily, fontSize = 24f, textColor = Color.BLACK)
                        markdown("文字 ${'$'}\\frac{1}{2}${'$'} 文字\n\n```mermaid\nflowchart LR\n A[输入] --> B[输出]\n```",
                            600f, style, runtime = runtime)
                    }
                }.use { ManualTestSupport.saveImage("issue7/github-embedded.png", it) }
            }
            assertFailsWith<IllegalStateException> { runtime.math("\\unknownTavoloCommand", false, 20f, Color.BLACK) }
            assertFailsWith<IllegalStateException> { runtime.mermaid("not-a-diagram", 400f, style.fontFamily, false) }
            assertFailsWith<IllegalStateException> { runtime.math("\\require{missingPackage}x", true, 20f, Color.BLACK) }
            val blocked = assertFailsWith<IllegalStateException> {
                runtime.mermaid("flowchart LR\n A@{ img: \"https://tavolo-resource.invalid/image.png\", label: \"资源\" }",
                    400f, style.fontFamily, false)
            }
            assertTrue(blocked.message.orEmpty().contains("外部资源"))
            val valid = runtime.math("x^2", false, 20f, Color.BLACK)
            assertTrue(valid.baseline > 0 && valid.baseline <= valid.height)
        }
    }
}
