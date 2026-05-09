package top.e404.tavolo.http.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class ExecuteRequest(
    val args: Map<String, String> = emptyMap(),
)

@Serializable
data class ErrorResponse(
    val message: String,
)
