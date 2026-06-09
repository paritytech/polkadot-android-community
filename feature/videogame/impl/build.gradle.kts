import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.dagger.hilt)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_videogame_impl"

    defaultConfig {
        val localProperties = gradleLocalProperties(rootDir, providers)
        buildConfigField(
            "String",
            "GAME_RESULTS_FALLBACK_URL",
            "\"${localProperties.readSecretOrNull("GAME_RESULTS_FALLBACK_URL") ?: "https://example.com/"}\""
        )
    }
}

dependencies {
    api(project(":feature:videogame:api"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.fragment.ktx)

    implementation(project(":design"))
    implementation(project(":database"))
    implementation(project(":bindings:airdrop-vrf"))
    implementation(project(":feature:members:api"))
    implementation(project(":feature:people:api"))
    implementation(project(":feature:tokens:api"))
    implementation(project(":feature:xcm:api"))
    implementation(project(":feature:vouchers:api"))
    implementation(project(":feature:transactions:api"))
    implementation(project(":feature:become-citizen:api"))
    implementation(project(":feature:statement-store:api"))
    implementation(project(":feature:chats:api"))
    implementation(project(":tools:media-connection:api"))
    implementation(project(":feature:transfers:api"))
    implementation(project(":tools:push-notifications:api"))
    implementation(project(":feature:upgrade-username:api"))
    implementation(project(":feature:chain-resources:api"))
    implementation(project(":feature:dotns:api"))
    implementation(project(":tools:remoteconfig:api"))
    implementation(project(":feature:usernames:api"))

    implementation(libs.bundles.nova.substrate)

    implementation(libs.androidx.work.runtime)

    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(project(":test-shared"))
}
