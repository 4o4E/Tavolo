import org.gradle.api.tasks.bundling.Zip

plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    kotlin("plugin.serialization")
}

dependencies {
    // ksp
    ksp(project(":ksp"))

    api(project(":annotation"))
    api(project(":common"))
    api(project(":gif-codec"))
    api(project(":graphics"))
    api(project(":bdf-parser"))
    // skiko
    compileOnly(skiko("windows-x64"))
    compileOnly(skiko("linux-x64"))
    // serialization
    implementation(kotlinx("serialization-core-jvm", Versions.KOTLINX_SERIALIZATION))
    // kaml
    implementation("com.charleskorn.kaml:kaml:0.45.0")
//    // reflect
//    implementation(kotlin("reflect", Versions.kotlin))

    // test
    testImplementation(kotlin("test", Versions.KOTLIN))
    // skiko
    testImplementation(skiko("windows-x64"))
    testImplementation(skiko("linux-x64"))
}

tasks {
    test {
        useJUnitPlatform()
        workingDir = rootProject.projectDir.resolve("run")
        systemProperty("tavolo.assets.dir", rootProject.projectDir.resolve("assets").absolutePath)
//        maxHeapSize = "8G"
//        minHeapSize = "8G"
    }
}

val commandAssetsDir = rootProject.projectDir.resolve("assets")

tasks.register("validateCommandAssets") {
    group = "verification"
    description = "校验外置指令资源目录结构"

    doLast {
        fun fail(message: String): Nothing = throw GradleException(message)
        fun unquote(value: String): String = value.trim().removeSurrounding("\"")
        fun resolveRelative(dir: File, path: String): File {
            val raw = unquote(path)
            if (raw.isBlank()) fail("资源路径不能为空: ${dir.name}")
            val file = File(raw)
            if (file.isAbsolute || raw.contains("..")) fail("资源路径不能是绝对路径或包含 ..: ${dir.name}/$raw")
            val root = dir.canonicalFile
            val resolved = dir.resolve(raw).canonicalFile
            if (!resolved.path.startsWith(root.path)) fail("资源路径越界: ${dir.name}/$raw")
            return resolved
        }

        fun validateConfiguredFiles(dir: File, text: String) {
            Regex("""(?m)^\s*file:\s*(.+?)\s*$""").findAll(text).forEach {
                val file = resolveRelative(dir, it.groupValues[1])
                if (!file.isFile) fail("配置引用的文件不存在: ${dir.name}/${unquote(it.groupValues[1])}")
                if (file.extension.lowercase() !in setOf("ttf", "ttc", "otf")) {
                    fail("fonts.file 只允许 ttf/ttc/otf: ${dir.name}/${file.name}")
                }
            }
        }
        fun flatFields(text: String): Map<String, String> =
            text.lineSequence()
                .map { it.trim().removePrefix("\uFEFF") }
                .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("-") }
                .mapNotNull {
                    val index = it.indexOf(':')
                    if (index == -1) null else it.substring(0, index).trim() to unquote(it.substring(index + 1))
                }
                .toMap()

        val versionFile = commandAssetsDir.resolve("version.yml")
        if (!versionFile.isFile) fail("资源目录缺少 version.yml: ${versionFile.absolutePath}")
        val versionText = versionFile.readText()
        val versionFields = flatFields(versionText)
        if (versionFields["version"].isNullOrBlank()) fail("version.yml 缺少 version 字段")
        if (versionFields["time"].isNullOrBlank()) fail("version.yml 缺少 time 字段")

        val handlersDir = commandAssetsDir.resolve("handlers")
        val generatorsDir = commandAssetsDir.resolve("generators")
        if (!handlersDir.isDirectory) fail("资源目录缺少 handlers 目录: ${handlersDir.absolutePath}")
        if (!generatorsDir.isDirectory) fail("资源目录缺少 generators 目录: ${generatorsDir.absolutePath}")

        handlersDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            val config = dir.resolve("handler.yml")
            if (!config.isFile) fail("handler 资源缺少 handler.yml: ${dir.name}")
            val text = config.readText()
            val fields = flatFields(text)
            if (fields["id"] != dir.name) {
                fail("handler.yml 的 id 与目录名不一致: ${dir.name}")
            }
            if (fields["type"] !in setOf("template", "kotlin")) {
                fail("handler.yml 缺少合法 type: ${dir.name}")
            }
            if (fields["type"] == "template") {
                val layout = fields["layout"] ?: "layout.yml"
                if (!dir.resolve(layout).isFile) fail("template handler 缺少 layout.yml: ${dir.name}")
            }
            validateConfiguredFiles(dir, text)
        }

        generatorsDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            val config = dir.resolve("generator.yml")
            if (!config.isFile) fail("generator 资源缺少 generator.yml: ${dir.name}")
            val text = config.readText()
            val fields = flatFields(text)
            if (fields["id"] != dir.name) {
                fail("generator.yml 的 id 与目录名不一致: ${dir.name}")
            }
            if (fields.containsKey("type")) {
                fail("generator.yml 不允许声明 type: ${dir.name}")
            }
            if (fields.containsKey("layout")) {
                fail("generator.yml 不允许声明 layout: ${dir.name}")
            }
            validateConfiguredFiles(dir, text)
        }
    }
}

tasks.register<Zip>("packageCommandAssets") {
    group = "distribution"
    description = "打包外置指令资源 zip"
    dependsOn("validateCommandAssets")
    from(commandAssetsDir)
    archiveFileName.set("tavolo-assets-${project.version}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}

val manualTest by sourceSets.creating {
    java.srcDir("src/manualTest/kotlin")
    resources.srcDir("src/manualTest/resources")
    compileClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
    runtimeClasspath += output + compileClasspath
}

configurations["manualTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["manualTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

tasks.register<Test>("manualTest") {
    description = "运行需要人工查看输出图片或依赖本地资源的 core 测试"
    group = "verification"
    testClassesDirs = manualTest.output.classesDirs
    classpath = manualTest.runtimeClasspath
    workingDir = rootProject.projectDir.resolve("run")
    systemProperty("tavolo.assets.dir", rootProject.projectDir.resolve("assets").absolutePath)
    shouldRunAfter(tasks.test)
    useJUnitPlatform()
}
