package top.e404.tavolo.http.client.request

import kotlinx.serialization.Serializable

@Serializable
data class ExecuteRequest(
    val args: Map<String, String> = emptyMap(),
)

sealed interface ExecuteCommandRequest {
    data class Handler(
        val image: ByteArray,
        val args: Map<String, String> = emptyMap(),
        val fileName: String = "image",
    ) : ExecuteCommandRequest

    data class Generator(
        val args: Map<String, String> = emptyMap(),
    ) : ExecuteCommandRequest
}

data class ExecutedImage(
    val bytes: ByteArray,
    val contentType: String,
) {
    val fileExtension: String
        get() = when {
            contentType.contains("gif", ignoreCase = true) -> "gif"
            else -> "png"
        }
}
