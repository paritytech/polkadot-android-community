plugins {
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dagger.hilt)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_upgrade_username_impl"
}

dependencies {
    api(project(":feature:upgrade-username:api"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.androidx.fragment.ktx)

    implementation(project(":feature:people:api"))
    implementation(project(":feature:chain-resources:api"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(project(":test-shared"))
}
