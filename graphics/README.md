# graphics

基于 Skiko/Skia 的图片绘制工具模块，包含 Compose 风格的 2D 图片 DSL、图表组件和一个 CPU 3D 渲染器。

## 引入

```kotlin
dependencies {
    implementation("top.e404.tavolo:tavolo-graphics:<version>")
}
```

## 包结构

模块名是 `graphics`，当前公开包名仍保留 `top.e404.tavolo.draw.*`，避免无关包名迁移影响已有调用方。

- `top.e404.tavolo.draw.compose`: 推荐使用的 2D 绘图 DSL。
- `top.e404.tavolo.draw.compose.charts`: 内置图表组件，目前包含折线图、饼图、donut、分类柱状图、雷达图和关系图。
- `top.e404.tavolo.draw.render3d`: CPU 3D 渲染相关类型和渲染函数。
- `top.e404.tavolo.draw.element`: 旧版绘图元素，已废弃，不建议新增使用。

## 测试分层

- `./gradlew :graphics:test` 只运行稳定单元测试，应使用断言验证布局、测量和绘制命令，不依赖人工查看图片。
- `./gradlew :graphics:manualTest` 运行人工测试，主要用于生成示例图片到 `run/out` 后人工检查渲染效果，允许依赖本地资源或网络资源。
- `./gradlew :graphics:jacocoTestReport` 生成单元测试覆盖率报告，HTML 报告位于 `graphics/build/reports/jacoco/test/html/index.html`。

## 2D Compose DSL

`render` 会先测量根节点尺寸，再创建 Skia `Image` 并绘制内容。布局元素包括 `column`、`row`、`box`、`table`、`waterfall`、`text`、`image` 等。

### 设计目标

`top.e404.tavolo.draw.compose` 的目标是提供一个适合图片绘制场景的宽度自适应组件库。调用方通常只描述内容、约束和组合关系，根节点会根据子元素测量结果得到最终宽高，再创建对应尺寸的 `Image`。

和直接操作 Skia `Canvas`、固定画布尺寸的绘图库或只提供低层绘制命令的方案相比，这套 DSL 更关注组件组合和自适应测量：

- 文本、图片、图表和容器组件可以先测量再布局，减少手写坐标和宽高计算。
- `column`、`row`、`box`、`table`、`waterfall` 会按子元素尺寸自动推导自身尺寸，适合生成宽度随内容变化的卡片、统计图和 README 图片。
- 仍保留底层 `canvas` 能力，用于需要自定义绘制的局部区域。

常用 `Modifier`:

- 尺寸：`size`、`width`、`height`、`sizeIn`、`widthIn`、`heightIn`
- 间距：`padding`
- 样式：`background`、`border`、`clip`、`shadow`、`rotate`

`border` 默认绘制实线，也可以通过 `StrokeStyle.Dashed` 或 `StrokeStyle.Dotted` 绘制虚线、点线边框。需要让边框沿圆角路径绘制时，传入 `shape = Shape.RoundedRect(...)`；不传 `shape` 时保持旧的矩形分边绘制行为。

文本样式可以作为 `text`、`iconText` 这类文本组件的参数传入，例如 `fontSize`、`textColor`、`fontFamily`、`textOverflow`。需要复用样式时，应使用独立的 `TextModifier`，例如 `TextModifier.font(...)`、`TextModifier.textStyle(...)`、`TextModifier.textUnderline(...)`；通用 `Modifier` 只保留布局和容器绘制属性。`TextModifier` 支持字重、斜体、行高、字间距和横向缩放。下划线可通过 `TextUnderline` 定义颜色、粗细、偏移、虚线样式，也可以使用 `TextUnderlineMode.Block` 绘制色块下划线。
图片溢出策略同样不属于通用 `Modifier`，应作为 `image` 的 `imageOverflow` 参数传入。

`Modifier` 按链式顺序逐层应用，和 Jetpack Compose 一样可以用多层 `padding` 表达外部留白和内部留白：

```kotlin
Modifier
    .padding(12f)      // 外层留白
    .shadow(blurRadius = 8f, offsetX = 0f, offsetY = 3f, shape = Shape.RoundedRect(8f))
    .background(color)
    .padding(8f)       // 背景内的内容留白
```

`size` 也遵循链式顺序：`.size(100f).padding(10f)` 表示总尺寸为 `100f`，`.padding(10f).size(100f)` 表示内容尺寸为 `100f` 且总尺寸额外包含外层 `padding`。

