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

## 设计文档

- [指令资源与能力注册设计](docs/指令资源与能力注册设计.md)
- [HTTP 指令服务设计](docs/HTTP指令服务设计.md)
- [Compose 绘图 DSL 与渲染抽象设计](docs/Compose绘图DSL与渲染抽象设计.md)
- [TODO](docs/TODO.md)

## 引入依赖

版本请在[release](https://github.com/4o4E/Tavolo/releases)中查看

```kotlin
val version = "2.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("top.e404.tavolo:tavolo-core:${version}")
    implementation("top.e404.tavolo:tavolo-graphics:${version}")
    implementation("top.e404.tavolo:tavolo-gif-codec:${version}")
    implementation("top.e404.tavolo:tavolo-common:${version}")
}
```
