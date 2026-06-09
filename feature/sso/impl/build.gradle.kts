plugins {
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_sso_impl"
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.fragment.ktx)

    implementation(project(":design"))
    implementation(project(":database"))

    implementation(project(":feature:sso:api"))
    implementation(project(":feature:scan:api"))
    implementation(project(":feature:account:api"))
    implementation(project(":feature:usernames:api"))
    implementation(project(":feature:statement-store:api"))
    implementation(project(":feature:products:api"))
    implementation(project(":feature:chats:api"))
    implementation(project(":feature:chain-resources:api"))

    testImplementation(project(":test-shared"))
}
