plugins {
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dagger.hilt)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_splash_impl"
}

dependencies {
    api(project(":feature:splash:api"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.fragment.ktx)

    implementation(project(":common"))
    implementation(project(":design"))
    implementation(project(":chains"))
    implementation(project(":tools:remoteconfig:api"))

    implementation(project(":feature:account:api"))
    implementation(project(":feature:usernames:api"))
    implementation(project(":feature:statement-store:api"))
    implementation(project(":feature:web3summit:api"))
}
