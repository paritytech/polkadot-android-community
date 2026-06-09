plugins {
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dagger.hilt)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_backup_impl"

    flavorDimensions += "distribution"

    productFlavors {
        create("gp") { dimension = "distribution" }
        create("vanilla") { dimension = "distribution" }
    }
}

dependencies {
    api(project(":feature:backup:api"))

    implementation(project(":common"))
    implementation(project(":design"))

    implementation(project(":chains"))
    implementation(project(":feature:account:api"))
    implementation(project(":tools:backup:api"))
    implementation(project(":tools:auth:api"))
    implementation(project(":feature:chain-resources:api"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.fragment.ktx)

    testImplementation(project(":test-shared"))
}
