package top.e404.tavolo.pipeline

import kotlinx.serialization.Serializable
import top.e404.tavolo.frame.Frame
import top.e404.tavolo.frame.decodeToFrames
import top.e404.tavolo.registry.CommandRegistry
import java.util.concurrent.CancellationException

@Serializable
data class HandlerPipelineStep(
    val id: String,
    val args: Map<String, String> = emptyMap(),
)

class HandlerPipelineExecutor(
    private val registryProvider: () -> CommandRegistry = { CommandRegistry.load() },
    private val maxSteps: Int = DEFAULT_MAX_STEPS,
) {
    suspend fun executeHandlers(
        image: ByteArray,
        steps: List<HandlerPipelineStep>,
    ): List<Frame> = executeHandlers(image.decodeToFrames(), steps)

    suspend fun executeHandlers(
        frames: MutableList<Frame>,
        steps: List<HandlerPipelineStep>,
    ): List<Frame> {
        validateSteps(steps)
        var current = frames
        val registry = registryProvider()

        steps.forEachIndexed { index, step ->
            val handlerId = step.id.trim()
            val registered = registry.handlers.firstOrNull { it.descriptor.id == handlerId }
                ?: throw HandlerPipelineCommandNotFoundException(index, handlerId)
            current = try {
                registered.handler
                    .handleFrames(current, step.args.toMutableMap())
                    .getOrThrow()
                    .toMutableList()
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                throw HandlerPipelineStepException(index, handlerId, t)
            }
        }

        return current
    }

    private fun validateSteps(steps: List<HandlerPipelineStep>) {
        if (steps.isEmpty()) throw HandlerPipelineValidationException("pipeline 至少需要一个 handler 步骤")
        if (steps.size > maxSteps) {
            throw HandlerPipelineValidationException("pipeline 步骤数量不能超过 $maxSteps")
        }
        steps.forEachIndexed { index, step ->
            if (step.id.isBlank()) {
                throw HandlerPipelineValidationException("pipeline 第 ${index + 1} 步缺少 handler id")
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_STEPS = 8
    }
}

open class HandlerPipelineException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class HandlerPipelineValidationException(message: String) : HandlerPipelineException(message)

class HandlerPipelineCommandNotFoundException(
    val stepIndex: Int,
    val handlerId: String,
) : HandlerPipelineException("pipeline 第 ${stepIndex + 1} 步 handler 不存在: $handlerId")

class HandlerPipelineStepException(
    val stepIndex: Int,
    val handlerId: String,
    cause: Throwable,
) : HandlerPipelineException("pipeline 第 ${stepIndex + 1} 步 handler 执行失败: $handlerId", cause)
