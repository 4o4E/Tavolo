import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import kotlin.apply

plugins {
    kotlin("jvm") version Versions.KOTLIN
    kotlin("plugin.serialization") version Versions.KOTLIN
    `maven-publish`
    `java-library`
    idea
}

val publishVersion = providers.gradleProperty("releaseVersion")
    .orElse(Versions.VERSION)
    .get()

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    group = Versions.GROUP
    version = publishVersion

    repositories {
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }

    dependencies {
        // kotlin
        implementation(kotlinx("coroutines-core-jvm", "1.10.2"))
    }
}

val local = Properties().apply {
    val file = projectDir.resolve("local.properties")
    if (file.exists()) file.bufferedReader().use { load(it) }
}

val nexusUsername get() = local.getProperty("nexus.username") ?: ""
val nexusPassword get() = local.getProperty("nexus.password") ?: ""
val githubPackagesRepository = providers.environmentVariable("GITHUB_REPOSITORY")
    .orElse("4o4E/Tavolo")
    .get()
val githubPackagesUsername = providers.environmentVariable("GITHUB_ACTOR")
    .orElse(local.getProperty("github.username") ?: "")
    .get()
val githubPackagesPassword = providers.environmentVariable("GITHUB_TOKEN")
    .orElse(local.getProperty("github.token") ?: "")
    .get()

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.gradle.maven-publish")
    apply(plugin = "org.gradle.java-library")

    kotlin {
        jvmToolchain(11)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    java {
        withSourcesJar()
    }

    afterEvaluate {
        publishing.publications.create<MavenPublication>("java") {
            from(components["kotlin"])
            artifact(tasks.getByName("sourcesJar"))
            artifactId = "${rootProject.name.lowercase()}-${project.name}"
            groupId = Versions.GROUP
            version = project.version.toString()
        }
    }

    publishing {
        repositories {
            // GitHub Packages 使用 GitHub Actions 自动注入的仓库名和令牌发布 Maven 包。
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/$githubPackagesRepository")
                credentials {
                    username = githubPackagesUsername
                    password = githubPackagesPassword
                }
            }

            maven {
                name = "snapshot"
                url = uri("https://nexus.e404.top:3443/repository/maven-snapshots/")
                isAllowInsecureProtocol = true
                credentials {
                    username = nexusUsername
                    password = nexusPassword
                }
            }
        }
    }
}

tasks.assemble {
    subprojects.forEach {
        dependsOn(it.tasks.assemble)
    }
}

tasks.register("manualTest") {
    group = "verification"
    description = "运行 core 和 graphics 模块的人工测试"
    dependsOn(":core:manualTest", ":graphics:manualTest")
}

idea {
    module.excludeDirs.addAll(arrayOf(
        file("run"),
        file(".idea"),
        file(".kotlin"),
    ))
}
