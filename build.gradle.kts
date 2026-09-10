import com.vanniktech.maven.publish.DeploymentValidation
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.plugins.signing.Sign
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

if (System.getenv("JITPACK") == null) {
    rootProject.layout.buildDirectory.set(file("./build"))
}

plugins {
    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.kotlin.parcelize).apply(false)
    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.library).apply(false)
    alias(libs.plugins.jetbrains.compose).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.dokka).apply(false)
    alias(libs.plugins.ktlint).apply(false)
    alias(libs.plugins.kotlinx.atomicfu).apply(false)
    alias(libs.plugins.kotlin.serialization).apply(false)
    alias(libs.plugins.vanniktech.maven.publish).apply(false)
}

val javaVersion = JavaVersion.toVersion(libs.versions.jvmTarget.get())
check(JavaVersion.current().isCompatibleWith(javaVersion)) {
    "This project needs to be run with Java ${javaVersion.getMajorVersion()} or higher (found: ${JavaVersion.current()})."
}

val publishGroup = "io.github.vickyleu.webview"
val publishVersion = "2.0.1"
val publishRepo = "compose-webview-multiplatform"
val publishUrl = "https://github.com/vickyleu/$publishRepo"

allprojects {
    if (tasks.findByName("testClasses") == null) {
        tasks.register("testClasses")
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }
}

subprojects {
    if (System.getenv("JITPACK") == null) {
        layout.buildDirectory.set(file("${rootProject.layout.buildDirectory.get().asFile.absolutePath}/${project.name}"))
    }

    configurations.all {
        resolutionStrategy {
            eachDependency {
                if (requested.group == "org.jetbrains.kotlin") {
                    useVersion(libs.versions.kotlin.get())
                } else if (requested.group.startsWith("org.jetbrains.compose")) {
                    useVersion(libs.versions.compose.plugin.get())
                } else if (requested.group == "org.jetbrains" && requested.name == "annotations") {
                    useVersion(libs.versions.annotations.get())
                }
            }
        }
    }

    afterEvaluate {
        apply(plugin = libs.plugins.ktlint.get().pluginId)
    }

    if (name != "webview") return@subprojects

    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "com.vanniktech.maven.publish")

    extensions.configure<org.jetbrains.dokka.gradle.DokkaExtension>("dokka") {
        moduleName.set("webview")
        dokkaPublications.named("html") {
            offlineMode.set(false)
            moduleName.set("webview")
        }
        dokkaSourceSets.configureEach {
            reportUndocumented.set(false)
            enableAndroidDocumentationLink.set(true)
            enableKotlinStdLibDocumentationLink.set(true)
            enableJdkDocumentationLink.set(true)
            jdkVersion.set(libs.versions.jvmTarget.get().toInt())
        }
    }

    extensions.configure<MavenPublishBaseExtension>("mavenPublishing") {
        coordinates(publishGroup, "webview", publishVersion)
        publishToMavenCentral(
            automaticRelease = true,
            validateDeployment = DeploymentValidation.PUBLISHED,
        )
        signAllPublications()

        pom {
            name.set("Vickyleu KMP WebView")
            description.set("A Compose Multiplatform WebView library for Android, iOS, and desktop.")
            inceptionYear.set("2024")
            url.set(publishUrl)
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("vickyleu")
                    name.set("Vickyleu")
                    url.set("https://github.com/vickyleu")
                }
            }
            scm {
                url.set(publishUrl)
                connection.set("scm:git:https://github.com/vickyleu/$publishRepo.git")
                developerConnection.set("scm:git:ssh://git@github.com/vickyleu/$publishRepo.git")
            }
            issueManagement {
                system.set("GitHub")
                url.set("$publishUrl/issues")
            }
            ciManagement {
                system.set("GitHub Actions")
                url.set("$publishUrl/actions")
            }
        }
    }

    tasks.withType<Sign>().configureEach {
        onlyIf {
            val signingKeyRingFile = providers.gradleProperty("signing.secretKeyRingFile").orNull
            val hasSigningKey =
                !providers.gradleProperty("signingInMemoryKey").orNull.isNullOrBlank() ||
                    (!signingKeyRingFile.isNullOrBlank() && file(signingKeyRingFile).isFile)
            val publishingToCentral = gradle.taskGraph.allTasks.any { task ->
                task.name.contains("MavenCentral")
            }
            hasSigningKey || publishingToCentral
        }
    }
}

tasks.register<Copy>("setUpGitHooks") {
    group = "help"
    from("$rootDir/.hooks")
    into("$rootDir/.git/hooks")
}
