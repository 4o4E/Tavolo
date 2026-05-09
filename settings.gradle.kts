pluginManagement {
    plugins {
        id("com.google.devtools.ksp") version "2.2.21-2.0.4"
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "Tavolo"
include(
    ":annotation",
    ":bdf-parser",
    ":common",
    ":core",
    ":gif-codec",
    ":graphics",
    ":http-client",
    ":http-server",
    ":ksp",
)
