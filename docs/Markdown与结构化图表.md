# Markdown 与结构化图表

`graphics` 提供原生 Compose 元素和独立图片入口。Markdown 由 commonmark-java 解析，HTML 由 jsoup 组织成文档树，再交给 Tavolo 排版；公式与 Mermaid 使用官方引擎，图表复用已有折线、分类柱状、饼图、环形图和雷达图组件。不依赖 Bot、QQ、Grafana 或业务公式。

本次提供库 API，不新增 HTTP 路由或 JSON 协议；原有指令 HTTP 接口不变。

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

GitHub 风格建议使用无衬线正文和独立等宽代码字体。上述例子使用 Noto Sans SC 与 Consolas；先确认部署机器已安装实际 family，不能把字体别名当成字体安装。可用 `FontManager.resolve(ui).familyName` 核对原生引擎最终解析的字体。Mermaid 会使用解析后的真实 family 名；同一字体也必须安装到运行 Chromium 的系统中。

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

正文和代码字体仍由 `fontFamily`、`codeFontFamily` 指定，不改变应用全局字体。窄图可将 `pagePadding` 设为 16px，正文保持 16px。静态链接显示链接文案和颜色，不在正文后追加地址；`details` 显示展开标记及全部内容。


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

### GitHub Markdown 兼容范围

兼容目标是语法和静态内容语义，排版参考 GitHub，但不逐像素复刻浏览器 CSS，也不把 PNG 当作交互网页。使用成熟解析器并不等于自动覆盖 GitHub 网站的额外处理；以下能力通过原生排版和官方渲染后端组合实现。

| 内容 | 行为 |
| --- | --- |
| 标题、段落、强调 | 支持 1–6 级标题、粗体、斜体及重叠样式 |
| 软／硬换行 | 默认按 Markdown 文件合并软换行；`MarkdownContext(softBreak = MarkdownSoftBreak.NEWLINE)` 用于 Issue/评论；硬换行始终保留 |
| 有序、无序及嵌套列表 | 保留起始编号、层级和缩进；HTML 有序列表支持 `start`、`value` |
| 任务列表 | 显示已勾选／未勾选状态，支持嵌套，不提供点击修改 |
| 引用、Alerts | 支持嵌套引用及 NOTE、TIP、IMPORTANT、WARNING、CAUTION 五种提示块 |
| 链接、自动链接 | 保留文字和可辨识的目标；自动识别 URL、邮箱，不访问目标。中文标点与后续正文不纳入自动链接，中文域名或路径可保留；显式链接的目标不做改写 |
| 脚注 | 按引用顺序编号，重复引用共享定义，正文统一放到文末；静态图不显示返回按钮 |
| Emoji 短码 | 使用 GitHub gemoji Unicode 别名数据；GitHub 专有图片表情的资源 URL 仍交给 `imageResolver` |
| 行内代码 | 遵循 CommonMark 空白归一化，反引号、公式分隔符、Emoji 短码均按代码文字保留 |
| 代码块 | 保留空格和字符，长行换行；带语言围栏通过 highlight.js 高亮；未知语言正常保留纯文本 |
| GFM 表格、删除线 | 等宽分列、表头与对齐、单元格换行；删除线可叠加其他样式 |
| 数学公式 | 支持 `$…$`、美元符号与反引号组合的行内写法、`$$…$$` 和 `math` 围栏；MathJax 生成 SVG，行内公式按基线排版 |
| Mermaid | `mermaid` 围栏交给官方引擎生成透明 PNG，再嵌入原生布局；不显示源码作为替代结果 |
| 常用 HTML 行内语义 | 支持 `br`、`wbr`、`b/strong`、`i/em`、`del/s/strike`、`ins`、`code/tt/kbd/samp`、`sub/sup`、`small`、`mark`、`q`、`ruby/rt/rp`、`bdo`；普通语义容器保留子内容 |
| HTML 块级语义 | 支持标题、段落、引用、列表、`div`、`details/summary`、`dl/dt/dd`、`figure/figcaption`；`details` 始终展开，包括嵌套内容 |
| HTML 表格 | 支持 `thead/tbody/tfoot`、表头、说明、`colspan` 与 `rowspan`；行高按跨行内容共同测量 |
| HTML 图片与对齐 | 支持 `img` 尺寸、块和单元格 `align`；`picture` 可按亮／暗主题选择 `source`，`srcset` 使用首个候选 |

HTML 不执行脚本、事件处理器、任意 CSS 或嵌入网页；脚本、样式和活动嵌入元素被移除，注释不显示。标签解析不是浏览器 DOM/CSS 实现。`picture` 的媒体选择支持无条件、`screen/all` 和 `prefers-color-scheme`；调用方可用 resolver 提供相应静态资源。

静态链接不可点击，只显示原始文案；相对链接不依赖仓库地址解析。`@用户`、`#编号`、`owner/repository#编号` 可通过 `MarkdownContext.referenceResolver` 判断引用是否有目标，有目标时使用链接配色，没有上下文时保留原文，不猜测仓库或请求 GitHub。任务状态、脚注编号、展开区正文均保留。

### 官方渲染运行时与部署

普通文字、列表、表格、HTML、Unicode Emoji 不要求 Node.js。公式、Mermaid 和带语言的代码高亮需要提供 `MarkdownRuntime`；内置适配器为 `NodeMarkdownRuntime`，不是只留一个未实现的回调。

仓库的 `graphics/markdown-runtime` 包含 worker、精确版本清单与 npm lockfile。安装 Node.js **22.13.0 或更新版本**，在项目根目录执行：

```shell
npm --prefix graphics/markdown-runtime ci
```

