//需要判断是否是jitpack的构建，如果是jitpack的构建，需要将build目录设置到项目根目录下
if (System.getenv("JITPACK") == null) {
    val buildDir = rootProject.rootDir.parentFile.resolve("./build/${project.name}")
    rootProject.layout.buildDirectory.set(buildDir)
}

plugins {
    alias(libs.plugins.kotlin.jvm)
}

afterEvaluate {
    tasks.named("clean", Delete::class) {
        delete(rootDir.resolve("**/.idea"))
        delete(rootDir.resolve("**/.gradle"))
        delete(rootDir.resolve("**/.kotlin"))
    }
}


configurations.all {
    resolutionStrategy {
        eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion(libs.versions.kotlin.get())
            } else if (requested.group == "org.jetbrains" && requested.name == "annotations") {
                useVersion(libs.versions.annotations.get())
            }
        }
    }
}
dependencies {
    implementation(project.dependencies.platform(libs.compose.bom))
    implementation(project.dependencies.platform(libs.coroutines.bom))
    implementation(project.dependencies.platform(libs.kotlin.bom))
}