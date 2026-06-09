import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_transactions_impl"

    defaultConfig {
        val localProperties = gradleLocalProperties(rootDir, providers)
        buildConfigField(
            "String",
            "NIGHTLY_FUNDING_MNEMONIC",
            "\"${localProperties.readSecretOrNull("NIGHTLY_FUNDING_MNEMONIC") ?: "bottom drive obey lake curtain smoke basket hold race lonely fit walk"}\""
        )
    }
}

dependencies {
    api(project(":feature:transactions:api"))

    implementation(libs.hilt.android)
    implementation(libs.hilt.androidx.work)
    ksp(libs.hilt.android.compiler)
    ksp(libs.hilt.androidx.compiler)

    implementation(libs.androidx.work.runtime)

    implementation(project(":database"))

    implementation(libs.nova.substrate.sdk)

    testImplementation(project(":test-shared"))
}
