package top.e404.tavolo.registry

import top.e404.tavolo.assets.Assets
import top.e404.tavolo.frame.FramesHandler
import top.e404.tavolo.generator.FramesGenerator
import top.e404.tavolo.handler.generatorMap
import top.e404.tavolo.handler.handlerMap

enum class CommandCategory {
    HANDLER,
    GENERATOR
}

enum class CommandType {
    TEMPLATE,
    KOTLIN
}

data class CommandDescriptor(
    val id: String,
    val category: CommandCategory,
    val type: CommandType,
    val name: String,
    val regex: Regex,
    val version: Int,
)

data class RegisteredHandler(
    val descriptor: CommandDescriptor,
    val handler: FramesHandler,
)

data class RegisteredGenerator(
    val descriptor: CommandDescriptor,
    val generator: FramesGenerator,
)

data class AssetsVersion(
    val version: String,
    val time: String,
)

class CommandRegistry(
    val assetsVersion: AssetsVersion,
    val handlers: List<RegisteredHandler>,
    val generators: List<RegisteredGenerator>,
) {
    val descriptors: List<CommandDescriptor> =
        handlers.map { it.descriptor } + generators.map { it.descriptor }

    fun matchHandler(commandName: String): RegisteredHandler? =
        handlers.firstOrNull { it.descriptor.regex.matches(commandName) }

    companion object {
        fun load(): CommandRegistry = ResourceCommandLoader().load()
    }
}

class ResourceCommandLoader(
    private val handlers: Map<String, FramesHandler> = handlerMap,
    private val generators: Map<String, FramesGenerator> = generatorMap,
) {
    fun load(): CommandRegistry {
        val assetsVersion = loadVersion()
        val registeredHandlers = loadHandlers()
        val registeredGenerators = loadGenerators()
        val duplicates = (registeredHandlers.map { it.descriptor.id } + registeredGenerators.map { it.descriptor.id })
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicates.isEmpty()) { "指令 id 重复: ${duplicates.joinToString()}" }
        return CommandRegistry(assetsVersion, registeredHandlers, registeredGenerators)
    }

    private fun loadVersion(): AssetsVersion {
        val fields = parseFields(Assets.text("version.yml"))
        return AssetsVersion(
            version = fields["version"] ?: error("version.yml 缺少 version"),
            time = fields["time"] ?: error("version.yml 缺少 time"),
        )
    }

    private fun loadHandlers(): List<RegisteredHandler> =
        Assets.resolve("handlers").toFile().listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            ?.mapNotNull { dir ->
                val config = dir.resolve("handler.yml")
                if (!config.isFile) return@mapNotNull null
                val command = CommandConfig.parse(Assets.text("handlers/${dir.name}/handler.yml"))
                require(command.id == dir.name) { "handler.yml 的 id 与目录名不一致: ${dir.name}" }
                val type = command.type?.let { CommandType.valueOf(it.uppercase()) }
                    ?: error("handler.yml 缺少 type: ${dir.name}")
                when (type) {
                    CommandType.KOTLIN -> {
                        val handler = handlers[command.id]
                            ?: error("handler 缺少 KSP 注册项: ${command.id}")
                        RegisteredHandler(command.toDescriptor(CommandCategory.HANDLER, type), handler)
                    }

                    CommandType.TEMPLATE -> error("模板 handler 运行时尚未接入: ${command.id}")
                }
            } ?: emptyList()

    private fun loadGenerators(): List<RegisteredGenerator> =
        Assets.resolve("generators").toFile().listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            ?.mapNotNull { dir ->
                val config = dir.resolve("generator.yml")
                if (!config.isFile) return@mapNotNull null
                val command = CommandConfig.parse(Assets.text("generators/${dir.name}/generator.yml"))
                require(command.id == dir.name) { "generator.yml 的 id 与目录名不一致: ${dir.name}" }
                require(command.type == null) { "generator.yml 不允许声明 type: ${command.id}" }
                val generator = generators[command.id]
                    ?: error("generator 缺少 KSP 注册项: ${command.id}")
                RegisteredGenerator(command.toDescriptor(CommandCategory.GENERATOR, CommandType.KOTLIN), generator)
            } ?: emptyList()

    private data class CommandConfig(
        val id: String,
        val type: String?,
        val version: Int,
        val name: String,
        val regex: String,
    ) {
        fun toDescriptor(category: CommandCategory, type: CommandType): CommandDescriptor =
            CommandDescriptor(
                id = id,
                category = category,
                type = type,
                name = name,
                regex = Regex(regex),
                version = version,
            )

        companion object {
            fun parse(text: String): CommandConfig {
                val fields = parseFields(text)
                val name = fields["name"] ?: fields["id"].orEmpty()
                return CommandConfig(
                    id = fields["id"] ?: error("配置缺少 id"),
                    type = fields["type"],
                    version = fields["version"]?.toIntOrNull() ?: 1,
                    name = name,
                    regex = fields["regex"]?.ifBlank { name } ?: name,
                )
            }

        }
    }
}

private fun parseFields(text: String): Map<String, String> =
    text.lineSequence()
        .map { it.trim().removePrefix("\uFEFF") }
        .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("-") }
        .mapNotNull {
            val index = it.indexOf(':')
            if (index == -1) null else it.substring(0, index).trim() to it.substring(index + 1).trim().unquote()
        }
        .toMap()

private fun String.unquote(): String {
    val raw = trim().removeSurrounding("\"")
    return raw.replace("\\\"", "\"").replace("\\\\", "\\")
}
