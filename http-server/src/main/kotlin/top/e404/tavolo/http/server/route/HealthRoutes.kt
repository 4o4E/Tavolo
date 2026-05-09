package top.e404.tavolo.http.server.route

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import top.e404.tavolo.http.server.dto.HealthResponse

fun Route.healthRoutes() {
    get("/health") {
        call.respond(HealthResponse(status = "ok"))
    }
}
