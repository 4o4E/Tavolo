# HTTP 指令服务设计

本文档描述 `http-server` 和 `http-client` 模块当前对 Tavolo 指令能力的 HTTP 暴露方式。

## 设计目标

- HTTP 层复用 `core` 的 `CommandRegistry` 和执行能力，不重新维护指令模型。
- 对外提供能力发现、健康检查、handler 执行、generator 执行和统一指令执行接口。
- 客户端模块封装 Ktor Client 调用，避免调用方手写 multipart 和二进制响应处理。
- 服务端返回稳定 JSON 错误结构，执行成功时返回 PNG/GIF 二进制。

## 服务端组成

`http-server` 主要结构：

```text
http-server/src/main/kotlin/top/e404/tavolo/http/server/
  ServerApplication.kt
  ServerConfig.kt
  ErrorHandling.kt
  route/
    HealthRoutes.kt
    CommandRoutes.kt
    ExecuteRoutes.kt
  service/
    CommandService.kt
    CommandExecuteService.kt
  dto/
    CommandDtos.kt
    ExecuteDtos.kt
```

`ServerApplication` 安装 JSON、压缩、调用日志和错误处理插件，并注册路由。

默认监听地址由 `ServerConfig` 控制，Docker 场景通过环境变量暴露端口和资源目录。

## API

### GET /health

进程存活检查。

响应：

```json
{
  "status": "ok"
}
```

### GET /commands

返回当前资源目录和 KSP 注册项合并后的能力列表。

响应示例：

```json
{
  "assets": {
    "version": "2.0.0",
    "time": "2026-05-09 00:00:00"
  },
  "commands": [
    {
      "id": "round",
      "category": "handler",
      "type": "kotlin",
      "name": "Round",
      "regex": "(?i)round",
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

### POST /handlers/{id}/execute

执行指定 handler。请求格式为 `multipart/form-data`：

- `image`：必填，输入图片或 GIF。
- `args`：可选，JSON 字符串，类型为 `Map<String, String>`。

成功响应：

- 单帧：`Content-Type: image/png`。
- 多帧：`Content-Type: image/gif`。

### POST /generators/{id}/execute

执行指定 generator。请求格式为 `application/json`：

```json
{
  "args": {
    "text": "示例文本"
  }
}
```

响应规则与 handler 相同。

### POST /commands/{id}/execute

统一执行入口。服务端根据 `CommandRegistry` 中的 `category` 分派到 handler 或 generator：

- handler：接受 multipart 请求，字段同 `/handlers/{id}/execute`。
- generator：接受 JSON 请求，字段同 `/generators/{id}/execute`。

## 错误响应

错误统一返回 JSON：

```json
{
  "code": "BAD_REQUEST",
  "message": "错误说明"
}
```

常见状态：

- `400`：请求格式错误、缺少图片、JSON 参数无法解析。
- `404`：指令 id 不存在。
- `500`：执行过程中的未预期错误。

## 客户端模块

`http-client` 提供 `TavoloCommandClient`：

- `health()`：调用 `/health`。
- `commands()`：调用 `/commands`。
- `executeHandler(id, image, args, fileName)`：调用 handler multipart 接口。
- `executeGenerator(id, args)`：调用 generator JSON 接口。
- `executeCommand(id, request)`：调用统一执行入口。

执行接口返回二进制响应对象，保留 `Content-Type`，调用方负责保存或继续转发。

## 部署与资源目录

HTTP 服务不内置指令资源。运行时必须让 `Assets` 能找到资源目录：

- 本地运行：从项目根目录启动，或传入 `-Dtavolo.assets.dir=...`。
- Docker Compose：把宿主机 `assets` 挂载到容器 `/app/assets`，并设置 `TAVOLO_ASSETS_DIR=/app/assets`。

连通性测试和 Docker 示例见 `http-server/README.md`。

## 验证

当前测试覆盖：

- `/health` 和 `/commands`。
- handler、generator 和统一 `/commands/{id}/execute` 执行成功路径。
- 未知 id、缺少图片、非法 args、非法 JSON 等错误路径。
- `TavoloCommandClient` 的请求路径、multipart、JSON 和二进制响应处理。
