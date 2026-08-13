# Compose 绘图 DSL 与渲染抽象设计

本文档描述 `graphics` 模块中 Compose 风格 2D 绘图 DSL 的当前设计。

## 设计目标

- 使用声明式 DSL 描述图片布局、文本、图片、图表和自定义绘制。
- 测量阶段和绘制阶段解耦，避免布局逻辑直接依赖 Skiko `Surface`。
- 抽象文本测量和画布绘制能力，让单元测试可以使用 fake measurer 和 recording canvas。
- 保留 Skiko 作为真实渲染后端，同时让核心布局和绘制命令可测试。

## 渲染入口

核心入口位于 `graphics/src/main/kotlin/compose/compose.kt`：

```kotlin
fun render(
    backgroundColor: Int = Color.TRANSPARENT,
    root: UiElement = Column(),
    measureContext: MeasureContext = MeasureContext(),
    content: Composable
): Image

fun renderCommands(
    backgroundColor: Int = Color.TRANSPARENT,
    root: UiElement = Column(),
    measureContext: MeasureContext = MeasureContext(),
    content: Composable
): List<DrawCommand>
```

`render` 使用 `SkiaDrawCanvas` 输出真实 `Image`。`renderCommands` 使用 `RecordingDrawCanvas` 记录绘制命令，供单元测试断言。

## 测量抽象

测量上下文只依赖文本测量能力：

```kotlin
class MeasureContext(
    val textMeasurer: TextMeasurer = SkiaTextMeasurer
)

interface TextMeasurer {
    fun measureTextWidth(text: String, font: Font, paint: Paint): Float
    fun metrics(font: Font): TextMetrics
}
```

生产环境使用 `SkiaTextMeasurer`。测试中可以注入固定字符宽度和固定行高的 measurer，使换行、省略号和布局尺寸稳定可断言。

## 绘制抽象

绘制上下文包含 `DrawCanvas`，并保留本次渲染使用的 `MeasureContext`，方便自定义绘制在 draw 阶段复用同一套文本测量能力：

```kotlin
class DrawContext(
    val canvas: DrawCanvas,
    val measureContext: MeasureContext = MeasureContext()
)
```

`DrawCanvas` 包装当前 DSL 需要的绘制能力，包括：

- 状态：`save`、`restore`。
- 变换：`translate`、`rotate`、`scale`。
- 裁剪：`clipPath`。
- 基础绘制：`drawRect`、`drawString`、`drawImageRect`、`drawPath`、`drawArc`、`drawCircle`、`drawLine`。

`RecordingDrawCanvas` 记录 `DrawCommand`，并快照 `Paint`、`Font`、`Rect` 等关键参数。

## 元素模型

所有 UI 组件实现 `UiElement`：

```kotlin
interface UiElement {
    fun measure(context: MeasureContext)
    fun layout(parentX: Float, parentY: Float)
    fun draw(context: DrawContext)
}
```

当前内置元素：

- `Column`、`Row`、`Box`。
- `Table`、`TableRow`、`TableCell`。
- `Text`。
- `ImageElement`。
- `CanvasElement`。
- 图表和图标组件基于 `CanvasElement` 与 `DrawCanvas` 绘制。

`CanvasElement` 支持两种绘制 lambda：旧的 `(DrawCanvas) -> Unit` 写法仍可使用；需要文本测量或测量阶段上下文时，可以使用 `(DrawCanvas, MeasureContext) -> Unit`。渲染入口会把测量阶段使用的同一个 `MeasureContext` 传入绘制阶段。

## 图表主题

图表主题 API 不要求调用方直接构造 Skia `Paint` 或 `Font`。当前图表组件使用稳定配置描述样式：

- `ChartFill`：填充颜色和抗锯齿。
- `ChartStroke`：描边颜色、宽度、线型和抗锯齿。
- `ChartTextStyle`：字号、颜色、字体名、字重、斜体、横向缩放和抗锯齿。
- `ChartTextBox`：文本测量后的宽高和 ascent/descent，用于标签位置修正。

雷达图标签和网格文字会通过 `MeasureContext.textMeasurer` 计算宽度与字体指标，`RadarFixPolicy` 只接收 `ChartTextBox`，不暴露 Skia `TextLine`。真实绘制时再由内部把主题配置转换为 Skia 对象。

## Modifier

通用 `Modifier` 只表达布局、容器绘制和绘制状态：

- 尺寸约束：`size`、`width`、`height`、`sizeIn`、`widthIn`、`heightIn`。
- 间距：`padding`。
- 背景与裁剪：`background(color)`、`background(image, imageOverflow)` / `backgroundImage(image, imageOverflow)`、`clip`。
- 边框：`border`，支持实线、虚线、点线；传入 `shape` 时沿对应路径绘制圆角或圆形边框。
- 效果：`shadow`、`rotate`。
- 抗锯齿：`antiAlias`，默认开启；`.antiAlias(false)` 可显式关闭。

`Modifier` 按链式顺序逐层应用。`padding` 和 `border` 会影响测量尺寸，`background`、`clip`、`shadow`、`rotate` 只影响绘制。`ImageElement` 和图片背景都支持 `ImageOverflow.Scale`、`ImageOverflow.Crop` 和 `ImageOverflow.Stretch`：`Scale` 保持比例完整显示并居中，`Crop` 保持比例居中裁剪并铺满当前 modifier 边界，`Stretch` 不保留比例，直接拉伸到当前 modifier 边界。

