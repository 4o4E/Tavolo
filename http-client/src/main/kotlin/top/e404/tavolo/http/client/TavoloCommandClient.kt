package top.e404.tavolo.http.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.e404.tavolo.http.client.dto.CommandsResponse
import top.e404.tavolo.http.client.dto.ErrorResponse
import top.e404.tavolo.http.client.dto.HealthResponse
import top.e404.tavolo.http.client.request.ExecuteCommandRequest
import top.e404.tavolo.http.client.request.ExecuteRequest
import top.e404.tavolo.http.client.request.ExecutedImage
import java.io.Closeable

class TavoloCommandClient(
    private val config: TavoloClientConfig = TavoloClientConfig(),
    private val httpClient: HttpClient = defaultHttpClient(),
) : Closeable {
    suspend fun commands(): CommandsResponse {
        val response = httpClient.get(config.resolve("/commands"))
        response.ensureSuccess()
        return response.body()
    }

    suspend fun health(): HealthResponse {
        val response = httpClient.get(config.resolve("/health"))
        response.ensureSuccess()
        return response.body()
    }

    suspend fun executeHandler(
        id: String,
        image: ByteArray,
        args: Map<String, String> = emptyMap(),
        fileName: String = "image",
    ): ExecutedImage =
        executeMultipart(config.resolve("/handlers/$id/execute"), image, args, fileName)

    suspend fun executeGenerator(
        id: String,
        args: Map<String, String> = emptyMap(),
    ): ExecutedImage {
        val response = httpClient.post(config.resolve("/generators/$id/execute")) {
            contentType(ContentType.Application.Json)
            setBody(ExecuteRequest(args))
        }
        return response.toExecutedImage()
    }

    suspend fun executeCommand(
        id: String,
        request: ExecuteCommandRequest,
    ): ExecutedImage =
        when (request) {
            is ExecuteCommandRequest.Handler -> executeMultipart(
                url = config.resolve("/commands/$id/execute"),
                image = request.image,
                args = request.args,
                fileName = request.fileName,
            )

            is ExecuteCommandRequest.Generator -> {
                val response = httpClient.post(config.resolve("/commands/$id/execute")) {
                    contentType(ContentType.Application.Json)
                    setBody(ExecuteRequest(request.args))
                }
                response.toExecutedImage()
            }
        }

    override fun close() {
        httpClient.close()
    }

    private suspend fun executeMultipart(
        url: String,
        image: ByteArray,
        args: Map<String, String>,
        fileName: String,
    ): ExecutedImage {
        val response = httpClient.submitFormWithBinaryData(
            url = url,
            formData = formData {
                append(
                    key = "image",
                    value = image,
                    headers = Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                    }
                )
                if (args.isNotEmpty()) {
                    append("args", Json.encodeToString(args))
                }
            }
        )
        return response.toExecutedImage()
    }

    private suspend fun HttpResponse.toExecutedImage(): ExecutedImage {
        ensureSuccess()
        return ExecutedImage(
            bytes = body(),
            contentType = headers[HttpHeaders.ContentType].orEmpty(),
        )
    }

    private suspend fun HttpResponse.ensureSuccess() {
        if (status.value in 200..299) return
        val text = runCatching { bodyAsText() }.getOrDefault("")
        val message = runCatching { json.decodeFromString<ErrorResponse>(text).message }.getOrNull()
            ?: text.ifBlank { status.description }
        throw TavoloCommandClientException(status.value, message)
    }

    private fun TavoloClientConfig.resolve(path: String): String =
        baseUrl.trimEnd('/') + path

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
        }

        fun defaultHttpClient(): HttpClient =
            HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(json)
                }
            }
    }
}

class TavoloCommandClientException(
    val statusCode: Int,
    message: String,
) : RuntimeException(message)