例如绘制虚线边框和色块下划线：

```kotlin
box(
    Modifier
        .size(180f, 80f)
        .rotate(-3f)
        .border(
            2f,
            Color.WHITE,
            StrokeStyle.Dashed(listOf(8f, 4f)),
            shape = Shape.RoundedRect(16f)
        )
) {
    text(
        "强调文本",
        style = TextStyle(
            fontSize = 28f,
            textColor = Color.WHITE,
            underline = TextUnderline(
                color = Color.YELLOW,
                thickness = 8f,
                offset = 2f,
                mode = TextUnderlineMode.Block,
                startPadding = 3f,
                endPadding = 3f
            )
        )
    )
}
```

多层圆角边框可以通过 `border + padding + clip/background` 逐层组合：

```kotlin
Modifier
    .clip(Shape.RoundedRect(32f))
    .background(Color.WHITE)
    .border(6f, Color.BLUE, shape = Shape.RoundedRect(32f))
    .padding(10f)
    .clip(Shape.RoundedRect(22f))
    .background(Color.LTGRAY)
    .border(4f, Color.RED, StrokeStyle.Dashed(listOf(12f, 6f)), shape = Shape.RoundedRect(22f))
    .padding(10f)
    .clip(Shape.RoundedRect(12f))
    .background(Color.DKGRAY)
    .border(3f, Color.WHITE, StrokeStyle.Dotted(dot = 2f, gap = 7f), shape = Shape.RoundedRect(12f))
    .padding(20f)
```

### 字体管理

Compose DSL 的 `fontFamily` 只保存字体名，不再把 Skia `Typeface` 放进语法树。这样语法树和后续远程渲染协议可以序列化为字符串配置，渲染端再通过 common 模块提供的全局 `FontManager` 把名字解析成本地 `Typeface`。

字体来源分两类：

- 业务随请求或部署包提供的字体文件，使用 `FontManager.registerFile("brand-title", file)` 或 `registerBytes` 注册。
- 渲染机器已有的系统字体，使用 `FontManager.registerSystem("ui", "Microsoft YaHei")` 建立稳定别名。
- 项目内置字体清单由 common 模块的 `TavoloFonts` 提供，默认字体为 `TavoloFonts.LW`。

渲染时通过名称引用字体：

```kotlin
import top.e404.tavolo.util.FontManager
import top.e404.tavolo.TavoloFonts

TavoloFonts.register(TavoloFonts.LW)
FontManager.registerFile("brand-title", File("font/BrandTitle.ttf"))
FontManager.registerSystem("ui", "Microsoft YaHei")
FontManager.defaultFamily = TavoloFonts.LW

render {
    text(
        "标题",
        fontSize = 28f,
        fontFamily = TavoloFonts.LW
    )
}
```

也可以把字体样式放进 `TextModifier` 复用，和通用布局 `Modifier` 分开传入：

```kotlin
val titleText = TextModifier
    .font(fontSize = 28f, textColor = Color.WHITE, fontFamily = TavoloFonts.LW)
    .textUnderline(
        TextUnderline(
            color = Color.YELLOW,
            thickness = 6f,
            offset = 3f,
            strokeStyle = StrokeStyle.Dashed(listOf(8f, 4f))
        )
    )

render {
    text("标题 A", textModifier = titleText)
    text("标题 B", modifier = Modifier.padding(top = 8f), textModifier = titleText)
}
```

远程渲染协议应该传 `"fontFamily": "lxgw-wenkai"` 这类名称。服务端启动或请求预处理阶段负责注册可用字体；如果名称没有注册，管理器会尝试按系统字体 family 查找，最后回退到默认字体。

### margin 迁移

Compose DSL 已移除 `margin` API，不再由父布局特殊读取子元素外边距。外部留白和内部留白统一用多层 `padding` 表达，并且 `Modifier` 顺序会影响测量、布局和绘制结果。

旧写法中表示外部留白的 `margin`，迁移时应放到链式调用靠外的一侧：

```kotlin
// 旧写法
Modifier
    .background(color)
    .padding(8f)
    .margin(12f)

// 新写法：外部留白在 background 之前
Modifier
    .padding(12f)
    .background(color)
    .padding(8f)
```

旧写法中放在固定尺寸元素之后的 `margin`，迁移时也应放到 `size` 之前，避免把留白算进固定内容区域：

