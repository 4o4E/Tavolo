# 指令资源包与模板化 Handler 实施计划

目标版本：`2.x`

本文档描述 `core` 模块中指令素材外置、模板化 Handler 抽象和 Release 资源包发布的实施方案。方案基于以下约束：

- 资源从 jar 内嵌一次性迁移到项目外部资源目录，并在 Release 时打包为独立 zip。
- 仓库中仍以未压缩目录保存资源，Release 时再打包为 zip；zip 由用户手动下载并解压。
- 每个能力使用唯一英文 `id` 对应配置、素材目录和注册项，`id` 可参考类名生成。
- 同质化绘图逻辑通过配置文件和 `TemplateHandler` 收口。
- 暂不拆分 jar，复杂 generator/handler 的 Kotlin 逻辑仍保留在当前模块。

## 目标

- 降低 jar 体积，让素材可以独立发布和手动替换。
- 把大量同质化表情指令从 Kotlin 硬编码迁移为配置驱动。
- 统一素材读取入口，避免继续散落 `getJarImage`、`readJarFile`、`DrawData.loadFromJar` 这类只支持 classpath 的读取路径。
- 将指令专用字体纳入对应指令资源目录，避免继续依赖全局 `data/font` 或 jar 内资源。
- 保留复杂指令的扩展能力，只外置素材路径、名称、匹配正则和可配置参数，不强行脚本化所有逻辑。
- Release 产物提供独立资源 zip；运行时只读取已解压的可配置资源目录，不处理 zip 下载、缓存或解压。

## 非目标

- 不拆分 core jar 或按指令拆 jar。
- 不把复杂 generator/handler 全部改成脚本执行。
- 不把指令资源继续放在 `src/main/resources` 中，也不依赖 jar 内资源作为主读取路径。
- 允许为统一注册做必要的破坏性 API 调整，例如将 `ImageGenerator` 改名为 `FramesGenerator`。

## 目标目录结构

仓库内资源以未压缩形式存储在项目根目录 `assets/` 下，不放入 `core/src/main/resources`，避免被打进 jar：

```text
assets/
  version.yml
  handlers/
    rub/
      handler.yml
      layout.yml
      assets/
        0.png
        1.png
      fonts/
        RubSpecial.ttf
    pat/
      handler.yml
      layout.yml
      assets/
        base.png
  generators/
    blue_archive/
      generator.yml
      assets/
      fonts/
```

Release 资源包由 Gradle 任务打包：

```text
tavolo-assets-${version}.zip
  version.yml
  handlers/
    rub/
      handler.yml
      layout.yml
      assets/
        0.png
        1.png
      fonts/
        RubSpecial.ttf
    pat/
      handler.yml
      layout.yml
      assets/
        base.png
  generators/
    blue_archive/
      generator.yml
      assets/
      fonts/
```

`handlers/{id}` 和 `generators/{id}` 是资源包的最小更新单元。后续如果某个能力素材需要单独更新，只替换该目录即可。

## 资源目录配置

运行时需要提供一个可主动配置的资源根目录。项目内默认使用仓库根目录下的 `assets`；作为库被外部使用且无法识别仓库根目录时，才退回到进程工作目录下的 `assets`。

建议新增配置对象：

```kotlin
data class AssetsConfig(
    val rootDir: Path = Paths.get("assets")
)
```

配置来源优先级：

1. 调用方显式传入 `AssetsConfig(rootDir = ...)`。
2. 系统属性：`tavolo.assets.dir`。
3. 环境变量：`TAVOLO_ASSETS_DIR`。
4. 默认目录：项目根目录 `assets`，无法定位项目根时使用工作目录 `assets`。

`rootDir` 下的强制项只有资源包版本文件和能力分类目录：

```text
version.yml
handlers/
generators/
```

每个已安装的 handler 目录必须包含 `handler.yml`；每个已安装的 generator 目录必须包含 `generator.yml`。`layout.yml` 仅在 `handler.yml` 声明 `type: template` 时强制存在；`assets/` 和 `fonts/` 仅在配置文件引用对应素材或字体时要求存在。

该配置同时用于开发环境、用户手动放置资源和用户自行解压 Release zip 后的资源目录。业务代码只通过 `Assets` 读取资源，不直接拼接项目路径。

## 配置文件设计

### version.yml

资源包根目录只需要一个最小版本文件：

```yaml
version: "2.0.0"
time: "2026-05-09 00:00:00"
```

字段说明：

