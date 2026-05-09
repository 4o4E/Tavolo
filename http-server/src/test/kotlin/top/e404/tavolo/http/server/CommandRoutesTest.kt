package top.e404.tavolo.http.server

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.skia.Color
import org.jetbrains.skia.Surface
import top.e404.tavolo.frame.Frame
import top.e404.tavolo.frame.encodeToBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.e404.tavolo.http.server.dto.CommandsResponse
import top.e404.tavolo.http.server.dto.ErrorResponse
import top.e404.tavolo.http.server.dto.HealthResponse

class CommandRoutesTest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun healthReturnsOk() = testApplication {
        application {
            tavoloModule()
        }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            HealthResponse(status = "ok"),
            json.decodeFromString<HealthResponse>(response.bodyAsText())
        )
    }

    @Test
    fun commandsReturnsRegisteredHandlersAndGenerators() = testApplication {
        application {
            tavoloModule()
        }

        val response = client.get("/commands")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.decodeFromString<CommandsResponse>(response.bodyAsText())
        assertEquals("2.0.0", body.assets.version)
        assertTrue(body.commands.isNotEmpty())
        assertTrue(body.commands.any { it.category == "handler" })
        assertTrue(body.commands.any { it.category == "generator" })
        assertTrue(body.commands.all { it.category == it.category.lowercase() })
        assertTrue(body.commands.all { it.type == it.type.lowercase() })
    }

    @Test
    fun executeHandlerReturnsImageBytes() = testApplication {
        application {
            tavoloModule()
        }

        val response = client.submitFormWithBinaryData(
            url = "/handlers/round/execute",
            formData = formData {
                append(
                    key = "image",
                    value = samplePng(),
                    headers = Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"input.png\"")
                    }
                )
            }
        )

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Image.PNG.toString(), response.headers[HttpHeaders.ContentType])
        assertTrue(response.body<ByteArray>().isNotEmpty())
    }

    @Test
    fun executeGeneratorReturnsImageBytes() = testApplication {
        application {
            tavoloModule()
        }

        val response = client.post("/generators/blue_archive/execute") {
            contentType(ContentType.Application.Json)
            setBody("""{"args":{"l":"Blue","r":"Archive","b":"测试"}}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Image.PNG.toString(), response.headers[HttpHeaders.ContentType])
        assertTrue(response.body<ByteArray>().isNotEmpty())
    }

    @Test
    fun executeCommandDispatchesByCategory() = testApplication {
        application {
            tavoloModule()
        }

        val response = client.post("/commands/blue_archive/execute") {
            contentType(ContentType.Application.Json)
            setBody("""{"args":{"l":"Blue","r":"Archive","b":"统一入口"}}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Image.PNG.toString(), response.headers[HttpHeaders.ContentType])
        assertTrue(response.body<ByteArray>().isNotEmpty())
    }

    @Test
    fun executeCommandSupportsHandlerMultipartRequest() = testApplication {
        application {
            tavoloModule()
        }

        val response = client.submitFormWithBinaryData(
            url = "/commands/round/execute",
            formData = formData {
                append(
                    key = "image",
                    value = samplePng(),
                    headers = Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"input.png\"")
                    }
                )
            }
        )

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Image.PNG.toString(), response.headers[HttpHeaders.ContentType])
        assertTrue(response.body<ByteArray>().isNotEmpty())
    }

    @Test
    fun executeUnknownCommandReturnsNotFound() = testApplication {
        application {
            tavoloModule()
        }

        val response = client.post("/generators/not_exists/execute") {
            contentType(ContentType.Application.Json)
            setBody("""{"args":{}}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        val body = json.decodeFromString<ErrorResponse>(response.bodyAsText())
        assertTrue(body.message.contains("not_exists"))
    }

    @Test
    fun executeHandlerWithoutImageReturnsBadRequest() = testApplication {
        application {
            tavoloModule()
        }

        val response = client.submitFormWithBinaryData(
            url = "/handlers/round/execute",
            formData = formData {
                append("args", "{}")
            }
        )

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = json.decodeFromString<ErrorResponse>(response.bodyAsText())
        assertTrue(body.message.contains("image"))
    }

    @Test
    fun executeHandlerWithInvalidArgsReturnsBadRequest() = testApplication {
        application {
            tavoloModule()
        }

        val response = client.submitFormWithBinaryData(
            url = "/handlers/round/execute",
            formData = formData {
                append(
                    key = "image",
                    value = samplePng(),
                    headers = Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"input.png\"")
                    }
                )
                append("args", "not-json")
            }
        )

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = json.decodeFromString<ErrorResponse>(response.bodyAsText())
        assertTrue(body.message.isNotBlank())
    }

    @Test
    fun executeGeneratorWithInvalidJsonReturnsBadRequest() = testApplication {
        application {
            tavoloModule()
        }

        val response = client.post("/generators/blue_archive/execute") {
            contentType(ContentType.Application.Json)
            setBody("{")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = json.decodeFromString<ErrorResponse>(response.bodyAsText())
        assertTrue(body.message.isNotBlank())
    }

    private fun samplePng(): ByteArray =
        listOf(
            Frame(
                0,
                Surface.makeRasterN32Premul(4, 4).apply {
                    canvas.clear(Color.RED)
                }.makeImageSnapshot()
            )
        ).encodeToBytes()
}
