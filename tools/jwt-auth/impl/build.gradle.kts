plugins {
    alias(libs.plugins.dagger.hilt)
}

android {
    namespace = "io.paritytech.polkadotapp.tools_jwt_auth_impl"
}

dependencies {
    api(project(":tools:jwt-auth:api"))
    implementation(project(":tools:integrity:api"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(project(":test-shared"))
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.squareup.retrofit2.core)
}
