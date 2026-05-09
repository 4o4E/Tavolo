package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.junit.Test
import top.e404.tavolo.draw.compose.HorizontalAlignment
import top.e404.tavolo.draw.compose.Modifier
import top.e404.tavolo.draw.compose.Shape
import top.e404.tavolo.draw.compose.StrokeStyle
import top.e404.tavolo.draw.compose.TextStyle
import top.e404.tavolo.draw.compose.TextModifier
import top.e404.tavolo.draw.compose.TextUnderline
import top.e404.tavolo.draw.compose.TextUnderlineMode
import top.e404.tavolo.draw.compose.UiDsl
import top.e404.tavolo.draw.compose.UiElement
import top.e404.tavolo.draw.compose.VerticalAlignment
import top.e404.tavolo.draw.compose.background
import top.e404.tavolo.draw.compose.border
import top.e404.tavolo.draw.compose.box
import top.e404.tavolo.draw.compose.bold
import top.e404.tavolo.draw.compose.clip
import top.e404.tavolo.draw.compose.column
import top.e404.tavolo.draw.compose.font
import top.e404.tavolo.draw.compose.italic
import top.e404.tavolo.draw.compose.letterSpacing
import top.e404.tavolo.draw.compose.lineHeight
import top.e404.tavolo.draw.compose.padding
import top.e404.tavolo.draw.compose.rotate
import top.e404.tavolo.draw.compose.row
import top.e404.tavolo.draw.compose.shadow
import top.e404.tavolo.draw.compose.size
import top.e404.tavolo.draw.compose.sizeIn
import top.e404.tavolo.draw.compose.scaleX
import top.e404.tavolo.draw.compose.text
import top.e404.tavolo.draw.compose.textUnderline
import top.e404.tavolo.draw.compose.width
import top.e404.tavolo.util.Colors

class TestComposeEffects {
    private val uiFont = ManualTestSupport.uiFont

    @UiDsl
    private fun UiElement.sectionTitle(title: String, subtitle: String? = null) {
        text(
            title,
            modifier = Modifier.padding(bottom = 4f),
            style = TextStyle(
                fontSize = 30f,
                textColor = Color.WHITE,
                fontFamily = uiFont,
                underline = TextUnderline(
                    color = Color.makeRGB(255, 204, 77),
                    thickness = 8f,
                    offset = 2f,
                    mode = TextUnderlineMode.Block,
                    startPadding = 3f,
                    endPadding = 3f
                )
            )
        )
        if (subtitle != null) {
            text(
                subtitle,
                modifier = Modifier.padding(bottom = 20f),
                fontSize = 17f,
                textColor = Color.makeRGB(198, 207, 220),
                fontFamily = uiFont
            )
        }
    }

    @UiDsl
    private fun UiElement.sampleCard(
        title: String,
        modifier: Modifier,
        titleColor: Int = Color.WHITE,
        block: UiElement.() -> Unit = {}
    ) {
        column(
            modifier = modifier
                .padding(18f),
            horizontalAlignment = HorizontalAlignment.Left
        ) {
            text(title, fontSize = 19f, textColor = titleColor, fontFamily = uiFont)
            block()
        }
    }

