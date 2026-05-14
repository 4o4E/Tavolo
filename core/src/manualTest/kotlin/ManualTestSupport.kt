package top.e404.tavolo

import java.io.File

object ManualTestSupport {
    private val coreOutputDir = File("out/manual/core")

    fun coreOutputFile(type: String, name: String, extension: String): File {
        coreOutputDir.mkdirs()
        return coreOutputDir.resolve("${sanitize(type)}-${sanitize(name)}.$extension")
    }

    fun coreOutputFile(type: String, name: String): File {
        coreOutputDir.mkdirs()
        return coreOutputDir.resolve("${sanitize(type)}-${sanitize(name)}")
    }

    private fun sanitize(value: String): String =
        value.trim()
            .ifBlank { "unnamed" }
            .replace(Regex("""[^\w.-]+"""), "_")
            .trim('_')
            .ifBlank { "unnamed" }
}