- `version`：资源包对应的项目版本。
- `time`：资源包打包时间，格式为 `yyyy-MM-dd HH:mm:ss`。

其他内容从 `handlers/*/handler.yml` 和 `generators/*/generator.yml` 扫描得到，不在 `version.yml` 中重复维护。

### handlers/{id}/handler.yml

`handler.yml` 描述 handler 元信息和处理入口。

```yaml
id: rub
type: template
version: 1
name: 揉
regex: "(?i)rub|搓|cuo"
layout: layout.yml
fonts:
  - name: rub-special
    file: fonts/RubSpecial.ttf
```

字段说明：

- `id`：稳定唯一英文标识，目录名、KSP 注册、运行时注册和素材索引都使用它。开发时必须显式填写 id，不使用类名推导；id 可参考类名命名，例如 `RubHandler` 使用 `rub`，`BlueArchiveGenerator` 使用 `blue_archive`。
- `type`：handler 实现类型，支持 `template` 和 `kotlin`。`template` 由配置运行时创建，`kotlin` 由 KSP 注册。
- `version`：该指令定义和资源的版本，用于更新判断。
- `name`：默认展示名。
- `regex`：指令匹配正则，沿用现有 `FramesHandler.regex` 的 Kotlin `Regex` 语义。
- `layout`：模板布局文件路径，仅 `type: template` 需要，默认 `layout.yml`。
- `fonts`：该指令专用字体列表。没有专用字体的指令可以省略。

字体字段说明：

- `name`：运行时注册到 `FontManager` 的稳定字体名。
- `file`：相对当前指令目录的字体文件路径，通常放在 `fonts/` 下。
- `index`：可选，`.ttc` 字体集合中的字体序号，默认 `0`。

### generators/{id}/generator.yml

`generator.yml` 描述 generator 元信息和资源入口。generator 总是由 KSP 注册，不支持 `template`。

```yaml
id: blue_archive
version: 1
name: BlueArchive
regex: "BlueArchive"
fonts:
  - name: glow-sans-sc-normal-heavy
    file: fonts/GlowSansSC-Normal-Heavy.otf
  - name: rog-sans-srf-std-bold
    file: fonts/RoGSanSrfStd-Bd.otf
```

字段说明：

- `id`：稳定唯一英文标识，必须与 `@ImageGenerator(id = "...")` 和目录名一致。
- `version`：该 generator 定义和资源的版本，用于更新判断。
- `name`：默认展示名。
- `regex`：匹配正则。generator 如果暂时没有更准确的匹配规则，可以先用 `name` 作为 `regex`。
- `fonts`：该 generator 专用字体列表。没有专用字体时可以省略。

### layout.yml

`layout.yml` 描述模板绘制流程，基于现有 `DrawData` 能力扩展。

```yaml
canvas:
  width: 300
  height: 300
  background: transparent
commonArgs: true
preprocess:
  - round
frameCount: 2

frames:
  - duration: 60
    layers:
      - source: asset
        path: assets/0.png
      - source: input
        draw:
          x: 40
          y: 56
          w: 120
          h: 120
          r: 0
          a: 255
          flip: n
  - duration: 60
    layers:
      - source: asset
        path: assets/1.png
      - source: input
        draw:
          x: 42
          y: 58
          w: 118
          h: 118
```

当前模板能力只覆盖现有同质化表情的常见能力：

- 固定画布尺寸。
- 多帧 duration。
- 静态图片层。
- 输入图片层。
- 位置、尺寸、旋转、透明度、翻转。
- 常用输入预处理。
- 补帧和帧索引取模。

模板执行顺序需要固定：

1. 对输入 frames 执行通用参数处理，即现有 `common(args)` 的 `d`、`w`、`h`。
2. 按 `layout.yml` 指定的预处理处理输入图片，例如 `round`、`subCenter`、`limitAsGif`。
3. 按目标帧数执行 `replenish`，目标帧数来自 `frames.size` 或配置中的 `frameCount`。
4. 对每一帧创建目标画布。
5. 按 `layers` 顺序绘制静态图片、输入图片或其他已声明图层。
6. 保留原帧 duration，除非 frame 或 args 显式覆盖。

`layout.yml` 字段：

- `frameCount`：目标帧数。
- `commonArgs`：是否执行 `common(args)`，默认 `true`。
- `preprocess`：输入图片预处理列表，当前支持 `round`、`subCenter`、`limitAsGif`。
- `layers[].source`：图层来源，支持 `asset`、`input`、`frameAsset`。
- `layers[].path`：`asset` 图层使用的静态素材路径。
- `layers[].framePattern`：`frameAsset` 图层按帧读取素材的路径模板，例如 `assets/{index}.png`。
- `layers[].draw`：沿用 `DrawData` 坐标、旋转、透明度和翻转。

