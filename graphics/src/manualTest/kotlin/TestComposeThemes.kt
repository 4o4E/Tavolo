package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.jetbrains.skia.Font
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.junit.Test
import top.e404.tavolo.draw.compose.HorizontalAlignment
import top.e404.tavolo.draw.compose.IconTheme
import top.e404.tavolo.draw.compose.ImageOverflow
import top.e404.tavolo.draw.compose.Modifier
import top.e404.tavolo.draw.compose.Shape
import top.e404.tavolo.draw.compose.StrokeStyle
import top.e404.tavolo.draw.compose.TextModifier
import top.e404.tavolo.draw.compose.TextOverflow
import top.e404.tavolo.draw.compose.TextUnderline
import top.e404.tavolo.draw.compose.TextUnderlineMode
import top.e404.tavolo.draw.compose.UiDsl
import top.e404.tavolo.draw.compose.UiElement
import top.e404.tavolo.draw.compose.VerticalAlignment
import top.e404.tavolo.draw.compose.background
import top.e404.tavolo.draw.compose.border
import top.e404.tavolo.draw.compose.bold
import top.e404.tavolo.draw.compose.box
import top.e404.tavolo.draw.compose.cell
import top.e404.tavolo.draw.compose.clip
import top.e404.tavolo.draw.compose.column
import top.e404.tavolo.draw.compose.font
import top.e404.tavolo.draw.compose.icon
import top.e404.tavolo.draw.compose.iconText
import top.e404.tavolo.draw.compose.image
import top.e404.tavolo.draw.compose.italic
import top.e404.tavolo.draw.compose.letterSpacing
import top.e404.tavolo.draw.compose.lineHeight
import top.e404.tavolo.draw.compose.padding
import top.e404.tavolo.draw.compose.rotate
import top.e404.tavolo.draw.compose.row
import top.e404.tavolo.draw.compose.scaleX
import top.e404.tavolo.draw.compose.shadow
import top.e404.tavolo.draw.compose.size
import top.e404.tavolo.draw.compose.sizeIn
import top.e404.tavolo.draw.compose.table
import top.e404.tavolo.draw.compose.tableRow
import top.e404.tavolo.draw.compose.text
import top.e404.tavolo.draw.compose.textUnderline
import top.e404.tavolo.draw.compose.charts.BarTheme
import top.e404.tavolo.draw.compose.charts.RadarFixPolicy
import top.e404.tavolo.draw.compose.charts.RadarTheme
import top.e404.tavolo.draw.compose.charts.bar
import top.e404.tavolo.draw.compose.charts.radar
import top.e404.tavolo.util.FontManager

class TestComposeThemes {
    private val uiFont = ManualTestSupport.uiFont
    private val titleText = TextModifier.font(fontSize = 32f, textColor = Color.WHITE, fontFamily = uiFont).bold()
    private val bodyText = TextModifier.font(fontSize = 18f, textColor = ink, fontFamily = uiFont)
    private val captionText = TextModifier.font(fontSize = 15f, textColor = muted, fontFamily = uiFont)

