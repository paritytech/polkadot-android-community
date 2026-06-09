plugins {
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dagger.hilt)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_scan_impl"
}

dependencies {
    api(project(":feature:scan:api"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.fragment.ktx)

    implementation(project(":design"))
}
