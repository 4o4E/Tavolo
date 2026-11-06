package top.e404.tavolo.http.server

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class RenderRoutesTest {
    @Test
    fun rejectsUnknownFieldsAndInvalidStructuredData() = testApplication {
        application { tavoloModule() }
        val invalid = listOf(
            "/render/chart" to """{"data":{"type":"line","series":[]}}""",
            "/render/chart" to """{"data":{"type":"line","series":[{"name":"重复","points":[{"x":1,"y":2},{"x":1,"y":3}]}]}}""",
            "/render/chart" to """{"data":{"type":"bars","categories":["甲","乙"],"series":[{"name":"数量","values":[1]}]}}""",
            "/render/chart" to """{"data":{"type":"timeLine","series":[{"name":"时间","points":[{"epochMillis":1767225600000,"y":1}]}],"xAxis":{"zone":"Invalid/City"}}}""",
            "/render/chart" to """{"data":{"type":"pie","slices":[{"name":"甲","value":1}]},"widht":800}""",
            "/render/markdown" to """{"source":"![图](https://example.invalid/image.png)"}""",
            "/render/markdown" to """{"source":"![图](asset:test)","images":{"asset:test":"not base64!"}}""",
            "/render/markdown" to """{"source":"文本","pixelRatio":0}""",
        )
        for ((path, body) in invalid) {
            val response = client.post(path) { contentType(ContentType.Application.Json); setBody(body) }
            assertEquals(HttpStatusCode.BadRequest, response.status, "$path $body: ${response.bodyAsText()}")
        }
    }

}