    @Test
    fun test_compose_theme_layout() {
        ManualTestSupport.saveCompose("主题-布局-Column_Row_Box_Table") {
        themePage("布局主题", "Column / Row / Box / Table 的对齐、间距和尺寸约束", width = 1560f) {
            row {
                themeCard("Column 对齐", 300f, 510f) {
                    alignmentStack(HorizontalAlignment.Left, "Left")
                    alignmentStack(HorizontalAlignment.Center, "Center")
                    alignmentStack(HorizontalAlignment.Right, "Right")
                }
                gap()
                themeCard("Row 垂直对齐", 360f, 510f) {
                    row(modifier = Modifier.padding(top = 16f)) {
                        alignBox(VerticalAlignment.Top, "Top")
                        alignBox(VerticalAlignment.Center, "Center")
                        alignBox(VerticalAlignment.Bottom, "Bottom")
                    }
                }
                gap()
                themeCard("Box 叠放定位", 300f, 510f) {
                    box(
                        modifier = Modifier
                            .padding(top = 18f)
                            .size(238f, 310f)
                            .clip(Shape.RoundedRect(18f))
                            .background(Color.makeRGB(237, 241, 248))
                            .border(2f, Color.makeRGB(205, 214, 228), shape = Shape.RoundedRect(18f))
                    ) {
                        box(
                            modifier = Modifier
                                .size(178f, 178f)
                                .clip(Shape.RoundedRect(26f))
                                .background(blue)
                        )
                        box(
                            modifier = Modifier
                                .padding(top = 72f, left = 62f)
                                .size(148f, 148f)
                                .clip(Shape.RoundedRect(26f))
                                .background(green)
                        )
                        box(
                            modifier = Modifier
                                .padding(top = 172f, left = 116f)
                                .size(88f, 88f)
                                .clip(Shape.Circle)
                                .background(yellow)
                        )
                    }
                    text("固定容器中多层元素叠放", modifier = Modifier.padding(top = 18f), textModifier = captionText)
                }
                gap()
                themeCard("Table 列宽与单元格对齐", 440f, 510f) {
                    table(columnSpacing = 10f, rowSpacing = 10f, modifier = Modifier.padding(top = 18f)) {
                        tableRow {
                            tableCell("字段", "Name", 92f, HorizontalAlignment.Left)
                            tableCell("状态", "Active", 104f, HorizontalAlignment.Center)
                            tableCell("说明", "自动换行的长文本列", 170f, HorizontalAlignment.Right)
                        }
                        tableRow {
                            tableCell("图表", "Radar", 92f, HorizontalAlignment.Left)
                            tableCell("变体", "4", 104f, HorizontalAlignment.Center)
                            tableCell("限制宽度后保持行高稳定", "wrap width", 170f, HorizontalAlignment.Right)
                        }
                        tableRow {
                            tableCell("文本", "Style", 92f, HorizontalAlignment.Left)
                            tableCell("覆盖", "Full", 104f, HorizontalAlignment.Center)
                            tableCell("包含省略、下划线和字距", "ellipsis", 170f, HorizontalAlignment.Right)
                        }
                    }
                    text("单元格内上下对齐", modifier = Modifier.padding(top = 16f), textModifier = captionText)
                    table(columnSpacing = 10f, rowSpacing = 10f, modifier = Modifier.padding(top = 8f)) {
                        tableRow {
                            tableAlignCell("Top", "顶部", 120f, VerticalAlignment.Top, blue)
                            tableAlignCell("Center", "居中", 120f, VerticalAlignment.Center, green)
                            tableAlignCell("Bottom", "底部", 120f, VerticalAlignment.Bottom, red)
                        }
                    }
                }
            }
        }
    }
    }

    @Test
    fun test_compose_theme_modifiers() {
        ManualTestSupport.saveCompose("主题-Modifier-边距阴影旋转裁剪边框") {
        themePage("Modifier 主题", "padding、阴影、旋转、裁剪、实线/虚线/点线边框与圆角路径边框") {
            row {
                themeCard("多层边框 + padding + 不同圆角", 390f, 500f) {
                    box(
                        modifier = Modifier
                            .padding(top = 18f)
                            .size(292f, 292f)
                            .shadow(18f, Color.makeARGB(90, 29, 37, 56), offsetY = 10f, spread = 2f, shape = Shape.RoundedRect(34f))
                            .clip(Shape.RoundedRect(34f))
                            .background(Color.makeRGB(248, 250, 253))
                            .border(7f, blue, StrokeStyle.Solid, Shape.RoundedRect(34f))
                            .padding(13f)
                            .border(5f, red, StrokeStyle.Dashed(listOf(16f, 9f)), Shape.RoundedRect(23f))
                            .padding(14f)
                            .border(4f, green, StrokeStyle.Dotted(dot = 3f, gap = 8f), Shape.RoundedRect(12f))
                            .padding(20f),
                        horizontalAlignment = HorizontalAlignment.Center,
                        verticalAlignment = VerticalAlignment.Center
                    ) {
                        text("内容区", textModifier = TextModifier.font(28f, Color.makeRGB(43, 52, 68), uiFont).bold())
                    }
                }
                gap()
                themeCard("边框线型", 330f, 500f) {
                    borderStrip("Solid", StrokeStyle.Solid, blue, 18f)
                    borderStrip("Dashed", StrokeStyle.Dashed(listOf(18f, 8f)), red, 18f)
                    borderStrip("Dotted", StrokeStyle.Dotted(dot = 3f, gap = 8f), green, 18f)
                    borderStrip("Rounded path", StrokeStyle.Dashed(listOf(8f, 6f), phase = 4f), yellow, 26f)
                }
                gap()
                themeCard("阴影、旋转、裁剪组合", 410f, 500f) {
                    row(modifier = Modifier.padding(top = 52f, left = 10f), verticalAlignment = VerticalAlignment.Center) {
                        transformTile("Rotate -8", -8f, blue)
                        transformTile("Rotate 6", 6f, red)
                    }
                    box(
                        modifier = Modifier
                            .padding(top = 50f, left = 82f)
                            .size(178f, 118f)
                            .shadow(16f, Color.makeARGB(95, 0, 0, 0), offsetX = 6f, offsetY = 10f, shape = Shape.RoundedRect(24f))
                            .clip(Shape.RoundedRect(24f))
                            .background(Color.makeRGB(51, 63, 84)),
                        horizontalAlignment = HorizontalAlignment.Center,
                        verticalAlignment = VerticalAlignment.Center
                    ) {
                        text("clip + shadow", textModifier = TextModifier.font(20f, Color.WHITE, uiFont).bold())
                    }
                }
            }
        }
    }
    }

