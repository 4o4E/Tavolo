package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.junit.Assume.assumeTrue
import org.junit.Test
import top.e404.tavolo.draw.compose.AnnotatedText
import top.e404.tavolo.draw.compose.Modifier
import top.e404.tavolo.draw.compose.TextModifier
import top.e404.tavolo.draw.compose.TextOverflow
import top.e404.tavolo.draw.compose.TextRange
import top.e404.tavolo.draw.compose.TextSpanStyle
import top.e404.tavolo.draw.compose.UiElement
import top.e404.tavolo.draw.compose.VerticalAlignment
import top.e404.tavolo.draw.compose.background
import top.e404.tavolo.draw.compose.bold
import top.e404.tavolo.draw.compose.border
import top.e404.tavolo.draw.compose.box
import top.e404.tavolo.draw.compose.column
import top.e404.tavolo.draw.compose.font
import top.e404.tavolo.draw.compose.lineHeight
import top.e404.tavolo.draw.compose.padding
import top.e404.tavolo.draw.compose.row
import top.e404.tavolo.draw.compose.size
import top.e404.tavolo.draw.compose.sizeIn
import top.e404.tavolo.draw.compose.text
import top.e404.tavolo.util.FontManager
import java.io.File

class ComposeTextFallbackManualTest {
    private val uiFont = ManualTestSupport.uiFont
    private val titleText = TextModifier.font(30f, ink, uiFont).bold()
    private val cardTitleText = TextModifier.font(21f, ink, uiFont).bold()
    private val bodyText = TextModifier.font(18f, muted, uiFont).lineHeight(28f)
    private val largeText = TextModifier.font(54f, ink, uiFont).lineHeight(70f)

    @Test
    fun test_compose_text_grapheme_cluster_fallback_overview() = withClusterFallback {
        ManualTestSupport.saveCompose("文本-fallback-01-grapheme-cluster-综合") {
            page("grapheme cluster fallback", "组合包围符号、emoji 序列和区域符号的基础覆盖") {
                row(verticalAlignment = VerticalAlignment.Top) {
                    panel("enclosing mark", 370f, 230f) {
                        text(
                            "୧⍤⃝   A⃝   1⃝",
                            modifier = Modifier.padding(top = 20f),
                            textModifier = largeText
                        )
                        text(
                            "重点看圆圈是否贴在基字上，而不是漂到右侧。",
                            modifier = Modifier.padding(top = 18f).sizeIn(maxWidth = 300f),
                            textModifier = bodyText
                        )
                    }
                    panel("emoji / flag / accent", 440f, 230f, Modifier.padding(left = 22f)) {
                        text(
                            "👨‍👩‍👧‍👦   🇨🇳   👍🏽   ❤️   é",
                            modifier = Modifier.padding(top = 26f).sizeIn(maxWidth = 370f),
                            textModifier = TextModifier.font(33f, ink, uiFont).lineHeight(50f)
                        )
                        text(
                            "ZWJ emoji、regional indicator、emoji modifier 和 combining mark 都不能按 Char 拆开。",
                            modifier = Modifier.padding(top = 18f).sizeIn(maxWidth = 350f),
                            textModifier = bodyText
                        )
                    }
                }
            }
        }
    }

    @Test
    fun test_compose_text_grapheme_cluster_fallback_layout() = withClusterFallback {
        val annotatedSample = "AnnotatedText 样式落在 cluster 内部：A⃝B ୧⍤⃝"
        val enclosingMarkIndex = annotatedSample.indexOf('\u20DD')

        ManualTestSupport.saveCompose("文本-fallback-02-换行省略和行内样式") {
            page("cluster-aware layout", "换行、省略号和 AnnotatedText span 都按 cluster 边界处理", 1120f, 500f) {
                row(verticalAlignment = VerticalAlignment.Top) {
                    panel("wrap", 310f, 290f) {
                        text(
                            "A⃝ B⃝ ୧⍤⃝ C⃝ D⃝ 组合符号在窄宽度内换行",
                            modifier = Modifier.padding(top = 18f).sizeIn(maxWidth = 220f),
                            textModifier = TextModifier.font(29f, ink, uiFont).lineHeight(42f),
                            textOverflow = TextOverflow.Wrap
                        )
                    }
                    panel("ellipsis", 310f, 290f, Modifier.padding(left = 22f)) {
                        text(
                            "Ellipsis: ୧⍤⃝ A⃝ B⃝ ABCDEFG",
                            modifier = Modifier.padding(top = 24f).sizeIn(maxWidth = 230f),
                            textModifier = TextModifier.font(30f, ink, uiFont),
                            textOverflow = TextOverflow.Ellipsis
                        )
                        text(
                            "省略号前不能留下半个组合符号。",
                            modifier = Modifier.padding(top = 24f).sizeIn(maxWidth = 230f),
                            textModifier = bodyText
                        )
                    }
                    panel("AnnotatedText", 340f, 290f, Modifier.padding(left = 22f)) {
                        text(
                            AnnotatedText(
                                text = annotatedSample,
                                spanStyles = listOf(
                                    TextRange(
                                        TextSpanStyle(textColor = red),
                                        enclosingMarkIndex,
                                        enclosingMarkIndex + 1
                                    )
                                )
                            ),
                            modifier = Modifier.padding(top = 18f).sizeIn(maxWidth = 260f),
                            fontSize = 24f,
                            textColor = muted,
                            fontFamily = uiFont
                        )
                        text(
                            "样式命中组合圈时，应扩展到整个 A⃝ cluster。",
                            modifier = Modifier.padding(top = 22f).sizeIn(maxWidth = 260f),
                            textModifier = bodyText
                        )
                    }
                }
            }
        }
    }