复杂文本排版、条件分支、动态算法不进入当前模板语法，继续保留 Kotlin 实现。

## 指令专用字体

指令专用字体应跟随指令资源目录存放，而不是继续放在全局 `data/font` 或 `src/main/resources` 中。目录约定：

```text
assets/
  handlers/
    ygo/
      handler.yml
      fonts/
        DFLeiSho-SB.ttf
        YGO-DIY-2-BIG5.ttf
        YGO-DIY-GB.ttf
        YGODIY-JP.otf
        YGODIY-MatrixBoldSmallCaps.ttf
        FOT-Rodin Pro M.ttf
```

`ygo` 需要在计划中明确迁移的专用字体：

| 字体名 | 文件名 | 当前用途 |
| --- | --- | --- |
| `df-leisho-sb` | `DFLeiSho-SB.ttf` | 卡名、类型、描述的候选字体 |
| `ygo-diy-2-big5` | `YGO-DIY-2-BIG5.ttf` | 卡名、类型、描述的候选字体 |
| `ygo-diy-gb` | `YGO-DIY-GB.ttf` | 卡名、类型、描述的候选字体 |
| `ygodiy-jp` | `YGODIY-JP.otf` | 卡名、类型、描述的候选字体 |
| `ygodiy-matrix-bold-small-caps` | `YGODIY-MatrixBoldSmallCaps.ttf` | ATK/DEF、卡号、属性数值等小写英数字区域 |
| `fot-rodin-pro-m` | `FOT-Rodin Pro M.ttf` | 版权行 |

已知需要作为指令专用资源迁移的字体清单：

| 指令 id | 字体名 | 文件名 | 说明 |
| --- | --- | --- | --- |
| `ygo` | `df-leisho-sb` | `DFLeiSho-SB.ttf` | YGO 卡面文本候选字体 |
| `ygo` | `ygo-diy-2-big5` | `YGO-DIY-2-BIG5.ttf` | YGO 卡面文本候选字体 |
| `ygo` | `ygo-diy-gb` | `YGO-DIY-GB.ttf` | YGO 卡面文本候选字体 |
| `ygo` | `ygodiy-jp` | `YGODIY-JP.otf` | YGO 卡面文本候选字体 |
| `ygo` | `ygodiy-matrix-bold-small-caps` | `YGODIY-MatrixBoldSmallCaps.ttf` | YGO ATK/DEF、卡号等区域 |
| `ygo` | `fot-rodin-pro-m` | `FOT-Rodin Pro M.ttf` | YGO 版权行 |
| `blue_archive` | `glow-sans-sc-normal-heavy` | `GlowSansSC-Normal-Heavy.otf` | Blue Archive 风格标题字体 |
| `blue_archive` | `rog-sans-srf-std-bold` | `RoGSanSrfStd-Bd.otf` | Blue Archive 风格标题字体 |

表中字体应优先放入对应指令的 `fonts/` 目录。若后续确认某个字体被多个指令稳定复用，再单独引入共享字体目录；当前不要把共享字体目录作为默认方案，避免重新形成一套难以追踪的全局素材。

`handlers/ygo/handler.yml` 示例：

```yaml
id: ygo
type: kotlin
version: 1
name: Ygo
regex: "(?i)ygo"
fonts:
  - name: df-leisho-sb
    file: fonts/DFLeiSho-SB.ttf
  - name: ygo-diy-2-big5
    file: fonts/YGO-DIY-2-BIG5.ttf
  - name: ygo-diy-gb
    file: fonts/YGO-DIY-GB.ttf
  - name: ygodiy-jp
    file: fonts/YGODIY-JP.otf
  - name: ygodiy-matrix-bold-small-caps
    file: fonts/YGODIY-MatrixBoldSmallCaps.ttf
  - name: fot-rodin-pro-m
    file: fonts/FOT-Rodin Pro M.ttf
```

后续迁移复杂 generator/handler 时，也按同一规则把指令专用字体放入对应能力目录。通用字体仍可保留全局字体机制，只有“某个能力独占、或强依赖某个作品风格”的字体才进入 `handlers/{id}/fonts` 或 `generators/{id}/fonts`。

`generators/blue_archive/generator.yml` 示例：

