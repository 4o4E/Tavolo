package top.e404.tavolo.http.server.service

import top.e404.tavolo.http.server.dto.CommandsResponse
import top.e404.tavolo.http.server.dto.toDto
import top.e404.tavolo.registry.CommandRegistry

class CommandService(
    private val registryProvider: () -> CommandRegistry = { CommandRegistry.load() }
) {
    fun commands(): CommandsResponse {
        val registry = registryProvider()
        return CommandsResponse(
            assets = registry.assetsVersion.toDto(),
            commands = registry.descriptors.map { it.toDto() },
        )
    }
}
