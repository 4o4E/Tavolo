package top.e404.tavolo.http.server.route

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import top.e404.tavolo.http.server.service.CommandService

fun Route.commandRoutes(commandService: CommandService) {
    get("/commands") {
        call.respond(commandService.commands())
    }
}
