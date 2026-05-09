# HTTP 指令能力服务实施计划

本文档先定义后端和客户端模块的文件大纲、接口边界和实施顺序。确认无误后再按阶段实施，并在新的实施日志文档中记录实际改动和验证结果。

## 目标

- 用 Ktor 在 `http-server` 模块中提供指令能力发现接口。
- 从 `core` 的 `CommandRegistry` 获取最终能力列表，不在 HTTP 层重新维护指令模型。
- 返回 handler 和 generator 的统一描述，供外部服务直接复用 `regex` 做指令匹配。
- 新增客户端模块，用 Ktor Client 封装请求后端的能力发现接口。

## 非目标

- 当前阶段不设计 alias，继续暴露配置中的 `regex`。
- 当前阶段不引入鉴权、服务注册、资源包下载或 zip 解压逻辑。
- 当前阶段不把 HTTP DTO 反向耦合到 `core` 的运行时对象，HTTP 层只做序列化映射。

## 接口设计

### GET /commands

返回当前资源目录和 KSP 注册合并后的全部能力。

响应示例：

```json
{
  "assets": {
    "version": "2.0.0",
    "time": "2026-05-09 00:00:00"
  },
  "commands": [
    {
      "id": "rub",
      "category": "handler",
      "type": "kotlin",
      "name": "揉",
      "regex": "揉|rub",
      "version": 1
    },
    {
      "id": "blue_archive",
      "category": "generator",
      "type": "kotlin",
      "name": "BlueArchive",
      "regex": "BlueArchive",
      "version": 1
    }
  ]
}
```

字段说明：

- `assets.version`：资源包版本，来自 `assets/version.yml`。
- `assets.time`：资源包打包时间，来自 `assets/version.yml`。
- `commands[].id`：能力唯一英文 id。
- `commands[].category`：`handler` 或 `generator`。
- `commands[].type`：实现类型，handler 为 `template` 或 `kotlin`，generator 固定为 `kotlin`。
- `commands[].name`：展示名。
- `commands[].regex`：正则字符串，外部服务可直接复用。
- `commands[].version`：单个能力配置版本。

### GET /health

用于进程存活检查，返回最小状态。

响应示例：

```json
{
  "status": "ok"
}
```

### POST /handlers/{id}/execute

调用已有 handler 能力处理一个输入图片或 GIF，返回最终 PNG/GIF 二进制。

请求格式为 `multipart/form-data`：

- `image`：必填，输入图片或 GIF 文件。
- `args`：可选，JSON 字符串，类型为 `Map<String, String>`。

请求示例：

```text
POST /handlers/rub/execute
Content-Type: multipart/form-data

image=@input.png
args={"d":"60","w":"300"}
```

响应：

- 成功：返回图片二进制。
- 单帧结果：`Content-Type: image/png`。
- 多帧结果：`Content-Type: image/gif`。
- 失败：返回 JSON 错误。

### POST /generators/{id}/execute

调用已有 generator 能力生成图片或 GIF，返回最终 PNG/GIF 二进制。

请求格式为 `application/json`：

```json
{
  "args": {
    "text": "示例文本",
    "color": "#ffffff"
  }
}
```

响应规则与 handler 相同。

### POST /commands/{id}/execute

统一执行入口，根据 `CommandRegistry` 中的 `category` 分发到 handler 或 generator。

- 若目标是 `handler`，请求必须是 `multipart/form-data`，并提供 `image`。
- 若目标是 `generator`，请求必须是 `application/json`。
- `id` 使用 `GET /commands` 返回的稳定英文 id，不使用 name 或 regex 调用。

该入口方便外部服务只保存一个能力 id，不需要自己区分 handler/generator。

### 错误响应

```json
{
  "message": "handler 不存在: unknown"
}
```

HTTP 状态码：

- `400`：请求格式错误、缺少图片、参数格式错误、能力执行失败。
- `404`：能力 id 不存在，或分类不匹配。
- `500`：未预期的服务端错误。

## 后端文件大纲

```text
http-server/
  build.gradle.kts
  src/main/kotlin/top/e404/tavolo/http/server/
    TavoloHttpServer.kt
    ServerConfig.kt
    ServerApplication.kt
    dto/
      CommandDtos.kt
    route/
      CommandRoutes.kt
      ExecuteRoutes.kt
      HealthRoutes.kt
    service/
      CommandService.kt
      CommandExecuteService.kt
```

文件职责：

- `TavoloHttpServer.kt`：提供 `main` 入口，读取配置并启动 Netty。
- `ServerConfig.kt`：读取 host、port、资源目录等运行配置；资源目录继续使用 `tavolo.assets.dir` 或 `TAVOLO_ASSETS_DIR`。
- `ServerApplication.kt`：安装 Ktor 插件，注册路由。
- `dto/CommandDtos.kt`：定义可序列化响应 DTO。
- `route/CommandRoutes.kt`：注册 `GET /commands`。
- `route/ExecuteRoutes.kt`：注册 handler、generator 和统一执行入口。
- `route/HealthRoutes.kt`：注册 `GET /health`。
- `service/CommandService.kt`：调用 `CommandRegistry.load()` 并映射为 DTO。
- `service/CommandExecuteService.kt`：调用已有 `FramesHandler` 或 `FramesGenerator`，编码最终成品。