    @Test
    fun test_compose_theme_text() {
        ManualTestSupport.saveCompose("主题-文本-样式换行省略下划线") {
        themePage("文本主题", "TextModifier 专用属性、换行/省略和不同高度色块下划线") {
            row {
                themeCard("TextModifier 样式复用", 410f, 540f) {
                    text(
                        "标题样式",
                        modifier = Modifier.padding(top = 18f),
                        textModifier = TextModifier
                            .font(fontSize = 32f, textColor = ink, fontFamily = uiFont)
                            .bold()
                            .textUnderline(blockUnderline(yellow, 12f))
                    )
                    text("加粗 + 斜体 + 颜色", modifier = Modifier.padding(top = 20f), textModifier = TextModifier.font(26f, blue, uiFont).bold().italic())
                    text("letterSpacing = 4", modifier = Modifier.padding(top = 18f), textModifier = TextModifier.font(24f, red, uiFont).letterSpacing(4f))
                    text("scaleX = 1.25", modifier = Modifier.padding(top = 18f), textModifier = TextModifier.font(24f, green, uiFont).scaleX(1.25f))
                    text(
                        "lineHeight 让多行文本更适合长说明展示",
                        modifier = Modifier.padding(top = 18f).sizeIn(maxWidth = 280f),
                        textModifier = TextModifier.font(21f, ink, uiFont).lineHeight(36f)
                    )
                }
                gap()
                themeCard("下划线与色块高度", 420f, 540f) {
                    underlineLine("细线 2px", TextUnderline(color = blue, thickness = 2f, offset = 5f))
                    underlineLine("虚线 3px", TextUnderline(color = red, thickness = 3f, offset = 6f, strokeStyle = StrokeStyle.Dashed(listOf(10f, 6f))))
                    underlineLine("色块 6px", blockUnderline(yellow, 6f))
                    underlineLine("色块 12px", blockUnderline(green, 12f))
                    underlineLine("色块 20px", blockUnderline(Color.makeRGB(255, 170, 120), 20f, 4f))
                }
                gap()
                themeCard("宽度约束与省略", 420f, 540f) {
                    overflowMarkedText(
                        "Wrap + 边框",
                        blue,
                        "中文长文本在 maxWidth 内换行。\n外框与色条用于观察边界。",
                        TextOverflow.Wrap,
                        maxHeight = 84f
                    )
                    overflowMarkedText(
                        "Ellipsis + 边框",
                        yellow,
                        "这是一段故意很长的标题文本，用来检查省略号和边框之间的距离是否稳定。",
                        TextOverflow.Ellipsis,
                        fontSize = 23f,
                        maxHeight = 34f
                    )
                    overflowMarkedText(
                        "MaxHeight 截断",
                        red,
                        "最多两行高度限制会截断后续内容，边框标记用于观察裁剪位置。",
                        TextOverflow.Wrap,
                        maxHeight = 58f
                    )
                }
            }
        }
    }
    }