    @Test
    fun test_compose_effects_overview() {
        ManualTestSupport.saveCompose("效果-01-总览_阴影旋转边框下划线") {
        column(
            modifier = Modifier
                .padding(30f)
                .background(Color.makeRGB(24, 28, 36))
                .padding(30f)
        ) {
            sectionTitle("Compose 新样式总览", "阴影、旋转、虚线边框、文本下划线样式组合")

            row(verticalAlignment = VerticalAlignment.Center) {
                sampleCard(
                    "阴影 + 圆角 + 色块下划线",
                    Modifier
                        .padding(right = 26f)
                        .shadow(
                            blurRadius = 16f,
                            color = Color.makeARGB(140, 0, 0, 0),
                            offsetY = 8f,
                            spread = 2f,
                            shape = Shape.RoundedRect(18f)
                        )
                        .clip(Shape.RoundedRect(18f))
                        .background(Color.makeRGB(44, 101, 255))
                        .size(260f, 140f)
                ) {
                    text(
                        "重点信息",
                        modifier = Modifier.padding(top = 22f),
                        style = TextStyle(
                            fontSize = 34f,
                            textColor = Color.WHITE,
                            fontFamily = uiFont,
                            underline = TextUnderline(
                                color = Color.makeRGB(255, 210, 65),
                                thickness = 12f,
                                offset = 3f,
                                mode = TextUnderlineMode.Block,
                                startPadding = 4f,
                                endPadding = 4f
                            )
                        )
                    )
                }

                sampleCard(
                    "旋转 + 虚线边框",
                    Modifier
                        .padding(left = 22f, right = 34f, top = 26f, bottom = 26f)
                        .rotate(-6f)
                        .shadow(
                            blurRadius = 12f,
                            color = Color.makeARGB(120, 0, 0, 0),
                            offsetX = 3f,
                            offsetY = 7f,
                            shape = Shape.RoundedRect(14f)
                        )
                        .background(Color.makeRGB(242, 245, 250))
                        .border(3f, Color.makeRGB(235, 88, 74), StrokeStyle.Dashed(listOf(12f, 7f)))
                        .size(250f, 130f),
                    titleColor = Color.makeRGB(32, 38, 48)
                ) {
                    text(
                        "像贴纸一样倾斜",
                        modifier = Modifier.padding(top = 18f),
                        fontSize = 27f,
                        textColor = Color.makeRGB(32, 38, 48),
                        fontFamily = uiFont
                    )
                }

                sampleCard(
                    "点线边框",
                    Modifier
                        .shadow(
                            blurRadius = 10f,
                            color = Color.makeARGB(100, 0, 0, 0),
                            offsetY = 5f,
                            shape = Shape.RoundedRect(14f)
                        )
                        .background(Color.makeRGB(47, 181, 128))
                        .border(4f, Color.WHITE, StrokeStyle.Dotted(dot = 2f, gap = 8f))
                        .size(230f, 130f)
                ) {
                    text(
                        "Dotted border",
                        modifier = Modifier.padding(top = 18f),
                        fontSize = 25f,
                        textColor = Color.WHITE,
                        fontFamily = uiFont
                    )
                }
            }

            row(modifier = Modifier.padding(top = 24f), verticalAlignment = VerticalAlignment.Center) {
                text(
                    "普通下划线",
                    modifier = Modifier.padding(right = 34f),
                    style = TextStyle(
                        fontSize = 28f,
                        textColor = Color.WHITE,
                        fontFamily = uiFont,
                        underline = TextUnderline(color = Color.makeRGB(120, 210, 255), thickness = 3f)
                    )
                )
                text(
                    "虚线下划线",
                    modifier = Modifier.padding(right = 34f),
                    style = TextStyle(
                        fontSize = 28f,
                        textColor = Color.WHITE,
                        fontFamily = uiFont,
                        underline = TextUnderline(
                            color = Color.makeRGB(255, 156, 94),
                            thickness = 3f,
                            offset = 5f,
                            strokeStyle = StrokeStyle.Dashed(listOf(8f, 5f))
                        )
                    )
                )
                text(
                    "色块下划线",
                    style = TextStyle(
                        fontSize = 28f,
                        textColor = Color.WHITE,
                        fontFamily = uiFont,
                        underline = TextUnderline(
                            color = Color.makeRGB(255, 204, 77),
                            thickness = 10f,
                            offset = 2f,
                            mode = TextUnderlineMode.Block,
                            startPadding = 3f,
                            endPadding = 3f
                        )
                    )
                )
            }
        }
    }
    }

