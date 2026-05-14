package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import org.junit.Test
import top.e404.tavolo.draw.compose.Composable
import top.e404.tavolo.draw.compose.HorizontalAlignment
import top.e404.tavolo.draw.compose.IconTheme
import top.e404.tavolo.draw.compose.Modifier
import top.e404.tavolo.draw.compose.Shape
import top.e404.tavolo.draw.compose.UiDsl
import top.e404.tavolo.draw.compose.UiElement
import top.e404.tavolo.draw.compose.VerticalAlignment
import top.e404.tavolo.draw.compose.background
import top.e404.tavolo.draw.compose.bold
import top.e404.tavolo.draw.compose.border
import top.e404.tavolo.draw.compose.box
import top.e404.tavolo.draw.compose.clip
import top.e404.tavolo.draw.compose.column
import top.e404.tavolo.draw.compose.debugBaseElement
import top.e404.tavolo.draw.compose.font
import top.e404.tavolo.draw.compose.icon
import top.e404.tavolo.draw.compose.iconText
import top.e404.tavolo.draw.compose.image
import top.e404.tavolo.draw.compose.padding
import top.e404.tavolo.draw.compose.row
import top.e404.tavolo.draw.compose.size
import top.e404.tavolo.draw.compose.sizeIn
import top.e404.tavolo.draw.compose.text
import top.e404.tavolo.draw.compose.TextModifier

class ComposeComponentManualTest {
    private val uiFont = ManualTestSupport.uiFont
    private val avatars = ManualTestSupport.drawnAvatars(8, 72, 72)
    private val titleText = TextModifier.font(fontSize = 30f, textColor = Color.WHITE, fontFamily = uiFont).bold()
    private val bodyText = TextModifier.font(fontSize = 18f, textColor = ink, fontFamily = uiFont)
    private val mutedText = TextModifier.font(fontSize = 15f, textColor = muted, fontFamily = uiFont)

    private val cards = listOf(
        UserCard(avatars[0], "Base64", "编码状态正常，最近一次输出耗时 12 ms", blue),
        UserCard(avatars[1], "Sha256", "摘要任务完成，结果已写入缓存", green),
        UserCard(avatars[2], "URL Encode", "包含中文与空格的参数已完成转义", yellow),
        UserCard(avatars[3], "HTML Decode", "实体字符已恢复为可读文本", red),
        UserCard(avatars[4], "Unicode", "长文本保留两行显示，超出部分受尺寸约束", purple),
        UserCard(avatars[5], "Markdown", "预览内容使用统一卡片样式展示", cyan)
    )

    private fun testCompose(name: String, content: Composable) {
        val root = ManualTestSupport.saveCompose(name, content)
        buildString {
            debugBaseElement(0, root, this)
        }.let {
            println(it)
        }
    }

    @Test
    fun test_compose() {
        testCompose("基础-01-组合卡片列表") {
            page("基础组件", "卡片列表、状态标签和按钮组合，使用本地绘图头像") {
                row(verticalAlignment = VerticalAlignment.Top) {
                    column {
                        cards.take(3).forEachIndexed { index, card ->
                            profileCard(index + 1, card)
                        }
                    }
                    column(modifier = Modifier.padding(left = 18f)) {
                        cards.drop(3).forEachIndexed { index, card ->
                            profileCard(index + 4, card)
                        }
                    }
                    column(modifier = Modifier.padding(left = 24f)) {
                        summaryPanel()
                    }
                }
            }
        }
    }

    @Test
    fun test_icon_text() {
        testCompose("组件-01-图标与图标文本") {
            page("图标组件", "SVG icon、iconText 和状态徽标合并到同一张组件测试图", width = 1120f, height = 520f) {
                row(verticalAlignment = VerticalAlignment.Top) {
                    componentCard("图标按钮", 330f, 330f) {
                        row(modifier = Modifier.padding(top = 18f), verticalAlignment = VerticalAlignment.Center) {
                            iconBadge(blue, arrowSvg)
                            iconBadge(green, checkSvg)
                            iconBadge(yellow, starSvg)
                        }
                        row(modifier = Modifier.padding(top = 24f), verticalAlignment = VerticalAlignment.Center) {
                            iconBadge(red, syncSvg)
                            iconBadge(purple, boltSvg)
                            iconBadge(cyan, layersSvg)
                        }
                    }
                    componentCard("图标文本", 360f, 330f, modifier = Modifier.padding(left = 22f)) {
                        iconText(
                            "构建成功",
                            fontSize = 28f,
                            modifier = Modifier.padding(top = 22f),
                            textModifier = TextModifier.font(textColor = ink, fontFamily = uiFont).bold(),
                            iconColor = green
                        )
                        iconText(
                            "等待同步",
                            fontSize = 24f,
                            modifier = Modifier.padding(top = 22f),
                            textModifier = TextModifier.font(textColor = ink, fontFamily = uiFont),
                            iconColor = yellow
                        )
                        iconText(
                            "需要检查",
                            fontSize = 24f,
                            modifier = Modifier.padding(top = 22f),
                            textModifier = TextModifier.font(textColor = ink, fontFamily = uiFont),
                            iconColor = red
                        )
                    }
                    componentCard("组合状态", 330f, 330f, modifier = Modifier.padding(left = 22f)) {
                        statusRow(green, checkSvg, "通过", "12 个组件")
                        statusRow(yellow, syncSvg, "排队", "3 个任务")
                        statusRow(red, boltSvg, "阻塞", "1 个告警")
                    }
                }
            }
        }
    }

