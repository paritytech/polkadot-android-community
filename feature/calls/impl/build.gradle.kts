plugins {
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_calls_impl"
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(project(":feature:calls:api"))
    implementation(project(":feature:chats:api"))
    implementation(project(":tools:media-connection:api"))

    implementation(project(":common"))
    implementation(project(":design"))

    testImplementation(project(":tools:media-connection:impl"))
    testImplementation(libs.junit)
}
