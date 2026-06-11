import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    alias(libs.plugins.dagger.hilt)
}

android {
    namespace = "io.paritytech.polkadotapp.tools_auth_impl"
    val localProperties = gradleLocalProperties(rootDir, providers)

    flavorDimensions += "distribution"

    productFlavors {
        create("gp") {
            dimension = "distribution"
            buildConfigField(
                "String",
                "GOOGLE_OAUTH_ID",
                localProperties.readStringSecret("GOOGLE_OAUTH_ID")
            )
        }
        create("vanilla") {
            dimension = "distribution"
            buildConfigField("String", "GOOGLE_OAUTH_ID", "\"\"")
        }
    }
}

dependencies {
    api(project(":tools:auth:api"))

    implementation(project(":tools:common"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    "gpImplementation"(libs.google.api.client)
    "gpImplementation"(libs.google.play.services.auth)
    "gpImplementation"(platform(libs.firebase.bom))
    "gpImplementation"(libs.firebase.auth)
}
