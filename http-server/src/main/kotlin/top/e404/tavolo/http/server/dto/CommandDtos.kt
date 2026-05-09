package top.e404.tavolo.http.server.dto

import kotlinx.serialization.Serializable
import top.e404.tavolo.registry.AssetsVersion
import top.e404.tavolo.registry.CommandCategory
import top.e404.tavolo.registry.CommandDescriptor
import top.e404.tavolo.registry.CommandType

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

fun AssetsVersion.toDto(): AssetsDto =
    AssetsDto(
        version = version,
        time = time,
    )

fun CommandDescriptor.toDto(): CommandDto =
    CommandDto(
        id = id,
        category = category.toResponseValue(),
        type = type.toResponseValue(),
        name = name,
        regex = regex.pattern,
        version = version,
    )

private fun CommandCategory.toResponseValue(): String =
    name.lowercase()

private fun CommandType.toResponseValue(): String =
    name.lowercase()
