import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    application
}
repositories {
    mavenCentral()
}
dependencies {
    implementation(project(":"))
    implementation(libs.dotenv)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logback)
}
kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}
application {
    mainClass.set("net.kigawa.fonsole.Main")
}