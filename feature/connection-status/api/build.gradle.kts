plugins {
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_connection_status_api"
}

dependencies {
    api(project(":common"))
    api(project(":design"))
    api(project(":chains"))
}