    @UiDsl
    private fun UiElement.page(title: String, subtitle: String, width: Float = 1180f, height: Float = 650f, block: UiElement.() -> Unit) {
        box(
            modifier = Modifier
                .size(width, height)
                .background(bg)
                .padding(28f)
        ) {
            column {
                text(title, textModifier = titleText)
                text(subtitle, modifier = Modifier.padding(top = 8f, bottom = 24f), textModifier = TextModifier.font(17f, muted, uiFont))
                block()
            }
        }
    }

    @UiDsl
    private fun UiElement.profileCard(index: Int, card: UserCard) {
        row(
            verticalAlignment = VerticalAlignment.Center,
            modifier = Modifier
                .padding(bottom = 14f)
                .size(390f, 132f)
                .clip(Shape.RoundedRect(14f))
                .background(surface)
                .border(1.5f, border, shape = Shape.RoundedRect(14f))
                .padding(18f)
        ) {
            box(
                modifier = Modifier
                    .size(46f)
                    .clip(Shape.Circle)
                    .background(card.color),
                horizontalAlignment = HorizontalAlignment.Center,
                verticalAlignment = VerticalAlignment.Center
            ) {
                text(index.toString(), textModifier = TextModifier.font(18f, Color.WHITE, uiFont).bold())
            }
            image(
                image = card.avatar,
                modifier = Modifier
                    .padding(left = 14f, right = 14f)
                    .sizeIn(maxWidth = 62f, maxHeight = 62f)
                    .clip(Shape.Circle)
            )
            column {
                text(card.name, textModifier = TextModifier.font(24f, ink, uiFont).bold())
                text(card.description, modifier = Modifier.padding(top = 8f).sizeIn(maxWidth = 210f, maxHeight = 48f), textModifier = mutedText)
            }
        }
    }

    @UiDsl
    private fun UiElement.summaryPanel() {
        componentCard("操作区", 300f, 470f) {
            text("批处理队列", modifier = Modifier.padding(top = 18f), textModifier = TextModifier.font(24f, ink, uiFont).bold())
            text("本图只保留基础组件组合，布局、文本、Modifier 和图表细节由主题测试覆盖。", modifier = Modifier.padding(top = 12f).sizeIn(maxWidth = 236f), textModifier = bodyText)
            row(modifier = Modifier.padding(top = 26f), verticalAlignment = VerticalAlignment.Center) {
                metricPill(green, "成功", "18")
                metricPill(yellow, "等待", "4")
            }
            row(modifier = Modifier.padding(top = 12f), verticalAlignment = VerticalAlignment.Center) {
                metricPill(red, "失败", "1")
                metricPill(blue, "总数", "23")
            }
            box(
                modifier = Modifier
                    .padding(top = 28f)
                    .size(236f, 58f)
                    .clip(Shape.RoundedRect(12f))
                    .background(blue),
                horizontalAlignment = HorizontalAlignment.Center,
                verticalAlignment = VerticalAlignment.Center
            ) {
                text("执行操作", textModifier = TextModifier.font(22f, Color.WHITE, uiFont).bold())
            }
        }
    }

    @UiDsl
    private fun UiElement.componentCard(title: String, width: Float, height: Float, modifier: Modifier = Modifier, block: UiElement.() -> Unit) {
        column(
            modifier = modifier
                .size(width, height)
                .clip(Shape.RoundedRect(14f))
                .background(surface)
                .border(1.5f, border, shape = Shape.RoundedRect(14f))
                .padding(22f)
        ) {
            text(title, textModifier = TextModifier.font(22f, ink, uiFont).bold())
            block()
        }
    }

