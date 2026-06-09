import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    alias(libs.plugins.dagger.hilt)
}

android {
    namespace = "io.paritytech.polkadotapp.tools_auth_impl"
    val localProperties = gradleLocalProperties(rootDir, providers)

    defaultConfig {
        buildConfigField(
            "String",
            "GOOGLE_OAUTH_ID",
            localProperties.readStringSecret("GOOGLE_OAUTH_ID")
        )
    }
}

dependencies {
    api(project(":tools:auth:api"))

    implementation(project(":tools:common"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.google.api.client)
    implementation(libs.google.play.services.auth)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)

}
