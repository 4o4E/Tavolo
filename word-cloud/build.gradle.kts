plugins {
    kotlin("jvm")
    jacoco
}

dependencies {
    api(project(":common"))

    api(skiko("windows-x64"))
    api(skiko("linux-x64"))

    testImplementation(kotlin("test", Versions.KOTLIN))
    testImplementation(skiko("windows-x64"))
    testImplementation(skiko("linux-x64"))
}

val manualTest by sourceSets.creating {
    java.srcDir("src/manualTest/kotlin")
    resources.srcDir("src/manualTest/resources")
    compileClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
    runtimeClasspath += output + compileClasspath
}

configurations["manualTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["manualTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

tasks.test {
    useJUnitPlatform()
    val runDir = rootDir.resolve("run")
    workingDir = runDir
    doFirst {
        runDir.mkdirs()
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        csv.required.set(true)
        html.required.set(true)
    }
}

tasks.register<Test>("manualTest") {
    description = "运行需要人工查看输出图片的词云渲染测试"
    group = "verification"
    useJUnitPlatform()
    testClassesDirs = manualTest.output.classesDirs
    classpath = manualTest.runtimeClasspath
    val runDir = rootDir.resolve("run")
    workingDir = runDir
    doFirst {
        runDir.mkdirs()
    }
    shouldRunAfter(tasks.test)
    if (providers.gradleProperty("manualTest.parallel").map { it.toBoolean() }.getOrElse(false)) {
        maxParallelForks = providers.gradleProperty("manualTest.maxParallelForks")
            .map { it.toInt() }
            .getOrElse((Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1))
            .coerceAtLeast(1)
    }
}
