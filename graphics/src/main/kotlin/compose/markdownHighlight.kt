package top.e404.tavolo.draw.compose

import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxTheme
import org.jetbrains.skia.Color

/** 代码高亮在 JVM 内完成；未识别的语言按普通代码显示。 */
internal object MarkdownCodeHighlighter {
    private val lightCode = SyntaxTheme.basic("tavolo-light", 0x1f2328, 0x0a3069, 0xcf222e, 0x0550ae, 0x59636e)
    private val darkCode = SyntaxTheme.basic("tavolo-dark", 0xe6edf3, 0xa5d6ff, 0xff7b72, 0x79c0ff, 0x9198a1)

    fun highlight(source: String, language: String, dark: Boolean): AnnotatedText {
        val name = when (language.lowercase()) {
            "kt", "kts" -> "kotlin"
            "js", "json", "jsonc" -> "javascript"
            "ts" -> "typescript"
            "py" -> "python"
            "sh", "bash", "zsh" -> "shell"
            "c++", "cxx" -> "cpp"
            "c#", "cs" -> "csharp"
            "rb" -> "ruby"
            "rs" -> "rust"
            "coffee" -> "coffeescript"
            else -> language
        }
        val syntax = SyntaxLanguage.getByName(name) ?: return AnnotatedText(source)
        val highlights = Highlights.Builder().code(source).language(syntax)
            .theme(if (dark) darkCode else lightCode).build().getHighlights()
        val styles = mutableMapOf<Int, TextSpanStyle>()
        return AnnotatedText(source, highlights.filterIsInstance<ColorHighlight>().map { token ->
            val color = token.rgb or Color.BLACK
            TextRange(styles.getOrPut(color) { TextSpanStyle(textColor = color) }, token.location.start, token.location.end)
        })
    }
}
