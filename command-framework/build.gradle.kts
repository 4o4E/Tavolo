plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":annotation"))
    api(project(":common"))
    api(project(":gif-codec"))

    implementation(kotlinx("serialization-core-jvm", Versions.KOTLINX_SERIALIZATION))

    testImplementation(kotlin("test", Versions.KOTLIN))
    testImplementation(skiko("windows-x64"))
    testImplementation(skiko("linux-x64"))
}

tasks.test {
    useJUnitPlatform()
    val runDir = rootProject.projectDir.resolve("run")
    workingDir = runDir
    doFirst {
        runDir.mkdirs()
    }
    systemProperty("tavolo.assets.dir", rootProject.projectDir.resolve("assets").absolutePath)
}
