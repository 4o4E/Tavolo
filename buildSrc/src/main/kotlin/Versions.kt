object Versions {
    const val GROUP = "top.e404.tavolo"
    const val VERSION = "2.0.1"
    const val KOTLIN = "2.2.21"
    const val KOTLINX_SERIALIZATION = "1.9.0"
    const val SKIKO = "0.9.30"
    const val KTOR = "2.3.13"
}

fun kotlinx(id: String, version: String = Versions.KOTLIN) = "org.jetbrains.kotlinx:kotlinx-$id:$version"
fun skiko(module: String, version: String = Versions.SKIKO) = "org.jetbrains.skiko:skiko-awt-runtime-$module:$version"
fun ktor(module: String, version: String = Versions.KTOR) = "io.ktor:ktor-$module:$version"
