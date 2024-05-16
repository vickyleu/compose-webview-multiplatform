import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.kotlin.plugin.atomicfu)
    alias(libs.plugins.compose.compiler)
}

val xcodeConfigProperties = Properties().apply {
    load(project.file("../xcode_config.properties").reader())
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jvmTarget.get()))
    }

    applyDefaultHierarchyTemplate()
    androidTarget {
        publishLibraryVariants("release")
        compilations.all {
            kotlinOptions.jvmTarget = libs.versions.jvmTarget.get()
        }
    }

    jvm("desktop"){
        compilations.all {
            kotlinOptions.jvmTarget = libs.versions.jvmTarget.get()
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach {
        it.binaries.framework {
            baseName = "shared"
            isStatic = true
            binaryOption("bundleId", "com.kevinnzou.sample")
        }
    }

    xcodeCheck()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                api(compose.material3)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.components.resources)
                api(project(":webview"))
                implementation(libs.kotlinx.atomicfu)
                implementation(libs.kermit)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val androidMain by getting {
            dependencies {
                api(libs.androidx.appcompat)
                api(libs.androidx.activity.compose)
                implementation(libs.kotlinx.coroutines.android)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.common) {
                    exclude(compose.material)
                }
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
compose {
    kotlinCompilerPlugin = "org.jetbrains.kotlin:kotlin-compose-compiler-plugin-embeddable:${libs.versions.kotlin.get()}"
}
android {
    namespace = "com.kevinnzou.sample"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    }
}



fun xcodeCheck() {
    getBuildRelativeDir().apply {
        val dir = this
        project.file("../iosApp/Configuration/Config.xcconfig").apply {
            updateXcodeConfigFile(
                // $(SRCROOT) xcode项目根路径
                // $(CONFIGURATION) Debug/Release
                // $(SDK_NAME) iphonesimulator17.2/iphoneos17.2
                { "KMM_BUILD_DIR" to dir }, // 此目录已弃用,kmm新版本中已处理build目录修改后找不到search_path的问题
                { "BUNDLE_ID" to xcodeConfigProperties.getProperty("BundleId") },
                { "TEAM_ID" to xcodeConfigProperties.getProperty("TeamId") },
                { "APP_NAME" to xcodeConfigProperties.getProperty("AppName") }
            )
        }
    }
    processPlistFiles(project.projectDir.parentFile)
}