`Modifier.antiAlias` 是元素级统一开关，不是提高渲染分辨率后再缩小的超采样方案。默认 `true` 时，背景、边框等几何绘制使用 Skia 覆盖率抗锯齿，圆形/圆角 `clip` 使用抗锯齿裁剪，图片内容和图片背景使用 `FilterMode.LINEAR + MipmapMode.LINEAR`：双线性过滤负责放大时的像素过渡，线性 mipmap 负责缩小时的高频信息聚合。设置为 `false` 后，几何绘制和裁剪关闭覆盖率抗锯齿，图片使用 Skia 默认最近邻采样。一个元素链上的 `antiAlias` 同时作用于这些路径，避免裁剪平滑但图片仍按最近邻缩放的半开状态。

通用图片工具 `Image.resize(w, h, smooth)` 使用相同采样语义。旧实现的两个分支不对称：`smooth=true` 使用 `Canvas.scale + drawImage`，`smooth=false` 反而调用内部实际为线性过滤的 `drawImageRectNearest`，开关名称与真实过滤路径不一致，放大和缩小也没有共享明确的采样策略。现在两个方向统一使用一次 `drawImageRect`：显式传入 `smooth=true` 时选择双线性过滤与线性 mipmap，`false` 时明确选择最近邻采样。`smooth` 的默认值继续保持 `false`，不改变既有 API 的默认参数约定。

## TextModifier

文本专用样式不进入通用 `Modifier`，而是通过 `TextModifier` 单独传入：

```kotlin
val titleText = TextModifier
    .font(fontSize = 28f, textColor = Color.WHITE, fontFamily = "ui")
    .textUnderline(TextUnderline(...))

text(
    "标题",
    modifier = Modifier.padding(top = 8f),
    textModifier = titleText
)
```

`TextModifier` 当前支持：

- `font(fontSize, textColor, fontFamily, underline, fontWeight, italic, lineHeight, letterSpacing, scaleX)`。
- `textStyle(TextStyle(...))`。
- `underline(...)` / `textUnderline(...)`。
- `bold(...)`、`italic(...)`、`fontWeight(...)`。
- `lineHeight(...)`、`letterSpacing(...)`、`scaleX(...)`。

`text(...)` 显式传入的 `fontSize`、`textColor`、`fontFamily`、`underline` 参数优先于 `TextModifier`。

## AnnotatedText

行内不同样式使用 `AnnotatedText`，普通 `text(String)` 保持整段统一样式：

```kotlin
text(
    buildAnnotatedText {
        append("运行 ")
        inlineCode(
            "./gradlew check",
            TextSpanStyle(
                fontFamily = "mono",
                textColor = Color.makeRGB(230, 237, 243),
                backgroundColor = Color.makeRGB(38, 44, 52),
                backgroundBorderColor = Color.makeRGB(86, 96, 106),
                backgroundBorderWidth = 1f,
                backgroundRadius = 6f,
                backgroundPaddingHorizontal = 6f,
                backgroundPaddingVertical = 2f
            )
        )
    }
)
```

`TextSpanStyle` 当前支持字符级的 `fontSize`、`textColor`、`fontFamily`、`backgroundColor`、`backgroundBorderColor`、`backgroundBorderWidth`、`backgroundRadius`、`backgroundPaddingHorizontal`、`backgroundPaddingVertical`、`fontWeight`、`italic` 和 `letterSpacing`。`lineHeight`、`overflow`、`underline` 仍属于整体 `Text` 语义，不进入行内 span。

## 文本布局

`Text` 支持：

- `TextOverflow.Wrap`：在 `sizeIn(maxWidth = ...)` 约束下换行。
- `TextOverflow.Ellipsis`：超出宽度时截断并追加省略号。
- `sizeIn(maxHeight = ...)`：限制可显示行数或裁剪高度。
- 下划线：普通线、虚线、点线、色块下划线，色块高度由 `TextUnderline.thickness` 控制。

字体通过 `FontManager.resolve(fontFamily)` 解析，DSL 中只保存稳定的字体名。

## 测试策略

当前测试以 command recording 为主：

- 使用固定文本测量器验证布局、换行、省略号和文本位置。
- 使用 `RecordingDrawCanvas` 验证背景、边框、阴影、旋转、裁剪、文本下划线和图片绘制命令。
- 少量 Skiko 后端测试确保真实 `SkiaDrawCanvas` 方法可调用。
- 使用小尺寸真实渲染像素测试覆盖 Skiko 后端输出。测试不保存整图 golden snapshot，而是断言关键像素和透明度特征，并允许少量通道容差，降低平台抗锯齿和 Skia 版本差异造成的误报。
- `graphics/src/manualTest/kotlin/TestComposeEffects.kt` 输出人工查看图片，用于检查阴影、旋转、边框路径和文本样式效果。

当前像素测试位于 `graphics/src/test/kotlin/ComposeSkiaPixelTest.kt`，覆盖：

- `render(backgroundColor = ...)` 的真实清屏结果与实色 `background`。
- `clip(Shape.Circle)` 在真实光栅化后的透明角、中心填充和可开关抗锯齿边缘。
- `border`、`padding`、`background` 按 modifier 顺序应用后的关键像素。
- 图片与背景图片通过真实 Skiko 后端绘制后的像素结果，包括默认抗锯齿、关闭开关、放大平滑和高频图案缩小。
