package top.e404.tavolo.draw.test

import org.jetbrains.skia.Color
import org.jetbrains.skia.Font
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Path
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
import top.e404.tavolo.draw.compose.charts.AxisTheme
import top.e404.tavolo.draw.compose.charts.BarTheme
import top.e404.tavolo.draw.compose.charts.BarChartMode
import top.e404.tavolo.draw.compose.charts.BarSeries
import top.e404.tavolo.draw.compose.charts.CategoryBarData
import top.e404.tavolo.draw.compose.charts.CategoryBarTheme
import top.e404.tavolo.draw.compose.charts.ChartInsets
import top.e404.tavolo.draw.compose.charts.ChartLegendPosition
import top.e404.tavolo.draw.compose.charts.ChartLegendTheme
import top.e404.tavolo.draw.compose.charts.ChartPalette
import top.e404.tavolo.draw.compose.charts.ChartTextStyle
import top.e404.tavolo.draw.compose.charts.LineChartTheme
import top.e404.tavolo.draw.compose.charts.LinePoint
import top.e404.tavolo.draw.compose.charts.LineSeries
import top.e404.tavolo.draw.compose.charts.PieChartTheme
import top.e404.tavolo.draw.compose.charts.PieSlice
import top.e404.tavolo.draw.compose.charts.RadarFixPolicy
import top.e404.tavolo.draw.compose.charts.RadarTheme
import top.e404.tavolo.draw.compose.charts.RelationEdge
import top.e404.tavolo.draw.compose.charts.RelationEdgeDrawer
import top.e404.tavolo.draw.compose.charts.RelationGraphLayout
import top.e404.tavolo.draw.compose.charts.RelationGraphTheme
import top.e404.tavolo.draw.compose.charts.RelationNode
import top.e404.tavolo.draw.compose.charts.RelationNodeDrawer
import top.e404.tavolo.draw.compose.charts.bar
import top.e404.tavolo.draw.compose.charts.categoryBarChart
import top.e404.tavolo.draw.compose.charts.donutChart
import top.e404.tavolo.draw.compose.charts.lineChart
import top.e404.tavolo.draw.compose.charts.pieChart
import top.e404.tavolo.draw.compose.charts.radar
import top.e404.tavolo.draw.compose.charts.relationGraph
import top.e404.tavolo.util.FontManager

