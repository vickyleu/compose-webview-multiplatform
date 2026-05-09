@file:Suppress("UnstableApiUsage")


rootProject.name = "compose-webview-multiplatform"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
// mother fucker, WorkQueue error throw in Iguana
gradle.startParameter.excludedTaskNames.addAll(listOf(
    ":buildSrc:testClasses",
))

include(":webview")
include(":composeApp")
//include(":sample:androidApp")
//include(":sample:desktopApp")
//include(":sample:shared")

pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        mavenCentral()
        maven("https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
        google {
            content {
                includeGroupByRegex(".*google.*")
                includeGroupByRegex(".*android.*")
            }
        }
        maven(url = "https://androidx.dev/storage/compose-compiler/repository")
        maven(url = "https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        mavenCentral()
        maven("https://maven.aliyun.com/repository/public")
        google {
            content {
                includeGroupByRegex(".*google.*")
                includeGroupByRegex(".*android.*")
            }
        }
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven { setUrl("https://dl.bintray.com/kotlin/kotlin-dev") }
        maven { setUrl("https://dl.bintray.com/kotlin/kotlin-eap") }
        // workaround for https://youtrack.jetbrains.com/issue/KT-51379
      /*  exclusiveContent {
            forRepository {
                ivy("https://download.jetbrains.com/kotlin/native/builds") {
                    name = "Kotlin Native"
                    url = uri("https://download.jetbrains.com/kotlin/native/builds") // 确保设置的是正确的基础 URL
                    patternLayout {

                        // example download URLs:
                        // https://download.jetbrains.com/kotlin/native/builds/releases/1.7.20/linux-x86_64/kotlin-native-prebuilt-linux-x86_64-1.7.20.tar.gz
                        // https://download.jetbrains.com/kotlin/native/builds/releases/1.7.20/windows-x86_64/kotlin-native-prebuilt-windows-x86_64-1.7.20.zip
                        // https://download.jetbrains.com/kotlin/native/builds/releases/1.7.20/macos-x86_64/kotlin-native-prebuilt-macos-x86_64-1.7.20.tar.gz
                        listOf(
                            "macos-x86_64",
                            "macos-aarch64",
                            "osx-x86_64",
                            "osx-aarch64",
                            "linux-aarch64",
                            "linux-x86_64",
                            "windows-x86_64",
                        ).forEach { os ->
                            listOf("dev", "releases").forEach { stage ->
                                artifact("$stage/[revision]/$os/[artifact]-$os-[revision].[ext]")
                            }
                        }
                    }
                }
            }
            filter { includeModuleByRegex(".*", ".*kotlin-native-prebuilt.*") }
        }*/
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroupByRegex("com.github.*")
            }
        }
        maven {
            // kcf is not available in maven central
            // slow to download by hongkong server,please use us proxy
            url = uri("https://jogamp.org/deployment/maven")
        }


    }
}
