plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    // No Hilt — :shared must remain DI-neutral and JVM-unit-testable
    jacoco
}

android {
    namespace = "com.mari.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        // No applicationId — this is a library module
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    // Kotlinx — the only allowed external dependencies in :shared
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.cbor)
    implementation(libs.kotlinx.coroutines.core)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Unit tests only — no Android instrumented tests in :shared
    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

detekt {
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

private val jacocoExcludes = listOf(
    "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
)

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    sourceDirectories.setFrom(files("src/main/kotlin"))
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
            exclude(jacocoExcludes)
        },
    )
    executionData.setFrom(
        fileTree(layout.buildDirectory) { include("jacoco/testDebugUnitTest.exec") },
    )
}

tasks.register<JacocoCoverageVerification>("jacocoCoverageVerification") {
    dependsOn("jacocoTestReport")
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
    sourceDirectories.setFrom(files("src/main/kotlin"))
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
            exclude(jacocoExcludes)
        },
    )
    executionData.setFrom(
        fileTree(layout.buildDirectory) { include("jacoco/testDebugUnitTest.exec") },
    )
}