```kotlin
// 旧写法
Modifier
    .size(100f)
    .margin(right = 10f)

// 新写法
Modifier
    .padding(right = 10f)
    .size(100f)
```

需要圆角背景时，`clip` 应放在 `background` 之前，让背景绘制发生在裁剪区域内：

```kotlin
Modifier
    .clip(Shape.RoundedRect(8f))
    .background(color)
```

### 瀑布流布局

`waterfall` 适合高度不一致的卡片列表。它使用固定列数和总宽度计算 `columnWidth`，先用 `columnWidth` 作为子元素最大宽度测量每个子项，再把子项放入当前累计高度最短的列。

核心参数：

- `columns`: 列数，传入小于 `1` 的值时会按 `1` 列处理。
- `width`: 瀑布流内容宽度，不包含瀑布流自身 `modifier` 产生的 padding 或 border。
- `columnSpacing`: 列间距。
- `rowSpacing`: 同一列内相邻子项的纵向间距。
- `columnWidth`: 在 `block` 中可读取的单列宽度，常用于设置卡片外层宽度。

如果卡片需要视觉上整列对齐，推荐把卡片外层宽度显式设为 `columnWidth`。卡片内部仍然可以继续使用 `box`、`row`、`column`、`text`、`image` 等普通组件。

```kotlin
data class NoteCard(
    val title: String,
    val description: String
)

val cards = listOf(
    NoteCard("发布检查", "运行测试并同步 README 示例图。"),
    NoteCard("布局迁移", "描述文本越长，换行越多，卡片自然高度越高。"),
    NoteCard("文档示例", "人工测试源码和 README 图片保持一致，重跑人工测试即可更新示例图。")
)

render(backgroundColor = Color.WHITE) {
    waterfall(
        columns = 3,
        width = 960f,
        columnSpacing = 16f,
        rowSpacing = 16f
    ) {
        cards.forEach { card ->
            column(
                modifier = Modifier
                    .width(columnWidth)
                    .clip(Shape.RoundedRect(12f))
                    .background(Color.makeRGB(250, 251, 252))
                    .border(1f, Color.makeRGB(220, 226, 232), shape = Shape.RoundedRect(12f))
                    .padding(16f)
            ) {
                text(card.title, fontSize = 22f, textColor = Color.BLACK)
                text(
                    card.description,
                    modifier = Modifier
                        .padding(top = 8f)
                        .sizeIn(maxWidth = columnWidth - 32f),
                    fontSize = 16f,
                    textColor = Color.makeRGB(80, 92, 108)
                )
            }
        }
    }
}
```

```kotlin
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import top.e404.tavolo.draw.compose.*
import java.io.File

fun main() {
    val image = render(backgroundColor = Color.WHITE) {
        column(
            modifier = Modifier
                .padding(24f)
                .background(Color.makeRGB(245, 247, 250))
        ) {
            text(
                "Tavolo",
                fontSize = 36f,
                textColor = Color.makeRGB(32, 38, 46)
            )
            text(
                "Compose style image rendering",
                modifier = Modifier
                    .padding(top = 8f),
                fontSize = 20f,
                textColor = Color.makeRGB(91, 103, 120)
            )
            row(modifier = Modifier.padding(top = 20f)) {
                box(
                    modifier = Modifier
                        .size(120f, 56f)
                        .background(Color.makeRGB(64, 128, 255))
                        .clip(Shape.RoundedRect(8f))
                ) {
                    text(
                        "Button",
                        modifier = Modifier
                            .padding(horizontal = 24f, vertical = 12f),
                        fontSize = 22f,
                        textColor = Color.WHITE
                    )
                }
            }
        }
    }

    File("out/card.png").apply { parentFile.mkdirs() }
        .writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
}
```

## 图表

图表组件面向静态图片渲染，适合聊天图片、统计卡片和离线报表。所有图表本质上都是固定宽高的 `CanvasElement`，外层仍然用 Compose DSL 的 `row`、`column`、`box`、`padding`、`background` 组合标题、说明和卡片样式。

统一导入：

```kotlin
import org.jetbrains.skia.Color
import top.e404.tavolo.draw.compose.*
import top.e404.tavolo.draw.compose.charts.*
```

### 通用配置

多数新图表共享以下配置：