```yaml
id: blue_archive
version: 1
name: BlueArchive
regex: "BlueArchive"
fonts:
  - name: glow-sans-sc-normal-heavy
    file: fonts/GlowSansSC-Normal-Heavy.otf
  - name: rog-sans-srf-std-bold
    file: fonts/RoGSanSrfStd-Bd.otf
```

generator 如果暂时没有更准确的匹配规则，可以先用 `name` 作为 `regex`，后续通过配置文件修正。

## 资源读取抽象

新增统一资源入口，建议放在：

```text
core/src/main/kotlin/assets/
  AssetProvider.kt
  DirectoryAssetProvider.kt
  Assets.kt
  AssetsConfig.kt
```

示例 API：

```kotlin
interface AssetProvider {
    fun exists(path: String): Boolean
    fun bytes(path: String): ByteArray
    fun text(path: String): String
    fun image(path: String): Image
    fun font(path: String, index: Int = 0): Typeface
}
```

实现职责：

- `DirectoryAssetProvider`：读取 `AssetsConfig.rootDir`，用于开发、手动安装和已解压资源包。
- `AssetsConfig`：描述资源根目录。
- `Assets`：生产环境统一入口，负责配置解析、路径规范化、缓存和错误信息。

字体加载规则：

- `Assets` 负责按 `handler.yml` 或 `generator.yml` 的 `fonts` 字段读取字体文件。
- 读取成功后注册到 `FontManager`，注册名使用 `fonts[].name`。
- 同一个字体名重复注册时，应校验路径一致；路径不一致要报错，避免不同指令覆盖同名字体。
- `.ttc` 字体通过 `fonts[].index` 指定集合序号。

默认查找路径：

- handler：`${rootDir}/handlers/{id}`。
- generator：`${rootDir}/generators/{id}`。

该步骤解决的问题：资源读取入口分散、只能读 jar、后续无法支持外部资源目录。

## Release 资源包任务

在 `core/build.gradle.kts` 或根构建逻辑中新增任务：

```text
validateCommandAssets
packageCommandAssets
```

`validateCommandAssets` 检查：

- `version.yml` 存在且提供 `version` 和 `time`。
- 从项目根目录 `assets/handlers` 和 `assets/generators` 读取资源。
- `handlers/{id}/handler.yml` 或 `generators/{id}/generator.yml` 存在且 `id` 与目录名一致。
- `id` 必须是英文稳定标识，建议使用小写字母、数字和下划线。
- `handler.yml` 中 `type`、`version`、`name` 等必填字段合法。
- `generator.yml` 中 `version`、`name` 等必填字段合法，且不允许出现 `type` 和 `layout`；generator 的运行时 `type` 固定派生为 `kotlin`。
- `regex` 如果缺失，生成运行时描述时使用 `name` 作为 regex。
- `type: template` 的 `layout.yml` 存在，且引用的素材文件存在。
- `fonts` 引用的字体文件存在，扩展名必须是 `.ttf`、`.ttc` 或 `.otf`。
- 字体文件可以正常通过 Skia `FontMgr` 解码。
- 路径不能越界，禁止 `..` 和绝对路径。
- 图片资源可以正常解码。

`packageCommandAssets` 产物：

```text
core/build/distributions/tavolo-assets-${version}.zip
```

打包内容来自项目根目录 `assets`，zip 根目录保留 `version.yml`、`handlers/` 和 `generators/`，不从 `core/src/main/resources` 取资源。

该步骤解决的问题：资源包可以稳定发布，Release 前能提前发现缺素材、错路径和坏图片。

## TemplateHandler

新增模板处理器，建议放在：

```text
core/src/main/kotlin/handler/template/
  TemplateCommand.kt
  TemplateLayout.kt
  TemplateHandler.kt
  TemplateRenderer.kt
```

职责划分：

- `TemplateCommand`：映射 `handler.yml`。
- `TemplateLayout`：映射 `layout.yml`。
- `TemplateHandler`：适配现有 handler 输入输出协议。
- `TemplateRenderer`：只负责根据 layout 绘制图片或 GIF 帧。

模板指令的目标注册方式是运行时读取 `handler.yml`：

```kotlin
TemplateHandler(commandId = "rub")
```

`type: template` 的 handler 不需要再为每个指令写一层 Kotlin object；运行时注册器读取 `assets/handlers/{id}/handler.yml` 后创建 `TemplateHandler`。`type: kotlin` 的复杂能力仍由 `@ImageHandler` 或 `@ImageGenerator` 和 KSP 注册，配置文件只提供资源、字体、名称、匹配正则等元信息。

