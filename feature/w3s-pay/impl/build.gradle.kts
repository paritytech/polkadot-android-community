plugins {
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_w3spay_impl"
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.nova.substrate.sdk)

    implementation(project(":common"))
    implementation(project(":chains"))

    implementation(project(":feature:wallet:api"))
    implementation(project(":feature:scan:api"))
    implementation(project(":feature:account:api"))
    implementation(project(":feature:tokens:api"))
    implementation(project(":feature:coinage:api"))
    implementation(project(":feature:statement-store:api"))

    implementation(project(":tools:remoteconfig:api"))

    testImplementation(project(":test-shared"))
}
