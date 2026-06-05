package top.e404.tavolo

import java.io.File

object ManualTestSupport {
    private val imageCommandsOutputDir = File("out/manual/image-commands")

    fun outputFile(type: String, name: String, extension: String): File {
        imageCommandsOutputDir.mkdirs()
        return imageCommandsOutputDir.resolve("${sanitize(type)}-${sanitize(name)}.$extension")
    }

    fun outputFile(type: String, name: String): File {
        imageCommandsOutputDir.mkdirs()
        return imageCommandsOutputDir.resolve("${sanitize(type)}-${sanitize(name)}")
    }

    private fun sanitize(value: String): String =
        value.trim()
            .ifBlank { "unnamed" }
            .replace(Regex("""[^\w.-]+"""), "_")
            .trim('_')
            .ifBlank { "unnamed" }
}
