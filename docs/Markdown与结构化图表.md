# Markdown 与结构化图表

`graphics` 提供简单 Markdown 和结构化图表的原生 Compose 元素及独立图片入口。Markdown 由 commonmark-java 解析，HTML 由 jsoup 组织成静态文档树，再交给 Tavolo 排版；代码高亮使用 Highlights，全部在 JVM／Skiko 内执行。图表复用已有折线、分类柱状、饼图、环形图和雷达图组件。

库 API 和 HTTP PNG 接口共用同一渲染链；HTTP 请求直接携带结构化 JSON，不把文档或点集塞入指令的字符串参数。既有指令接口保持不变。

## 入口与字体

```kotlin
import top.e404.tavolo.draw.compose.*
import top.e404.tavolo.draw.compose.charts.*
import top.e404.tavolo.util.FontManager

// 使用部署环境已有字体，或通过 registerFile/registerBytes 显式注册自带字体。
val ui = FontManager.registerSystem("report-ui", "Noto Sans SC")
val mono = FontManager.registerSystem("report-mono", "Consolas")
```

| 用途 | Markdown | 图表 |
| --- | --- | --- |
| 嵌入现有布局 | `UiElement.markdown` | `UiElement.structuredChart` |
| 返回 Skia Image | `renderMarkdown` | `renderChart` |
| 返回 PNG ByteArray | `renderMarkdownPng` | `renderChartPng` |

返回的 `Image` 由调用方关闭，PNG 入口内部关闭输出图片。字体沿用 `FontManager`，没有隐式字体下载。中文、Emoji 和组合字符的字形取决于调用方注册的字体及系统 fallback；例如 Windows 的部分 Emoji 字体将国旗显示为区域字母，而不是彩色旗帜。跨平台要求一致字形时，应由应用显式提供相同字体。

静态文档建议使用无衬线正文和独立等宽代码字体。上述例子使用 Noto Sans SC 与 Consolas；先确认部署机器已安装实际 family，不能把字体别名当成字体安装。可用 `FontManager.resolve(ui).familyName` 核对字体。

宽度是组件宽度，包含自身主题内边距；高度根据文字、标签和图例测量。嵌入时支持 `Modifier.width/sizeIn` 以及外层 padding、border，实际阅读宽度会扣除这些开销。固定高度不足时明确报错，不静默截断内容。祖先容器仍需为组件分配足够空间，不应在外层另加裁剪。

## Markdown

### 独立 PNG 与嵌入

```kotlin
val markdownStyle = MarkdownStyle(fontFamily = ui, codeFontFamily = mono)
val png = renderMarkdownPng(
    source = "# 报告\n\n中文、**粗体**与 `code`。\n\n[项目](https://github.com/4o4E/Tavolo)",
    width = 800f,
    style = markdownStyle
)
java.io.File("report.png").writeBytes(png)

val image = render(pixelRatio = 2f) {
    column(Modifier.padding(16f)) {
        markdown("## 可嵌入的正文\n\n不需要调用方编写绘图代码。", 800f, markdownStyle)
    }
}
image.close()
```

`width` 和样式尺寸均为逻辑像素。`renderMarkdown`、`renderMarkdownPng` 默认 `pixelRatio = 2f`，例如 800px 阅读宽度输出 1600px 宽的图片；文字和矢量内容直接在高分辨率画布绘制，不是放大已有位图。显示时按逻辑尺寸缩放，可减少文字模糊。需要原始像素尺寸时显式传入 `pixelRatio = 1f`。2 倍像素密度的画布像素量为 1 倍的 4 倍；调用方提供的位图也应有足够分辨率。嵌入 Compose 时在外层 `render` 设置该参数，未指定时仍为 1 倍。

### GitHub 风格排版