该步骤解决的问题：同质化 handler 里重复的素材路径、帧循环和 `DrawData` 绘制逻辑可以集中维护。

## 指令注册与合并

最终能力列表由运行时加载流程合并得到：

1. KSP 只负责收集 `@ImageHandler` 和 `@ImageGenerator` 标记的 Kotlin 实现，生成 `handlerSet` 和 `generatorSet`。
2. 运行时扫描 `assets/handlers/*/handler.yml` 和 `assets/generators/*/generator.yml`。
3. 加载器按 id 找到 KSP 实现对应的资源配置，补全名称、匹配正则、字体和资源上下文。
4. 加载器为 `type: template` 的 handler 创建 `TemplateHandler`。

`type: kotlin` 的 `handler.yml` 不创建新实现，只用于给 KSP 注册项补充资源、字体和元信息。例如 `ygo` 仍由 KSP 注册 `YgoHandler`，但图片素材、专用字体、`name`、`regex` 可以从 `handlers/ygo/handler.yml` 读取。

建议新增读取层：

```text
core/src/main/kotlin/registry/
  CommandDescriptor.kt
  CommandRegistry.kt
  KspHandlerSource.kt
  KspGeneratorSource.kt
  ResourceCommandLoader.kt
```

职责划分：

- `CommandDescriptor`：把 `handler.yml` 或 `generator.yml` 映射为运行时元信息。
- `KspHandlerSource`：只包装 KSP 生成的 `handlerSet`。
- `KspGeneratorSource`：只包装 KSP 生成的 `generatorSet`。
- `ResourceCommandLoader`：扫描资源目录，按 id 为 KSP 注册项补全配置，并创建 template handler。
- `CommandRegistry`：接收 loader 输出的 handler、generator 和 descriptor，输出最终能力列表。

合并规则：

- `id` 是唯一主键。
- KSP 注解必须显式提供英文 `id`，不使用类名推导。id 可以参考类名，例如 `RubHandler` 使用 `rub`，`BlueArchiveGenerator` 使用 `blue_archive`。
- `handlers/{id}/handler.yml` 中 `type: kotlin` 必须能在 KSP handler 中找到同 id 的 handler，否则校验失败。
- `generators/{id}/generator.yml` 必须能在 KSP generator 中找到同 id 的 generator，否则校验失败。
- `handlers/{id}/handler.yml` 中 `type: template` 由运行时创建；如果同 id 已存在 KSP handler 或 KSP generator，校验失败，避免重复注册。
- KSP 注册项和配置注册项最终都以 `CommandDescriptor` 暴露 `id`、`category`、`type`、`name`、`regex`、`version` 等字段，其中 `category` 由目录决定，取值为 `handler` 或 `generator`；`type` 表示实现类型，handler 取值为 `template` 或 `kotlin`，generator 固定派生为 `kotlin`，不需要在 `generator.yml` 中声明。
- 对 KSP 注册项，`handler.yml` 或 `generator.yml` 中的 `name`、`regex` 优先级高于 Kotlin 对象中的默认值；没有配置时继续使用 Kotlin 原值。
- 对 generator，如果没有现成 `regex`，运行时描述先使用 `name` 作为 regex，后续可在 `generator.yml` 中补充更准确的正则。

该步骤解决的问题：硬编码逻辑继续享受 KSP 的静态注册和类型安全，同质化模板指令可以通过资源配置新增或更新，handler 和 generator 最终对外只暴露一个合并后的能力列表。

## KSP 注册改造

需要为 handler 和 generator 都提供 KSP 自动注册。

