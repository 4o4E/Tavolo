plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("me.champeau.jmh") version "0.7.3"
}

dependencies {
    api(project(":common"))
    // skiko
    compileOnly(skiko("windows-x64"))
    compileOnly(skiko("linux-x64"))
    // test
    testImplementation(kotlin("test", Versions.KOTLIN))
    testImplementation(skiko("windows-x64"))
    testImplementation(skiko("linux-x64"))
    jmhImplementation(skiko("windows-x64"))
    jmhImplementation(skiko("linux-x64"))
}

jmh {
    jmhVersion = "1.37"
    includes = listOf(
        providers.gradleProperty("jmhInclude")
            .orElse(".*GifEncoding.*")
            .get()
    )
    profilers = listOf("gc")
    resultFormat = "JSON"
    resultsFile = layout.buildDirectory
        .file(providers.gradleProperty("jmhResult").orElse("reports/jmh/results.json"))
        .get()
        .asFile
    humanOutputFile = layout.buildDirectory
        .file(providers.gradleProperty("jmhOutput").orElse("reports/jmh/output.txt"))
        .get()
        .asFile
    failOnError = true
}