class ComposeThemeManualTest {
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
                    text(
                        "emoji fallback: 😀 😂 🥰 ❤️ ☀️",
                        modifier = Modifier.padding(top = 22f).sizeIn(maxWidth = 320f),
                        textModifier = TextModifier.font(24f, ink, uiFont).letterSpacing(1f)
                    )
                    text(
                        "中文、English 和 emoji 混排换行：😀a 不应被拆成无效字符。",
                        modifier = Modifier.padding(top = 14f).sizeIn(maxWidth = 320f),
                        textModifier = TextModifier.font(18f, muted, uiFont).lineHeight(26f)
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
        ManualTestSupport.saveCompose("主题-图表-统计图能力合集") {
            themePage("图表主题", "折线图、饼图、donut、分组/堆叠柱状图、legacy donut 与 radar 变体", width = 1680f, height = 1840f) {
                row {
                    lineAbilityCard(
                        title = "折线图：单系列趋势",
                        description = "覆盖坐标轴、网格、点标记和底部图例",
                        series = lineTrendSeries,
                        fill = false,
                        yMax = 90f
                    )
                    gap(26f)
                    lineAbilityCard(
                        title = "折线图：多系列 + 空值断线",
                        description = "覆盖多系列、虚线、空值断线和面积填充",
                        series = lineServerSeries,
                        fill = true,
                        yMax = 100f
                    )
                    gap(26f)
                    pieAbilityCard(
                        title = "饼图：分类占比",
                        description = "覆盖实心饼图、扇区标签和右侧图例",
                        donut = false,
                        data = pieRuntimeSlices
                    )
                }
                row(modifier = Modifier.padding(top = 26f)) {
                    pieAbilityCard(
                        title = "Donut：Top N + 其它",
                        description = "覆盖命名切片上限、其它合并和环形内径",
                        donut = true,
                        data = piePluginSlices,
                        maxNamedSlices = 4
                    )
                    gap(26f)
                    categoryBarAbilityCard(
                        title = "柱状图：分组 + 数值标签",
                        description = "覆盖多系列并列、分类轴和数值标签",
                        data = groupedBarData,
                        mode = BarChartMode.GROUPED,
                        yMax = 90f,
                        showValueLabels = true
                    )
                    gap(26f)
                    categoryBarAbilityCard(
                        title = "柱状图：堆叠 + 图例",
                        description = "覆盖堆叠累计、底部图例和自定义色板",
                        data = stackedBarData,
                        mode = BarChartMode.STACKED,
                        yMax = 160f
                    )
                }
                row(modifier = Modifier.padding(top = 26f)) {
                    chartCard("Legacy Donut 均分", 260f, 310f) {
                        bar(BarTheme(outerRadius = 82f, innerRadius = 48f, strokeWidth = 3f), equalSegments)
                    }
                    gap(18f)
                    chartCard("Legacy Donut 权重", 260f, 310f) {
                        bar(BarTheme(outerRadius = 82f, innerRadius = 38f, strokeWidth = 3f), weightedSegments)
                    }
                    gap(18f)
                    chartCard("Legacy Donut 起始角", 260f, 310f) {
                        bar(BarTheme(outerRadius = 82f, innerRadius = 58f, strokeWidth = 7f, start = 35f), weightedSegments)
                    }
                    gap(18f)
                    chartCard("Legacy Donut 细环", 260f, 310f) {
                        bar(BarTheme(outerRadius = 82f, innerRadius = 70f, strokeWidth = 2f, start = -130f), equalSegments)
                    }
                }
                row(modifier = Modifier.padding(top = 26f)) {
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

    @Test
    fun test_compose_theme_relation_graph() {
        ManualTestSupport.saveCompose("主题-图表-RelationGraph关系图") {
            themePage("关系图主题", "静态关系图支持分层、环形、固定坐标、自环、力导向和复杂依赖", width = 1540f, height = 2120f) {
                row {
                    themeCard("分层依赖关系", 535f, 500f) {
                        relationGraph(
                            RelationGraphTheme(
                                width = 485f,
                                height = 370f,
                                layout = RelationGraphLayout.Layered(roots = listOf("input")),
                                padding = 42f,
                                nodeRadius = 34f,
                                nodeFillColor = blue,
                                nodeStrokeColor = Color.makeRGB(229, 234, 244),
                                nodeTextStyle = ChartTextStyle(16f, Color.WHITE, uiFont),
                                edgeColor = Color.makeRGB(132, 145, 166),
                                edgeWidth = 2.5f,
                                edgeTextStyle = ChartTextStyle(13f, muted, uiFont),
                                arrowSize = 13f
                            ),
                            relationNodes,
                            relationEdges
                        )
                        text("适合依赖图、调用链和流程关系", modifier = Modifier.padding(top = 18f), textModifier = captionText)
                    }
                    gap(26f)
                    themeCard("环形关联网络", 535f, 500f) {
                        relationGraph(
                            RelationGraphTheme(
                                width = 485f,
                                height = 370f,
                                layout = RelationGraphLayout.Circular,
                                padding = 44f,
                                nodeRadius = 32f,
                                nodeFillColor = green,
                                nodeStrokeColor = Color.makeRGB(229, 234, 244),
                                nodeTextStyle = ChartTextStyle(16f, Color.WHITE, uiFont),
                                edgeColor = Color.makeRGB(132, 145, 166),
                                edgeWidth = 2.2f,
                                edgeLineStyle = StrokeStyle.Dashed(listOf(8f, 7f)),
                                edgeTextStyle = ChartTextStyle(13f, muted, uiFont),
                                arrowSize = 12f
                            ),
                            relationNetworkNodes,
                            relationNetworkEdges
                        )
                        text("适合人物关系、模块关联和小规模网络", modifier = Modifier.padding(top = 18f), textModifier = captionText)
                    }
                }
                row(modifier = Modifier.padding(top = 26f)) {
                    themeCard("复杂分层与回流边", 720f, 720f) {
                        relationGraph(
                            RelationGraphTheme(
                                width = 670f,
                                height = 590f,
                                layout = RelationGraphLayout.Layered(roots = listOf("start")),
                                padding = 58f,
                                nodeRadius = 30f,
                                nodeFillColor = blue,
                                nodeStrokeColor = Color.makeRGB(229, 234, 244),
                                nodeTextStyle = ChartTextStyle(14f, Color.WHITE, uiFont),
                                edgeColor = Color.makeRGB(132, 145, 166),
                                edgeWidth = 2.1f,
                                edgeTextStyle = ChartTextStyle(12f, muted, uiFont),
                                arrowSize = 12f
                            ),
                            relationComplexNodes,
                            relationComplexEdges
                        )
                        text("覆盖多路径最长分层、环回边和孤立兜底节点", modifier = Modifier.padding(top = 18f), textModifier = captionText)
                    }
                    gap(26f)
                    themeCard("固定坐标与自环", 720f, 720f) {
                        relationGraph(
                            RelationGraphTheme(
                                width = 670f,
                                height = 590f,
                                layout = RelationGraphLayout.Fixed(relationFixedPositions),
                                padding = 58f,
                                nodeRadius = 32f,
                                nodeFillColor = red,
                                nodeStrokeColor = Color.makeRGB(229, 234, 244),
                                nodeTextStyle = ChartTextStyle(15f, Color.WHITE, uiFont),
                                edgeColor = Color.makeRGB(132, 145, 166),
                                edgeWidth = 2.4f,
                                edgeTextStyle = ChartTextStyle(12f, muted, uiFont),
                                arrowSize = 12f,
                                nodeDrawer = RelationNodeDrawer { scope ->
                                    if (scope.node.id == "center") {
                                        scope.canvas.drawCircle(
                                            scope.centerX,
                                            scope.centerY,
                                            scope.radius + 10f,
                                            Paint().apply { color = Color.makeARGB(42, 44, 101, 255) }
                                        )
                                    }
                                    if (scope.node.id == "fallback") {
                                        scope.canvas.drawCircle(
                                            scope.centerX,
                                            scope.centerY,
                                            scope.radius + 7f,
                                            Paint().apply { color = Color.makeARGB(48, 255, 204, 77) }
                                        )
                                    }
                                    scope.drawDefault()
                                },
                                edgeDrawer = RelationEdgeDrawer { scope ->
                                    scope.drawDefault()
                                    if (scope.edge.label == "失败") {
                                        scope.canvas.drawCircle(
                                            (scope.startX + scope.endX) / 2f,
                                            (scope.startY + scope.endY) / 2f,
                                            5f,
                                            Paint().apply { color = red }
                                        )
                                    }
                                }
                            ),
                            relationFixedNodes,
                            relationFixedEdges
                        )
                        text("覆盖手工排版、绘制器扩展、虚线、无向边、自环标签和缺省坐标兜底", modifier = Modifier.padding(top = 18f), textModifier = captionText)
                    }
                }
                row(modifier = Modifier.padding(top = 26f)) {
                    themeCard("力导向复杂网络", 1466f, 660f) {
                        relationGraph(
                            RelationGraphTheme(
                                width = 1416f,
                                height = 530f,
                                layout = RelationGraphLayout.Force(
                                    iterations = 520,
                                    linkDistance = 270f,
                                    repulsion = 17000f,
                                    centerStrength = 0.0025f,
                                    collisionPadding = 94f,
                                    initialRadiusRatio = 0.22f
                                ),
                                padding = 64f,
                                nodeRadius = 28f,
                                nodeFillColor = blue,
                                nodeStrokeColor = Color.makeRGB(229, 234, 244),
                                nodeTextStyle = ChartTextStyle(14f, Color.WHITE, uiFont),
                                edgeColor = Color.makeRGB(132, 145, 166),
                                edgeWidth = 2.1f,
                                edgeTextStyle = ChartTextStyle(12f, muted, uiFont),
                                arrowSize = 11f
                            ),
                            relationForceNodes,
                            relationForceEdges
                        )
                        text("覆盖有环、多中心、跨簇连接和节点碰撞分离", modifier = Modifier.padding(top = 18f), textModifier = captionText)
                    }
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
    private fun UiElement.lineAbilityCard(
        title: String,
        description: String,
        series: List<LineSeries>,
        fill: Boolean,
        yMax: Float
    ) {
        themeCard(title, 520f, 420f) {
            val preparedSeries = if (fill) {
                series.map { item -> item.copy(fillColor = item.fillColor ?: translucent(item.color ?: blue, 32)) }
            } else {
                series
            }
            lineChart(
                LineChartTheme(
                    width = 470f,
                    height = 292f,
                    insets = ChartInsets(left = 50f, top = 24f, right = 24f, bottom = 46f),
                    palette = chartPalette(),
                    axis = chartAxis(),
                    legend = chartLegend(ChartLegendPosition.BOTTOM),
                    plotBackgroundColor = Color.makeRGB(248, 250, 253),
                    lineWidth = 2.8f,
                    pointRadius = 4f,
                    xTickCount = 6,
                    yTickCount = 4,
                    yMax = yMax,
                    xLabelFormatter = { "D${it.toInt()}" },
                    yLabelFormatter = { it.toInt().toString() }
                ),
                preparedSeries
            )
            text(description, modifier = Modifier.padding(top = 14f).sizeIn(maxWidth = 440f), textModifier = captionText)
        }
    }

    @UiDsl
    private fun UiElement.pieAbilityCard(
        title: String,
        description: String,
        donut: Boolean,
        data: List<PieSlice>,
        maxNamedSlices: Int? = null
    ) {
        themeCard(title, 500f, 420f) {
            if (donut) {
                donutChart(pieTheme(donut = true, maxNamedSlices = maxNamedSlices), data)
            } else {
                pieChart(pieTheme(donut = false, maxNamedSlices = maxNamedSlices), data)
            }
            text(description, modifier = Modifier.padding(top = 14f).sizeIn(maxWidth = 440f), textModifier = captionText)
        }
    }

    @UiDsl
    private fun UiElement.categoryBarAbilityCard(
        title: String,
        description: String,
        data: CategoryBarData,
        mode: BarChartMode,
        yMax: Float,
        showValueLabels: Boolean = false
    ) {
        themeCard(title, 520f, 420f) {
            categoryBarChart(
                CategoryBarTheme(
                    width = 470f,
                    height = 292f,
                    insets = ChartInsets(left = 52f, top = 24f, right = 24f, bottom = 46f),
                    palette = chartPalette(),
                    axis = chartAxis(),
                    legend = chartLegend(ChartLegendPosition.BOTTOM),
                    mode = mode,
                    plotBackgroundColor = Color.makeRGB(248, 250, 253),
                    yTickCount = 4,
                    barAreaRatio = if (mode == BarChartMode.GROUPED) 0.74f else 0.58f,
                    stackedGap = 1.5f,
                    showValueLabels = showValueLabels,
                    valueLabelTextStyle = ChartTextStyle(10f, ink, uiFont),
                    yMax = yMax,
                    yLabelFormatter = { it.toInt().toString() }
                ),
                data
            )
            text(description, modifier = Modifier.padding(top = 14f).sizeIn(maxWidth = 440f), textModifier = captionText)
        }
    }

    private fun pieTheme(donut: Boolean, maxNamedSlices: Int?): PieChartTheme {
        return PieChartTheme(
            width = 450f,
            height = 292f,
            radius = if (donut) 92f else 96f,
            innerRadius = if (donut) 54f else 0f,
            insets = ChartInsets(left = 22f, top = 26f, right = 24f, bottom = 24f),
            palette = chartPalette(),
            legend = chartLegend(ChartLegendPosition.RIGHT).copy(maxWidth = 172f),
            strokeWidth = 2f,
            labelTextStyle = ChartTextStyle(12f, Color.WHITE, uiFont),
            minLabelPercent = if (donut) 0.07f else 0.08f,
            maxNamedSlices = maxNamedSlices,
            labelFormatter = { slice, _, _ -> slice.label }
        )
    }

    private fun chartAxis(): AxisTheme {
        return AxisTheme(
            axisColor = Color.makeRGB(161, 174, 194),
            axisWidth = 1.2f,
            gridColor = Color.makeARGB(70, 161, 174, 194),
            labelTextStyle = ChartTextStyle(12f, muted, uiFont)
        )
    }

    private fun chartLegend(position: ChartLegendPosition): ChartLegendTheme {
        return ChartLegendTheme(
            position = position,
            textStyle = ChartTextStyle(12f, muted, uiFont),
            swatchSize = 10f,
            itemGap = 8f,
            columnGap = 18f,
            maxWidth = 180f
        )
    }

    private fun chartPalette(): ChartPalette {
        return ChartPalette(
            listOf(
                blue,
                green,
                yellow,
                red,
                Color.makeRGB(132, 94, 247),
                Color.makeRGB(40, 177, 210),
                Color.makeRGB(233, 92, 156),
                Color.makeRGB(98, 114, 136)
            )
        )
    }

    private fun translucent(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

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
                    fillOutlineWidth = 2.5f,
                    fillColor = color and 0x00FFFFFF or (0x55 shl 24),
                    gridCount = gridCount,
                    gridLineColor = Color.makeRGB(194, 203, 216),
                    gridFontProvider = { if (showGridText) "${it + 1}" else null },
                    gridFontSize = 10f,
                    gridFontFamily = uiFont,
                    gridFontColor = Color.makeRGB(109, 122, 142),
                    labelOuterLength = 16f,
                    labelFixPolicy = fixPolicy,
                    labelFontSize = 16f,
                    labelFontFamily = uiFont,
                    labelFontColor = Color.makeRGB(43, 52, 68),
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
        val lineTrendSeries = listOf(
            LineSeries(
                name = "日活",
                color = blue,
                points = listOf(
                    LinePoint(1f, 36f),
                    LinePoint(2f, 42f),
                    LinePoint(3f, 47f),
                    LinePoint(4f, 58f),
                    LinePoint(5f, 63f),
                    LinePoint(6f, 69f),
                    LinePoint(7f, 78f)
                )
            )
        )
        val lineServerSeries = listOf(
            LineSeries(
                name = "API",
                color = blue,
                fillColor = Color.makeARGB(34, 44, 101, 255),
                points = listOf(
                    LinePoint(1f, 52f),
                    LinePoint(2f, 58f),
                    LinePoint(3f, 62f),
                    LinePoint(4f, 71f),
                    LinePoint(5f, 69f),
                    LinePoint(6f, 78f),
                    LinePoint(7f, 84f)
                )
            ),
            LineSeries(
                name = "Worker",
                color = green,
                lineStyle = StrokeStyle.Dashed(listOf(10f, 6f)),
                points = listOf(
                    LinePoint(1f, 34f),
                    LinePoint(2f, 39f),
                    LinePoint(3f, 46f),
                    LinePoint(4f, null),
                    LinePoint(5f, 55f),
                    LinePoint(6f, 61f),
                    LinePoint(7f, 66f)
                )
            ),
            LineSeries(
                name = "降级",
                color = red,
                lineStyle = StrokeStyle.Dotted(dot = 3f, gap = 5f),
                showPoints = false,
                points = listOf(
                    LinePoint(1f, 18f),
                    LinePoint(2f, 22f),
                    LinePoint(3f, 19f),
                    LinePoint(4f, 28f),
                    LinePoint(5f, 24f),
                    LinePoint(6f, 31f),
                    LinePoint(7f, 26f)
                )
            )
        )
        val pieRuntimeSlices = listOf(
            PieSlice("Kotlin", 46f, blue),
            PieSlice("Skia", 24f, green),
            PieSlice("Compose", 18f, red),
            PieSlice("Gradle", 12f, yellow)
        )
        val piePluginSlices = listOf(
            PieSlice("文本", 31f, blue),
            PieSlice("图片", 24f, green),
            PieSlice("图表", 18f, red),
            PieSlice("关系图", 12f, yellow),
            PieSlice("表格", 8f, Color.makeRGB(132, 94, 247)),
            PieSlice("图标", 5f, Color.makeRGB(40, 177, 210)),
            PieSlice("其它", 2f, Color.makeRGB(98, 114, 136))
        )
        val groupedBarData = CategoryBarData(
            categories = listOf("周一", "周二", "周三", "周四"),
            series = listOf(
                BarSeries("生成", listOf(42f, 58f, 64f, 73f), blue),
                BarSeries("缓存", listOf(28f, 34f, 38f, 49f), green),
                BarSeries("失败", listOf(8f, 11f, 6f, 9f), red)
            )
        )
        val stackedBarData = CategoryBarData(
            categories = listOf("Q1", "Q2", "Q3", "Q4"),
            series = listOf(
                BarSeries("基础", listOf(42f, 48f, 55f, 64f), blue),
                BarSeries("扩展", listOf(22f, 28f, 34f, 39f), green),
                BarSeries("插件", listOf(14f, 20f, 26f, 31f), yellow),
                BarSeries("人工", listOf(8f, 10f, 12f, 15f), red)
            )
        )
        val radarData = listOf(
            "质量" to 0.92f,
            "速度" to 0.72f,
            "稳定" to 0.84f,
            "覆盖" to 0.64f,
            "成本" to 0.56f,
            "体验" to 0.78f
        )
        val relationNodes = listOf(
            RelationNode("input", "输入", blue),
            RelationNode("parser", "解析", red),
            RelationNode("layout", "布局", green),
            RelationNode("theme", "主题", yellow),
            RelationNode("skia", "渲染", blue),
            RelationNode("image", "图片", green)
        )
        val relationEdges = listOf(
            RelationEdge("input", "parser", "文本"),
            RelationEdge("parser", "layout", "结构"),
            RelationEdge("parser", "theme", "样式"),
            RelationEdge("layout", "skia", "坐标"),
            RelationEdge("theme", "skia", "画笔"),
            RelationEdge("skia", "image", "输出")
        )
        val relationNetworkNodes = listOf(
            RelationNode("api", "API", blue),
            RelationNode("core", "Core", red),
            RelationNode("graphics", "绘图", green),
            RelationNode("assets", "资源", yellow),
            RelationNode("server", "服务", blue)
        )
        val relationNetworkEdges = listOf(
            RelationEdge("api", "server", "请求"),
            RelationEdge("server", "core", "执行"),
            RelationEdge("core", "graphics", "生成"),
            RelationEdge("graphics", "assets", "加载"),
            RelationEdge("assets", "core", "缓存"),
            RelationEdge("graphics", "api", "返回")
        )
        val relationComplexNodes = listOf(
            RelationNode("start", "入口", blue),
            RelationNode("parse", "解析", red),
            RelationNode("rules", "规则", yellow),
            RelationNode("layout2", "布局", green),
            RelationNode("render", "渲染", blue),
            RelationNode("audit", "审计", red),
            RelationNode("done", "完成", green),
            RelationNode("orphan", "孤立", yellow)
        )
        val relationComplexEdges = listOf(
            RelationEdge("start", "parse", "输入"),
            RelationEdge("start", "rules", "配置"),
            RelationEdge("parse", "layout2", "结构"),
            RelationEdge("rules", "layout2", "约束"),
            RelationEdge("parse", "render", "预览"),
            RelationEdge("layout2", "render", "坐标"),
            RelationEdge("render", "audit", "记录"),
            RelationEdge("audit", "rules", "回流", color = red, width = 2.5f, style = StrokeStyle.Dashed(listOf(7f, 6f))),
            RelationEdge("render", "done", "产物")
        )
        val relationFixedNodes = listOf(
            RelationNode("center", "中心", blue, 36f),
            RelationNode("cache", "缓存", green, 28f),
            RelationNode("retry", "重试", red, 28f),
            RelationNode(
                id = "manual",
                label = "人工",
                color = yellow,
                radius = 30f,
                drawer = RelationNodeDrawer { scope ->
                    val marker = Path().apply {
                        moveTo(scope.centerX, scope.centerY - scope.radius - 18f)
                        lineTo(scope.centerX + 14f, scope.centerY - scope.radius - 2f)
                        lineTo(scope.centerX, scope.centerY - scope.radius + 14f)
                        lineTo(scope.centerX - 14f, scope.centerY - scope.radius - 2f)
                        closePath()
                    }
                    scope.canvas.drawPath(
                        marker,
                        Paint().apply {
                            color = yellow
                            mode = PaintMode.FILL
                            isAntiAlias = true
                        }
                    )
                    scope.drawDefault()
                }
            ),
            RelationNode("fallback", "兜底", blue, 26f)
        )
        val relationFixedPositions = mapOf(
            "center" to (335f to 295f),
            "cache" to (170f to 185f),
            "retry" to (510f to 165f),
            "manual" to (335f to 470f)
        )
        val relationFixedEdges = listOf(
            RelationEdge("center", "cache", "读写"),
            RelationEdge("cache", "center", "命中", color = green, width = 2.2f),
            RelationEdge("center", "retry", "失败", color = red, width = 2.6f),
            RelationEdge("retry", "retry", "自环", color = red, width = 2.2f, style = StrokeStyle.Dotted(3f, 4f)),
            RelationEdge("manual", "center", "复核", directed = false, color = yellow, width = 3f),
            RelationEdge("fallback", "center", "缺省坐标")
        )
        val relationForceNodes = listOf(
            RelationNode("u1", "用户", blue),
            RelationNode("u2", "成员", blue),
            RelationNode("u3", "访客", blue),
            RelationNode("svc", "服务", red, 32f),
            RelationNode("auth", "认证", red),
            RelationNode("cache2", "缓存", green),
            RelationNode("db", "数据", green, 32f),
            RelationNode("mq", "队列", yellow),
            RelationNode("log", "日志", yellow),
            RelationNode("job", "任务", green),
            RelationNode("bot", "机器人", blue),
            RelationNode("ops", "运维", red)
        )
        val relationForceEdges = listOf(
            RelationEdge("u1", "svc", "请求"),
            RelationEdge("u2", "svc", "请求"),
            RelationEdge("u3", "auth", "登录"),
            RelationEdge("auth", "svc", "授权"),
            RelationEdge("svc", "cache2", "读写"),
            RelationEdge("cache2", "db", "回源"),
            RelationEdge("svc", "db", "事务"),
            RelationEdge("svc", "mq", "投递"),
            RelationEdge("mq", "job", "消费"),
            RelationEdge("job", "db", "落库"),
            RelationEdge("svc", "log", "记录"),
            RelationEdge("ops", "log", "巡检"),
            RelationEdge("bot", "mq", "触发"),
            RelationEdge("job", "bot", "通知"),
            RelationEdge("db", "cache2", "刷新", color = green, style = StrokeStyle.Dashed(listOf(8f, 6f)))
        )
    }
}
