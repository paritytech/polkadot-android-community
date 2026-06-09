plugins {
    alias(libs.plugins.dagger.hilt)
}

android {
    namespace = "io.paritytech.polkadotapp.tools_ipfs_impl"
}

dependencies {
    api(project(":tools:ipfs:api"))

    implementation(project(":common"))
    implementation(project(":tools:remoteconfig:api"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.bundles.squareup.okhttp3)
}