建议调整或新增注解：

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class ImageHandler(
    val id: String
)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class ImageGenerator(
    val id: String
)
```

生成结果：

```kotlin
val handlerSet: Set<FramesHandler> = setOf(...)
val generatorSet: Set<FramesGenerator> = setOf(...)
```

`id` 规则：

- 注解必须显式填写 `id`。
- 不使用类名推导。
- id 必须满足英文 id 规则，建议小写字母、数字和下划线。
- id 可以参考类名：`RubHandler` 填 `rub`，`BlueArchiveGenerator` 填 `blue_archive`。

接口调整：

```kotlin
interface FramesHandler {
    val id: String
    val name: String
    val regex: Regex
    suspend fun handleFrames(frames: MutableList<Frame>, args: MutableMap<String, String>): HandleResult
}
```

generator 也需要纳入统一描述。现有 `ImageGenerator` 应改名为 `FramesGenerator`，`@ImageGenerator` 作为 KSP 注册注解。可以将 `FramesGenerator` 从 `fun interface` 调整为带元信息的接口，或保留生成函数接口并由 KSP 生成 `GeneratorDescriptor` 包装。目标是最终 `CommandRegistry` 能拿到 generator 的 `id`、`name`、`regex` 和 `generate` 能力。

## 加载流程

运行时按以下顺序初始化：

1. 解析 `AssetsConfig`，确定 `rootDir`。
2. 读取 `${rootDir}/version.yml`，得到项目版本和打包日期。
3. 扫描 `${rootDir}/handlers/*/handler.yml` 和 `${rootDir}/generators/*/generator.yml`，解析为 `CommandDescriptor` 草稿；能力类型由目录决定。
4. 校验 `id`、目录名、handler `type`、`name`、`regex`、`layout` 等基础字段。`regex` 缺失时，用 `name` 作为 regex；generator 的 `type` 固定派生为 `kotlin`。
5. 读取 KSP 生成的 `handlerSet` 和 `generatorSet`。
6. 对 `type: kotlin` 的 handler，根据 id 找到 KSP handler，并用 `handler.yml` 补全 `name`、`regex`、资源路径、字体列表等描述信息。
7. 对 generator，根据 id 找到 KSP generator，并用 `generator.yml` 补全 `name`、`regex`、资源路径、字体列表等描述信息。
8. 对 `type: template` 的 handler，根据 `handler.yml` 和 `layout.yml` 创建 `TemplateHandler`。
9. 合并 KSP handler、KSP generator 和 template handler，得到最终能力列表。
10. 对重复 id、配置类型和 KSP 注册类型不匹配、`type: kotlin` 找不到 KSP 注册项等情况报错。

资源、图片、SVG、字体使用懒加载：

- 初始化阶段只解析配置和建立索引，不强制读取所有图片和字体。
- handler/generator 实际执行时再通过 `Assets` 读取对应资源。
- 读取成功的资源可以缓存。
- 读取失败不能永久缓存失败结果；用户补齐资源文件后，再次调用应重新尝试读取并恢复正常。
- `validateCommandAssets` 是发布前主动完整校验入口，可以提前发现缺文件、坏图片、坏字体和坏配置。

## 匹配语义

最终能力列表中的每一项都必须有 `regex`：

- `FramesHandler` 使用现有 `regex`。
- generator 或其他能力没有正则时，先使用 `name` 构造 `Regex(name)`。
- 配置文件中的 `regex` 优先级最高。

匹配规则：

- 对用户输入的指令名使用 `descriptor.regex.matches(commandName)`。
- 匹配成功后，继续向 args 写入 `args["command_name"] = commandName`，兼容 `Percent0Handler` 这类依赖原始指令名的逻辑。
- 如果多个能力匹配同一个指令名，按 registry 初始化顺序取第一个，并在校验任务中输出冲突警告；同 id 冲突仍然直接失败。
- 后续 HTTP 服务暴露同一份 `regex` 和匹配语义，外部服务不需要维护另一份别名或匹配规则。

## 资源安装方式

系统不负责下载、缓存或解压 Release zip。Release 只发布 `tavolo-assets-${version}.zip`，用户需要手动下载并解压到配置的资源根目录。

解压后的资源目录应为：

```text
assets/
  version.yml
  handlers/
  generators/
```

用户可通过以下方式指定资源目录：

```text
-Dtavolo.assets.dir=/path/to/assets
TAVOLO_ASSETS_DIR=/path/to/assets
```

更新和回滚由用户或外部部署脚本完成，本项目运行时只校验并读取当前 `rootDir` 下的资源。外部 zip 只包含数据、图片和字体，不包含可执行代码。

该步骤解决的问题：职责边界清晰，项目只负责读取目录资源和生成 Release zip，不维护下载器、缓存目录或版本切换逻辑。

## 实施顺序

### 1. 定义资源目录和配置格式

新增文档、`version.yml` 和少量示例资源目录：

```text
assets/
  version.yml
  handlers/
    _example/
      handler.yml
      layout.yml
      assets/
  generators/
    _example/
      generator.yml
      assets/
