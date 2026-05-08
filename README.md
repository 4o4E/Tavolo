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

## 未来计划

- [ ] 嵌套的图片处理功能
- [ ] http server

## 引入依赖

版本请在[release](https://github.com/4o4E/Tavolo/releases)中查看

```kotlin
val version = "2.0.0"

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
