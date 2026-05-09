package top.e404.tavolo.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import top.e404.tavolo.annotation.ImageHandler
import top.e404.tavolo.annotation.ImageGenerator

class FramesHandlerProcessor(environment: SymbolProcessorEnvironment) : SymbolProcessor {
    private val codeGenerator = environment.codeGenerator
    private val logger = environment.logger
    private val handlerSignName = ImageHandler::class.java.name
    private val generatorSignName = ImageGenerator::class.java.name
    private val handlers = linkedMapOf<String, String>()
    private val generators = linkedMapOf<String, String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getSymbolsWithAnnotation(handlerSignName)
            .filterIsInstance<KSClassDeclaration>()
            .forEach { declaration ->
                val id = declaration.requiredId(handlerSignName)
                val qualifiedName = declaration.qualifiedName!!.asString()
                handlers.put(id, qualifiedName)?.takeIf { it != qualifiedName }?.let {
                    error("handler 注册 id 重复: $id")
                }
            }
        resolver.getSymbolsWithAnnotation(generatorSignName)
            .filterIsInstance<KSClassDeclaration>()
            .forEach { declaration ->
                val id = declaration.requiredId(generatorSignName)
                val qualifiedName = declaration.qualifiedName!!.asString()
                generators.put(id, qualifiedName)?.takeIf { it != qualifiedName }?.let {
                    error("generator 注册 id 重复: $id")
                }
            }
        return emptyList()
    }

    override fun finish() {
        val stream = try {
            codeGenerator.createNewFile(
                dependencies = Dependencies(true),
                packageName = "top.e404.tavolo.handler",
                fileName = "registries",
                extensionName = "kt"
            )
        } catch (e: Exception) {
            logger.warn("skip exists file top/e404/tavolo/handler/registries.kt")
            return
        }
        logger.warn("process ${handlers.size} handlers, ${generators.size} generators")
        stream.bufferedWriter().use { bw ->
            bw.appendLine("package top.e404.tavolo.handler").appendLine()
            bw.appendLine("// handler size: ${handlers.size}")
            bw.appendLine("val handlerMap: Map<String, () -> top.e404.tavolo.frame.FramesHandler> = mapOf(")
            handlers.toSortedMap().forEach { (id, qualifiedName) ->
                bw.append("    ")
                    .append("\"")
                    .append(id)
                    .append("\" to { ")
                    .append(qualifiedName)
                    .appendLine(" },")
            }
            bw.appendLine(")")
            bw.appendLine()
            bw.appendLine("val handlerSet: Set<top.e404.tavolo.frame.FramesHandler>")
            bw.appendLine("    get() = handlerMap.values.map { it() }.toSet()")
            bw.appendLine()
            bw.appendLine("// generator size: ${generators.size}")
            bw.appendLine("val generatorMap: Map<String, () -> top.e404.tavolo.generator.FramesGenerator> = mapOf(")
            generators.toSortedMap().forEach { (id, qualifiedName) ->
                bw.append("    ")
                    .append("\"")
                    .append(id)
                    .append("\" to { ")
                    .append(qualifiedName)
                    .appendLine(" },")
            }
            bw.appendLine(")")
            bw.appendLine()
            bw.appendLine("val generatorSet: Set<top.e404.tavolo.generator.FramesGenerator>")
            bw.appendLine("    get() = generatorMap.values.map { it() }.toSet()")
        }
    }

    private fun KSClassDeclaration.requiredId(annotationName: String): String {
        val annotation = annotations.firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == annotationName }
            ?: error("${qualifiedName!!.asString()} 缺少注册注解")
        val id = annotation.stringArgument("id")
        if (id.isBlank()) error("${qualifiedName!!.asString()} 的注册 id 不能为空")
        return id
    }

    private fun KSAnnotation.stringArgument(name: String): String =
        arguments.firstOrNull { it.name?.asString() == name }?.value as? String ?: ""
}

class FramesHandlerProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment) = FramesHandlerProcessor(environment)
}
