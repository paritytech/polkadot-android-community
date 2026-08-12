plugins {
    id("polkadotapp.android.library")
}

android {
    namespace = "io.paritytech.polkadotapp.tools_jwt_auth_api"
}

dependencies {
    api(project(":common"))

    implementation(libs.hilt.android)
}
