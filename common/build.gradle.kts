plugins {
    kotlin("jvm")
}

dependencies {
    // skiko
    compileOnly(skiko("windows-x64"))

    // test
    testImplementation(kotlin("test", Versions.KOTLIN))
    testImplementation(skiko("windows-x64"))
}

tasks.test {
    useJUnitPlatform()
}
