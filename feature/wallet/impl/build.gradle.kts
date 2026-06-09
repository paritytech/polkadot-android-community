plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dagger.hilt)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_wallet_impl"
}

dependencies {
    api(project(":feature:wallet:api"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.fragment.ktx)

    implementation(libs.nova.substrate.sdk)

    implementation(project(":common"))
    implementation(project(":chains"))
    implementation(project(":design"))

    implementation(project(":feature:chats:api"))
    implementation(project(":feature:tokens:api"))
    implementation(project(":feature:balances:api"))
    implementation(project(":feature:transfers:api"))
    implementation(project(":feature:usernames:api"))
    implementation(project(":feature:transactions:api"))
    implementation(project(":feature:prices:api"))
    implementation(project(":feature:coinage:api"))
    implementation(project(":feature:scan:api"))
    implementation(project(":feature:fund:api"))
    implementation(project(":feature:videogame:api"))
}