    @Test
    fun test_compose_theme_media() {
        ManualTestSupport.saveCompose("主题-媒体-本地绘图图片与SVG图标") {
        val testImage = createDrawnTestImage()
        themePage("媒体与图标主题", "使用绘图工具生成本地测试图，覆盖图片缩放/裁剪、裁剪形状、SVG icon 和 iconText", width = 1480f) {
            row {
                themeCard("绘图生成测试图", 310f, 510f) {
                    image(
                        testImage,
                        modifier = Modifier
                            .padding(top = 20f)
                            .sizeIn(maxWidth = 230f, maxHeight = 158f)
                            .border(2f, Color.makeRGB(205, 214, 228))
                    )
                    text("不依赖网络资源，包含文字、色块、圆形和斜线，方便观察缩放与裁剪。", modifier = Modifier.padding(top = 24f).sizeIn(maxWidth = 236f), textModifier = bodyText)
                }
                gap()
                themeCard("ImageOverflow", 360f, 510f) {
                    row(modifier = Modifier.padding(top = 22f), verticalAlignment = VerticalAlignment.Center) {
                        imageSample("Scale", testImage, ImageOverflow.Scale)
                        imageSample("Crop", testImage, ImageOverflow.Crop)
                    }
                    row(modifier = Modifier.padding(top = 28f), verticalAlignment = VerticalAlignment.Center) {
                        imageSample("Tall scale", testImage, ImageOverflow.Scale, width = 82f, height = 150f)
                        imageSample("Wide crop", testImage, ImageOverflow.Crop, width = 150f, height = 82f)
                    }
                }
                gap()
                themeCard("图片裁剪形状", 330f, 510f) {
                    row(modifier = Modifier.padding(top = 22f), verticalAlignment = VerticalAlignment.Center) {
                        clippedImage("圆形", testImage, Shape.Circle)
                        clippedImage("圆角", testImage, Shape.RoundedRect(24f))
                    }
                    box(
                        modifier = Modifier
                            .padding(top = 34f, left = 34f)
                            .size(180f, 124f)
                            .shadow(14f, Color.makeARGB(85, 0, 0, 0), offsetY = 8f, shape = Shape.RoundedRect(22f))
                            .clip(Shape.RoundedRect(22f))
                            .border(4f, blue, StrokeStyle.Dashed(listOf(14f, 8f)), Shape.RoundedRect(22f))
                    ) {
                        image(testImage, modifier = Modifier.sizeIn(maxWidth = 180f, maxHeight = 124f), imageOverflow = ImageOverflow.Crop)
                    }
                }
                gap()
                themeCard("SVG icon 与 iconText", 370f, 510f) {
                    row(modifier = Modifier.padding(top = 26f), verticalAlignment = VerticalAlignment.Center) {
                        iconBadge(blue, arrowSvg)
                        iconBadge(red, checkSvg)
                        iconBadge(green, starSvg)
                    }
                    iconText(
                        "状态项：iconText 组合",
                        fontSize = 24f,
                        modifier = Modifier.padding(left = 12f),
                        textModifier = TextModifier.font(textColor = ink, fontFamily = uiFont).bold(),
                        iconColor = yellow
                    )
                    text(
                        "迁移自旧 icon / iconText 人工测试，并放到同一张媒体主题图中。",
                        modifier = Modifier.padding(top = 28f).sizeIn(maxWidth = 290f),
                        textModifier = bodyText
                    )
                }
            }
        }
    }
    }

