plugins {
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_products_api"
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.androidx.compose.runtime)

    api(project(":feature:chats:api"))
    api(project(":feature:statement-store:api"))
    api(project(":feature:transactions:api"))
    api(project(":common"))
    api(project(":design"))
    api(project(":bindings:bandersnatch-crypto"))
    api(project(":feature:dotns:api"))
    api(project(":chains"))

    implementation(libs.nova.substrate.serialization)
}
