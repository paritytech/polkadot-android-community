plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dagger.hilt)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_revive_impl"
}

dependencies {
    api(project(":feature:revive:api"))

    implementation(project(":common"))
    implementation(project(":chains"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(project(":test-shared"))
}
