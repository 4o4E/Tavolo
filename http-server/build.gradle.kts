import org.gradle.api.tasks.bundling.Jar

plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    kotlin("plugin.serialization")
    application
}

application {
    mainClass.set("top.e404.tavolo.http.server.TavoloHttpServer")
}

val serverReleaseVersion = providers.gradleProperty("releaseVersion")
    .orElse(project.version.toString())

dependencies {
    implementation(project(":core"))
    // skiko
    runtimeOnly(skiko("windows-x64"))
    runtimeOnly(skiko("linux-x64"))
    // ktor
    implementation(ktor("server-content-negotiation"))
    implementation(ktor("server-compression-jvm"))
    implementation(ktor("serialization-kotlinx-json"))
    implementation(ktor("server-core-jvm"))
    implementation(ktor("server-netty-jvm"))
    implementation(ktor("server-call-logging-jvm"))
    implementation(ktor("server-status-pages-jvm"))
    // logging
    implementation("ch.qos.logback:logback-classic:1.5.18")
    // serialization
    implementation(kotlinx("serialization-core-jvm", Versions.KOTLINX_SERIALIZATION))
    implementation(kotlinx("serialization-json-jvm", Versions.KOTLINX_SERIALIZATION))
    // reflect
    implementation(kotlin("reflect", Versions.KOTLIN))

    testImplementation(kotlin("test", Versions.KOTLIN))
    testImplementation(ktor("server-tests-jvm"))
    testImplementation(skiko("windows-x64"))
    testImplementation(skiko("linux-x64"))
}

tasks {
    test {
        useJUnitPlatform()
        val runDir = rootProject.projectDir.resolve("run")
        workingDir = runDir
        doFirst {
            runDir.mkdirs()
        }
        systemProperty("tavolo.assets.dir", rootProject.projectDir.resolve("assets").absolutePath)
    }
}

tasks.register<Jar>("packageServerJar") {
    group = "distribution"
    description = "打包可直接通过 java -jar 启动的 HTTP 服务 jar"
    dependsOn(tasks.named("classes"))
    dependsOn(configurations.runtimeClasspath)

    archiveFileName.set(serverReleaseVersion.map { "tavolo-server-$it.jar" })
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes("Main-Class" to application.mainClass.get())
    }

    from(sourceSets.main.get().output)

    // 将运行时依赖展开进同一个 jar，保证 Release 只需要 server jar 和资源 zip。
    from({
        configurations.runtimeClasspath.get()
            .filter { it.exists() }
            .map { if (it.isDirectory) it else zipTree(it) }
    })

    exclude(
        "META-INF/*.DSA",
        "META-INF/*.RSA",
        "META-INF/*.SF",
    )
}
