package top.e404.tavolo.http.server.service

import top.e404.tavolo.frame.encodeToBytes
import top.e404.tavolo.pipeline.HandlerPipelineExecutor
import top.e404.tavolo.pipeline.HandlerPipelineStep
import top.e404.tavolo.registry.CommandCategory
import top.e404.tavolo.registry.CommandRegistry

class CommandExecuteService(
    private val registryProvider: () -> CommandRegistry = { CommandRegistry.load() }
) {
    private val pipelineExecutor = HandlerPipelineExecutor(registryProvider)

    suspend fun executeHandler(id: String, image: ByteArray, args: Map<String, String>): ExecutedImage {
        val registry = registryProvider()
        val registered = registry.handlers.firstOrNull { it.descriptor.id == id }
            ?: throw CommandNotFoundException("handler 不存在: $id")
        val result = registered.handler.handleBytes(image, args.toMutableMap())
        val frames = result.getOrThrow()
        return frames.toExecutedImage()
    }

    suspend fun executeGenerator(id: String, args: Map<String, String>): ExecutedImage {
        val registry = registryProvider()
        val registered = registry.generators.firstOrNull { it.descriptor.id == id }
            ?: throw CommandNotFoundException("generator 不存在: $id")
        val frames = registered.generator.generate(args.toMutableMap())
        return frames.toExecutedImage()
    }

    suspend fun executeCommand(id: String, image: ByteArray?, args: Map<String, String>): ExecutedImage {
        val registry = registryProvider()
        val descriptor = registry.descriptors.firstOrNull { it.id == id }
            ?: throw CommandNotFoundException("指令不存在: $id")
        return when (descriptor.category) {
            CommandCategory.HANDLER -> {
                val input = image ?: throw BadExecuteRequestException("handler 执行缺少 image")
                val registered = registry.handlers.first { it.descriptor.id == id }
                val result = registered.handler.handleBytes(input, args.toMutableMap())
                val frames = result.getOrThrow()
                frames.toExecutedImage()
            }

            CommandCategory.GENERATOR -> {
                val registered = registry.generators.first { it.descriptor.id == id }
                registered.generator.generate(args.toMutableMap()).toExecutedImage()
            }
        }
    }

    suspend fun executePipeline(image: ByteArray, steps: List<HandlerPipelineStep>): ExecutedImage {
        val frames = pipelineExecutor.executeHandlers(image, steps)
        return frames.toExecutedImage()
    }

    private fun List<top.e404.tavolo.frame.Frame>.toExecutedImage(): ExecutedImage =
        ExecutedImage(
            bytes = encodeToBytes(),
            contentType = if (size == 1) "image/png" else "image/gif",
        )
}

data class ExecutedImage(
    val bytes: ByteArray,
    val contentType: String,
)

class CommandNotFoundException(message: String) : RuntimeException(message)

class BadExecuteRequestException(message: String) : RuntimeException(message)
