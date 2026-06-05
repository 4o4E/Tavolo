# TODO

本文档只记录当前尚未完成或需要继续打磨的事项。

## 指令资源与注册

- 接入 `type: template` handler 的运行时执行链路，避免配置校验和运行时能力不一致。
- 将当前轻量 key-value 配置解析替换为正式 YAML 解析，支持更复杂的资源、字体和布局配置。
- 为资源包增加变更清单或校验信息，方便外部系统做版本比对和增量更新。
- 梳理 generator 配置中的 `fonts` 字段加载策略，统一注册时机和错误提示。

## HTTP 服务

- 增加可选鉴权、请求体大小限制和超时配置。
- 补充 OpenAPI 或等价接口描述，降低外部服务接入成本。
- 为 Docker 镜像增加 CI 构建和发布流程。
- 评估是否需要暴露资源包版本校验接口或更细粒度的能力查询接口。

## 3D 渲染

- 补充少量端到端像素特征测试，覆盖单个 cube 非背景像素和不同抗锯齿级别，不优先使用整图 golden snapshot。

## 文本字体 fallback 与 grapheme cluster

- 在 Tavolo 文本层处理 grapheme cluster（用户感知字符簇）级别的 fallback，避免业务项目为特殊 Unicode 组合符号逐个绕过。
- 已知复现场景：`୧⍤⃝` 包含 `U+0B67`、`U+2364`、`U+20DD`，Skia Paragraph fallback 会把 `⍤` 和组合圈 `⃝` 分到不同字体 run，导致组合圈无法正确附着。
- 需求目标：调用方仍只写 `text("୧⍤⃝")` 或 `AnnotatedText`，Tavolo 自动保证不能拆开的 cluster 在测量、换行、ellipsis 和绘制时作为最小单元处理。
- 规则来源应优先参考 Unicode UAX #29 的 extended grapheme cluster，不要只按 code point 或 Kotlin `Char` 拆分；至少需要覆盖 combining mark、enclosing mark、emoji ZWJ、regional indicator 等常见边界。
- 字体选择策略需要区分两层：Skia Paragraph 负责常规 fallback，Tavolo 只在 cluster 被拆 run 会破坏成形时介入，为整个 cluster 选择一个能覆盖关键码点并实际成形正常的字体。
- 已验证字体经验：当前 Noto Sans / Noto Sans Symbols / Noto Sans Symbols 2 / Noto Sans Math 不足以保证 `⍤⃝` 正确附着；`FreeMono.ttf` 对该组合效果较好，可作为 enclosing mark 兜底候选。若无 FreeMono，GNU Unifont 可作为低质量兜底但视觉风格明显。
- 实现候选：先提供内部 `segmentGraphemeClusters(text)`，让 `Text` 的测量、换行、ellipsis、AnnotatedText span 拆分都按 cluster 边界运行；再增加一个 cluster 字体选择 hook，必要时把特殊 cluster 包成单独 span。
- 兼容边界：不要把普通中文、emoji、复杂脚本一律强制到符号字体；复杂脚本仍应交给对应脚本字体和 Skia shaping。业务层指定的主字体和已有 `fontFamily` 语义不能被改成全局 Noto。
- 测试建议：增加 `୧⍤⃝`、`A⃝`、`1⃝`、emoji ZWJ、国旗 regional indicator、带变音符号拉丁文本的人工/像素特征测试；同时覆盖普通 `text(...)` 与 `AnnotatedText`、换行和 ellipsis。

## 其他

- 继续评估嵌套图片处理能力。
- 为 `image-commands` 模块中适合测试的 handler / generator 补充单元测试。
- 添加完整成体系的使用文档。
- 修复 `common` 模块当前不正确的包名。
