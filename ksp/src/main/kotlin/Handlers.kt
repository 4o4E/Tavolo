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
    private val contributionPackage = environment.options["tavolo.command.contributionPackage"]
        ?: "top.e404.tavolo.generated.command"
    private val contributionClass = environment.options["tavolo.command.contributionClass"]
        ?: "GeneratedCommandContribution"
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
        if (handlers.isEmpty() && generators.isEmpty()) {
            logger.warn("skip empty command contribution")
            return
        }
        val stream = try {
            codeGenerator.createNewFile(
                dependencies = Dependencies(true),
                packageName = contributionPackage,
                fileName = contributionClass,
                extensionName = "kt"
            )
        } catch (e: Exception) {
            logger.warn("skip exists file ${contributionPackage.replace('.', '/')}/$contributionClass.kt")
            return
        }
        logger.warn("process ${handlers.size} handlers, ${generators.size} generators")
        stream.bufferedWriter().use { bw ->
            bw.appendLine("package $contributionPackage").appendLine()
            bw.appendLine("class $contributionClass : top.e404.tavolo.registry.CommandContribution {")
            bw.appendLine("    // handler 数量: ${handlers.size}")
            bw.appendLine("    override val handlers: Map<String, () -> top.e404.tavolo.frame.FramesHandler> = mapOf(")
            handlers.toSortedMap().forEach { (id, qualifiedName) ->
                bw.append("        ")
                    .append("\"")
                    .append(id)
                    .append("\" to { ")
                    .append(qualifiedName)
                    .appendLine(" },")
            }
            bw.appendLine("    )")
            bw.appendLine()
            bw.appendLine("    // generator 数量: ${generators.size}")
            bw.appendLine("    override val generators: Map<String, () -> top.e404.tavolo.generator.FramesGenerator> = mapOf(")
            generators.toSortedMap().forEach { (id, qualifiedName) ->
                bw.append("        ")
                    .append("\"")
                    .append(id)
                    .append("\" to { ")
                    .append(qualifiedName)
                    .appendLine(" },")
            }
            bw.appendLine("    )")
            bw.appendLine("}")
        }
        writeServiceFile()
    }

    private fun writeServiceFile() {
        val servicePath = "META-INF/services/top.e404.tavolo.registry.CommandContribution"
        val serviceStream = try {
            codeGenerator.createNewFileByPath(
                dependencies = Dependencies(true),
                path = servicePath,
                extensionName = ""
            )
        } catch (e: Exception) {
            logger.warn("skip exists file $servicePath")
            return
        }
        serviceStream.bufferedWriter().use { bw ->
            bw.append(contributionPackage).append('.').appendLine(contributionClass)
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