## 客户端模块大纲

新增 Gradle 模块：

```text
http-client/
  build.gradle.kts
  src/main/kotlin/top/e404/tavolo/http/client/
    TavoloCommandClient.kt
    TavoloClientConfig.kt
    dto/
      CommandDtos.kt
    request/
      ExecuteRequests.kt
```

文件职责：

- `TavoloCommandClient.kt`：封装 Ktor Client，请求 `GET /commands` 和 `GET /health`。
- `TavoloCommandClient.kt`：同时封装 `executeHandler`、`executeGenerator` 和 `executeCommand`。
- `TavoloClientConfig.kt`：配置 baseUrl、超时等客户端参数。
- `dto/CommandDtos.kt`：客户端 DTO。字段与后端响应保持一致，避免客户端依赖 `http-server`。
- `request/ExecuteRequests.kt`：定义客户端执行请求和二进制响应模型。

客户端 API 草案：

```kotlin
class TavoloCommandClient(
    config: TavoloClientConfig,
    httpClient: HttpClient = defaultHttpClient()
) : Closeable {
    suspend fun commands(): CommandsResponse
    suspend fun health(): HealthResponse
    suspend fun executeHandler(id: String, image: ByteArray, args: Map<String, String> = emptyMap()): ExecutedImage
    suspend fun executeGenerator(id: String, args: Map<String, String> = emptyMap()): ExecutedImage
    suspend fun executeCommand(id: String, request: ExecuteCommandRequest): ExecutedImage
}
```

`ExecutedImage` 保存：

- `bytes`：最终成品图片或 GIF。
- `contentType`：服务端返回的 MIME。
- `fileExtension`：根据 MIME 推导的 `png` 或 `gif`。

## 构建调整

- `settings.gradle.kts` 新增 `:http-client`。
- `http-client/build.gradle.kts` 引入：
  - `ktor-client-core-jvm`
  - `ktor-client-cio-jvm`
  - `ktor-client-content-negotiation-jvm`
  - `ktor-serialization-kotlinx-json`
  - `kotlinx-serialization-json-jvm`
- `http-server/build.gradle.kts` 保留对 `:core` 的依赖，不依赖 `:http-client`。

## 实施顺序

### 1. 定义 HTTP DTO

先在后端定义 `CommandDto`、`AssetsDto`、`CommandsResponse` 和 `HealthResponse`。

解决问题：把 `Regex`、枚举大小写和 core 内部对象转换为稳定 JSON 结构。

### 2. 实现后端服务和路由

实现 `CommandService`、`GET /commands` 和 `GET /health`。

解决问题：外部服务可以通过 HTTP 获取全部 handler/generator 能力定义。

### 3. 增加服务启动入口

实现 `TavoloHttpServer.main`、`ServerConfig` 和 Ktor Netty 启动。

解决问题：模块可以作为独立后端进程启动。

### 4. 新增客户端模块

新增 `:http-client`，封装 Ktor Client 请求。

解决问题：调用方无需直接拼 URL 或处理 Ktor 底层请求。

### 5. 补测试

后端测试：

- `GET /health` 返回 `ok`。
- `GET /commands` 返回非空列表。
- 返回的 `commands` 包含 handler 和 generator。
- DTO 中 `category/type` 为小写字符串。

客户端测试：

- 使用 Ktor MockEngine 测试 `commands()` 能正确反序列化。
- 使用 Ktor MockEngine 测试 `health()` 能正确反序列化。

### 6. 记录实施日志

新增实施日志文档：

```text
docs/HTTP_COMMAND_SERVICE_IMPLEMENTATION_LOG.md
```

每个阶段记录：

- 实际改动。
- 验证命令。
- 遗留问题或范围调整。

## 验证计划

- `./gradlew.bat :http-server:compileKotlin`
- `./gradlew.bat :http-client:compileKotlin`
- `./gradlew.bat :http-server:test`
- `./gradlew.bat :http-client:test`
- 如需整体确认，再运行 `./gradlew.bat :http-server:build :http-client:build`

测试任务需要保证 `tavolo.assets.dir` 指向项目根目录下的 `assets`，避免后端测试受工作目录影响。

## 已确认点

- 保留 `GET /health`，用于进程存活检查和基础测试。
- 客户端模块名使用 `http-client`，与现有 `http-server` 对称。
- 当前阶段只暴露能力定义，具体能力来自 `core` 模块已有的 `CommandRegistry`，不新增图片处理或图片生成执行接口。