    @Test
    fun test_compose_theme_charts() {
        ManualTestSupport.saveCompose("主题-图表-Donut与Radar变体合集") {
        themePage("图表主题", "一张图集中展示 donut 与 radar 的主要类型和关键变体", width = 1500f, height = 980f) {
            row {
                chartCard("Donut 均分", 260f, 310f) {
                    bar(BarTheme(outerRadius = 82f, innerRadius = 48f, strokeWidth = 3f), equalSegments)
                }
                gap(18f)
                chartCard("Donut 权重", 260f, 310f) {
                    bar(BarTheme(outerRadius = 82f, innerRadius = 38f, strokeWidth = 3f), weightedSegments)
                }
                gap(18f)
                chartCard("Donut 起始角 + 粗描边", 260f, 310f) {
                    bar(BarTheme(outerRadius = 82f, innerRadius = 58f, strokeWidth = 7f, start = 35f), weightedSegments)
                }
                gap(18f)
                chartCard("Donut 细环", 260f, 310f) {
                    bar(BarTheme(outerRadius = 82f, innerRadius = 70f, strokeWidth = 2f, start = -130f), equalSegments)
                }
                gap(18f)
                chartCard("Donut 无描边色块", 260f, 310f) {
                    bar(BarTheme(outerRadius = 82f, innerRadius = 22f, strokeColor = Color.TRANSPARENT, strokeWidth = 0f), weightedSegments)
                }
            }
            row(modifier = Modifier.padding(top = 22f)) {
                radarCard("Radar 默认标签修正", RadarFixPolicy.RATED_FIX, gridCount = 5, showGridText = true, color = blue)
                gap(18f)
                radarCard("Radar 标签不修正", RadarFixPolicy.NONE, gridCount = 5, showGridText = true, color = red)
                gap(18f)
                radarCard("Radar 标签外移", RadarFixPolicy.MOVE_OUTSIDE, gridCount = 4, showGridText = true, color = green)
                gap(18f)
                radarCard("Radar 隐藏网格文字", RadarFixPolicy.TILT, gridCount = 3, showGridText = false, color = yellow)
            }
        }
    }
    }

    @UiDsl
    private fun UiElement.themePage(title: String, subtitle: String, width: Float = 1360f, height: Float = 720f, block: UiElement.() -> Unit) {
        box(
            modifier = Modifier
                .size(width, height)
                .background(Color.makeRGB(28, 34, 45))
        ) {
            column(modifier = Modifier.padding(30f)) {
                text(title, textModifier = titleText.textUnderline(blockUnderline(yellow, 10f, 2f)))
                text(subtitle, modifier = Modifier.padding(top = 10f, bottom = 26f), textModifier = TextModifier.font(18f, Color.makeRGB(200, 209, 223), uiFont))
                block()
            }
        }
    }

    @UiDsl
    private fun UiElement.themeCard(title: String, width: Float, height: Float, block: UiElement.() -> Unit) {
        column(
            modifier = Modifier
                .size(width, height)
                .clip(Shape.RoundedRect(18f))
                .background(surface)
                .border(1.5f, Color.makeRGB(218, 225, 236), shape = Shape.RoundedRect(18f))
                .padding(22f)
        ) {
            text(title, textModifier = TextModifier.font(21f, Color.makeRGB(35, 44, 58), uiFont).bold())
            block()
        }
    }

    @UiDsl
    private fun UiElement.chartCard(title: String, width: Float, height: Float, block: UiElement.() -> Unit) {
        column(
            modifier = Modifier
                .size(width, height)
                .clip(Shape.RoundedRect(16f))
                .background(surface)
                .border(1.5f, Color.makeRGB(218, 225, 236), shape = Shape.RoundedRect(16f))
                .padding(18f),
            horizontalAlignment = HorizontalAlignment.Center
        ) {
            text(title, textModifier = TextModifier.font(18f, Color.makeRGB(35, 44, 58), uiFont).bold())
            box(modifier = Modifier.padding(top = 20f).size(178f, 178f), horizontalAlignment = HorizontalAlignment.Center, verticalAlignment = VerticalAlignment.Center) {
                block()
            }
            chartLegend()
        }
    }

    @UiDsl
    private fun UiElement.radarCard(title: String, fixPolicy: RadarFixPolicy, gridCount: Int, showGridText: Boolean, color: Int) {
        column(
            modifier = Modifier
                .size(348f, 410f)
                .clip(Shape.RoundedRect(16f))
                .background(surface)
                .border(1.5f, Color.makeRGB(218, 225, 236), shape = Shape.RoundedRect(16f))
                .padding(18f),
            horizontalAlignment = HorizontalAlignment.Center
        ) {
            text(title, textModifier = TextModifier.font(18f, Color.makeRGB(35, 44, 58), uiFont).bold())
            radar(
                RadarTheme(
                    width = 292f,
                    height = 292f,
                    radius = 88f,
                    fillOutlineColor = color,
                    fillOutlinePaint = Paint().apply {
                        this.color = color
                        strokeWidth = 2.5f
                        mode = PaintMode.STROKE
                        isAntiAlias = true
                    },
                    fillColor = color and 0x00FFFFFF or (0x55 shl 24),
                    fillPaint = Paint().apply {
                        this.color = color and 0x00FFFFFF or (0x55 shl 24)
                        isAntiAlias = true
                    },
                    gridCount = gridCount,
                    gridLineColor = Color.makeRGB(194, 203, 216),
                    gridFontProvider = { if (showGridText) "${it + 1}" else null },
                    gridFont = Font(FontManager.resolve(uiFont), 10f),
                    gridFontColor = Color.makeRGB(109, 122, 142),
                    gridFontPaint = Paint().apply {
                        this.color = Color.makeRGB(109, 122, 142)
                        isAntiAlias = true
                    },
                    labelOuterLength = 16f,
                    labelFixPolicy = fixPolicy,
                    labelFont = Font(FontManager.resolve(uiFont), 16f),
                    labelFontColor = Color.makeRGB(43, 52, 68),
                    labelFontPaint = Paint().apply {
                        this.color = Color.makeRGB(43, 52, 68)
                        isAntiAlias = true
                    }
                ),
                radarData
            )
        }
    }

