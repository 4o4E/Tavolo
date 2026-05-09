plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(ktor("client-core-jvm"))
    implementation(ktor("client-cio-jvm"))
    implementation(ktor("client-content-negotiation-jvm"))
    implementation(ktor("serialization-kotlinx-json"))
    implementation(kotlinx("serialization-core-jvm", Versions.KOTLINX_SERIALIZATION))
    implementation(kotlinx("serialization-json-jvm", Versions.KOTLINX_SERIALIZATION))

    testImplementation(kotlin("test", Versions.KOTLIN))
    testImplementation(ktor("client-mock-jvm"))
}

tasks {
    test {
        useJUnitPlatform()
    }
}
