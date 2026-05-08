# draw

基于 Skiko/Skia 的图片绘制工具模块，包含 Compose 风格的 2D 图片 DSL、图表组件和一个 CPU 3D 渲染器。

## 引入

```kotlin
dependencies {
    implementation("top.e404.skiko-util:skiko-util-draw:<version>")
}
```

## 包结构

- `top.e404.skiko.draw.compose`: 推荐使用的 2D 绘图 DSL。
- `top.e404.skiko.draw.compose.charts`: 内置图表组件，目前包含环形图和雷达图。
- `top.e404.skiko.draw.render3d`: CPU 3D 渲染相关类型和渲染函数。
- `top.e404.skiko.draw.element`: 旧版绘图元素，已废弃，不建议新增使用。

## 测试分层

- `./gradlew :draw:test` 只运行稳定单元测试，应使用断言验证布局、测量和绘制命令，不依赖人工查看图片。
- `./gradlew :draw:manualTest` 运行人工测试，主要用于生成示例图片到 `run/out` 后人工检查渲染效果，允许依赖本地资源或网络资源。
- `./gradlew :draw:jacocoTestReport` 生成单元测试覆盖率报告，HTML 报告位于 `draw/build/reports/jacoco/test/html/index.html`。

## 2D Compose DSL

`render` 会先测量根节点尺寸，再创建 Skia `Image` 并绘制内容。布局元素包括 `column`、`row`、`box`、`table`、`text`、`image` 等。

常用 `Modifier`:

- 尺寸：`size`、`width`、`height`、`maxSize`
- 间距：`padding`
- 样式：`background`、`border`、`clip`
- 文本：`fontSize`、`fontFamily`、`textColor`、`textOverflow`
- 图片：`imageOverflow`

`Modifier` 按链式顺序逐层应用，和 Jetpack Compose 一样可以用多层 `padding` 表达外部留白和内部留白：

```kotlin
Modifier
    .padding(12f)      // 外层留白
    .background(color)
    .padding(8f)       // 背景内的内容留白
```

`size` 也遵循链式顺序：`.size(100f).padding(10f)` 表示总尺寸为 `100f`，`.padding(10f).size(100f)` 表示内容尺寸为 `100f` 且总尺寸额外包含外层 `padding`。

```kotlin
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import top.e404.skiko.draw.compose.*
import java.io.File

fun main() {
    val image = render(backgroundColor = Color.WHITE) {
        column(
            modifier = Modifier
                .padding(24f)
                .background(Color.makeRGB(245, 247, 250))
        ) {
            text(
                "skiko-util",
                modifier = Modifier
                    .fontSize(36f)
                    .textColor(Color.makeRGB(32, 38, 46))
            )
            text(
                "Compose style image rendering",
                modifier = Modifier
                    .padding(top = 8f)
                    .fontSize(20f)
                    .textColor(Color.makeRGB(91, 103, 120))
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
                            .padding(horizontal = 24f, vertical = 12f)
                            .fontSize(22f)
                            .textColor(Color.WHITE)
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

```kotlin
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import top.e404.skiko.draw.compose.*
import top.e404.skiko.draw.compose.charts.BarTheme
import top.e404.skiko.draw.compose.charts.RadarTheme
import top.e404.skiko.draw.compose.charts.bar
import top.e404.skiko.draw.compose.charts.radar
import java.io.File

fun main() {
    val image = render(Color.makeRGB(24, 28, 34)) {
        row(modifier = Modifier.padding(24f)) {
            bar(
                BarTheme(outerRadius = 90f),
                listOf(
                    Color.makeRGB(72, 149, 239) to 42f,
                    Color.makeRGB(247, 127, 0) to 28f,
                    Color.makeRGB(76, 175, 80) to 30f,
                )
            )

            radar(
                RadarTheme(width = 360f, height = 260f, radius = 90f),
                listOf(
                    "A" to 0.85f,
                    "B" to 0.65f,
                    "C" to 0.92f,
                    "D" to 0.55f,
                    "E" to 0.75f,
                )
            )
        }
    }

    File("out/charts.png").apply { parentFile.mkdirs() }
        .writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
}
```

## 3D 渲染

`render3d` 包提供基础向量、矩阵、网格、相机和渲染配置。适合渲染简单几何体、带 UV 的方块模型和静态预览图。

```kotlin
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import top.e404.skiko.draw.render3d.*
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
