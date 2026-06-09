plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_upgrade_username_api"
}

dependencies {
    api(project(":common"))
    api(project(":design"))

    api(project(":feature:chats:api"))
    api(project(":feature:usernames:api"))
}
