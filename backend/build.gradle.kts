plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    application
}

group = "calendar"
version = "0.1.0" // x-release-please-version

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors = true
    }
}

application {
    mainClass = "calendar.ApplicationKt"
}

ktlint {
    version = libs.versions.ktlintCli.get()
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)
    runtimeOnly(libs.logback.classic)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.openapi.validator)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// The generated contract is a build input: editing it re-runs the API tests.
val openApiSpec = layout.projectDirectory.file("../api/openapi/openapi.yaml")

tasks.test {
    useJUnitPlatform()
    inputs.file(openApiSpec).withPropertyName("openApiSpec").withPathSensitivity(PathSensitivity.NONE)
    systemProperty("openapi.spec", openApiSpec.asFile.absolutePath)
}
