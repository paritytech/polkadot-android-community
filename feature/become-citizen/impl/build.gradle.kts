import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dagger.hilt)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_become_citizen_impl"

    defaultConfig {
        val localProperties = gradleLocalProperties(rootDir, providers)
        buildConfigField(
            "String",
            "REFERRAL_WEB_HOST",
            "\"${localProperties.readSecretOrNull("REFERRAL_WEB_HOST") ?: "referral.example.com"}\""
        )
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    api(project(":feature:become-citizen:api"))
    implementation(project(":tools:ipfs:api"))

    implementation(libs.hilt.android)
    implementation(libs.hilt.androidx.work)
    ksp(libs.hilt.android.compiler)
    ksp(libs.hilt.androidx.compiler)

    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.fragment.ktx)

    implementation(project(":feature:transaction-storage:api"))
    implementation(project(":feature:tokens:api"))
    implementation(project(":feature:vouchers:api"))
    implementation(project(":feature:transfers:api"))
    implementation(project(":feature:videogame:api"))
    implementation(project(":feature:upgrade-username:api"))

    implementation(libs.coil.kt)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.installreferrer)

    implementation(libs.bundles.androidx.media3)
}
