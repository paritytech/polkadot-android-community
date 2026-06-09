import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    alias(libs.plugins.dagger.hilt)
}

android {
    namespace = "io.paritytech.polkadotapp.tools_integrity_impl"
    val localProperties = gradleLocalProperties(rootDir, providers)

    defaultConfig {
        buildConfigField(
            "long",
            "GOOGLE_PROJECT_ID",
            localProperties.readSecret("GOOGLE_PROJECT_ID")
        )
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("gp") { dimension = "distribution" }
        create("vanilla") { dimension = "distribution" }
    }
}

dependencies {
    api(project(":tools:integrity:api"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.google.integrity)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.integrity)
    implementation(libs.firebase.integrity.debug)
}
