plugins{
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_transactions_api"
}

dependencies {
    api(project(":common"))
    api(project(":chains"))
    api(project(":design"))

    api(project(":feature:account:api"))
}