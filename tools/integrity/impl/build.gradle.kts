import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    id("polkadotapp.android.library")
    id("polkadotapp.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.paritytech.polkadotapp.tools_integrity_impl"
    val localProperties = gradleLocalProperties(rootDir, providers)

    flavorDimensions += "distribution"

    productFlavors {
        create("gp") {
            dimension = "distribution"
            buildConfigField(
                "long",
                "GOOGLE_PROJECT_ID",
                localProperties.readSecretOrDefault("GOOGLE_PROJECT_ID", "0")
            )
        }
        create("vanilla") {
            dimension = "distribution"
            buildConfigField("long", "GOOGLE_PROJECT_ID", "0L")
        }
    }
}

dependencies {
    api(project(":tools:integrity:api"))

    implementation(libs.google.integrity)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