- `ChartInsets`: 控制图表内容、坐标轴标签和图例之间的留白。
- `ChartPalette`: 多 series 或多分类默认色板，显式传入颜色时优先使用数据自身颜色。
- `AxisTheme`: 坐标轴、网格线和轴标签样式，用于折线图和分类柱状图。
- `ChartLegendTheme`: 图例样式，`ChartLegendPosition.RIGHT` 表示右侧图例，`BOTTOM` 表示底部图例，`NONE` 表示不绘制图例。
- `ChartTextStyle`: 图表内部文本样式，包含字号、颜色和字体名。

推荐把标题、子标题、脚注等放在图表外层：

```kotlin
column(
    modifier = Modifier
        .padding(24f)
        .background(Color.WHITE)
        .padding(20f)
) {
    text("在线玩家趋势", fontSize = 24f, textColor = Color.makeRGB(32, 38, 46))
    lineChart(theme = lineTheme, series = playerSeries)
    text("数据按小时聚合", modifier = Modifier.padding(top = 8f), fontSize = 14f, textColor = Color.GRAY)
}
```

### 折线图

入口：`lineChart(theme: LineChartTheme, series: List<LineSeries>)`

适用场景：时间趋势、版本变化、在线人数、请求量、延迟等连续数据。`single_linechart` 使用一个 `LineSeries`，`multi_linechart` 使用多个 `LineSeries`。

关键数据模型：

- `LinePoint(x, y)`: `x` 是数值坐标，`y` 允许为 `null`；`null` 会断开折线，用于缺失数据。
- `LineSeries.name`: 图例名称。
- `LineSeries.color`: 当前 series 颜色；不传则从 `ChartPalette` 取色。
- `LineSeries.fillColor`: 面积填充色；不传则只画线。
- `LineSeries.lineStyle`: 支持 `StrokeStyle.Solid`、`Dashed`、`Dotted`。
- `LineSeries.showPoints`: 是否绘制点标记。

常用主题参数：

- `xMin` / `xMax`、`yMin` / `yMax`: 手动固定坐标范围，适合多张图统一尺度。
- `includeZeroY`: 自动计算 y 轴范围时是否包含 0。
- `xTickCount` / `yTickCount`: 刻度段数。
- `xLabelFormatter` / `yLabelFormatter`: 轴标签格式化。
- `maxPointsPerSeries`: 数据抽样上限，避免超长数据压缩成一团。

```kotlin
val lineTheme = LineChartTheme(
    width = 620f,
    height = 280f,
    insets = ChartInsets(left = 56f, top = 24f, right = 24f, bottom = 48f),
    legend = ChartLegendTheme(position = ChartLegendPosition.BOTTOM),
    xTickCount = 6,
    yTickCount = 4,
    yMax = 100f,
    xLabelFormatter = { "D${it.toInt()}" },
    yLabelFormatter = { it.toInt().toString() }
)

val series = listOf(
    LineSeries(
        name = "API",
        color = Color.makeRGB(54, 112, 255),
        fillColor = Color.makeARGB(34, 54, 112, 255),
        points = listOf(
            LinePoint(1f, 52f),
            LinePoint(2f, 58f),
            LinePoint(3f, 62f),
            LinePoint(4f, 71f)
        )
    ),
    LineSeries(
        name = "Worker",
        color = Color.makeRGB(50, 181, 128),
        lineStyle = StrokeStyle.Dashed(listOf(10f, 6f)),
        points = listOf(
            LinePoint(1f, 34f),
            LinePoint(2f, 39f),
            LinePoint(3f, null),
            LinePoint(4f, 55f)
        )
    )
)

lineChart(lineTheme, series)
```

### 饼图和 Donut

入口：

- `pieChart(theme: PieChartTheme, data: List<PieSlice>)`
- `donutChart(theme: PieChartTheme, data: List<PieSlice>)`

适用场景：分类占比、构成比例、Top N 分类。`simple_pie` 使用普通 `pieChart`，`advanced_pie` 可开启 `maxNamedSlices` 合并长尾；donut 通过 `innerRadius` 表达环形图。

关键数据模型：

- `PieSlice.label`: 扇区名称。
- `PieSlice.value`: 扇区数值；非正数会被忽略。
- `PieSlice.color`: 扇区颜色；不传则从 `ChartPalette` 取色。

常用主题参数：

