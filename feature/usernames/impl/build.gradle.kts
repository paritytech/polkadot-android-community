plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dagger.hilt)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_usernames_impl"
}

dependencies {
    api(project(":feature:usernames:api"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.androidx.fragment.ktx)

    implementation(project(":chains"))

    implementation(project(":feature:backup:api"))
    implementation(project(":feature:usernames:api"))
    implementation(project(":feature:chain-resources:api"))
    implementation(project(":feature:web3summit:api"))

    implementation(project(":tools:integrity:api"))
    implementation(project(":tools:jwt-auth:api"))
}
