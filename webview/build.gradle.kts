@file:Suppress("OPT_IN_USAGE")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(libs.plugins.kotlin.multiplatform.get().pluginId)
    alias(libs.plugins.jetbrains.compose)
    id(libs.plugins.android.library.get().pluginId)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

tasks
    .withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>()
    .configureEach {
        compilerOptions
            .jvmTarget
            .set(JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
    }

kotlin {
//    explicitApi = ExplicitApiMode.Strict
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jvmTarget.get()))
    }
    applyDefaultHierarchyTemplate()
    androidTarget {
        publishLibraryVariants("release")
    }
    jvm("desktop")

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "webview"
            isStatic = true
            binaryOption("bundleId", "com.multiplatform.webview")
            binaryOption("bundleVersion", "2")
        }
        iosTarget.setUpiOSObserver()
    }

    sourceSets {
        commonMain{
            dependencies {
                implementation(project.dependencies.platform(libs.coroutines.bom))
                implementation(project.dependencies.platform(libs.kotlin.bom))

                implementation(compose.runtime)
                implementation(compose.foundation)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.components.resources)
                implementation(libs.coroutines.core)
                implementation(libs.kermit)
                implementation(libs.kotlin.serialization.json)
            }
        }

        androidMain{
            dependencies {
                api(libs.androidx.activity.compose)
                api(libs.webkit)
                implementation(libs.coroutines.android)
            }
        }
        iosMain {
        }
        commonTest{

        }
        val desktopMain by getting {
//            dependsOn(commonMain.get())
            dependencies {
                implementation(compose.desktop.common) {
                    exclude(compose.material)
                }
                api(libs.kcef)
                implementation(libs.coroutines.swing)
            }
        }
    }
}

compose.resources{
    publicResClass = false
    packageOfResClass="webview.generated.resources"
    generateResClass = always
}

android {
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    namespace = "com.multiplatform.webview"

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
//    sourceSets["main"].resources.srcDirs("src/commonMain/resources")
//    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    }
}

fun org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget.setUpiOSObserver() {
    val path = projectDir.resolve("src/nativeInterop/cinterop/observer")

    binaries.all {
        linkerOpts("-F $path")
        linkerOpts("-ObjC")
    }

    compilations.getByName("main") {
        cinterops.create("observer") {
            compilerOpts("-F $path")
        }
    }
}
