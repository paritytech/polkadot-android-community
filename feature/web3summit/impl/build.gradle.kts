import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_web3summit_impl"

    defaultConfig {
        val localProperties = gradleLocalProperties(rootDir, providers)
        buildConfigField(
            "String",
            "W3S_AUTH_KEY",
            localProperties.readStringSecret("W3S_AUTH_KEY")
        )
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(project(":feature:web3summit:api"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.fragment.ktx)

    implementation(project(":common"))
    implementation(project(":design"))
    implementation(project(":chains"))

    implementation(project(":feature:dotns:api"))
    implementation(project(":feature:people:api"))
    implementation(project(":feature:products:api"))
    implementation(project(":feature:revive:api"))
    implementation(project(":feature:transactions:api"))

    implementation(project(":tools:remoteconfig:api"))

    implementation(libs.web3j.abi)

    testImplementation(project(":test-shared"))
}
