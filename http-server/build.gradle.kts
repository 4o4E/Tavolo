plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    kotlin("plugin.serialization")
    application
}

application {
    mainClass.set("top.e404.tavolo.http.server.TavoloHttpServer")
}

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
