# Tavolo Word Cloud

`word-cloud` 模块提供基于 Skiko 的词云渲染能力，只处理已经聚合好的词频列表，不负责消息采集、分词、词频存储和业务权限。

## 基础渲染

```kotlin
val image = TavoloWordCloud.render(
    entries = listOf(
        WordCloudEntry("Tavolo", 100),
        WordCloudEntry("词云", 80),
    ),
    options = WordCloudOptions(width = 1200, height = 800)
)
```

`TavoloWordCloud.render()` 返回 Skiko `Image`，调用方可以自行编码为 PNG、JPG 或上传到业务平台。需要直接得到 PNG 字节时使用 `renderPng()`。

布局会按旋转后的文字实际像素检查蒙版和碰撞，透明外接区域不会占位；这比矩形碰撞更密，但大尺寸模板和大量词条会增加计算成本。

## 模板蒙版

模板先转换为 `WordCloudMask`，再传给渲染入口：

```kotlin
val mask = WordCloudMaskFactory.fromOtsu(templateImage)
val image = TavoloWordCloud.render(entries, mask)
```

当前提供三种模板生成方式：

- `fromOtsu()`：使用 OTSU 阈值算法把深色区域转成可绘制区域，适合黑白或明暗对比清晰的模板。
- `fromEdges(fillBetweenEdges = false)`：只保留 Sobel 边缘检测结果，适合观察轮廓，但可绘制区域很薄，词通常放不多。
- `fromEdges(fillBetweenEdges = true)`：把边缘当作边界，按轮廓层级交替填充边缘之间的区域。

边缘填充不会按扫描段切条。它会先把非边缘区域分成连通域，再根据相邻边缘组件计算从画布外部跨过了几圈边缘；奇数层区域会填充，偶数层区域保留空白。真实图片边缘断裂时，可以调大 `dilateRadius`，但过大会吞掉细节。

## 人工测试

运行：

```shell
./gradlew :word-cloud:manualTest
```

输出目录：

```text
run/out/manual/word-cloud
```

人工测试使用固定尺寸大画布渲染单个加粗数字作为模板，并输出左右对应的组合对比图。词频样例包含大量短词、长词和明显权重差异，用来观察大词、中词和长尾词在不同模板策略下的密集效果。
