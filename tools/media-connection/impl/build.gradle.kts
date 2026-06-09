plugins {
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.paritytech.polkadotapp.tools_media_connection_impl"
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(project(":tools:media-connection:api"))
    implementation(project(":tools:jwt-auth:api"))
    implementation(project(":common"))

    implementation(libs.bundles.webrtc)

    testImplementation(libs.junit)
}
