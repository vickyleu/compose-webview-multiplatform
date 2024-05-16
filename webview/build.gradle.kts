@file:Suppress("OPT_IN_USAGE")

import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.getCurrentOperatingSystem
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.android.library)
    alias(libs.plugins.dokka)
    alias(libs.plugins.compose.compiler)
//    alias(libs.plugins.maven.publish)
    alias(libs.plugins.kotlin.plugin.serialization)
    id("maven-publish")
//    id("signing")
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
                implementation(project.dependencies.platform(libs.compose.bom))
                implementation(project.dependencies.platform(libs.coroutines.bom))
                implementation(project.dependencies.platform(libs.kotlin.bom))

                implementation(compose.runtime)
                implementation(compose.foundation)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.components.resources)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kermit)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        androidMain{
            dependencies {
                api(libs.androidx.activity.compose)
                api(libs.webkit)
                implementation(libs.kotlinx.coroutines.android)
            }
        }
        iosMain {
        }
        commonTest{

        }
        val desktopMain by getting {
            dependsOn(commonMain.get())
            dependencies {
                implementation(compose.desktop.common) {
                    exclude(compose.material)
                }
                api(libs.kcef)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
    }
}
compose {
    kotlinCompilerPlugin = "org.jetbrains.kotlin:kotlin-compose-compiler-plugin-embeddable:${libs.versions.kotlin.get()}"
}
android {
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    namespace = "com.multiplatform.webview"

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


buildscript {
    dependencies {
        val dokkaVersion = libs.versions.dokka.get()
        classpath("org.jetbrains.dokka:dokka-base:$dokkaVersion")
    }
}
group = "com.vickyleu.webview"
version = "1.0.2"

tasks.withType<PublishToMavenRepository> {
    val isMac = getCurrentOperatingSystem().isMacOsX
    onlyIf {
        isMac.also {
            if (!isMac) logger.error(
                """
                    Publishing the library requires macOS to be able to generate iOS artifacts.
                    Run the task on a mac or use the project GitHub workflows for publication and release.
                """
            )
        }
    }
}

val javadocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.dokkaHtml)
    from(tasks.dokkaHtml.flatMap(DokkaTask::outputDirectory))
    archiveClassifier = "javadoc"
}

tasks.dokkaHtml {
    // outputDirectory = layout.buildDirectory.get().resolve("dokka")
    offlineMode = false
    moduleName = "webview"

    /*// See the buildscript block above and also
    // https://github.com/Kotlin/dokka/issues/2406
    pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
        customAssets = listOf(file("../asset/logo-icon.svg"))
        customStyleSheets = listOf(file("../asset/logo-styles.css"))
        separateInheritedMembers = true
    }*/

    dokkaSourceSets {
        configureEach {
            reportUndocumented = true
            noAndroidSdkLink = false
            noStdlibLink = false
            noJdkLink = false
            jdkVersion = libs.versions.jvmTarget.get().toInt()
            // sourceLink {
            //     // Unix based directory relative path to the root of the project (where you execute gradle respectively).
            //     // localDirectory.set(file("src/main/kotlin"))
            //     // URL showing where the source code can be accessed through the web browser
            //     // remoteUrl = uri("https://github.com/mahozad/${project.name}/blob/main/${project.name}/src/main/kotlin").toURL()
            //     // Suffix which is used to append the line number to the URL. Use #L for GitHub
            //     remoteLineSuffix = "#L"
            // }
        }
    }
}

val properties = Properties().apply {
    runCatching { rootProject.file("local.properties") }
        .getOrNull()
        .takeIf { it?.exists() ?: false }
        ?.reader()
        ?.use(::load)
}
// For information about signing.* properties,
// see comments on signing { ... } block below
val environment: Map<String, String?> = System.getenv()
extra["githubToken"] = properties["github.token"] as? String
    ?: environment["GITHUB_TOKEN"] ?: ""

publishing {

    val projectName = rootProject.name
    repositories {
        /*maven {
            name = "CustomLocal"
            url = uri("file://${layout.buildDirectory.get()}/local-repository")
        }
        maven {
            name = "MavenCentral"
            setUrl("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = extra["ossrhUsername"]?.toString()
                password = extra["ossrhPassword"]?.toString()
            }
        }*/
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/vickyleu/${projectName}")
            credentials {
                username = "vickyleu"
                password = extra["githubToken"]?.toString()
            }
        }
    }
    afterEvaluate {
        publications.withType<MavenPublication> {
            artifact(javadocJar) // Required a workaround. See below
            version = project.version.toString()
            groupId = project.group.toString()
            artifactId=artifactId
                .replace("compose-", "")
                .replace("-multiplatform", "")
            pom {
                url = "https://github.com/vickyleu/${projectName}"
                name = projectName
                description = """
                Visit the project on GitHub to learn more.
            """.trimIndent()
                inceptionYear = "2024"
                licenses {
                    license {
                        name = "Apache-2.0 License"
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                developers {
                    developer {
                        id = "kevinnzou"
                        name = "kevinnzou"
                        email = ""
                        roles = listOf("Netease Mobile Developer")
                        timezone = "GMT+8"
                    }
                }
                contributors {
                    // contributor {}
                }
                scm {
                    tag = "HEAD"
                    url = "https://github.com/vickyleu/${projectName}"
                    connection = "scm:git:github.com/vickyleu/${projectName}.git"
                    developerConnection = "scm:git:ssh://github.com/vickyleu/${projectName}.git"
                }
                issueManagement {
                    system = "GitHub"
                    url = "https://github.com/vickyleu/${projectName}/issues"
                }
                ciManagement {
                    system = "GitHub Actions"
                    url = "https://github.com/vickyleu/${projectName}/actions"
                }
            }
        }
    }

}

// TODO: Remove after https://youtrack.jetbrains.com/issue/KT-46466 is fixed
//  Thanks to KSoup repository for this code snippet
tasks.withType(AbstractPublishToMaven::class).configureEach {
    dependsOn(tasks.withType(Sign::class))
}

// * Uses signing.* properties defined in gradle.properties in ~/.gradle/ or project root
// * Can also pass from command line like below
// * ./gradlew task -Psigning.secretKeyRingFile=... -Psigning.password=... -Psigning.keyId=...
// * See https://docs.gradle.org/current/userguide/signing_plugin.html
// * and https://stackoverflow.com/a/67115705
/*signing {
    sign(publishing.publications)
}*/
