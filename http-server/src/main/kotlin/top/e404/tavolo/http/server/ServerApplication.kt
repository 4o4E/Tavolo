package top.e404.tavolo.http.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import top.e404.tavolo.http.server.route.commandRoutes
import top.e404.tavolo.http.server.route.executeRoutes
import top.e404.tavolo.http.server.route.healthRoutes
import top.e404.tavolo.http.server.service.CommandService
import top.e404.tavolo.http.server.service.CommandExecuteService

fun Application.tavoloModule(
    commandService: CommandService = CommandService(),
    executeService: CommandExecuteService = CommandExecuteService(),
) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = false
        })
    }
    install(Compression)
    install(CallLogging)
    configureErrorHandling()

    routing {
        healthRoutes()
        commandRoutes(commandService)
        executeRoutes(executeService)
    }
}
