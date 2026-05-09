package top.e404.tavolo.http.server

data class ServerConfig(
    val host: String = propertyOrEnv("tavolo.http.host", "TAVOLO_HTTP_HOST") ?: "0.0.0.0",
    val port: Int = propertyOrEnv("tavolo.http.port", "TAVOLO_HTTP_PORT")?.toIntOrNull() ?: 8080,
) {
    companion object {
        private fun propertyOrEnv(property: String, env: String): String? =
            System.getProperty(property)?.takeIf { it.isNotBlank() }
                ?: System.getenv(env)?.takeIf { it.isNotBlank() }
    }
}
