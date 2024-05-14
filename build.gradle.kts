//需要判断是否是jitpack的构建，如果是jitpack的构建，需要将build目录设置到项目根目录下
if (System.getenv("JITPACK") == null) {
    rootProject.layout.buildDirectory.set(file("./build"))
}

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.kotlin.plugin.parcelize).apply(false)
    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.library).apply(false)
    alias(libs.plugins.jetbrains.compose).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotlin.plugin.atomicfu)

}
val javaVersion = JavaVersion.toVersion(libs.versions.jvmTarget.get())
check(JavaVersion.current().isCompatibleWith(javaVersion)) {
    "This project needs to be run with Java ${javaVersion.getMajorVersion()} or higher (found: ${JavaVersion.current()})."
}

allprojects {
    tasks.register("testClasses")
}

subprojects {
    if (System.getenv("JITPACK") == null) {
        this.layout.buildDirectory.set(file("${rootProject.layout.buildDirectory.get().asFile.absolutePath}/${project.name}"))
    }
    afterEvaluate {
        apply(plugin = libs.plugins.ktlint.get().pluginId) // Version should be inherited from parent
        // Optionally configure plugin
        configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
            version.set("1.0.1")
        }
    }
    configurations.all {
        exclude(group = "org.jetbrains.compose.material", module = "material")
        resolutionStrategy {
            eachDependency {
                if (requested.group == "org.jetbrains.kotlin") {
                    useVersion(libs.versions.kotlin.get())
                } else if (requested.group.startsWith("org.jetbrains.compose.")
                    && !requested.group.endsWith(".compiler")
                ) {
                    useVersion(libs.versions.compose.plugin.get())
                } else if (requested.group == "org.jetbrains" && requested.name == "annotations") {
                    useVersion(libs.versions.annotations.get())
                }
            }
        }
    }
}

tasks.register<Copy>("setUpGitHooks") {
    group = "help"
    from("$rootDir/.hooks")
    into("$rootDir/.git/hooks")
}

tasks {
    task<Delete>("clean") {
        delete(rootProject.layout.buildDirectory.get().asFile)
        delete(rootDir.resolve("**/.idea"))
        delete(rootDir.resolve("**/.gradle"))
        delete(rootDir.resolve("**/.kotlin"))
        project(":sample").projectDir.apply {
            delete(resolve("iosApp/iosApp.xcworkspace"))
            delete(resolve("iosApp/Pods"))
            delete(resolve("iosApp/iosApp.xcodeproj/project.xcworkspace"))
            delete(resolve("iosApp/iosApp.xcodeproj/xcuserdata"))
        }
    }
}