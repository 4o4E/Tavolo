package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.junit.Test
import top.e404.tavolo.draw.compose.*
import java.io.File

/** 使用公开库入口生成静态格式样例，检查文字、图片和布局边界。 */
class GithubMarkdownManualTest {
    @Test
    fun renderMarkdownFormattingExamples() {
        val source = requireNotNull(javaClass.getResource("/issue7/github.md")).readText()
        val inlineSource = requireNotNull(javaClass.getResource("/issue7/inline-layout.md")).readText()
        val style = MarkdownStyle(fontFamily = ManualTestSupport.uiFont, codeFontFamily = ManualTestSupport.monoFont)
        val context = MarkdownContext(referenceResolver = { reference -> when {
            reference.startsWith("@") -> "https://github.com/${reference.drop(1)}"
            reference.startsWith("#") -> "https://github.com/4o4E/Tavolo/issues/${reference.drop(1)}"
            else -> null
        } })
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
                    val png = renderMarkdownPng(source, width, theme, resolver, context)
                    File("out/issue7/$name.png").apply { parentFile.mkdirs(); writeBytes(png) }
                    val detail = renderMarkdownPng(inlineSource, minOf(width, 640f), theme, resolver, context)
                    File("out/issue7/$name-inline.png").writeBytes(detail)
                }
                render(Color.WHITE, pixelRatio = 2f) {
                    column(Modifier.padding(12f)) {
                        text("已有 Compose 布局", fontFamily = style.fontFamily, fontSize = 24f, textColor = Color.BLACK)
                        markdown("## 简单 Markdown\n\n中文、**粗体**和 `inline code`。\n\n- 第一项\n- 第二项",
                            600f, style)
                    }
                }.use { ManualTestSupport.saveImage("issue7/github-embedded.png", it) }
            }
    }
}