- `radius`: 外半径。
- `innerRadius`: 内半径，`0f` 是实心饼图，大于 `0f` 是 donut。
- `maxNamedSlices`: 只保留前 N 个分类，其余合并为 `othersLabel`。
- `minLabelPercent`: 低于该占比的小扇区不画扇区内标签。
- `labelFormatter`: 扇区内标签格式。
- `legendLabelFormatter`: 图例标签格式，默认包含百分比。
- `startAngle`: 起始角度，默认从顶部开始。

```kotlin
val pieData = listOf(
    PieSlice("Paper", 52f),
    PieSlice("Spigot", 31f),
    PieSlice("Fabric", 12f),
    PieSlice("Other", 5f)
)

pieChart(
    theme = PieChartTheme(
        width = 420f,
        height = 240f,
        radius = 82f,
        legend = ChartLegendTheme(position = ChartLegendPosition.RIGHT),
        maxNamedSlices = 3,
        minLabelPercent = 0.06f
    ),
    data = pieData
)

donutChart(
    theme = PieChartTheme(
        width = 420f,
        height = 240f,
        radius = 82f,
        innerRadius = 48f,
        legend = ChartLegendTheme(position = ChartLegendPosition.RIGHT),
        maxNamedSlices = 3,
        othersLabel = "其它"
    ),
    data = pieData
)
```

当前饼图标签不会做复杂避让。分类很多或小扇区很多时，优先用 `maxNamedSlices` 合并长尾，或提高 `minLabelPercent` 隐藏小标签。

### 分类柱状图

入口：`categoryBarChart(theme: CategoryBarTheme, data: CategoryBarData)`

适用场景：分类对比、版本分布、按时间桶统计。`simple_bar` 可用单 series；`advanced_bar` 可用多 series，并选择分组或堆叠。

关键数据模型：

- `CategoryBarData.categories`: 分类轴标签。
- `CategoryBarData.series`: 多个柱状图系列。
- `BarSeries.values`: 与 `categories` 按下标对应；缺失、非有限数值会按 `0f` 处理。

常用主题参数：

- `mode`: `BarChartMode.GROUPED` 分组柱，`BarChartMode.STACKED` 堆叠柱。
- `barAreaRatio`: 每个分类槽内柱子占用比例。
- `stackedGap`: 堆叠柱块之间的间隙。
- `showValueLabels`: 是否显示数值标签。
- `categoryLabelEvery`: 分类很多时每隔几个分类显示一个标签。
- `yMin` / `yMax`: 固定数值轴范围。

```kotlin
val barData = CategoryBarData(
    categories = listOf("1.20", "1.21", "1.22", "1.23"),
    series = listOf(
        BarSeries("Servers", listOf(80f, 126f, 142f, 155f)),
        BarSeries("Players", listOf(430f, 610f, 690f, 760f)),
        BarSeries("Errors", listOf(8f, 12f, 7f, 10f))
    )
)

categoryBarChart(
    theme = CategoryBarTheme(
        width = 620f,
        height = 280f,
        mode = BarChartMode.GROUPED,
        legend = ChartLegendTheme(position = ChartLegendPosition.BOTTOM),
        showValueLabels = true,
        yMax = 800f
    ),
    data = barData
)

categoryBarChart(
    theme = CategoryBarTheme(
        width = 620f,
        height = 280f,
        mode = BarChartMode.STACKED,
        legend = ChartLegendTheme(position = ChartLegendPosition.BOTTOM),
        stackedGap = 1.5f,
        yMax = 1500f
    ),
    data = barData
)
```

### Legacy Donut

旧入口 `bar(theme: BarTheme, data: List<Pair<Int, Float>>)` 仍可用，但它实际绘制的是 donut，不是柱状图。它只负责扇区绘制，没有内置标签、图例、Top N 合并等能力；新代码优先使用 `pieChart` / `donutChart`。

```kotlin
bar(
    theme = BarTheme(
        outerRadius = 82f,
        innerRadius = 48f,
        start = -90f,
        strokeWidth = 3f
    ),
    data = listOf(
        Color.makeRGB(54, 112, 255) to 42f,
        Color.makeRGB(50, 181, 128) to 24f,
        Color.makeRGB(245, 167, 36) to 18f
    )
)
```

### 雷达图

入口：`radar(theme: RadarTheme, data: List<Pair<String, Float>>)`

适用场景：能力模型、评分维度、质量画像。建议把值归一化到 `0f..1f`，便于不同图之间对比。

常用主题参数：