    @UiDsl
    private fun UiElement.gap(width: Float = 22f) {
        box(Modifier.size(width, 1f))
    }

    @UiDsl
    private fun UiElement.alignmentStack(alignment: HorizontalAlignment, label: String) {
        column(
            modifier = Modifier
                .padding(top = 16f)
                .size(230f, 116f)
                .clip(Shape.RoundedRect(14f))
                .background(Color.makeRGB(237, 241, 248))
                .padding(12f),
            horizontalAlignment = alignment
        ) {
            text(label, textModifier = TextModifier.font(16f, muted, uiFont).bold())
            box(modifier = Modifier.padding(top = 10f).size(132f, 34f).clip(Shape.RoundedRect(8f)).background(blue))
        }
    }

    @UiDsl
    private fun UiElement.alignBox(alignment: VerticalAlignment, label: String) {
        box(
            modifier = Modifier
                .padding(right = 14f)
                .size(94f, 284f)
                .clip(Shape.RoundedRect(14f))
                .background(Color.makeRGB(237, 241, 248))
                .padding(10f),
            horizontalAlignment = HorizontalAlignment.Center,
            verticalAlignment = alignment
        ) {
            column(horizontalAlignment = HorizontalAlignment.Center) {
                box(modifier = Modifier.size(48f, 48f).clip(Shape.RoundedRect(10f)).background(red))
                text(label, modifier = Modifier.padding(top = 8f), textModifier = TextModifier.font(14f, muted, uiFont))
            }
        }
    }

    @UiDsl
    private fun top.e404.tavolo.draw.compose.TableRow.tableCell(title: String, value: String, width: Float, alignment: HorizontalAlignment) {
        cell(
            modifier = Modifier
                .size(width, 72f)
                .clip(Shape.RoundedRect(12f))
                .background(Color.makeRGB(237, 241, 248))
                .padding(10f),
            horizontalAlignment = alignment,
            verticalAlignment = VerticalAlignment.Center
        ) {
            column(horizontalAlignment = alignment) {
                text(title, textModifier = TextModifier.font(13f, muted, uiFont))
                text(value, modifier = Modifier.padding(top = 6f).sizeIn(maxWidth = width - 20f), textModifier = TextModifier.font(16f, ink, uiFont).bold())
            }
        }
    }

    @UiDsl
    private fun top.e404.tavolo.draw.compose.TableRow.tableAlignCell(label: String, value: String, width: Float, alignment: VerticalAlignment, color: Int) {
        cell(
            modifier = Modifier
                .size(width, 96f)
                .clip(Shape.RoundedRect(12f))
                .background(Color.makeRGB(237, 241, 248))
                .border(2f, color, shape = Shape.RoundedRect(12f))
                .padding(10f),
            horizontalAlignment = HorizontalAlignment.Center,
            verticalAlignment = alignment
        ) {
            column(horizontalAlignment = HorizontalAlignment.Center) {
                box(modifier = Modifier.size(32f, 8f).clip(Shape.RoundedRect(4f)).background(color))
                text(label, modifier = Modifier.padding(top = 6f), textModifier = TextModifier.font(14f, muted, uiFont))
                text(value, modifier = Modifier.padding(top = 4f), textModifier = TextModifier.font(16f, ink, uiFont).bold())
            }
        }
    }

