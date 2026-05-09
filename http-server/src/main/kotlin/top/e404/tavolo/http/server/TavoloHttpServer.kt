package top.e404.tavolo.http.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

object TavoloHttpServer {
    @JvmStatic
    fun main(args: Array<String>) {
        val config = ServerConfig()
        embeddedServer(
            factory = Netty,
            host = config.host,
            port = config.port,
            module = { tavoloModule() }
        ).start(wait = true)
    }
}
