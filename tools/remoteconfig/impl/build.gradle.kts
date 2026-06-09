plugins {
    alias(libs.plugins.dagger.hilt)
}

android {
    namespace = "io.paritytech.polkadotapp.tools_remoteconfig_impl"
}

dependencies {
    api(project(":tools:remoteconfig:api"))

    implementation(project(":tools:common"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.remote.config)
}