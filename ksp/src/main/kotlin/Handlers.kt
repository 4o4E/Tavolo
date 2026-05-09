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

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val handlerSet = resolver.getSymbolsWithAnnotation(handlerSignName).filterIsInstance<KSClassDeclaration>().toList()
        val generatorSet = resolver.getSymbolsWithAnnotation(generatorSignName).filterIsInstance<KSClassDeclaration>().toList()
        val stream = try {
            codeGenerator.createNewFile(
                dependencies = Dependencies(false),
                packageName = "top.e404.tavolo.handler",
                fileName = "registries",
                extensionName = "kt"
            )
        } catch (e: Exception) {
            logger.warn("skip exists file top/e404/tavolo/handler/registries.kt")
            return emptyList()
        }
        logger.warn("process ${handlerSet.size} handlers, ${generatorSet.size} generators")
        stream.bufferedWriter().use { bw ->
            bw.appendLine("package top.e404.tavolo.handler").appendLine()
            bw.appendLine("// handler size: ${handlerSet.size}")
            bw.appendLine("val handlerSet: Set<top.e404.tavolo.frame.FramesHandler> = setOf(")
            handlerSet.forEach {
                bw.append("    ").appendLine("${it.qualifiedName!!.asString()},")
            }
            bw.appendLine(")")
            bw.appendLine()
            bw.appendLine("val handlerMap: Map<String, top.e404.tavolo.frame.FramesHandler> = mapOf(")
            handlerSet.forEach {
                bw.append("    ")
                    .append("\"")
                    .append(it.requiredId(handlerSignName))
                    .append("\" to ")
                    .append(it.qualifiedName!!.asString())
                    .appendLine(",")
            }
            bw.appendLine(")")
            bw.appendLine()
            bw.appendLine("// generator size: ${generatorSet.size}")
            bw.appendLine("val generatorSet: Set<top.e404.tavolo.generator.FramesGenerator> = setOf(")
            generatorSet.forEach {
                bw.append("    ").appendLine("${it.qualifiedName!!.asString()},")
            }
            bw.appendLine(")")
            bw.appendLine()
            bw.appendLine("val generatorMap: Map<String, top.e404.tavolo.generator.FramesGenerator> = mapOf(")
            generatorSet.forEach {
                bw.append("    ")
                    .append("\"")
                    .append(it.requiredId(generatorSignName))
                    .append("\" to ")
                    .append(it.qualifiedName!!.asString())
                    .appendLine(",")
            }
            bw.appendLine(")")
        }
        return emptyList()
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
