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
}

tasks {
    test {
        useJUnitPlatform()
        workingDir = rootProject.projectDir.resolve("run")
//        maxHeapSize = "8G"
//        minHeapSize = "8G"
    }
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
    shouldRunAfter(tasks.test)
    useJUnitPlatform()
}
