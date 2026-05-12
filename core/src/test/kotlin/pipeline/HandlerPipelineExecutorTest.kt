package top.e404.tavolo.pipeline

import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Color
import org.jetbrains.skia.Surface
import top.e404.tavolo.frame.Frame
import top.e404.tavolo.frame.FramesHandler
import top.e404.tavolo.frame.HandleResult
import top.e404.tavolo.frame.HandleResult.Companion.result
import top.e404.tavolo.frame.encodeToBytes
import top.e404.tavolo.registry.AssetsVersion
import top.e404.tavolo.registry.CommandCategory
import top.e404.tavolo.registry.CommandDescriptor
import top.e404.tavolo.registry.CommandRegistry
import top.e404.tavolo.registry.CommandType
import top.e404.tavolo.registry.RegisteredHandler
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class HandlerPipelineExecutorTest {
    @Test
    fun executeHandlersRunsStepsInOrderWithIndependentArgs() = runBlocking {
        val first = DurationHandler("first")
        val second = DurationHandler("second")
        val executor = HandlerPipelineExecutor(
            registryProvider = { registryOf(first, second) },
            maxSteps = 4
        )

        val result = executor.executeHandlers(
            frames = mutableListOf(frame(duration = 10)),
            steps = listOf(
                HandlerPipelineStep("first", mapOf("delta" to "2")),
                HandlerPipelineStep("second", mapOf("delta" to "3"))
            )
        )

        assertEquals(15, result.single().duration)
        assertEquals(listOf(mapOf("delta" to "2")), first.receivedArgs)
        assertEquals(listOf(mapOf("delta" to "3")), second.receivedArgs)
    }

    @Test
    fun executeHandlersCanDecodeImageBytes() = runBlocking {
        val executor = HandlerPipelineExecutor(
            registryProvider = { registryOf(DurationHandler("first")) }
        )

        val result = executor.executeHandlers(
            image = listOf(frame(duration = 10)).encodeToBytes(),
            steps = listOf(HandlerPipelineStep("first", mapOf("delta" to "5")))
        )

        assertEquals(55, result.single().duration)
    }

    @Test
    fun executeHandlersPassesMutableArgCopyWithoutMutatingPipelineStep() = runBlocking {
        val handler = MutatingArgsHandler("mutating")
        val executor = HandlerPipelineExecutor(registryProvider = { registryOf(handler) })
        val originalArgs = mapOf("value" to "original")

        executor.executeHandlers(
            mutableListOf(frame()),
            listOf(HandlerPipelineStep("mutating", originalArgs))
        )

        assertEquals("original", originalArgs["value"])
        assertEquals(listOf<String?>("changed"), handler.mutatedValues)
    }

    @Test
    fun executeHandlersValidatesStepList() = runBlocking {
        val executor = HandlerPipelineExecutor(registryProvider = { registryOf(DurationHandler("first")) }, maxSteps = 1)

        assertFailsWith<HandlerPipelineValidationException> {
            executor.executeHandlers(mutableListOf(frame()), emptyList())
        }
        assertFailsWith<HandlerPipelineValidationException> {
            executor.executeHandlers(
                mutableListOf(frame()),
                listOf(HandlerPipelineStep("first"), HandlerPipelineStep("first"))
            )
        }
        assertFailsWith<HandlerPipelineValidationException> {
            executor.executeHandlers(mutableListOf(frame()), listOf(HandlerPipelineStep(" ")))
        }
    }

    @Test
    fun executeHandlersReportsMissingHandlerWithStepIndex() = runBlocking {
        val executor = HandlerPipelineExecutor(registryProvider = { registryOf(DurationHandler("first")) })

        val error = assertFailsWith<HandlerPipelineCommandNotFoundException> {
            executor.executeHandlers(
                mutableListOf(frame()),
                listOf(HandlerPipelineStep("first"), HandlerPipelineStep("missing"))
            )
        }

        assertEquals(1, error.stepIndex)
        assertEquals("missing", error.handlerId)
    }

    @Test
    fun executeHandlersWrapsStepFailureWithContext() = runBlocking {
        val failure = IllegalStateException("boom")
        val executor = HandlerPipelineExecutor(registryProvider = { registryOf(FailingHandler("broken", failure)) })

        val error = assertFailsWith<HandlerPipelineStepException> {
            executor.executeHandlers(mutableListOf(frame()), listOf(HandlerPipelineStep("broken")))
        }

        assertEquals(0, error.stepIndex)
        assertEquals("broken", error.handlerId)
        assertSame(failure, error.cause)
    }

    @Test
    fun executeHandlersDoesNotWrapCancellation() = runBlocking {
        val cancellation = CancellationException("cancelled")
        val executor = HandlerPipelineExecutor(registryProvider = { registryOf(FailingHandler("cancel", cancellation)) })

        val error = assertFailsWith<CancellationException> {
            executor.executeHandlers(mutableListOf(frame()), listOf(HandlerPipelineStep("cancel")))
        }

        assertSame(cancellation, error)
    }

    @Test
    fun executeHandlersWrapsHandleResultFailureWithContext() = runBlocking {
        val executor = HandlerPipelineExecutor(registryProvider = { registryOf(ResultFailingHandler("failed")) })

        val error = assertFailsWith<HandlerPipelineStepException> {
            executor.executeHandlers(mutableListOf(frame()), listOf(HandlerPipelineStep("failed")))
        }

        assertEquals(0, error.stepIndex)
        assertEquals("failed", error.handlerId)
        assertEquals("显式失败", error.cause?.message)
    }

    private class DurationHandler(
        override val name: String,
    ) : FramesHandler {
        override val regex = Regex(name)
        val receivedArgs = mutableListOf<Map<String, String>>()

        override suspend fun handleFrames(
            frames: MutableList<Frame>,
            args: MutableMap<String, String>,
        ): HandleResult = frames.result {
            receivedArgs += args.toMap()
            val delta = args["delta"]?.toIntOrNull() ?: 1
            onEach { it.duration += delta }
        }
    }

    private class FailingHandler(
        override val name: String,
        private val failure: Throwable,
    ) : FramesHandler {
        override val regex = Regex(name)

        override suspend fun handleFrames(
            frames: MutableList<Frame>,
            args: MutableMap<String, String>,
        ): HandleResult {
            throw failure
        }
    }

    private class ResultFailingHandler(
        override val name: String,
    ) : FramesHandler {
        override val regex = Regex(name)

        override suspend fun handleFrames(
            frames: MutableList<Frame>,
            args: MutableMap<String, String>,
        ): HandleResult = HandleResult.fail("显式失败")
    }

    private class MutatingArgsHandler(
        override val name: String,
    ) : FramesHandler {
        override val regex = Regex(name)
        val mutatedValues = mutableListOf<String?>()

        override suspend fun handleFrames(
            frames: MutableList<Frame>,
            args: MutableMap<String, String>,
        ): HandleResult = frames.result {
            args["value"] = "changed"
            mutatedValues += args["value"]
            this
        }
    }

    private fun registryOf(vararg handlers: FramesHandler): CommandRegistry =
        CommandRegistry(
            assetsVersion = AssetsVersion("test", "test"),
            handlers = handlers.map { handler ->
                RegisteredHandler(
                    descriptor = CommandDescriptor(
                        id = handler.name,
                        category = CommandCategory.HANDLER,
                        type = CommandType.KOTLIN,
                        name = handler.name,
                        regex = handler.regex,
                        version = 1
                    ),
                    handlerProvider = { handler }
                )
            },
            generators = emptyList()
        )

    private fun frame(duration: Int = 0): Frame =
        Frame(
            duration = duration,
            image = Surface.makeRasterN32Premul(1, 1).apply {
                canvas.clear(Color.RED)
            }.makeImageSnapshot()
        )
}
