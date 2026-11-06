package top.e404.tavolo.draw.compose

import kotlinx.serialization.json.*
import java.nio.file.Path
import java.time.Duration
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** 图形保留逻辑尺寸、基线与原始 SVG／透明 PNG 数据。 */
data class MarkdownGraphic(
    val bytes: ByteArray,
    val width: Float,
    val height: Float,
    val baseline: Float,
    val svg: Boolean
)

interface MarkdownRuntime {
    fun math(source: String, display: Boolean, fontSize: Float, color: Int, fontXHeight: Float? = null): MarkdownGraphic
    fun mermaid(source: String, width: Float, fontFamily: String, dark: Boolean): MarkdownGraphic
    /** 返回带 hljs-* 样式类的 HTML；未知语言按 GitHub 规则保留纯文本。 */
    fun highlight(source: String, language: String): String
}

/**
 * 每个实例维护一个串行 Node worker；Mermaid 浏览器按需启动并复用。
 * 调用方负责 close，可在多个文档间共享。超时会关闭实例，不重试、不降级。
 */
class NodeMarkdownRuntime(
    runtimeDirectory: Path,
    nodeExecutable: String = "node",
    private val timeout: Duration = Duration.ofSeconds(60)
) : MarkdownRuntime, AutoCloseable {
    private val process: Process
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "tavolo-markdown-worker").apply { isDaemon = true }
    }
    private val input: java.io.BufferedWriter
    private val output: java.io.BufferedReader
    private var closed = false

    init {
        require(!timeout.isNegative && !timeout.isZero) { "Markdown 引擎超时必须为正数" }
        val script = runtimeDirectory.toAbsolutePath().resolve("worker.mjs").toFile()
        require(script.isFile) { "缺少 Markdown 引擎: $script；请按部署文档安装运行时" }
        process = ProcessBuilder(nodeExecutable, script.absolutePath)
            .directory(runtimeDirectory.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT).start()
        input = process.outputStream.bufferedWriter(Charsets.UTF_8)
        output = process.inputStream.bufferedReader(Charsets.UTF_8)
    }

    @Synchronized
    private fun request(operation: String, body: JsonObjectBuilder.() -> Unit): JsonObject {
        check(!closed) { "Markdown 引擎已经关闭" }
        val message = buildJsonObject { put("operation", operation); body() }.toString()
        val future = executor.submit<JsonObject> {
            input.write(message)
            input.newLine()
            input.flush()
            val line = output.readLine() ?: error("Markdown 引擎已退出，请检查 Node.js、依赖和 Chromium 配置")
            val response = Json.parseToJsonElement(line).jsonObject
            response["error"]?.let { error("Markdown $operation 渲染失败: ${it.jsonPrimitive.content}") }
            response
        }
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            abort(e)
            throw IllegalStateException("Markdown $operation 渲染超时，运行时已关闭", e)
        } catch (e: InterruptedException) {
            abort(e)
            Thread.currentThread().interrupt()
            throw IllegalStateException("Markdown 渲染被中断", e)
        } catch (e: ExecutionException) {
            throw (e.cause ?: e)
        }
    }

    override fun math(source: String, display: Boolean, fontSize: Float, color: Int, fontXHeight: Float?) = graphic(request("math") {
        put("source", source); put("display", display); put("fontSize", fontSize)
        fontXHeight?.let { put("fontXHeight", it) }
        put("color", "#%06x".format(color and 0xffffff))
    })

    override fun mermaid(source: String, width: Float, fontFamily: String, dark: Boolean) = graphic(request("mermaid") {
        put("source", source); put("width", width); put("fontFamily", fontFamily); put("dark", dark)
    })

    override fun highlight(source: String, language: String): String = request("highlight") {
        put("source", source); put("language", language)
    }.getValue("html").jsonPrimitive.content

    private fun graphic(json: JsonObject) = MarkdownGraphic(
        Base64.getDecoder().decode(json.getValue("data").jsonPrimitive.content),
        json.getValue("width").jsonPrimitive.float,
        json.getValue("height").jsonPrimitive.float,
        json.getValue("baseline").jsonPrimitive.float,
        json.getValue("svg").jsonPrimitive.boolean
    ).also {
        require(it.width.isFinite() && it.width > 0 && it.height.isFinite() && it.height > 0 && it.baseline.isFinite()) {
            "Markdown 引擎返回无效图形尺寸"
        }
    }

    private fun abort(cause: Exception) {
        // 先结束不再读取输入的进程，解除正在写入管道的线程，再关闭流。
        process.descendants().use { descendants -> descendants.forEach { it.destroyForcibly() } }
        process.destroyForcibly()
        try {
            close()
        } catch (error: Exception) {
            cause.addSuppressed(error)
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        // EOF 让 worker 正常关闭浏览器；异常/超时退出时也回收整个子进程树。
        try {
            input.close()
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.descendants().use { descendants -> descendants.forEach { it.destroyForcibly() } }
                process.destroyForcibly()
            }
        } finally {
            try { output.close() } finally { executor.shutdownNow() }
        }
    }
}