    @Test
    fun test_compose_effects_underlines() {
        ManualTestSupport.saveCompose("效果-02-文本下划线") {
        column(
            modifier = Modifier
                .padding(28f)
                .background(Color.makeRGB(28, 32, 42))
                .padding(30f)
        ) {
            sectionTitle("文本下划线样式", "覆盖普通线、虚线、点线、色块和 padding 扩展")

            val items = listOf(
                "默认文本色下划线" to TextUnderline(thickness = 3f),
                "自定义颜色和偏移" to TextUnderline(color = Colors.LIGHT_BLUE.argb, thickness = 4f, offset = 7f),
                "虚线下划线" to TextUnderline(
                    color = Colors.ORANGE.argb,
                    thickness = 3f,
                    offset = 6f,
                    strokeStyle = StrokeStyle.Dashed(listOf(10f, 5f))
                ),
                "点线下划线" to TextUnderline(
                    color = Colors.GREEN.argb,
                    thickness = 4f,
                    offset = 7f,
                    strokeStyle = StrokeStyle.Dotted(dot = 1f, gap = 6f)
                ),
                "色块下划线" to TextUnderline(
                    color = Color.makeRGB(255, 221, 91),
                    thickness = 13f,
                    offset = 3f,
                    mode = TextUnderlineMode.Block,
                    startPadding = 5f,
                    endPadding = 5f
                )
            )

            for ((label, underline) in items) {
                text(
                    label,
                    modifier = Modifier.padding(bottom = 24f),
                    style = TextStyle(
                        fontSize = 34f,
                        textColor = Color.WHITE,
                        fontFamily = uiFont,
                        underline = underline
                    )
                )
            }

            text(
                "色块也可以比文字更宽",
                modifier = Modifier.padding(bottom = 26f),
                style = TextStyle(
                    fontSize = 38f,
                    textColor = Color.WHITE,
                    fontFamily = uiFont,
                    underline = TextUnderline(
                        color = Color.makeRGB(255, 150, 180),
                        thickness = 16f,
                        offset = 4f,
                        mode = TextUnderlineMode.Block,
                        startPadding = 18f,
                        endPadding = 28f
                    )
                )
            )

            text(
                "不同色块高度",
                modifier = Modifier.padding(bottom = 12f),
                fontSize = 20f,
                textColor = Color.makeRGB(198, 207, 220),
                fontFamily = uiFont
            )
            row(verticalAlignment = VerticalAlignment.Center) {
                listOf(
                    4f to Color.makeRGB(120, 210, 255),
                    8f to Color.makeRGB(255, 204, 77),
                    14f to Color.makeRGB(255, 150, 180),
                    22f to Color.makeRGB(155, 236, 143)
                ).forEachIndexed { index, (height, color) ->
                    text(
                        "${height.toInt()}px",
                        modifier = Modifier.padding(right = if (index == 3) 0f else 28f),
                        style = TextStyle(
                            fontSize = 30f,
                            textColor = Color.WHITE,
                            fontFamily = uiFont,
                            underline = TextUnderline(
                                color = color,
                                thickness = height,
                                offset = 3f,
                                mode = TextUnderlineMode.Block,
                                startPadding = 4f,
                                endPadding = 4f
                            )
                        )
                    )
                }
            }

            text(
                "复用字体 modifier",
                modifier = Modifier.padding(top = 26f, bottom = 12f),
                fontSize = 20f,
                textColor = Color.makeRGB(198, 207, 220),
                fontFamily = uiFont
            )
            val reusableText = TextModifier
                .font(
                    fontSize = 30f,
                    textColor = Color.WHITE,
                    fontFamily = uiFont
                )
                .textUnderline(
                    TextUnderline(
                        color = Color.makeRGB(120, 210, 255),
                        thickness = 5f,
                        offset = 4f,
                        strokeStyle = StrokeStyle.Dashed(listOf(10f, 5f))
                    )
                )
            row {
                text("标题 A", modifier = Modifier.padding(right = 32f), textModifier = reusableText)
                text("标题 B", modifier = Modifier.padding(right = 32f), textModifier = reusableText)
                text("局部改色", textModifier = reusableText, textColor = Color.makeRGB(255, 204, 77))
            }

            text(
                "字重 / 斜体 / 字间距 / 行高",
                modifier = Modifier.padding(top = 26f, bottom = 12f),
                fontSize = 20f,
                textColor = Color.makeRGB(198, 207, 220),
                fontFamily = uiFont
            )
            row(verticalAlignment = VerticalAlignment.Center) {
                text(
                    "Bold",
                    modifier = Modifier.padding(right = 28f),
                    textModifier = TextModifier.font(fontSize = 30f, textColor = Color.WHITE, fontFamily = uiFont).bold()
                )
                text(
                    "Italic",
                    modifier = Modifier.padding(right = 28f),
                    textModifier = TextModifier.font(fontSize = 30f, textColor = Color.WHITE, fontFamily = uiFont).italic()
                )
                text(
                    "Spacing",
                    modifier = Modifier.padding(right = 28f),
                    textModifier = TextModifier.font(fontSize = 30f, textColor = Color.WHITE, fontFamily = uiFont).letterSpacing(5f)
                )
                text(
                    "ScaleX",
                    textModifier = TextModifier.font(fontSize = 30f, textColor = Color.WHITE, fontFamily = uiFont).scaleX(1.25f)
                )
            }
            row(modifier = Modifier.padding(top = 10f)) {
                text(
                    "行高 18 行高 18",
                    modifier = Modifier.padding(right = 34f).sizeIn(maxWidth = 92f),
                    textModifier = TextModifier.font(fontSize = 24f, textColor = Color.WHITE, fontFamily = uiFont).lineHeight(18f)
                )
                text(
                    "行高 34 行高 34",
                    modifier = Modifier.sizeIn(maxWidth = 92f),
                    textModifier = TextModifier.font(fontSize = 24f, textColor = Color.WHITE, fontFamily = uiFont).lineHeight(34f)
                )
            }
        }
    }
    }