依赖固定为 MathJax 3.2.2、Mermaid CLI 12.2.0、Puppeteer 25.0.4、highlight.js 11.12.0。Puppeteer 安装阶段下载匹配的 Chromium；部署应保留其浏览器缓存，或设置 `PUPPETEER_EXECUTABLE_PATH` 指向已安装的兼容 Chromium。Linux 还需安装 Chromium 所需系统库及中文字体。安装阶段需要依赖来源，渲染阶段不要求联网。

部署时复制整个运行时目录及安装后的依赖，或在目标环境执行同一条 `npm ci`；它不自动包含在 JVM jar 中。浏览器字体取自运行环境，Skia 注册的内存字体不会自动安装到 Chromium。

```kotlin
import java.nio.file.Path
import java.time.Duration

NodeMarkdownRuntime(
    runtimeDirectory = Path.of("graphics/markdown-runtime"),
    timeout = Duration.ofSeconds(60)
).use { runtime ->
    val png = renderMarkdownPng(
        source = "## 公式\n\n```math\n\\frac{1}{2}+\\sqrt{x^2+y^2}\n```",
        width = 800f,
        style = markdownStyle,
        runtime = runtime
    )
    java.io.File("formula.png").writeBytes(png)
}
```

同一个 runtime 可供多个文档共享；内部串行处理请求，复用 Node 进程，Chromium 仅在第一次 Mermaid 请求时启动。调用方负责在最后一次测量完成后关闭 runtime；可在 `use` 中完成整次渲染。超时会先终止实例及子进程树，再关闭通信流，即使请求写入被阻塞也不等待工作进程读取；不自动重试或切换成源码显示。`nodeExecutable` 可指定 Node 的完整路径。

数学符号由 MathJax 输出为 SVG 路径；中文等 SVG 文字由 Tavolo 使用已注册字体成形并转换为路径，不要求浏览器。行内公式按正文字体的 x-height 缩放、按公式基线对齐，并预留上下间距；无描边字形不会被原生 SVG 的零线宽行为额外加粗，取消线、圈注等有效描边保留。部署字体必须覆盖公式中的文字，缺字会明确报错。常用数学扩展预先导入；`\require` 仅允许已安装、导入的扩展，不能读取任意文件或下载脚本。公式内部的宏定义有效，公式之间不共享可变宏定义，避免共享 worker 导致不同文档相互影响。此能力是 MathJax 数学语法，不是任意 LaTeX 文档或宏包编译器。

Mermaid 使用固定版本官方引擎与 strict 配置，默认采用 `classic` 外观，不添加渐变描边或投影，深色节点的各边保持统一实色；图形以 2 倍像素密度生成。浏览器只读取引擎安装资源；文档中的外部网络／文件资源请求明确报错。普通文内图片和 GitHub 图片表情继续走调用方 resolver，不能借图形引擎隐式加载。

`MarkdownStyle` 可设置字体、字号、文字与背景颜色、链接颜色、代码背景、引用线、表格边框、间距、内边距、行高及 `darkTheme`。`darkTheme` 控制 Mermaid、代码 token、Alerts、次要文字和行内背景的主题；正文背景、文字、链接、块级代码背景及边框颜色仍由相应字段显式设置。深色验收图采用 GitHub 的 `#0d1117` 背景、`#f0f6fc` 正文、`#4493f8` 链接、`#151b23` 代码背景及 `#3d444d` 边框。

### 尺寸和错误

空文档、非正或非有限字号／宽度、负间距、表格或嵌套导致阅读宽度不足，以及固定高度不足时明确报错。文字和代码按宽度换行，不截断字符；公式与 Mermaid 保持比例缩小到可用宽度，不截掉图形。复杂图形需要调用方分配足够宽度以保持可读。

缺少 runtime、缺少图片 resolver、未闭合的块级公式、公式／Mermaid 语法错误、引擎异常及超时均向调用方报告，不产生占位图、不隐藏错误、不静默退回源码。未知代码语言是正常文档内容，按纯文本显示，并非引擎错误的降级路径。

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

对应 Bash 环境使用 `./gradlew`。先按上文安装 Markdown 运行时；人工用例渲染阶段不访问网络或生产服务。人工样例使用 Noto Sans SC、Consolas 字体，其他系统可在 `ManualTestSupport` 中注册对应的无衬线和等宽字体，并确保 Chromium 能访问同名系统字体。

验收输入位于 `graphics/src/manualTest/resources/issue7`：`codex.csv` 是完整 25 点脱敏样本；`document.md` 覆盖基本 Markdown、代码、表格和原生嵌入；`github.md` 覆盖任务状态、五种 Alerts、脚注、自动链接、Emoji、公式与编号、Mermaid、HTML 嵌套、合并单元格、图片混排及高亮；`inline-layout.md` 集中覆盖代码圆角与边距、Emoji 后的代码、长代码换行、SVG 任务图标、嵌套分数及公式描边。

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
- `github.png`、`github-narrow.png`、`github-dark.png`：800px、480px 逻辑宽度和深色主题，默认 2 倍输出；检查公式基线、编号、跨行／跨列单元格、折叠内容展开、网址和邮箱的中文标点边界、图片混排和末尾内容。
- `github-embedded.png`：公式、Mermaid 与已有 Compose 元素组合。
- `github-inline.png`、`github-narrow-inline.png`、`github-dark-inline.png`：行内排版专项图，检查圆角代码及换行、任务图标首行对齐、公式与正文混排、细笔画和保留的取消线／圈注，以及 Mermaid 深浅主题的均匀描边。

必须打开 PNG 检查文字、边界、曲线和线型；单元测试不代替实际图片验收。
