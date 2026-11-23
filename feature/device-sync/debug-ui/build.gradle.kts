plugins {
    id("polkadotapp.android.library")
    id("polkadotapp.android.compose")
    id("polkadotapp.android.hilt")
}

android {
    namespace = "io.paritytech.polkadotapp.feature_device_sync_debug_ui"
}

dependencies {
    implementation(project(":feature:device-sync:api"))

    implementation(project(":common"))
    implementation(project(":design"))
}
