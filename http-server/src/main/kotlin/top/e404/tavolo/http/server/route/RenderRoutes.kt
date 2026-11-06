package top.e404.tavolo.http.server.route

import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import top.e404.tavolo.http.server.dto.RenderChartRequest
import top.e404.tavolo.http.server.dto.RenderMarkdownRequest
import top.e404.tavolo.http.server.service.BadRenderRequestException
import top.e404.tavolo.http.server.service.RenderService

// 新接口拒绝未知字段，避免错误拼写的坐标／样式被静默丢弃；保留现有指令接口的解析约定。
private val renderJson = Json

fun Route.renderRoutes(service: RenderService) {
    post("/render/markdown") {
        if (!call.request.contentType().match(ContentType.Application.Json)) throw BadRenderRequestException("请求需要 application/json")
        val request = renderJson.decodeFromString<RenderMarkdownRequest>(call.receiveText())
        call.respondBytes(service.markdown(request), ContentType.Image.PNG)
    }
    post("/render/chart") {
        if (!call.request.contentType().match(ContentType.Application.Json)) throw BadRenderRequestException("请求需要 application/json")
        val request = renderJson.decodeFromString<RenderChartRequest>(call.receiveText())
        call.respondBytes(service.chart(request), ContentType.Image.PNG)
    }
}