    @Test
    fun test_compose_effects_borders() {
        ManualTestSupport.saveCompose("效果-03-边框样式") {
        column(
            modifier = Modifier
                .padding(28f)
                .background(Color.makeRGB(242, 245, 250))
                .padding(30f)
        ) {
            text(
                "边框样式",
                modifier = Modifier.padding(bottom = 20f),
                fontSize = 30f,
                textColor = Color.makeRGB(30, 35, 45),
                fontFamily = uiFont
            )

            row(modifier = Modifier.padding(bottom = 22f)) {
                sampleCard(
                    "实线",
                    Modifier
                        .padding(right = 18f)
                        .background(Color.WHITE)
                        .border(4f, Color.makeRGB(44, 101, 255))
                        .size(200f, 112f),
                    titleColor = Color.makeRGB(30, 35, 45)
                )
                sampleCard(
                    "虚线",
                    Modifier
                        .padding(right = 18f)
                        .clip(Shape.RoundedRect(18f))
                        .background(Color.WHITE)
                        .border(4f, Color.makeRGB(235, 88, 74), StrokeStyle.Dashed(listOf(14f, 7f)), shape = Shape.RoundedRect(18f))
                        .size(200f, 112f),
                    titleColor = Color.makeRGB(30, 35, 45)
                )
                sampleCard(
                    "点线",
                    Modifier
                        .clip(Shape.RoundedRect(34f))
                        .background(Color.WHITE)
                        .border(4f, Color.makeRGB(34, 158, 112), StrokeStyle.Dotted(dot = 2f, gap = 8f), shape = Shape.RoundedRect(34f))
                        .size(200f, 112f),
                    titleColor = Color.makeRGB(30, 35, 45)
                )
            }

            row {
                sampleCard(
                    "分边虚线",
                    Modifier
                        .padding(right = 18f)
                        .background(Color.WHITE)
                        .border(top = 6f, bottom = 2f, left = 10f, right = 4f, color = Color.makeRGB(132, 93, 181), strokeStyle = StrokeStyle.Dashed(listOf(8f, 5f)))
                        .size(280f, 120f),
                    titleColor = Color.makeRGB(30, 35, 45)
                ) {
                    text("top/bottom/left/right 宽度不同", modifier = Modifier.padding(top = 16f), fontSize = 17f, textColor = Color.makeRGB(92, 99, 112), fontFamily = uiFont)
                }
                sampleCard(
                    "边框 + 裁剪 + 背景",
                    Modifier
                        .clip(Shape.RoundedRect(16f))
                        .background(Color.makeRGB(36, 42, 54))
                        .border(5f, Color.makeRGB(255, 204, 77), StrokeStyle.Dashed(listOf(10f, 6f)), shape = Shape.RoundedRect(16f))
                        .size(280f, 120f)
                ) {
                    text("用于卡片、标签、气泡边缘", modifier = Modifier.padding(top = 16f), fontSize = 17f, textColor = Color.WHITE, fontFamily = uiFont)
                }
            }

            text(
                "不同圆角弧度",
                modifier = Modifier.padding(top = 26f, bottom = 14f),
                fontSize = 23f,
                textColor = Color.makeRGB(30, 35, 45),
                fontFamily = uiFont
            )
            row(modifier = Modifier.padding(bottom = 28f), verticalAlignment = VerticalAlignment.Center) {
                listOf(
                    0f to "0",
                    10f to "10",
                    24f to "24",
                    44f to "44"
                ).forEachIndexed { index, (radius, label) ->
                    box(
                        modifier = Modifier
                            .padding(right = if (index == 3) 0f else 18f)
                            .clip(Shape.RoundedRect(radius))
                            .background(Color.WHITE)
                            .border(
                                4f,
                                Color.makeRGB(44, 101, 255),
                                StrokeStyle.Dashed(listOf(12f, 6f)),
                                shape = Shape.RoundedRect(radius)
                            )
                            .size(134f, 76f),
                        horizontalAlignment = HorizontalAlignment.Center,
                        verticalAlignment = VerticalAlignment.Center
                    ) {
                        text("R$label", fontSize = 22f, textColor = Color.makeRGB(30, 35, 45), fontFamily = uiFont)
                    }
                }
            }

            text(
                "多层边框 + padding + 不同圆角",
                modifier = Modifier.padding(bottom = 14f),
                fontSize = 23f,
                textColor = Color.makeRGB(30, 35, 45),
                fontFamily = uiFont
            )
            box(
                modifier = Modifier
                    .shadow(blurRadius = 16f, color = Color.makeARGB(90, 0, 0, 0), offsetY = 8f, shape = Shape.RoundedRect(34f))
                    .clip(Shape.RoundedRect(34f))
                    .background(Color.WHITE)
                    .border(6f, Color.makeRGB(44, 101, 255), shape = Shape.RoundedRect(34f))
                    .padding(10f)
                    .clip(Shape.RoundedRect(24f))
                    .background(Color.makeRGB(235, 241, 255))
                    .border(4f, Color.makeRGB(235, 88, 74), StrokeStyle.Dashed(listOf(14f, 7f)), shape = Shape.RoundedRect(24f))
                    .padding(10f)
                    .clip(Shape.RoundedRect(14f))
                    .background(Color.makeRGB(36, 42, 54))
                    .border(3f, Color.WHITE, StrokeStyle.Dotted(dot = 2f, gap = 7f), shape = Shape.RoundedRect(14f))
                    .padding(horizontal = 28f, vertical = 20f),
                horizontalAlignment = HorizontalAlignment.Center,
                verticalAlignment = VerticalAlignment.Center
            ) {
                text(
                    "三层圆角边框",
                    style = TextStyle(
                        fontSize = 28f,
                        textColor = Color.WHITE,
                        fontFamily = uiFont,
                        underline = TextUnderline(
                            color = Color.makeRGB(255, 204, 77),
                            thickness = 10f,
                            offset = 3f,
                            mode = TextUnderlineMode.Block,
                            startPadding = 3f,
                            endPadding = 3f
                        )
                    )
                )
            }
        }
    }
    }

