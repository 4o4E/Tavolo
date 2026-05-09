# TODO

## HTTP 服务

- 提供获取所有指令定义的接口，例如 `GET /commands`。
- 返回内容至少包含 `id`、`category`、`type`、`name`、`regex`、`version`；`category` 表示 `handler` 或 `generator`，`type` 表示实现类型，handler 为 `template` 或 `kotlin`，generator 固定派生为 `kotlin`。
- handler 和 generator 都从统一 registry 暴露，外部服务不需要区分它来自 KSP 还是配置注册。
- 外部对接服务可以直接复用 `regex` 做指令匹配，不需要重新维护别名表。
- HTTP 服务只消费当前 core 的指令描述信息，不在资源包实施阶段单独设计一套指令模型。
