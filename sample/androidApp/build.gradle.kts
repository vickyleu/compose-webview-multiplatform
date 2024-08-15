plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jvmTarget.get()))
    }

    androidTarget {
        compilations.all {
            kotlinOptions.jvmTarget = libs.versions.jvmTarget.get()
        }
    }
    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation(project(":sample:shared"))
            }
        }
    }
}



android {
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    namespace = "com.multiplatform.webview.sample"

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    defaultConfig {
        applicationId = "com.kevinnzou.sample"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    }
}