```

先不迁移真实指令，只确定 schema 和校验规则。

解决问题：避免后续边写代码边反复调整目录规范。

### 2. 实现 AssetProvider

新增 `AssetProvider` 及 directory 实现。

该阶段只建立统一资源入口和路径规范，不迁移真实 handler/generator 调用点。需要记录并约束后续一次性迁移的旧入口清单：

- `getJarImage`
- `readJarFile`
- `DrawData.loadFromJar`
- 指令专用字体加载

真实调用点统一在“一次性迁移所有真实 handler 和 generator”阶段改为 `handlers/{id}/...` 或 `generators/{id}/...`，不做全局旧路径自动映射，也不保留长期混合读取状态。

解决问题：先形成稳定的新资源读取入口，避免在真实迁移前把旧 jar 资源路径和外部资源目录混在同一套运行逻辑里。

### 3. 增加 KSP id 和 generator 注册

调整 `@ImageHandler` 要求必填 `id`，新增 `@ImageGenerator` 注册注解和 `generatorSet` 生成逻辑，并将原 `ImageGenerator` 接口改名为 `FramesGenerator`。

同时给现有 handler 和 generator 补英文 id：

- handler 参考类名，例如 `RubHandler` -> `rub`。
- generator 参考类名，例如 `BlueArchiveGenerator` -> `blue_archive`。

解决问题：`CommandRegistry` 可以稳定合并 KSP 注册项和配置项，generator 也能被统一列出，后续 HTTP 服务可以直接暴露这部分能力。

### 4. 增加资源校验和打包任务

实现 `validateCommandAssets` 和 `packageCommandAssets`。

CI 或 Release 流程中先执行：

```text
test
validateCommandAssets
packageCommandAssets
```

测试任务需要显式指定项目根目录下的 `assets`：

```kotlin
tasks.test {
    systemProperty("tavolo.assets.dir", rootProject.projectDir.resolve("assets").absolutePath)
}
```

解决问题：资源包发布质量可控，Release 附件可自动生成。

### 5. 实现 TemplateHandler 最小闭环

支持一个输入头像、固定帧序列、静态素材层和 `DrawData` 等价绘制能力。

先用测试夹具和一个最小示例验证闭环，再进行真实资源的一次性迁移。

解决问题：验证配置格式、资源读取、绘制输出和现有 handler 协议是否能闭环。

### 6. 实现指令注册合并

新增 `CommandRegistry` 和 `ResourceCommandLoader`，把 KSP 注册的 Kotlin handler/generator 和运行时创建的模板 handler 合并为最终列表。

合并后：

- 复杂 Kotlin handler 仍来自 KSP。
- `type: template` handler 来自 `handler.yml` 和 `layout.yml`。
- `type: kotlin` 的 `handler.yml` 只覆盖或补充 KSP handler 的描述信息和资源配置。
- generator 来自 KSP，`generator.yml` 只覆盖或补充 KSP generator 的描述信息和资源配置。

解决问题：硬编码逻辑和配置化模板共存，对调用方仍是同一个 handler 列表。

### 7. 一次性迁移所有真实 handler 和 generator

在 `Assets`、KSP 注册、模板闭环和 `CommandRegistry` 都具备后，一次性迁移当前项目内所有 handler/generator 的资源路径，不保留长期 jar 资源和外部 `assets` 混合读取状态。

迁移规则：

- 所有 handler 素材移动到 `handlers/{id}/assets`，并新增 `handler.yml`。
- 所有 generator 素材移动到 `generators/{id}/assets`，并新增 `generator.yml`。
- 指令专用字体移动到对应的 `handlers/{id}/fonts` 或 `generators/{id}/fonts`，并写入配置文件。
- 可模板化的同质化 handler 转为 `type: template`，新增 `layout.yml`，删除对应重复 Kotlin handler。
- 复杂 Kotlin handler 保留 Kotlin 绘制逻辑和 KSP 注册，`handler.yml` 使用 `type: kotlin`，只迁移素材路径、字体、名称和正则。
- generator 固定由 KSP 注册，`generator.yml` 不声明 `type`，运行时描述的 `type` 固定派生为 `kotlin`。
- 迁移后的调用点直接使用 `Assets` 和新路径，不再通过 `getJarImage`、`readJarFile`、`DrawData.loadFromJar` 读取新资源。

迁移时需要完整确认以下类型都被覆盖：

- 纯模板 GIF。
- 单图模板。
- 多输入但仍是固定 layout 的模板。
- 复杂 Kotlin handler。
- generator。

其中 `ygo` 属于复杂 Kotlin handler，迁移时不改绘制逻辑，也继续通过 KSP 注册；但需要优先把图片素材和专用字体迁移到 `handlers/ygo`，并让 `YgoHandler` 从 `Assets` 和 `handler.yml` 注册字体。

解决问题：一次性切断 jar 资源路径依赖，避免长期维护两套资源读取路径，同时保留复杂逻辑的 Kotlin 实现边界。

### 8. 发布资源 zip

Release 流程执行 `packageCommandAssets`，产出 `tavolo-assets-${version}.zip`，并把该 zip 作为 Release 附件发布。

系统运行时不读取 zip，也不负责下载和解压。用户下载 zip 后手动解压到 `AssetsConfig.rootDir` 指向的目录。

解决问题：资源可以随 Release 发布，同时避免在运行时引入 zip 缓存、网络下载和版本切换复杂度。

## 测试计划

新增测试优先级：

- `AssetProvider`：directory 读取、路径规范化和错误信息。
- 路径安全：禁止绝对路径和 `..`。
- 配置解析：`version.yml` 的项目版本和打包日期、`handler.yml`、`generator.yml`、`layout.yml` 必填字段和默认值。
- 目录格式：`handlers/*` 只能使用 `handler.yml`，`generators/*` 只能使用 `generator.yml`；两者格式不允许混用。
- 资源校验：缺素材、坏图片、错误 id 能报出中文错误。
- 字体校验：缺字体、坏字体、重复字体名冲突能报出中文错误。
- KSP 生成：handler id、generator id、缺失 id、非法 id。
- 注册合并：KSP handler、KSP generator、模板 handler、重复 id、`type: kotlin` 缺少 KSP 注册项、`type: template` 重复注册。
- 加载流程：先读配置，再补充 KSP 注册项，再创建模板 handler，资源懒加载失败后补文件可重试。
- `TemplateRenderer`：用最小测试资源验证帧数、duration、图层顺序、透明度、旋转和翻转。
- 迁移指令 smoke test：确认返回类型、帧数、尺寸和非空图像。
- Gradle 任务：确认 zip 结构和 `version.yml` 内容稳定。

generator 和复杂 handler 的最终视觉效果仍以人工检查为主；自动测试覆盖它们的 KSP 注册、配置合并、资源读取和字体加载。

## 兼容策略

- 资源迁移一次完成：新逻辑不再从 `src/main/resources/statistic` 读取素材。
- `getJarImage`、`readJarFile`、`DrawData.loadFromJar` 这类旧入口不再作为新资源读取入口；迁移后的调用点直接使用 `Assets` 和新路径。
- 硬编码逻辑 handler 继续使用 Kotlin object 和 KSP 注册；模板 handler 由运行时配置注册。
- generator 从直接依赖 jar 调用改为 KSP 注册；调用方后续应通过统一 registry 或 HTTP 服务发现能力。
- 资源目录缺失或未安装对应指令资源时，报出包含 `rootDir` 和指令 `id` 的中文错误，提示用户下载或配置资源目录。

## 风险和处理

- 路径迁移导致旧指令找不到素材：通过一次性资源校验、smoke test 和明确的 `rootDir` 错误信息降低风险。
- 模板语法膨胀：当前实施只支持已知重复场景，复杂逻辑继续 Kotlin 实现。
- 配置错误运行时才暴露：Release 前强制执行 `validateCommandAssets`。
- 用户配置了错误的资源目录：错误信息中输出当前 `rootDir` 和命中的资源来源，便于定位。

## 完成标准

- `AssetProvider` 覆盖 directory 场景。
- 旧资源调用点已统一迁移到 `Assets`，`DrawData`、`getJarImage` 和 `readJarFile` 不再作为新资源读取入口。
- `validateCommandAssets` 能校验全部 `handlers/{id}` 和 `generators/{id}` 目录。
- `packageCommandAssets` 能生成 Release 可用 zip。
- handler 和 generator 都已具备 KSP 自动注册和稳定英文 id。
- 当前所有 handler/generator 的资源路径已一次性迁移到 `assets`，可模板化 handler 已迁移为 `TemplateHandler`，复杂 Kotlin handler 和 generator 已接入配置补全和 KSP 注册。
- `CommandRegistry` 能合并 KSP handler、KSP generator 和配置 handler，并对重复 id 报错。
- `ygo` 专用字体已列入 `handlers/ygo/handler.yml` 并可通过 `Assets` 注册。
- 默认 `assets` 目录存在时，无需额外配置即可读取指令资源。
- 显式配置资源目录后，资源从该目录读取。
- 文档中记录资源目录规范、配置字段、Release zip 打包流程和手动安装方式。
