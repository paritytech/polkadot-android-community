plugins {
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_wallet_api"
}

dependencies {
    api(project(":feature:account:api"))
}