默认字号为 16px、行高为 1.5、段间距为 16px、页内边距为 32px。参考 [GitHub Primer 的 Markdown 样式](https://github.com/primer/css/tree/main/src/markdown)：标题使用独立字号和紧凑行高，一级、二级标题带底部分隔线；代码使用缩小字号、圆角浅灰背景；引用与 Alerts 使用侧边线；表格使用紧凑单元格、细边框与隔行底色。列表及表格内不累加段落末尾空白。

行内代码的圆角背景和内边距参与混合文字排版，换行时不会留下脱离文字的空背景。任务状态使用固定比例的 SVG 复选框，与首行文字对齐，不依赖 Emoji 字体；长任务换行后图标仍跟随首行。

正文和代码字体由 `fontFamily`、`codeFontFamily` 指定，不改变应用全局字体。窄图可将 `pagePadding` 设为 16px，正文保持 16px。静态链接显示“文案 (目标)”，文案已经是目标时不重复；`details` 显示展开标记及全部内容。


### 图片解析 lambda

```kotlin
// downloadedBytes 由应用提供；Tavolo 不决定网络地址、文件目录或认证方式。
val avatar = org.jetbrains.skia.Image.makeFromEncoded(downloadedBytes)
try {
    val png = renderMarkdownPng(
        source = "前面的文字\n\n![头像](asset:avatar)\n\n后面的文字",
        width = 800f,
        style = markdownStyle,
        imageResolver = { destination ->
            require(destination == "asset:avatar") { "未知图片资源: $destination" }
            avatar
        }
    )
    java.io.File("with-image.png").writeBytes(png)
} finally {
    avatar.close()
}
```

`MarkdownImageResolver` 是 `(destination: String) -> Image`：

- 测量时调用；同一元素内相同地址只解析一次。网络、缓存、文件读取和异常处理均由调用方决定。
- 返回图片归调用方所有；必须存活到该元素最后一次绘制完成，Tavolo 不关闭它。
- 图片参与行内排版，与前后文字保持顺序；默认保留原始尺寸并按阅读宽度缩小。HTML `width`、`height` 可指定尺寸，`width` 支持百分比；图片内容保持比例放入目标区域，不提供浏览器式浮动环绕。
- 缺少 resolver、图片尺寸无效时抛出 `IllegalArgumentException`；resolver 抛出的异常原样向调用方传播，没有占位图或静默跳过。
- 传入 `file:`、HTTP URL 或其他地址不会自行触发加载；这些字符串只会交给调用方的 resolver。

### 简单 Markdown 支持范围

Tavolo 面向静态报告中的文本、列表、代码和表格排版，支持下表列出的语法与静态 HTML 子集。完整 GitHub Markdown 页面效果、Mermaid 和 LaTeX 应在外部渲染完成，再通过已有图片能力使用；Tavolo 不内置这些专用渲染能力或浏览器。

| 内容 | 行为 |
| --- | --- |
| 标题、段落、强调 | 支持 1–6 级标题、粗体、斜体及重叠样式 |
| 软／硬换行 | 默认按 Markdown 文件合并软换行；`MarkdownContext(softBreak = MarkdownSoftBreak.NEWLINE)` 用于 Issue/评论；硬换行始终保留 |
| 有序、无序及嵌套列表 | 保留起始编号、层级和缩进；HTML 有序列表支持 `start`、`value` |
| 任务列表 | 显示已勾选／未勾选状态，支持嵌套，不提供点击修改 |
| 引用、Alerts | 支持嵌套引用及 NOTE、TIP、IMPORTANT、WARNING、CAUTION 五种提示块 |
| 链接、自动链接 | 保留文字和可辨识的目标；自动识别 URL、邮箱，不访问目标。中文标点与后续正文不纳入自动链接，中文域名或路径可保留；显式链接的目标不做改写 |
| 脚注 | 按引用顺序编号，重复引用共享定义，正文统一放到文末；静态图不显示返回按钮 |
| 行内代码 | 遵循 CommonMark 空白归一化，反引号、美元符号及 Unicode 文字均按代码文字保留 |
| 代码块 | 保留空格和字符，长行换行；带语言围栏通过 Highlights 高亮；未知语言及 `math`／`mermaid` 围栏按普通代码显示 |
| GFM 表格、删除线 | 等宽分列、表头与对齐、单元格换行；删除线可叠加其他样式 |
| 常用 HTML 行内语义 | 支持 `br`、`wbr`、`b/strong`、`i/em`、`del/s/strike`、`ins`、`code/tt/kbd/samp`、`sub/sup`、`small`、`mark`、`q`、`ruby/rt/rp`、`bdo`；普通语义容器保留子内容 |
| HTML 块级语义 | 支持标题、段落、引用、列表、`div`、`details/summary`、`dl/dt/dd`、`figure/figcaption`；`details` 始终展开，包括嵌套内容 |
| HTML 表格 | 支持 `thead/tbody/tfoot`、表头、说明、`colspan` 与 `rowspan`；行高按跨行内容共同测量 |
| HTML 图片与对齐 | 支持 `img` 尺寸、块和单元格 `align`；`picture` 可按亮／暗主题选择 `source`，`srcset` 使用首个候选 |

HTML 不执行脚本、事件处理器、任意 CSS 或嵌入网页；脚本、样式和活动嵌入元素被移除，注释不显示。标签解析不是浏览器 DOM/CSS 实现。`picture` 的媒体选择支持无条件、`screen/all` 和 `prefers-color-scheme`；调用方可用 resolver 提供相应静态资源。

静态链接不可点击，同时显示文案和目标；相对链接保留原始目标。`@用户`、`#编号`、`owner/repository#编号` 可通过 `MarkdownContext.referenceResolver` 判断引用是否有目标，有目标时使用链接配色，没有上下文时保留原文，不猜测仓库或请求 GitHub。脚注通过编号对应文末正文，不附加内部锚点。

### 原生渲染与代码高亮

调用 `renderMarkdownPng` 或 `markdown` 即可使用 JVM／Skiko 渲染，不需要 Node.js 或浏览器。图片资源由调用方通过 `imageResolver` 显式提供，布局缓存只属于当前文档。

美元符号及 `:rocket:`、`:octocat:` 等冒号文本遵循普通 Markdown 规则，`$…$`、`$$…$$` 不触发数学解析。复杂文档由外部系统负责渲染，本库不提供公式或 Mermaid 的专用接口。

Java 运行基线保持 11；第三方渲染依赖从 Maven Central 解析。Tavolo 使用 Kotlin 2.2.21、KSP 2.2.21-2.0.4，Skiko JVM 与 native 统一为 0.9.30。代码高亮使用 [Highlights](https://github.com/SnipMeDev/Highlights) 1.0.0，其 JVM 制品可在 Java 11 使用。

`MarkdownStyle` 可设置字体、字号、文字与背景颜色、链接颜色、代码背景、引用线、表格边框、间距、内边距、行高及 `darkTheme`。`darkTheme` 控制代码 token、Alerts、次要文字和行内背景的主题；正文背景、文字、链接、块级代码背景及边框颜色仍由相应字段显式设置。深色验收图采用 `#0d1117` 背景、`#f0f6fc` 正文、`#4493f8` 链接、`#151b23` 代码背景及 `#3d444d` 边框。

### 尺寸和错误

空文档、非正或非有限字号／宽度、负间距、表格或嵌套导致阅读宽度不足，以及固定高度不足时明确报错。文字和代码按宽度换行，保持字符及顺序。

缺少图片 resolver、无效图片或资源解析失败均向调用方报告，不产生占位图、不隐藏错误。未知代码语言是正常文档内容，按普通代码显示。

## 结构化图表

调用方通过 `StructuredChart` 的具体类型明确选择图表，不根据数据猜图表类型或单位。`title` 是标题，`unit` 是数值标签后缀；传入 `%` 不会自动乘 100，百分比数据应直接传 0–100 口径。

### 单图与多曲线

```kotlin
val chartStyle = StructuredChartStyle(width = 900f, fontFamily = ui)
val pie = StructuredChart.Pie(listOf(
    PieSlice("完成", 75f), PieSlice("剩余", 25f)
))
val piePng = renderChartPng(pie, title = "任务统计", unit = "项", style = chartStyle)

val lines = StructuredChart.Line(
    series = listOf(
        LineSeries("实际已用", listOf(LinePoint(0f, 66f), LinePoint(24f, 89f)),
            color = 0xFF1E6ED2.toInt(), showPoints = false),
        LineSeries("均速参考", listOf(LinePoint(0f, 31.87f), LinePoint(24f, 46.16f)),
            color = 0xFFDC781E.toInt(), lineStyle = StrokeStyle.Dashed(listOf(10f, 6f)), showPoints = false)
    ),
    xAxis = NumericXAxis(min = 0f, max = 24f, interval = 4f, unit = "h"),
    options = StructuredLineOptions(yRange = ChartRange(0f, 100f))
)
val linePng = renderChartPng(lines, "双曲线示例", "%", chartStyle)
```

上面的两端点示例仅演示调用。完整 Codex 验收使用 `graphics/src/manualTest/resources/issue7/codex.csv` 的全部 25 个点，不以端点替代中间数据，也不根据实际用量拟合参考线。

```kotlin
val image = render {
    column {
        markdown("## 图表说明\n\n正文与图表可以组合。", 900f, markdownStyle)
        structuredChart(lines, "趋势", "%", chartStyle)
    }
}
image.close()
```

### 时间轴

```kotlin
val t0 = java.time.Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
val timeline = StructuredChart.TimeLine(
    series = listOf(
        TimeLineSeries("指标", listOf(
            TimeLinePoint(t0, 20f),
            TimeLinePoint(t0 + 3_600_000, null),
            TimeLinePoint(t0 + 7_200_000, 50f)
        ))
    ),
    xAxis = TimeXAxis(
        intervalMillis = 3_600_000,
        zone = java.time.ZoneId.of("Asia/Shanghai"),
        format = TimeAxisFormat.HOUR_MINUTE,
        style = XAxisStyle(name = "北京时间")
    ),
    options = StructuredLineOptions(yRange = ChartRange(0f, 100f))
)
val png = renderChartPng(timeline, "小时指标", "%", chartStyle)
```

时间输入是 `Long` 毫秒时间戳。先以整数减去共同时间原点，再转换底层绘图坐标；不会把绝对毫秒时间戳直接转成 Float。各曲线独立排序，并按真实横坐标对齐，不按数组下标对齐。不同曲线可共用同一时间点，同一曲线内重复横坐标报错。

`null` 明确表示缺失并断线，不补零。序列可以不同长度；没有显式 `null` 的两个相邻样本会直接相连，因为库不知道业务采样周期。不做抽样、拟合或平滑。关闭 `showPoints` 时孤立点没有可连接线段；要显示这些点，应保持 `showPoints = true`。

空序列、全缺失序列、NaN／Infinity、范围溢出、手动范围排除数据、或时间跨度与采样精度无法同时表示时，明确抛出 `IllegalArgumentException`，不合并或丢弃数据。

### 横轴常用选项

配置语义参考 [ECharts 坐标轴](https://echarts.apache.org/handbook/zh/concepts/axis/)，但不是 ECharts option 的完整兼容层。

| 配置类型 | 适用范围与参数 |
| --- | --- |
| `NumericXAxis` | 数值折线：`min/max`、`boundaryGap`、`splitNumber`、`interval`、`unit`、`formatter(Float)`、`style` |
| `TimeXAxis` | 时间折线：毫秒 `min/max`、`boundaryGap`、`splitNumber`、`intervalMillis`、`zone`、`format`、`pattern`、`formatter(Instant)`、`style` |
| `CategoryXAxis` | 分类柱状：`labelInterval`、`formatter(String, Int)`、`style`；保留完整分类和两端半组空间，不提供会截掉柱子的范围裁切 |

- 数值轴自动选择易读间隔（1、2、5 的十进制倍数），`splitNumber` 是期望分段数；显式 `interval` 优先。未指定的边界可向完整刻度外扩。
- 时间轴选择常用固定时长间隔，`intervalMillis` 优先。刻度按 Unix epoch 的固定时长对齐；时区只影响显示，夏令时不会改变实际间距。没有“日历月／年”步进语义。
- `AxisBoundaryGap(start, end)` 表示两端相对数据跨度的留白比例；显式 `min/max` 覆盖对应一侧留白。数据不因留白或范围设置而改变。
- 分类 `labelInterval = 0` 显示每项，`1` 表示显示一项、跳过一项标签，依此类推；不会减少柱子或数据。
- 时间格式提供 `DATE`、`HOUR_MINUTE`、`HOUR_MINUTE_SECOND`、`DATE_TIME`、`MILLISECOND`；优先级为 `formatter > pattern > format`。`pattern` 使用 Java `DateTimeFormatter` 语法，默认时区 UTC。
- 刻度最多 1000 个；过密、无可用刻度或标签重叠时明确报错，调用方可增大间隔、增加图片宽度、缩小标签宽度或旋转标签。

共享的 `XAxisStyle`：

| 参数 | 作用 |
| --- | --- |
| `name`、`nameGap` | 下方居中的轴名称及与标签的间距 |
| `label.show` | 是否显示标签，不影响数据、轴线、刻度和网格 |
| `label.rotate` | −90° 至 90°，正值逆时针；旋转后的外接框计入布局 |
| `label.margin`、`label.maxWidth` | 与刻度的间距、换行宽度；未指定宽度时按可用空间分配 |
| `label.textStyle` | 可选 `ChartTextStyle`，指定字号、字体、颜色、字重等 |
| `showAxisLine`、`axisLine` | 轴线开关及可选 `ChartStroke` |
| `showAxisTick`、`axisTick`、`tickLength` | 刻度线开关、描边及长度 |
| `showSplitLine`、`splitLine` | 竖向网格开关及可选描边 |

```kotlin
val axis = NumericXAxis(
    min = 0f, max = 24f, interval = 4f,
    formatter = { "第 ${it.toInt()} 小时" },
    style = XAxisStyle(
        name = "相对时间",
        label = XAxisLabelStyle(rotate = 35f, maxWidth = 150f, margin = 12f),
        tickLength = 8f,
        splitLine = ChartStroke(0xFFBECDDC.toInt(), 1f, StrokeStyle.Dashed())
    )
)
```

### 主题、图例与其他图表

`StructuredChartStyle` 设置图片宽度、绘图区高度、内边距、字体、正文字号、标题字号、文字／背景／绘图区／网格颜色、调色板及数值格式化 lambda。标题、横轴标签、轴名称和图例占用的空间会增加最终图片高度。

图例默认 `StructuredLegendOrientation.HORIZONTAL`：项之间横向排列，空间不足时整项换行；单项名称过长时在项内换行。可设 `VERTICAL` 改成每项一行。折线图例保留各曲线的颜色及实线／虚线／点线样式。默认数字标签保留六位有效数字，可通过 `valueFormatter` 改写；这只改变标签，不舍入数据点。

```kotlin
val verticalStyle = chartStyle.copy(legendOrientation = StructuredLegendOrientation.VERTICAL)
val customNumbers = chartStyle.copy(valueFormatter = { java.lang.String.format(java.util.Locale.ROOT, "%.2f", it) })
```

| 类型 | 数据与可调参数 |
| --- | --- |
| `Line` / `TimeLine` | 每条曲线名称、独立点集、颜色、可选填充、线型、是否显示点；`StructuredLineOptions` 控制纵轴范围、是否包含零、纵轴分段数、线宽、点半径、水平网格 |
| `Bars` | `CategoryBarData(categories, series)`；每条 `BarSeries` 的名称、数值、颜色；支持 `GROUPED/STACKED`、纵轴范围、纵轴分段数、柱组宽比例与分类横轴配置 |
| `Pie` | `PieSlice(label, value, color)`；起始角度、圆角、扇区间距 |
| `Donut` | 与饼图相同，另有 `innerRadiusRatio`，必须在 0 与 1 之间 |
| `Radar` | 至少三个“名称—数值”维度，显式 `maximum`、颜色和网格数；数值范围为 0–maximum |

柱状图每个序列必须与分类等长，不补零。饼／环形图数值必须非负且总值大于零；零值保留在图例中，不生成扇区，也不改变其他项的配色。饼图图例同时显示原始值、单位和占比，不把长标签挤进扇区。雷达图超出 `maximum` 的值报错，不截成最大值。

现有低层图表 API 保留，可用于结构化入口未暴露的特殊排版、饼图内部／外部标签、下钻摘要等功能。`LineChartTheme` 和 `CategoryBarTheme` 的 `showAxes` 默认仍为 true；结构化入口关闭低层坐标轴，由测量后的轴布局统一绘制，不改变旧调用默认行为。

## 离线验收

```powershell
.\gradlew.bat :graphics:test
.\gradlew.bat :graphics:manualTest --tests "*StructuredRenderingManualTest" --tests "*GithubMarkdownManualTest"
```

对应 Bash 环境使用 `./gradlew`。人工用例渲染阶段不访问网络或生产服务；使用 Noto Sans SC、Consolas 字体，其他系统可在 `ManualTestSupport` 中注册对应字体。

验收输入位于 `graphics/src/manualTest/resources/issue7`：`codex.csv` 是完整 25 点脱敏样本；`document.md` 覆盖基本 Markdown、代码、表格和图片嵌入；`github.md` 覆盖任务状态、五种 Alerts、脚注、自动链接、Emoji、静态 HTML、合并单元格、图片混排及高亮；`inline-layout.md` 集中覆盖代码圆角与边距、Emoji 后的代码、长代码换行、SVG 任务图标和普通美元符号。这些样例只验证已支持的静态格式子集。

生成图片位于 `run/out/issue7`：

- `codex.png`：0–100%，实际用量 66→89，第 11–17 小时为 84%；参考线 31.87→46.16，独立虚线。
- `time-gaps.png`：真实毫秒时间戳、乱序、不同长度、同时间点对齐与缺失断线。
- `markdown.png`、`markdown-narrow.png`：800px 与 540px 阅读宽度，包含显式 lambda 提供的离线图片。
- `bars.png`、`pie.png`、`donut.png`、`radar.png`：五类图表中的其他四类。
- `embedded.png`：Markdown 与图表嵌入已有 Compose 布局。
- `axis-numeric-legend-wrap.png`、`legend-vertical.png`：横轴范围、固定间隔、旋转、样式以及横／纵向图例。
- `axis-time.png`：半小时固定间隔、时区和常用时间格式。
- `axis-category.png`：标签间隔与旋转，全部分类数据仍存在。
- `axis-gap-hidden-labels.png`：留白和轴部件独立开关。
- `github.png`、`github-narrow.png`、`github-dark.png`：800px、480px 逻辑宽度和深色主题，默认 2 倍输出；检查任务状态、编号、跨行／跨列单元格、折叠内容展开、网址和邮箱的中文标点边界、图片混排和末尾内容。
- `github-embedded.png`：简单 Markdown 与已有 Compose 元素组合。
- `github-inline.png`、`github-narrow-inline.png`、`github-dark-inline.png`：行内排版专项图，检查圆角代码及换行、任务图标首行对齐、正文样式与普通美元符号。

必须打开 PNG 检查文字、边界、曲线和线型；单元测试不代替实际图片验收。

## HTTP JSON 与客户端

两个接口均接收 `application/json`，成功返回 `image/png`；参数、语法、缺失图片或不支持的字段返回 `400 {"message":"…"}`，非预期服务错误返回 500。新接口严格检查未知字段，既有 handler／generator 接口行为不变。

### Markdown

`POST /render/markdown`：

```json
{
  "source": "# 报告\n\n中文、**粗体**与 `inline code`。\n\n- 第一项\n- 第二项",
  "width": 800,
  "pixelRatio": 2,
  "softBreak": "SPACE",
  "style": {
    "fontFamily": "Noto Sans SC",
    "codeFontFamily": "Consolas",
    "fontSize": 16,
    "darkTheme": false
  },
  "images": {}
}
```

`style` 支持库主题中的颜色、间距、页内边距和行高。颜色为 `#RRGGBB` 或 `#AARRGGBB`。HTTP 的 `darkTheme: true` 为未指定的正文、背景、链接和边框提供配套深色；显式颜色优先。字体名应为服务器已安装的 family，或嵌入服务启动时通过 `FontManager` 注册的别名；请求不读取字体路径。

`images` 的键是文档中的原始图片目标，值是图片文件的纯 Base64 字符串（不带 data URL 前缀）。即使目标是 HTTP URL，也只从此映射读取，不下载目标。相同目标在请求内解码一次，渲染完成即关闭；未引用的图片不解码。`softBreak` 可为 `SPACE` 或 `NEWLINE`，对应 Markdown 文件和评论换行语义。

### 结构化图表

`POST /render/chart`：

```json
{
  "title": "双曲线示例",
  "unit": "%",
  "data": {
    "type": "line",
    "series": [
      {"name":"实际已用","points":[{"x":0,"y":66},{"x":24,"y":89}],"color":"#1E6ED2","showPoints":false},
      {"name":"均速参考","points":[{"x":0,"y":31.87},{"x":24,"y":46.16}],"color":"#DC781E","showPoints":false,"lineStyle":{"type":"dashed","intervals":[10,6]}}
    ],
    "xAxis": {"min":0,"max":24,"interval":4,"unit":"h"},
    "options": {"yRange":{"min":0,"max":100}}
  },
  "style": {"width":900,"plotHeight":360,"fontFamily":"Noto Sans SC"}
}
```

| `data.type` | 数据字段 |
| --- | --- |
| `line` | `series[].points[{x,y}]`、`xAxis`、`options` |
| `timeLine` | `series[].points[{epochMillis,y}]`、时间 `xAxis`、`options`；毫秒是 JSON 整数 |
| `bars` | `categories`、`series[{name,values,color?}]`、`mode: GROUPED/STACKED`、`yRange`、`yTickCount`、`barAreaRatio`、分类 `xAxis` |
| `pie` | `slices[{name,value,color?}]`、`startAngle`、`cornerRadius`、`sliceGap` |
| `donut` | 同饼图，另有 `innerRadiusRatio` |
| `radar` | `axes[{name,value}]`、`maximum`、可选 `color`、`gridCount` |

数值／时间序列独立携带名称、颜色、可选 `fillColor`、`showPoints` 和 `lineStyle`。`y: null` 表示断线，时间点不先转换成 Float。线型为 `{"type":"solid"}`、`{"type":"dashed","intervals":[8,4],"phase":0}` 或 `{"type":"dotted","dot":1,"gap":3,"phase":0}`。

`options` 与 `StructuredLineOptions` 对应。数值轴支持范围、留白、间隔、单位和样式；时间轴支持整数毫秒范围／间隔、`zone`、`format`、`pattern` 和样式；分类轴支持 `labelInterval` 和样式。`xAxis.style` 包含轴名、标签旋转／宽度／文字样式、轴线、刻度和分隔线。JSON 不传递 Kotlin 格式化 lambda；时间自定义格式用 `pattern`。

图表 `style` 对应宽度、绘图区高度、内边距、字体、字号、颜色、可选 `palette` 与 `legendOrientation: HORIZONTAL/VERTICAL`。每种类型的范围及数据校验与库 API 一致；总高度由标题、标签和图例共同测量。

### 调用

把请求保存为 UTF-8 `markdown.json` 或 `chart.json`：

```shell
curl -X POST http://127.0.0.1:8080/render/markdown -H "Content-Type: application/json" --data-binary @markdown.json -o markdown.png
curl -X POST http://127.0.0.1:8080/render/chart -H "Content-Type: application/json" --data-binary @chart.json -o chart.png
```

客户端请求类型位于 `top.e404.tavolo.http.client.request`：

```kotlin
TavoloCommandClient().use { client ->
    val image = client.renderMarkdown(RenderMarkdownRequest("# 报告\n\n中文、**粗体**与 `code`。"))
    java.io.File("markdown.png").writeBytes(image.bytes)
    val chart = client.renderChart(RenderChartRequest(
        ChartData.Pie(listOf(Slice("完成", 75f), Slice("剩余", 25f)))
    ))
    java.io.File("chart.png").writeBytes(chart.bytes)
}
```

调用发生在 coroutine 中。返回沿用 `ExecutedImage`；非 2xx 响应通过 `TavoloCommandClientException` 保留状态码和服务错误说明。
