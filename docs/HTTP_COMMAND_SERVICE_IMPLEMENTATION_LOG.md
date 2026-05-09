# HTTP 指令能力服务实施日志

本文档记录 `HTTP_COMMAND_SERVICE_IMPLEMENTATION.md` 的实际实施过程。每个阶段完成后补充实际改动、验证结果和遗留问题。

## 2026-05-09 阶段 0：方案确认

- 保留 `GET /health`。
- 客户端模块命名为 `http-client`。
- 当前阶段只暴露 `core` 模块已有 handler/generator 的能力定义，不新增图片处理或图片生成执行接口。

## 2026-05-09 阶段 1：后端接口

- 删除 `http-server/src/main/kotlin/util.kt` 空文件。
- 新增 Ktor Netty 启动入口 `TavoloHttpServer`。
- 新增 `ServerConfig`，支持通过 `tavolo.http.host`、`TAVOLO_HTTP_HOST`、`tavolo.http.port` 和 `TAVOLO_HTTP_PORT` 配置监听地址。
- 新增 `ServerApplication`，安装 JSON、压缩和调用日志插件。
- 新增 `GET /health` 和 `GET /commands` 路由。
- 新增 `CommandService`，从 `CommandRegistry` 读取当前已有 handler/generator 能力并映射为 HTTP DTO。

## 2026-05-09 阶段 2：客户端模块

- `settings.gradle.kts` 新增 `:http-client`。
- 新增 `http-client` 模块，使用 Ktor Client CIO 和 JSON ContentNegotiation。
- 新增 `TavoloCommandClient`，封装 `commands()` 和 `health()` 请求。
- 新增客户端 DTO，字段与后端响应保持一致，客户端不依赖 `http-server`。

## 2026-05-09 阶段 3：测试覆盖

- `http-server` 新增路由测试，覆盖 `GET /health` 和 `GET /commands`。
- `GET /commands` 测试确认返回资源版本、非空能力列表，且同时包含 handler 和 generator。
- `http-client` 新增 MockEngine 测试，覆盖 `commands()` 和 `health()` 的请求与反序列化。

## 2026-05-09 阶段 4：能力发现懒加载修正

- 调整 KSP 生成的 `handlerMap` 和 `generatorMap`，value 改为 provider，避免仅加载能力列表时初始化所有 handler/generator。
- 调整 KSP 生成的 `handlerSet` 和 `generatorSet` 为 getter，继续兼容旧调用方，但不影响 registry 只读能力列表的懒加载路径。
- 调整 `CommandRegistry`，只在实际访问 `RegisteredHandler.handler` 或 `RegisteredGenerator.generator` 时构造具体实现。

## 2026-05-09 阶段 5：执行接口设计

- 新增 `POST /handlers/{id}/execute`，以 multipart 传入一个图片或 GIF 和可选 JSON args，返回 PNG/GIF 成品。
- 新增 `POST /generators/{id}/execute`，以 JSON 传入 args，返回 PNG/GIF 成品。
- 新增 `POST /commands/{id}/execute` 统一入口，按 `CommandRegistry` 中的 category 分发到已有 handler/generator。
- 调用目标使用 `GET /commands` 返回的稳定英文 id，不使用 name 或 regex 作为执行入口。

## 2026-05-09 阶段 6：执行接口实现

- 后端新增 `CommandExecuteService`，封装 handler/generator 执行并将 `Frame` 编码为 PNG/GIF 二进制。
- 后端新增 `ExecuteRoutes`，实现 `/handlers/{id}/execute`、`/generators/{id}/execute` 和 `/commands/{id}/execute`。
- 后端新增执行请求和错误响应 DTO。
- 客户端新增 `ExecuteRequest`、`ExecuteCommandRequest` 和 `ExecutedImage`。
- 客户端新增 `executeHandler`、`executeGenerator` 和 `executeCommand` 封装。

## 验证记录

- `./gradlew.bat clean :http-server:test :http-client:test "-Dorg.gradle.java.installations.paths=D:\Jdk\dragonwell-11.0.21.18+9-GA"` 通过。
- `./gradlew.bat :http-server:test :http-client:test "-Dorg.gradle.java.installations.paths=D:\Jdk\dragonwell-11.0.21.18+9-GA"` 通过，覆盖能力列表、健康检查、handler 执行、generator 执行、统一执行入口和客户端封装。