- `radius`: 雷达图半径。
- `gridCount`: 网格层数。
- `gridFontProvider`: 网格文字提供器，返回 `null` 可隐藏某层文字。
- `labelFixPolicy`: 标签修正策略，常用 `RATED_FIX`、`MOVE_OUTSIDE`、`NONE`、`TILT`。
- `fillColor` / `fillOutlineColor`: 数据区域填充和描边。

```kotlin
radar(
    RadarTheme(
        width = 320f,
        height = 320f,
        radius = 96f,
        gridCount = 5,
        gridFontProvider = { "${it + 1}" },
        labelFixPolicy = RadarFixPolicy.RATED_FIX,
        fillOutlineColor = Color.makeRGB(54, 112, 255),
        fillColor = Color.makeARGB(80, 54, 112, 255)
    ),
    listOf(
        "质量" to 0.92f,
        "速度" to 0.72f,
        "稳定" to 0.84f,
        "覆盖" to 0.64f
    )
)
```

### 关系图

入口：`relationGraph(theme: RelationGraphTheme, nodes: List<RelationNode>, edges: List<RelationEdge>)`

适用场景：依赖图、调用链、人物关系、模块关联。节点通过 `RelationNode.id` 和边的 `from` / `to` 关联。

布局模式：

- `RelationGraphLayout.Layered`: 分层布局，适合 DAG、流程图和依赖链。
- `RelationGraphLayout.Circular`: 环形布局，适合小规模关联网络。
- `RelationGraphLayout.Fixed`: 固定坐标布局，适合人工排版或部分节点定位。
- `RelationGraphLayout.Force`: 力导向布局，适合有环、多中心的复杂网络。

扩展点：

- `RelationNodeDrawer`: 自定义节点绘制。
- `RelationEdgeDrawer`: 自定义边绘制，包含普通边和自环边。
- 绘制器可以配置在 `RelationGraphTheme` 上作为全局默认，也可以配置到单个 `RelationNode` 或 `RelationEdge` 上做局部覆盖。
- 绘制上下文会暴露画布、测量上下文、节点坐标、边端点和 `drawDefault()`，可在默认图形前后追加高亮、徽标、状态标记等自定义内容。

```kotlin
relationGraph(
    RelationGraphTheme(
        width = 620f,
        height = 360f,
        layout = RelationGraphLayout.Layered(roots = listOf("input")),
        nodeRadius = 30f,
        arrowSize = 12f
    ),
    nodes = listOf(
        RelationNode("input", "输入", Color.makeRGB(54, 112, 255)),
        RelationNode("parser", "解析", Color.makeRGB(232, 76, 92)),
        RelationNode("render", "渲染", Color.makeRGB(50, 181, 128))
    ),
    edges = listOf(
        RelationEdge("input", "parser", "文本"),
        RelationEdge("parser", "render", "结构")
    )
)
```

### 人工测试示例

图表能力合集的人工测试会生成包含折线图、饼图、donut、分组柱状图、堆叠柱状图、legacy donut 和 radar 变体的示例图片：

```powershell
.\gradlew.bat :graphics:manualTest --tests "*ComposeThemeManualTest.test_compose_theme_charts"
```

输出文件：

```text
run/out/compose/主题-图表-统计图能力合集.png
```

## 3D 渲染

`render3d` 包提供基础向量、矩阵、网格、相机和渲染配置。适合渲染简单几何体、带 UV 的方块模型和静态预览图。

```kotlin
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import top.e404.tavolo.draw.render3d.*
import java.io.File

fun main() {
    val mesh = createCuboid(
        dimensions = Vec3(2f, 2f, 2f),
        baseColor = Color.makeRGB(72, 149, 239)
    )

    val image = renderSceneToImage(
        scene = Scene(listOf(mesh)),
        config = RenderConfig(
            width = 800,
            height = 600,
            camera = OrbitCamera(
                target = Vec3(0f, 0f, 0f),
                yaw = 35f,
                pitch = 25f,
                distance = 7f
            ),
            backgroundColor = Color.makeRGB(18, 22, 28),
            renderFaces = true,
            usePerspective = true,
            useBackFaceCulling = true
        )
    )

    File("out/cube.png").apply { parentFile.mkdirs() }
        .writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
}
```

## 调试

`debugBaseElement(layer, element, stringBuilder)` 可以输出 Compose DSL 渲染树的测量结果和 modifier 列表，排查布局尺寸或裁剪问题时比较有用。