    @Test
    fun test_compose_text_grapheme_cluster_fallback_candidates() = withClusterFallback {
        ManualTestSupport.saveCompose("文本-fallback-03-字体候选和混排边界") {
            page("font fallback candidates", "只在风险 cluster 需要同字体成形时介入，普通中文和英文仍保持主字体", 1160f, 460f) {
                row(verticalAlignment = VerticalAlignment.Top) {
                    panel("primary text", 320f, 260f) {
                        text(
                            "普通中文 English 123",
                            modifier = Modifier.padding(top = 20f).sizeIn(maxWidth = 250f),
                            textModifier = TextModifier.font(29f, ink, uiFont).lineHeight(42f)
                        )
                        text(
                            "这行没有强制切到符号字体，用来观察主字体风格是否保持。",
                            modifier = Modifier.padding(top = 22f).sizeIn(maxWidth = 250f),
                            textModifier = bodyText
                        )
                    }
                    panel("cluster fallback", 320f, 260f, Modifier.padding(left = 22f)) {
                        text(
                            "୧⍤⃝ A⃝ 1⃝",
                            modifier = Modifier.padding(top = 22f),
                            textModifier = TextModifier.font(48f, ink, uiFont).lineHeight(62f)
                        )
                        text(
                            "FreeMono 优先用于 enclosing mark；若字体不能让圆圈附着，会继续尝试其他候选。",
                            modifier = Modifier.padding(top = 18f).sizeIn(maxWidth = 255f),
                            textModifier = bodyText
                        )
                    }
                    panel("mixed paragraph", 340f, 260f, Modifier.padding(left = 22f)) {
                        text(
                            "收益率 A⃝ +12.5%，风险 ୧⍤⃝；emoji 😀 和国旗 🇨🇳 保持完整。",
                            modifier = Modifier.padding(top = 18f).sizeIn(maxWidth = 270f),
                            textModifier = TextModifier.font(25f, ink, uiFont).lineHeight(38f),
                            textOverflow = TextOverflow.Wrap
                        )
                    }
                }
            }
        }
    }

    private fun withClusterFallback(block: () -> Unit) {
        val fallbackFile = File("font/FreeMono.ttf")
        assumeTrue("缺少 run/font/FreeMono.ttf，跳过 grapheme cluster 字体回退人工测试", fallbackFile.exists())

        val previous = FontManager.graphemeClusterFallbackFamilies
        val fallbackFont = FontManager.registerFile("manual-compose-cluster-fallback", fallbackFile)
        try {
            FontManager.graphemeClusterFallbackFamilies = listOf(fallbackFont)
            block()
        } finally {
            FontManager.graphemeClusterFallbackFamilies = previous
        }
    }

    private fun UiElement.page(
        title: String,
        subtitle: String,
        width: Float = 900f,
        height: Float = 420f,
        content: UiElement.() -> Unit
    ) {
        box(
            modifier = Modifier
                .size(width, height)
                .background(pageBg)
                .padding(40f)
        ) {
            column {
                text(title, textModifier = titleText)
                text(
                    subtitle,
                    modifier = Modifier.padding(top = 8f, bottom = 26f).sizeIn(maxWidth = width - 100f),
                    textModifier = bodyText
                )
                content()
            }
        }
    }

    private fun UiElement.panel(
        title: String,
        width: Float,
        height: Float,
        modifier: Modifier = Modifier,
        content: UiElement.() -> Unit
    ) {
        box(
            modifier = modifier
                .size(width, height)
                .background(Color.WHITE)
                .border(1f, borderColor)
                .padding(24f)
        ) {
            column {
                text(title, textModifier = cardTitleText)
                content()
            }
        }
    }

    private companion object {
        private val pageBg = Color.makeRGB(248, 250, 252)
        private val ink = Color.makeRGB(17, 24, 39)
        private val muted = Color.makeRGB(75, 85, 99)
        private val borderColor = Color.makeRGB(209, 218, 230)
        private val red = Color.makeRGB(220, 38, 38)
    }
}
