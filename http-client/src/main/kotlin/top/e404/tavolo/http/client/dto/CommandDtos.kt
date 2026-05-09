package top.e404.tavolo.http.client.dto

import kotlinx.serialization.Serializable

@Serializable
data class AssetsDto(
    val version: String,
    val time: String,
)

@Serializable
data class CommandDto(
    val id: String,
    val category: String,
    val type: String,
    val name: String,
    val regex: String,
    val version: Int,
)

@Serializable
data class CommandsResponse(
    val assets: AssetsDto,
    val commands: List<CommandDto>,
)

@Serializable
data class HealthResponse(
    val status: String,
)

@Serializable
data class ErrorResponse(
    val message: String,
)
