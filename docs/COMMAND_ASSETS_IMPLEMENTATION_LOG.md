# 指令资源外置实施日志

本文档记录 `COMMAND_ASSETS_IMPLEMENTATION.md` 的实际实施过程。每个阶段完成后补充实际改动、验证结果和遗留问题。

## 2026-05-09 阶段 0：文档收敛

- 修正实施计划中“第 2 步迁移真实调用点”和“第 7 步一次性迁移”的时机冲突。
- 提交文档变更：`75c33cf docs: 完善指令资源外置实施计划`。

## 2026-05-09 阶段 1：基础设施

- 新增统一资源读取入口 `top.e404.tavolo.assets.Assets` 和 `AssetsConfig`。
- `Assets` 支持 `tavolo.assets.dir`、`TAVOLO_ASSETS_DIR` 和默认 `assets` 目录。
- 调整 `@ImageHandler` 必填 `id`，新增 `@ImageGenerator(id)` 注解。
- 将原 `ImageGenerator` 接口改名为 `FramesGenerator`。
- 扩展 KSP，生成 `handlerSet`、`handlerMap`、`generatorSet` 和 `generatorMap`。
- 新增 `CommandRegistry` 和 `ResourceCommandLoader`，先读取 `version.yml`，再从 `assets/handlers/*/handler.yml`、`assets/generators/*/generator.yml` 合并 KSP 注册项。

## 2026-05-09 阶段 2：资源迁移

- 新增根目录 `assets/version.yml`。
- 为 73 个已注册 handler 生成 `assets/handlers/{id}/handler.yml`。
- 为 9 个 generator 生成 `assets/generators/{id}/generator.yml`。
- 将 handler/generator 的 `statistic/...` 资源引用改为 `Assets` 读取。
- 将 `core/src/main/resources/statistic` 中被引用的素材复制到对应 `assets/handlers/{id}/assets` 或 `assets/generators/{id}/assets`。
- 删除 `core/src/main/resources/statistic`，避免素材继续打进 core jar。
- `getJarImage`、`getJarFile`、`getJarFileStream`、`readJarFile` 已从公共工具中移除，新资源不再提供 jar 读取入口。

## 2026-05-09 阶段 3：构建任务

- `core` 测试任务显式指定 `tavolo.assets.dir` 为项目根目录下的 `assets`。
- 新增 `validateCommandAssets`，校验 `version.yml`、handler/generator 配置文件和 generator 不声明 `type/layout`。
- 新增 `packageCommandAssets`，从根目录 `assets` 打包 Release 资源 zip。

## 2026-05-09 阶段 4：专用字体迁移

- 将 `ygo` 专用字体放入 `assets/handlers/ygo/fonts`，并写入 `handler.yml` 的 `fonts` 列表。
- 将 `blue_archive` 专用字体放入 `assets/generators/blue_archive/fonts`，并写入 `generator.yml` 的 `fonts` 列表。
- `YgoHandler` 改为通过 `Assets.typeface` 从 `handlers/ygo/fonts` 懒加载专用字体。
- `BlueArchiveGenerator` 改为通过 `Assets.typeface` 从 `generators/blue_archive/fonts` 懒加载专用字体。
- `validateCommandAssets` 增加 `fonts.file` 存在性、扩展名和路径越界校验。

## 验证记录

- `./gradlew.bat clean :core:compileKotlin "-Dorg.gradle.java.installations.paths=D:\Jdk\dragonwell-11.0.21.18+9-GA"` 通过，KSP 从空构建目录生成 73 个 handler 和 9 个 generator 注册项。
- `./gradlew.bat :core:test :core:packageCommandAssets "-Dorg.gradle.java.installations.paths=D:\Jdk\dragonwell-11.0.21.18+9-GA"` 通过；`core:test` 当前无自动测试源码，资源 zip 已生成。
- `git diff --check` 通过。
- PowerShell 静态资源检查通过：源码中静态 `Assets.*("...")` 路径均能在根 `assets` 下找到文件。
- PowerShell 动态资源前缀检查通过：包含 `$` 插值的 `Assets.*("...")` 路径，其资源目录前缀均存在。
- PowerShell 配置检查通过：73 个 handler 和 9 个 generator 的配置目录、`id`、handler `type`、generator 禁止字段均符合当前规则。
