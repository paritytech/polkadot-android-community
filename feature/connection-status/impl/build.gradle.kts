plugins {
    alias(libs.plugins.dagger.hilt)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_connection_status_impl"
}

dependencies {
    api(project(":feature:connection-status:api"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
}
