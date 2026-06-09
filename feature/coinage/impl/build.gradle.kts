plugins {
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_coinage_impl"
}

dependencies {
    api(project(":feature:coinage:api"))
    api(project(":feature:transactions:api"))
    implementation(project(":feature:account:api"))
    implementation(project(":feature:tokens:api"))
    implementation(project(":feature:transfers:api"))
    implementation(project(":feature:usernames:api"))

    implementation(project(":database"))

    implementation(libs.hilt.android)
    implementation(libs.hilt.androidx.work)
    ksp(libs.hilt.android.compiler)
    ksp(libs.hilt.androidx.compiler)

    implementation(libs.androidx.work.runtime)

    testImplementation(project(":test-shared"))
}
