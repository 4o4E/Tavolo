import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.api.GradleException
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.publish.PublishingExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version Versions.KOTLIN
    kotlin("plugin.serialization") version Versions.KOTLIN
    id("com.vanniktech.maven.publish") version "0.36.0" apply false
    `java-library`
    idea
}

val isCi = providers.environmentVariable("GITHUB_ACTIONS")
    .map { it == "true" }
    .orElse(false)

val isCiTag = providers.environmentVariable("GITHUB_REF_TYPE")
    .map { it == "tag" }
    .orElse(providers.environmentVariable("GITHUB_REF").map { it.startsWith("refs/tags/") })
    .orElse(false)

val localSnapshotVersion = Versions.VERSION
    .removeSuffix("-SNAPSHOT")
    .let { "$it-SNAPSHOT" }

val ciReleaseVersion = providers.environmentVariable("GITHUB_REF_NAME")

val publishVersion = providers.provider {
    if (isCi.get() && isCiTag.get()) ciReleaseVersion.orNull ?: localSnapshotVersion else localSnapshotVersion
}.get()

val projectUrl = "https://github.com/4o4E/Tavolo"
val nexusReleaseUrl = "https://nexus.e404.top:3443/repository/maven-releases/"
val nexusSnapshotUrl = "https://nexus.e404.top:3443/repository/maven-snapshots/"
val isSnapshotVersion = publishVersion.endsWith("-SNAPSHOT")
// 空集合表示 settings.gradle.kts 中 include 的业务模块全部发布。
val publishModuleFilter = emptySet<String>()

fun nexusCredential(propertyName: String, ciSecretEnvName: String): Provider<String> =
    if (isCi.get()) providers.environmentVariable(ciSecretEnvName) else providers.gradleProperty(propertyName)

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    group = Versions.GROUP
    version = publishVersion

    repositories {
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }

    dependencies {
        // Kotlin 协程是各模块共享的异步基础能力。
        implementation(kotlinx("coroutines-core-jvm", "1.10.2"))
    }
}

subprojects {
    val shouldPublish = publishModuleFilter.isEmpty() || project.name in publishModuleFilter

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.gradle.java-library")
    if (shouldPublish) {
        apply(plugin = "com.vanniktech.maven.publish")
    }

    kotlin {
        jvmToolchain(17)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    if (shouldPublish) {
        extensions.configure<MavenPublishBaseExtension>("mavenPublishing") {
            coordinates(
                groupId = Versions.GROUP,
                artifactId = "${rootProject.name.lowercase()}-${project.name}",
                version = publishVersion,
            )

            // Kotlin JVM 项目使用源码包和空 javadoc 包，满足 Maven Central 的制品完整性要求。
            configure(
                KotlinJvm(
                    javadocJar = JavadocJar.Empty(),
                    sourcesJar = SourcesJar.Sources(),
                )
            )

            if (isCi.get() && isCiTag.get()) {
                publishToMavenCentral()
                signAllPublications()
            }

            pom {
                name.set("Tavolo ${project.name}")
                description.set("Tavolo ${project.name} module for headless image processing and offline rendering.")
                url.set(projectUrl)
                licenses {
                    license {
                        name.set("GNU General Public License v3.0")
                        url.set("https://www.gnu.org/licenses/gpl-3.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("4o4E")
                        name.set("4o4E")
                        email.set("869951226@qq.com")
                        organization.set("4o4E")
                        organizationUrl.set("https://github.com/4o4E")
                    }
                }
                scm {
                    url.set(projectUrl)
                    connection.set("scm:git:$projectUrl.git")
                    developerConnection.set("scm:git:$projectUrl.git")
                }
            }
        }

        extensions.configure<PublishingExtension>("publishing") {
            repositories {
                maven {
                    name = "nexus"
                    url = uri(if (isSnapshotVersion) nexusSnapshotUrl else nexusReleaseUrl)
                    credentials(PasswordCredentials::class) {
                        username = nexusCredential("nexus.username", "NEXUS_USERNAME")
                            .orNull
                        password = nexusCredential("nexus.password", "NEXUS_PASSWORD")
                            .orNull
                    }
                }
            }
        }

        if (!isCi.get()) {
            listOf("publishToMavenCentral", "publishAndReleaseToMavenCentral").forEach { taskName ->
                tasks.register(taskName) {
                    group = "publishing"
                    description = "Maven Central 发布保护任务"
                }
            }
        }

        tasks.matching {
            it.name == "publishToMavenCentral" ||
                it.name == "publishAndReleaseToMavenCentral" ||
                it.name.endsWith("ToMavenCentralRepository")
        }.configureEach {
            doFirst {
                if (!isCi.get()) {
                    throw GradleException(
                        "Maven Central 只允许通过 CI 发布，本地请使用 publishAllPublicationsToNexusRepository 或 publishToMavenLocal。"
                    )
                }
                if (!isCiTag.get()) {
                    throw GradleException("Maven Central 只允许通过 CI tag 发布。")
                }
                if (rootProject.version.toString().endsWith("-SNAPSHOT")) {
                    throw GradleException("Maven Central 只能发布 CI tag 推导出的正式版本，不能发布 SNAPSHOT。")
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
    module.excludeDirs.addAll(
        arrayOf(
            file("run"),
            file(".idea"),
            file(".kotlin"),
        )
    )
}
