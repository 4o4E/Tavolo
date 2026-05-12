package top.e404.tavolo.http.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.SerializationException
import top.e404.tavolo.http.server.dto.ErrorResponse
import top.e404.tavolo.http.server.service.BadExecuteRequestException
import top.e404.tavolo.http.server.service.CommandNotFoundException
import top.e404.tavolo.pipeline.HandlerPipelineCommandNotFoundException
import top.e404.tavolo.pipeline.HandlerPipelineStepException
import top.e404.tavolo.pipeline.HandlerPipelineValidationException

fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception(CommandNotFoundException::class) { call, cause ->
            call.respondError(HttpStatusCode.NotFound, cause.message ?: "指令不存在")
        }
        exception(HandlerPipelineCommandNotFoundException::class) { call, cause ->
            call.respondError(HttpStatusCode.NotFound, cause.message ?: "handler 不存在")
        }
        exception(HandlerPipelineValidationException::class) { call, cause ->
            call.respondError(HttpStatusCode.BadRequest, cause.message ?: "pipeline 请求格式错误")
        }
        exception(HandlerPipelineStepException::class) { call, cause ->
            call.application.environment.log.error("pipeline handler 执行失败", cause)
            call.respondError(HttpStatusCode.InternalServerError, cause.message ?: "pipeline handler 执行失败")
        }
        exception(BadExecuteRequestException::class) { call, cause ->
            call.respondError(HttpStatusCode.BadRequest, cause.message ?: "请求格式错误")
        }
        exception(BadRequestException::class) { call, cause ->
            call.respondError(HttpStatusCode.BadRequest, cause.message ?: "请求格式错误")
        }
        exception(JsonConvertException::class) { call, cause ->
            call.respondError(HttpStatusCode.BadRequest, cause.message ?: "JSON 请求格式错误")
        }
        exception(SerializationException::class) { call, cause ->
            call.respondError(HttpStatusCode.BadRequest, cause.message ?: "JSON 请求格式错误")
        }
        exception(Throwable::class) { call, cause ->
            call.application.environment.log.error("HTTP 请求处理失败", cause)
            call.respondError(HttpStatusCode.InternalServerError, "服务内部错误")
        }
    }
}

suspend fun ApplicationCall.respondError(
    status: HttpStatusCode,
    message: String,
) {
    respond(status, ErrorResponse(message))
}
