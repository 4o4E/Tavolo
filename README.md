# Tavolo

`Tavolo` 读作 `TAH-vo-lo`，来自 Esperanto，含义为“图层”。这里取“承载图像处理、绘制、编码和渲染能力的工作台”这一层含义。

基于[skiko](https://github.com/JetBrains/skiko)的绘图工具库, 包括

- [gif编码](gif-codec/src/main/kotlin/gif)(参考[cssxsh/mirai-skia-plugin](https://github.com/cssxsh/mirai-skia-plugin))
- [图片逐帧处理框架](gif-codec/src/main/kotlin/frame)
- [compose语法生成图片](graphics/src/main/kotlin)
- [图片滤镜/特效](core/src/main/kotlin/handler/list)
- [基于输入图片生成表情](core/src/main/kotlin/handler/face)
- [基于输入生成图片](core/src/main/kotlin/generator/list)
- [bdf点阵字体解析](bdf-parser/src/main/kotlin)
- [HTTP 指令服务](http-server)

## 渲染示例

这些示例图片由 `graphics` 模块的 Compose DSL 人工测试生成。README 只保留一个最小示例，复杂示例直接链接到对应人工测试源码，避免图片和简化代码不一致。

### Hello World

![Hello World Compose 示例](docs/assets/readme/compose-hello-world.png)

对应人工测试：[`ComposeHelloWorldManualTest.kt`](graphics/src/manualTest/kotlin/ComposeHelloWorldManualTest.kt)

对应 Compose 语法：

```kotlin
val uiFont = ManualTestSupport.uiFont

ManualTestSupport.saveCompose("README-01-Hello-World") {
    box(
        modifier = Modifier
            .size(860f, 360f)
            .background(Color.makeRGB(26, 34, 48))
            .padding(42f)
    ) {
        column(
            modifier = Modifier
                .background(Color.makeRGB(255, 255, 255))
                .padding(36f)
        ) {
            text(
                "Hello, World!",
                fontSize = 56f,
                textColor = Color.makeRGB(38, 58, 92),
                fontFamily = uiFont
            )
            text(
                "Tavolo Compose DSL",
                modifier = Modifier.padding(top = 18f),
                fontSize = 28f,
                textColor = Color.makeRGB(87, 103, 128),
                fontFamily = uiFont
            )
        }
    }
}
```

### 标准 SVG 组件

![SVG Compose 组件](docs/assets/readme/compose-svg.png)

对应人工测试：[`ComposeSvgManualTest.kt`](graphics/src/manualTest/kotlin/ComposeSvgManualTest.kt)

### Modifier 视觉效果

![Compose Modifier 效果](docs/assets/readme/compose-effects.png)

对应人工测试：[`ComposeEffectManualTest.kt`](graphics/src/manualTest/kotlin/ComposeEffectManualTest.kt)

### 图表组件

![Compose 图表组件](docs/assets/readme/compose-charts.png)

对应人工测试：[`ComposeThemeManualTest.kt`](graphics/src/manualTest/kotlin/ComposeThemeManualTest.kt)

## 设计文档

- [快速开始](docs/快速开始.md)
- [指令资源与能力注册设计](docs/指令资源与能力注册设计.md)
- [HTTP 指令服务设计](docs/HTTP指令服务设计.md)
- [Compose 绘图 DSL 与渲染抽象设计](docs/Compose绘图DSL与渲染抽象设计.md)
- [TODO](docs/TODO.md)

## 引入依赖

版本请在[release](https://github.com/4o4E/Tavolo/releases)中查看

```kotlin
val version = "2.0.0-SNAPSHOT"

repositories {
    maven("https://nexus.e404.top:3443/repository/maven-snapshots/")
}

dependencies {
    implementation("top.e404.tavolo:tavolo-core:${version}")
    implementation("top.e404.tavolo:tavolo-graphics:${version}")
    implementation("top.e404.tavolo:tavolo-gif-codec:${version}")
    implementation("top.e404.tavolo:tavolo-common:${version}")
}
```
