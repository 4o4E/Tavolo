package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.junit.Test
import top.e404.tavolo.draw.compose.HorizontalAlignment
import top.e404.tavolo.draw.compose.Modifier
import top.e404.tavolo.draw.compose.Shape
import top.e404.tavolo.draw.compose.TextModifier
import top.e404.tavolo.draw.compose.UiDsl
import top.e404.tavolo.draw.compose.UiElement
import top.e404.tavolo.draw.compose.VerticalAlignment
import top.e404.tavolo.draw.compose.background
import top.e404.tavolo.draw.compose.bold
import top.e404.tavolo.draw.compose.border
import top.e404.tavolo.draw.compose.box
import top.e404.tavolo.draw.compose.clip
import top.e404.tavolo.draw.compose.column
import top.e404.tavolo.draw.compose.font
import top.e404.tavolo.draw.compose.padding
import top.e404.tavolo.draw.compose.row
import top.e404.tavolo.draw.compose.size
import top.e404.tavolo.draw.compose.sizeIn
import top.e404.tavolo.draw.compose.text
import top.e404.tavolo.draw.compose.waterfall
import top.e404.tavolo.draw.compose.width

class ComposeWaterfallManualTest {
    private val uiFont = ManualTestSupport.uiFont
    private val titleText = TextModifier.font(34f, ink, uiFont).bold()
    private val bodyText = TextModifier.font(17f, muted, uiFont)
    private val cardTitleText = TextModifier.font(22f, ink, uiFont).bold()
    private val cardBodyText = TextModifier.font(16f, muted, uiFont)

    private val cards = listOf(
        WaterfallCard("发布检查", "运行测试并同步 README 示例图。", blue),
        WaterfallCard("布局迁移", "瀑布流会先测量每张卡片，再把卡片放入当前累计高度最短的列。", green),
        WaterfallCard("列宽约束", "子项会被限制到 columnWidth，带 padding、border 和圆角背景的卡片也不会撑出列宽。", purple),
        WaterfallCard("图片摘要", "短文本卡片只占用少量纵向空间，后续卡片会更快补到这一列。", yellow),
        WaterfallCard("文档示例", "README 图由本人工测试生成。修改样式、文案或布局策略后，重跑人工测试即可得到新的示例图，再同步到 docs/assets/readme。", red),
        WaterfallCard("运行输出", "目标文件输出到 run/out/compose，再同步到 docs/assets/readme，方便 README 直接引用真实渲染结果。", cyan),
        WaterfallCard("组合能力", "卡片内部仍然可以使用 box、row、column、text、image 等普通组件。描述文本越长，换行越多，卡片自然高度越高。", orange),
        WaterfallCard("最短列策略", "不同文本长度会形成不同卡片高度，瀑布流用最短列策略减少空洞。", slate),
    )

    @Test
    fun test_readme_waterfall_layout() {
        ManualTestSupport.saveCompose("README-02-Waterfall-Layout") {
            box(
                modifier = Modifier
                    .size(1100f, 760f)
                    .background(bg)
                    .padding(38f)
            ) {
                column {
                    text("WaterfallLayout 瀑布流布局", textModifier = titleText)
                    text(
                        "固定列数与总宽度，子项按测量后的高度放入当前最短列。",
                        modifier = Modifier.padding(top = 8f, bottom = 24f),
                        textModifier = bodyText
                    )
                    waterfall(columns = 3, width = 1002f, columnSpacing = 18f, rowSpacing = 18f) {
                        cards.forEachIndexed { index, card ->
                            waterfallCard(index + 1, card, columnWidth)
                        }
                    }
                }
            }
        }
    }

    @UiDsl
    private fun UiElement.waterfallCard(index: Int, card: WaterfallCard, width: Float) {
        column(
            modifier = Modifier
                .width(width)
                .clip(Shape.RoundedRect(18f))
                .background(surface)
                .border(1.4f, border, shape = Shape.RoundedRect(18f))
                .padding(18f)
        ) {
            row(verticalAlignment = VerticalAlignment.Center) {
                box(
                    modifier = Modifier
                        .size(42f)
                        .clip(Shape.RoundedRect(12f))
                        .background(card.color),
                    horizontalAlignment = HorizontalAlignment.Center,
                    verticalAlignment = VerticalAlignment.Center
                ) {
                    text(index.toString(), textModifier = TextModifier.font(18f, Color.WHITE, uiFont).bold())
                }
                column(modifier = Modifier.padding(left = 14f)) {
                    text(card.title, textModifier = cardTitleText)
                    text(
                        "文本 ${card.description.length} 字",
                        modifier = Modifier.padding(top = 4f),
                        textModifier = TextModifier.font(14f, card.color, uiFont).bold()
                    )
                }
            }
            text(
                card.description,
                modifier = Modifier
                    .padding(top = 16f)
                    .sizeIn(maxWidth = width - 36f),
                textModifier = cardBodyText
            )
            box(
                modifier = Modifier
                    .padding(top = 18f)
                    .size(width - 36f, 8f)
                    .clip(Shape.RoundedRect(4f))
                    .background(card.color)
            )
        }
    }

    private data class WaterfallCard(
        val title: String,
        val description: String,
        val color: Int
    )

    private companion object {
        private val bg = Color.makeRGB(244, 247, 249)
        private val surface = Color.WHITE
        private val border = Color.makeRGB(218, 225, 231)
        private val ink = Color.makeRGB(34, 43, 53)
        private val muted = Color.makeRGB(93, 108, 123)
        private val blue = Color.makeRGB(49, 106, 197)
        private val green = Color.makeRGB(38, 145, 115)
        private val purple = Color.makeRGB(127, 91, 186)
        private val yellow = Color.makeRGB(190, 132, 32)
        private val red = Color.makeRGB(201, 83, 72)
        private val cyan = Color.makeRGB(37, 143, 168)
        private val orange = Color.makeRGB(214, 104, 42)
        private val slate = Color.makeRGB(79, 95, 119)
    }
}
