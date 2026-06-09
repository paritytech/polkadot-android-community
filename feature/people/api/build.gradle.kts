plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_people_api"
}

dependencies {
    api(project(":common"))
    api(project(":chains"))
    api(project(":design"))
    api(project(":bindings:bandersnatch-crypto"))
    api(project(":feature:account:api"))
    api(project(":feature:transactions:api"))
    api(project(":feature:members:api"))
}
