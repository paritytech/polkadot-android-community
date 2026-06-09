plugins {
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_chats_impl"
}

dependencies {
    api(project(":feature:chats:api"))
    implementation(project(":feature:chats:transport-protocol"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    ksp(libs.hilt.androidx.compiler)

    implementation(libs.hilt.androidx.work)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.exifinterface)

    implementation(libs.bundles.androidx.media3)

    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.fragment.ktx)

    implementation(project(":database"))
    implementation(project(":chains"))

    implementation(project(":feature:tokens:api"))
    implementation(project(":feature:wallet:api"))
    implementation(project(":feature:transfers:api"))
    implementation(project(":feature:chain-resources:api"))
    implementation(project(":feature:statement-store:api"))
    implementation(project(":feature:calls:api"))
    implementation(project(":feature:coinage:api"))
    implementation(project(":feature:sso:api"))
    implementation(project(":feature:transaction-storage:api"))
    implementation(project(":feature:scan:api"))

    implementation(project(":tools:push-notifications:api"))

    testImplementation(project(":test-shared"))
}
