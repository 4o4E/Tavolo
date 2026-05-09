package top.e404.tavolo.http.server.route

import io.ktor.http.ContentType
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.call
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import top.e404.tavolo.http.server.dto.ExecuteRequest
import top.e404.tavolo.http.server.service.BadExecuteRequestException
import top.e404.tavolo.http.server.service.CommandExecuteService
import top.e404.tavolo.http.server.service.ExecutedImage

fun Route.executeRoutes(executeService: CommandExecuteService) {
    post("/handlers/{id}/execute") {
        val id = call.parameters["id"] ?: throw BadExecuteRequestException("缺少 handler id")
        val request = call.receiveExecuteMultipart()
        val image = request.image ?: throw BadExecuteRequestException("handler 执行缺少 image")
        call.respondExecuted {
            executeService.executeHandler(id, image, request.args)
        }
    }

    post("/generators/{id}/execute") {
        val id = call.parameters["id"] ?: throw BadExecuteRequestException("缺少 generator id")
        val request = call.receive<ExecuteRequest>()
        call.respondExecuted {
            executeService.executeGenerator(id, request.args)
        }
    }

    post("/commands/{id}/execute") {
        val id = call.parameters["id"] ?: throw BadExecuteRequestException("缺少指令 id")
        if (call.request.contentType().match(ContentType.MultiPart.FormData)) {
            val request = call.receiveExecuteMultipart()
            call.respondExecuted {
                executeService.executeCommand(id, request.image, request.args)
            }
        } else {
            val request = call.receive<ExecuteRequest>()
            call.respondExecuted {
                executeService.executeCommand(id, null, request.args)
            }
        }
    }
}

private data class MultipartExecuteRequest(
    val image: ByteArray?,
    val args: Map<String, String>,
)

private suspend fun io.ktor.server.application.ApplicationCall.receiveExecuteMultipart(): MultipartExecuteRequest {
    var image: ByteArray? = null
    var args = emptyMap<String, String>()
    receiveMultipart().forEachPart { part ->
        when (part) {
            is PartData.FileItem -> {
                if (part.name == "image") {
                    image = part.streamProvider().use { it.readBytes() }
                }
            }

            is PartData.FormItem -> {
                if (part.name == "args" && part.value.isNotBlank()) {
                    args = Json.decodeFromString<Map<String, String>>(part.value)
                }
            }

            else -> Unit
        }
        part.dispose()
    }
    return MultipartExecuteRequest(image, args)
}

private suspend fun io.ktor.server.application.ApplicationCall.respondExecuted(
    block: suspend () -> ExecutedImage
) {
    val image = block()
    respondBytes(
        bytes = image.bytes,
        contentType = ContentType.parse(image.contentType),
    )
}
