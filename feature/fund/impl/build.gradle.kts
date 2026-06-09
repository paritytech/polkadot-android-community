plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dagger.hilt)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_fund_impl"
}

dependencies {
    api(project(":feature:fund:api"))

    implementation(project(":design"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.fragment.ktx)

    implementation(project(":feature:swap:api"))
    implementation(project(":feature:tokens:api"))
    implementation(project(":feature:balances:api"))
    implementation(project(":feature:wallet:api"))
    implementation(project(":feature:coinage:api"))
}
