package top.e404.tavolo.http.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import top.e404.tavolo.http.client.request.ExecuteCommandRequest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TavoloCommandClientTest {
    @Test
    fun commandsRequestsServerAndParsesResponse() = runBlocking {
        val client = TavoloCommandClient(
            config = TavoloClientConfig(baseUrl = "http://example.test"),
            httpClient = mockClient(
                """
                {
                  "assets": {
                    "version": "2.0.1",
                    "time": "2026-05-15 15:04:15"
                  },
                  "commands": [
                    {
                      "id": "rub",
                      "category": "handler",
                      "type": "kotlin",
                      "name": "揉",
                      "regex": "揉|rub",
                      "version": 1
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        client.use {
            val response = it.commands()
            assertEquals("2.0.1", response.assets.version)
            assertEquals("rub", response.commands.single().id)
            assertEquals("handler", response.commands.single().category)
        }
    }

    @Test
    fun healthRequestsServerAndParsesResponse() = runBlocking {
        val client = TavoloCommandClient(
            config = TavoloClientConfig(baseUrl = "http://example.test/"),
            httpClient = mockClient("""{"status":"ok"}""")
        )

        client.use {
            val response = it.health()
            assertEquals("ok", response.status)
        }
    }

    @Test
    fun executeHandlerReturnsBinaryResponse() = runBlocking {
        val client = TavoloCommandClient(
            config = TavoloClientConfig(baseUrl = "http://example.test"),
            httpClient = mockBinaryClient(
                expectedPath = "/handlers/round/execute",
                expectedMethod = HttpMethod.Post,
            )
        )

        client.use {
            val response = it.executeHandler(
                id = "round",
                image = byteArrayOf(1, 2, 3),
                args = mapOf("d" to "60"),
            )
            assertEquals("image/png", response.contentType)
            assertEquals("png", response.fileExtension)
            assertTrue(response.bytes.isNotEmpty())
        }
    }

    @Test
    fun executeGeneratorReturnsBinaryResponse() = runBlocking {
        val client = TavoloCommandClient(
            config = TavoloClientConfig(baseUrl = "http://example.test"),
            httpClient = mockBinaryClient(
                expectedPath = "/generators/shake_text/execute",
                expectedMethod = HttpMethod.Post,
            )
        )

        client.use {
            val response = it.executeGenerator(
                id = "shake_text",
                args = mapOf("text" to "测试"),
            )
            assertEquals("image/png", response.contentType)
            assertTrue(response.bytes.isNotEmpty())
        }
    }

    @Test
    fun executeCommandSupportsGeneratorRequest() = runBlocking {
        val client = TavoloCommandClient(
            config = TavoloClientConfig(baseUrl = "http://example.test"),
            httpClient = mockBinaryClient(
                expectedPath = "/commands/shake_text/execute",
                expectedMethod = HttpMethod.Post,
            )
        )

        client.use {
            val response = it.executeCommand(
                id = "shake_text",
                request = ExecuteCommandRequest.Generator(mapOf("text" to "统一入口")),
            )
            assertEquals("image/png", response.contentType)
            assertTrue(response.bytes.isNotEmpty())
        }
    }

    @Test
    fun executeCommandSupportsHandlerRequest() = runBlocking {
        val client = TavoloCommandClient(
            config = TavoloClientConfig(baseUrl = "http://example.test"),
            httpClient = mockBinaryClient(
                expectedPath = "/commands/round/execute",
                expectedMethod = HttpMethod.Post,
            )
        )

        client.use {
            val response = it.executeCommand(
                id = "round",
                request = ExecuteCommandRequest.Handler(
                    image = byteArrayOf(1, 2, 3),
                    args = mapOf("d" to "60"),
                ),
            )
            assertEquals("image/png", response.contentType)
            assertTrue(response.bytes.isNotEmpty())
        }
    }

    @Test
    fun nonSuccessResponseThrowsClientException() = runBlocking {
        val client = TavoloCommandClient(
            config = TavoloClientConfig(baseUrl = "http://example.test"),
            httpClient = mockErrorClient(
                expectedPath = "/generators/not_exists/execute",
                expectedMethod = HttpMethod.Post,
                status = HttpStatusCode.NotFound,
                body = """{"message":"generator 不存在: not_exists"}""",
            )
        )

        client.use {
            val error = assertFailsWith<TavoloCommandClientException> {
                it.executeGenerator("not_exists")
            }
            assertEquals(404, error.statusCode)
            assertEquals("generator 不存在: not_exists", error.message)
        }
    }

    private fun mockClient(body: String): HttpClient =
        HttpClient(MockEngine { request ->
            assertTrue(request.url.toString().startsWith("http://example.test/"))
            respond(
                content = body,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
        }

    private fun mockBinaryClient(expectedPath: String, expectedMethod: HttpMethod): HttpClient =
        HttpClient(MockEngine { request ->
            assertEquals(expectedMethod, request.method)
            assertEquals(expectedPath, request.url.encodedPath)
            respond(
                content = byteArrayOf(1, 2, 3),
                headers = headersOf(HttpHeaders.ContentType, "image/png")
            )
        }) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
        }

    private fun mockErrorClient(
        expectedPath: String,
        expectedMethod: HttpMethod,
        status: HttpStatusCode,
        body: String,
    ): HttpClient =
        HttpClient(MockEngine { request ->
            assertEquals(expectedMethod, request.method)
            assertEquals(expectedPath, request.url.encodedPath)
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
        }
}