    @UiDsl
    private fun UiElement.metricPill(color: Int, label: String, value: String) {
        column(
            modifier = Modifier
                .padding(right = 10f)
                .size(106f, 70f)
                .clip(Shape.RoundedRect(12f))
                .background(color)
                .padding(10f),
            horizontalAlignment = HorizontalAlignment.Center
        ) {
            text(value, textModifier = TextModifier.font(24f, Color.WHITE, uiFont).bold())
            text(label, modifier = Modifier.padding(top = 3f), textModifier = TextModifier.font(14f, Color.WHITE, uiFont))
        }
    }

    @UiDsl
    private fun UiElement.iconBadge(color: Int, svg: String) {
        box(
            modifier = Modifier
                .padding(right = 16f)
                .size(74f)
                .clip(Shape.RoundedRect(16f))
                .background(color),
            horizontalAlignment = HorizontalAlignment.Center,
            verticalAlignment = VerticalAlignment.Center
        ) {
            icon(IconTheme(size = 42f, color = Color.WHITE), svg)
        }
    }

    @UiDsl
    private fun UiElement.statusRow(color: Int, svg: String, label: String, value: String) {
        row(
            modifier = Modifier
                .padding(top = 20f)
                .size(246f, 64f)
                .clip(Shape.RoundedRect(12f))
                .background(Color.makeRGB(244, 247, 252))
                .padding(12f),
            verticalAlignment = VerticalAlignment.Center
        ) {
            box(
                modifier = Modifier
                    .size(40f)
                    .clip(Shape.RoundedRect(10f))
                    .background(color),
                horizontalAlignment = HorizontalAlignment.Center,
                verticalAlignment = VerticalAlignment.Center
            ) {
                icon(IconTheme(size = 24f, color = Color.WHITE), svg)
            }
            column(modifier = Modifier.padding(left = 12f)) {
                text(label, textModifier = TextModifier.font(18f, ink, uiFont).bold())
                text(value, modifier = Modifier.padding(top = 4f), textModifier = mutedText)
            }
        }
    }

    private data class UserCard(
        val avatar: Image,
        val name: String,
        val description: String,
        val color: Int
    )

    private companion object {
        val bg: Int = Color.makeRGB(27, 34, 46)
        val surface: Int = Color.makeRGB(255, 255, 255)
        val border: Int = Color.makeRGB(216, 224, 236)
        val ink: Int = Color.makeRGB(39, 49, 66)
        val muted: Int = Color.makeRGB(105, 119, 140)
        val blue: Int = Color.makeRGB(48, 104, 255)
        val green: Int = Color.makeRGB(41, 178, 123)
        val yellow: Int = Color.makeRGB(245, 177, 48)
        val red: Int = Color.makeRGB(229, 83, 72)
        val purple: Int = Color.makeRGB(126, 91, 220)
        val cyan: Int = Color.makeRGB(35, 167, 197)

        const val arrowSvg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960"><path d="M504-480 320-664l56-56 240 240-240 240-56-56 184-184Z"/></svg>"""
        const val checkSvg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960"><path d="M382-240 154-468l57-57 171 171 367-367 57 57-424 424Z"/></svg>"""
        const val starSvg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960"><path d="m233-80 65-281L80-550l288-25 112-265 112 265 288 25-218 189 65 281-247-149L233-80Z"/></svg>"""
        const val syncSvg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960"><path d="M204-318q-22-38-33-78t-11-82q0-134 93-228t227-94h7l-64-64 56-56 160 160-160 160-56-56 64-64h-7q-100 0-170 70t-70 170q0 28 7 54t21 50l-64 58Zm277 278L321-200l160-160 56 56-64 64h7q100 0 170-70t70-170q0-28-7-54t-21-50l64-58q22 38 33 78t11 82q0 134-93 228T480-160h-7l64 64-56 56Z"/></svg>"""
        const val boltSvg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960"><path d="m422-232 207-248H469l29-227-185 267h139l-30 208Zm-92 152 40-280H160l360-520h80l-40 320h240L410-80h-80Z"/></svg>"""
        const val layersSvg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960"><path d="M480-118 120-318l80-44 280 156 280-156 80 44-360 200Zm0-160L120-478l360-200 360 200-360 200Zm0-92 192-108-192-108-192 108 192 108Zm0-388L200-602l-80-44 360-200 360 200-80 44-280-156Z"/></svg>"""
    }
}