    @UiDsl
    private fun UiElement.overflowMarkedText(
        label: String,
        color: Int,
        content: String,
        overflow: TextOverflow,
        fontSize: Float = 19f,
        maxHeight: Float
    ) {
        row(
            modifier = Modifier
                .padding(top = 18f)
                .size(350f, 118f)
                .clip(Shape.RoundedRect(12f))
                .background(Color.makeRGB(246, 248, 252))
                .border(2f, color, shape = Shape.RoundedRect(12f))
                .padding(12f),
            verticalAlignment = VerticalAlignment.Center
        ) {
            box(modifier = Modifier.size(8f, 82f).clip(Shape.RoundedRect(4f)).background(color))
            column(modifier = Modifier.padding(left = 12f)) {
                text(label, textModifier = TextModifier.font(16f, color, uiFont).bold())
                text(
                    content,
                    modifier = Modifier.padding(top = 7f).sizeIn(maxWidth = 292f, maxHeight = maxHeight),
                    textModifier = TextModifier.font(fontSize, ink, uiFont).lineHeight(25f),
                    textOverflow = overflow
                )
            }
        }
    }

    @UiDsl
    private fun UiElement.borderStrip(label: String, style: StrokeStyle, color: Int, radius: Float) {
        row(modifier = Modifier.padding(top = 18f), verticalAlignment = VerticalAlignment.Center) {
            box(
                modifier = Modifier
                    .size(132f, 48f)
                    .clip(Shape.RoundedRect(radius))
                    .background(Color.makeRGB(246, 248, 252))
                    .border(4f, color, style, Shape.RoundedRect(radius))
            )
            text(label, modifier = Modifier.padding(left = 16f), textModifier = bodyText)
        }
    }

    @UiDsl
    private fun UiElement.transformTile(label: String, degrees: Float, color: Int) {
        box(
            modifier = Modifier
                .padding(right = 26f)
                .rotate(degrees)
                .size(132f, 112f)
                .shadow(12f, Color.makeARGB(80, 0, 0, 0), offsetY = 8f, shape = Shape.RoundedRect(18f))
                .clip(Shape.RoundedRect(18f))
                .background(color),
            horizontalAlignment = HorizontalAlignment.Center,
            verticalAlignment = VerticalAlignment.Center
        ) {
            text(label, textModifier = TextModifier.font(17f, Color.WHITE, uiFont).bold())
        }
    }

    @UiDsl
    private fun UiElement.underlineLine(label: String, underline: TextUnderline) {
        text(
            label,
            modifier = Modifier.padding(top = 26f),
            textModifier = TextModifier
                .font(fontSize = 29f, textColor = Color.makeRGB(43, 52, 68), fontFamily = uiFont)
                .bold()
                .textUnderline(underline)
        )
    }

    @UiDsl
    private fun UiElement.chartLegend() {
        row(modifier = Modifier.padding(top = 18f), verticalAlignment = VerticalAlignment.Center) {
            legendDot(blue, "A")
            legendDot(red, "B")
            legendDot(green, "C")
            legendDot(yellow, "D")
        }
    }

    @UiDsl
    private fun UiElement.legendDot(color: Int, label: String) {
        row(modifier = Modifier.padding(right = 10f), verticalAlignment = VerticalAlignment.Center) {
            box(Modifier.size(10f).clip(Shape.Circle).background(color))
            text(label, modifier = Modifier.padding(left = 4f), textModifier = TextModifier.font(13f, muted, uiFont))
        }
    }

    private fun createDrawnTestImage(): Image {
        val width = 320
        val height = 220
        return Surface.makeRasterN32Premul(width, height).use { surface ->
            val canvas = surface.canvas
            canvas.clear(Color.makeRGB(246, 248, 252))

            val paint = Paint().apply { isAntiAlias = true }
            paint.color = blue
            canvas.drawRect(Rect.makeXYWH(0f, 0f, width.toFloat(), 48f), paint)
            paint.color = yellow
            canvas.drawCircle(270f, 38f, 28f, paint)

            paint.color = Color.makeRGB(231, 236, 245)
            canvas.drawRect(Rect.makeXYWH(22f, 74f, 276f, 110f), paint)
            paint.color = red
            canvas.drawCircle(78f, 128f, 34f, paint)
            paint.color = green
            canvas.drawRect(Rect.makeXYWH(130f, 94f, 70f, 70f), paint)
            paint.color = blue
            canvas.drawRect(Rect.makeXYWH(220f, 88f, 46f, 88f), paint)

            paint.apply {
                color = Color.makeRGB(35, 44, 58)
                mode = PaintMode.STROKE
                strokeWidth = 4f
            }
            canvas.drawLine(28f, 188f, 292f, 72f, paint)
            canvas.drawLine(28f, 72f, 292f, 188f, paint)

            paint.apply {
                color = Color.WHITE
                mode = PaintMode.FILL
            }
            canvas.drawString("测试图", 24f, 34f, Font(FontManager.resolve(uiFont), 26f), paint)
            paint.color = Color.makeRGB(35, 44, 58)
            canvas.drawString("scale / crop / clip", 62f, 208f, Font(FontManager.resolve(uiFont), 18f), paint)

            surface.makeImageSnapshot()
        }
    }