    @Test
    fun test_compose_effects_transform_stack() {
        ManualTestSupport.saveCompose("效果-04-变换层叠") {
        column(
            modifier = Modifier
                .padding(40f)
                .background(Color.makeRGB(34, 38, 48))
                .padding(36f)
        ) {
            sectionTitle("阴影与旋转组合", "覆盖不同 blur、offset、spread、shape 和旋转中心")

            row(verticalAlignment = VerticalAlignment.Center) {
                val cardBase = Modifier
                    .padding(left = 22f, right = 22f, top = 36f, bottom = 36f)
                    .size(210f, 128f)

                sampleCard(
                    "轻阴影",
                    cardBase
                        .rotate(-8f)
                        .shadow(blurRadius = 10f, color = Color.makeARGB(110, 0, 0, 0), offsetY = 5f, shape = Shape.RoundedRect(12f))
                        .clip(Shape.RoundedRect(12f))
                        .background(Color.makeRGB(255, 255, 255)),
                    titleColor = Color.makeRGB(30, 35, 45)
                )
                sampleCard(
                    "大扩散阴影",
                    cardBase
                        .rotate(4f)
                        .shadow(blurRadius = 22f, color = Color.makeARGB(150, 0, 0, 0), offsetY = 12f, spread = 4f, shape = Shape.RoundedRect(20f))
                        .clip(Shape.RoundedRect(20f))
                        .background(Color.makeRGB(255, 210, 65)),
                    titleColor = Color.makeRGB(30, 35, 45)
                )
                sampleCard(
                    "圆形阴影",
                    Modifier
                        .padding(left = 22f, right = 22f, top = 36f, bottom = 36f)
                        .rotate(11f)
                        .shadow(blurRadius = 16f, color = Color.makeARGB(120, 0, 0, 0), offsetX = 5f, offsetY = 8f, shape = Shape.Circle)
                        .clip(Shape.Circle)
                        .background(Color.makeRGB(47, 181, 128))
                        .size(150f),
                    titleColor = Color.WHITE
                )
            }

            box(
                modifier = Modifier
                    .padding(top = 12f)
                    .width(760f)
                    .shadow(blurRadius = 18f, color = Color.makeARGB(130, 0, 0, 0), offsetY = 9f, shape = Shape.RoundedRect(18f))
                    .clip(Shape.RoundedRect(18f))
                    .background(Color.makeRGB(46, 101, 255))
                    .border(3f, Color.WHITE, StrokeStyle.Dashed(listOf(12f, 8f)), shape = Shape.RoundedRect(18f))
                    .padding(24f),
                verticalAlignment = VerticalAlignment.Center
            ) {
                text(
                    "外层阴影 + 裁剪 + 虚线边框 + 内部色块下划线",
                    style = TextStyle(
                        fontSize = 26f,
                        textColor = Color.WHITE,
                        fontFamily = uiFont,
                        underline = TextUnderline(
                            color = Color.makeRGB(255, 210, 65),
                            thickness = 11f,
                            offset = 3f,
                            mode = TextUnderlineMode.Block,
                            startPadding = 4f,
                            endPadding = 4f
                        )
                    )
                )
            }
        }
    }
    }
}
