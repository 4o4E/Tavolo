# Tavolo HTTP Server

HTTP 服务暴露 handler／generator 指令及 Markdown／结构化图表 PNG 渲染，默认监听 `http://127.0.0.1:8080`。

## 运行前提

- 资源目录需要指向项目根目录下的 `assets`，例如从项目根目录启动，或显式传入 `-Dtavolo.assets.dir=./assets`。
- 以下示例分别提供标准 `curl` 和 PowerShell 写法，JSON 参数请按对应 shell 复制。
- 示例输入图 URL 取自 `graphics/src/manualTest/kotlin/TestRender.kt` 中的测试引用图。

## 连通性测试

下面只保留最小连通性检查：下载输入图、检查服务、查看指令列表、执行一个 handler、执行一个 generator。

### 标准 curl

```bash
mkdir -p run/in run/out
curl -L "https://i1.hdslb.com/bfs/face/c1733474892caa45952b2c09a89323157df7129a.jpg@64w_64h.jpg" -o run/in/http-test.jpg
curl "http://127.0.0.1:8080/health"
curl "http://127.0.0.1:8080/commands"
curl -X POST "http://127.0.0.1:8080/handlers/round/execute" -F "image=@run/in/http-test.jpg" -w "\n%{http_code} %{content_type}\n" -o run/out/http-round.png
curl -X POST "http://127.0.0.1:8080/generators/blue_archive/execute" -H "Content-Type: application/json" -d '{"args":{"l":"Tavolo","r":"HTTP","b":"SERVER TEST"}}' -w "\n%{http_code} %{content_type}\n" -o run/out/http-blue-archive.png
```

### PowerShell

```powershell
New-Item -ItemType Directory -Force run/in, run/out
curl.exe -L "https://i1.hdslb.com/bfs/face/c1733474892caa45952b2c09a89323157df7129a.jpg@64w_64h.jpg" -o run/in/http-test.jpg
curl.exe "http://127.0.0.1:8080/health"
curl.exe "http://127.0.0.1:8080/commands"
curl.exe -X POST "http://127.0.0.1:8080/handlers/round/execute" -F "image=@run/in/http-test.jpg" -w "`n%{http_code} %{content_type}`n" -o run/out/http-round.png
curl.exe -X POST "http://127.0.0.1:8080/generators/blue_archive/execute" -H "Content-Type: application/json" -d '{"args":{"l":"Tavolo","r":"HTTP","b":"SERVER TEST"}}' -w "`n%{http_code} %{content_type}`n" -o run/out/http-blue-archive.png
```

如果执行接口返回 `200 image/png`，并且 `run/out` 下生成了对应文件，说明 HTTP 服务、资源目录和指令执行链路已经连通。

## Docker 运行

Docker 镜像不内置指令资源，默认通过 Compose 把项目根目录下的 `assets` 挂载到容器 `/app/assets`。

```bash
cd http-server
cp .env.example .env
docker compose up --build
```

默认访问地址仍然是 `http://127.0.0.1:8080`。如需修改宿主机端口或资源目录，编辑 `http-server/.env`：

```dotenv
HTTP_SERVER_PORT=8080
ASSETS_DIR=../assets
```

也可以直接构建镜像：

```bash
docker build -f http-server/Dockerfile -t tavolo-http-server:latest .
docker run --rm -p 8080:8080 -e TAVOLO_ASSETS_DIR=/app/assets -v "$(pwd)/assets:/app/assets:ro" tavolo-http-server:latest
```

## Markdown 与结构化图表

- `POST /render/markdown`：JSON 简单 Markdown 文档、阅读宽度、主题和显式 Base64 图片映射；复杂文档、Mermaid 与 LaTeX 由外部渲染。
- `POST /render/chart`：JSON 图表模型、独立序列、坐标轴和主题。
- 成功直接返回 `image/png`，无 Node.js／浏览器运行环境要求，Java 运行基线为 11。
- 字体由部署环境提供；`style.fontFamily`／`codeFontFamily` 使用已安装 family 或应用注册的别名。

完整 JSON 示例、六类图表数据字段、图片资源规则、错误响应及 Kotlin 客户端见 [Markdown 与结构化图表](../docs/Markdown与结构化图表.md#http-json-与客户端)。指令资源目录仍按上文配置。