    @UiDsl
    private fun UiElement.imageSample(label: String, source: Image, overflow: ImageOverflow, width: Float = 116f, height: Float = 116f) {
        column(modifier = Modifier.padding(right = 18f), horizontalAlignment = HorizontalAlignment.Center) {
            box(
                modifier = Modifier
                    .size(width, height)
                    .background(Color.makeRGB(237, 241, 248))
                    .border(2f, Color.makeRGB(205, 214, 228))
            ) {
                image(source, modifier = Modifier.sizeIn(maxWidth = width, maxHeight = height), imageOverflow = overflow)
            }
            text(label, modifier = Modifier.padding(top = 10f), textModifier = captionText)
        }
    }

    @UiDsl
    private fun UiElement.clippedImage(label: String, source: Image, shape: Shape) {
        column(modifier = Modifier.padding(right = 22f), horizontalAlignment = HorizontalAlignment.Center) {
            image(
                source,
                modifier = Modifier
                    .sizeIn(maxWidth = 112f, maxHeight = 112f)
                    .clip(shape)
                    .border(3f, Color.makeRGB(205, 214, 228), shape = shape),
                imageOverflow = ImageOverflow.Crop
            )
            text(label, modifier = Modifier.padding(top = 10f), textModifier = captionText)
        }
    }

    @UiDsl
    private fun UiElement.iconBadge(color: Int, svg: String) {
        box(
            modifier = Modifier
                .padding(right = 18f)
                .size(72f)
                .clip(Shape.RoundedRect(18f))
                .background(color),
            horizontalAlignment = HorizontalAlignment.Center,
            verticalAlignment = VerticalAlignment.Center
        ) {
            icon(IconTheme(size = 42f, color = Color.WHITE), svg)
        }
    }

    private fun blockUnderline(color: Int, thickness: Float, offset: Float = 2f): TextUnderline {
        return TextUnderline(
            color = color,
            thickness = thickness,
            offset = offset,
            mode = TextUnderlineMode.Block,
            startPadding = 3f,
            endPadding = 3f
        )
    }

    private companion object {
        val surface: Int = Color.makeRGB(255, 255, 255)
        val ink: Int = Color.makeRGB(43, 52, 68)
        val muted: Int = Color.makeRGB(109, 122, 142)
        val blue: Int = Color.makeRGB(44, 101, 255)
        val red: Int = Color.makeRGB(235, 88, 74)
        val green: Int = Color.makeRGB(47, 181, 128)
        val yellow: Int = Color.makeRGB(255, 204, 77)
        const val arrowSvg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960"><path d="M504-480 320-664l56-56 240 240-240 240-56-56 184-184Z"/></svg>"""
        const val checkSvg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960"><path d="M382-240 154-468l57-57 171 171 367-367 57 57-424 424Z"/></svg>"""
        const val starSvg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960"><path d="m233-80 65-281L80-550l288-25 112-265 112 265 288 25-218 189 65 281-247-149L233-80Z"/></svg>"""

        val equalSegments = listOf(
            blue to 1f,
            red to 1f,
            green to 1f,
            yellow to 1f
        )
        val weightedSegments = listOf(
            blue to 42f,
            red to 24f,
            green to 18f,
            yellow to 16f
        )
        val radarData = listOf(
            "质量" to 0.92f,
            "速度" to 0.72f,
            "稳定" to 0.84f,
            "覆盖" to 0.64f,
            "成本" to 0.56f,
            "体验" to 0.78f
        )
    }
}
