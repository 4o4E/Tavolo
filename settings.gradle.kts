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
    ":ksp",
    ":bdf-parser",
    ":gif-codec",
    ":draw",
    ":core",
    ":util",
    ":http-server",
)
