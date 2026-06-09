plugins {
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.paritytech.polkadotapp.common"

    defaultConfig {
        buildConfigField("String", "TESTNET_ENVIRONMENT", "\"TESTNET\"")
        buildConfigField("boolean", "ALLOW_SHORT_EVIDENCE_VIDEO", "true")
        buildConfigField("boolean", "DIM1_ENABLED", "true")
        buildConfigField("boolean", "FAQ_ENABLED", "true")
        buildConfigField("boolean", "COINAGE_WIDGETS_ENABLED", "true")
        buildConfigField("boolean", "TESTNET_FUND_ENABLED", "true")
        buildConfigField("boolean", "PEER_BOT_BY_DEFAULT", "true")
        buildConfigField("boolean", "DIM1_BOT_BY_DEFAULT", "true")
        buildConfigField("boolean", "SAMPLE_BOT", "true")
    }

    buildTypes {
        getByName("release") {
            initWith(getByName("release"))
            buildConfigField("String", "TESTNET_ENVIRONMENT", "\"PRODUCTION\"")
            buildConfigField("boolean", "ALLOW_SHORT_EVIDENCE_VIDEO", "false")
            buildConfigField("boolean", "DIM1_ENABLED", "false")
            buildConfigField("boolean", "FAQ_ENABLED", "false")
            buildConfigField("boolean", "COINAGE_WIDGETS_ENABLED", "false")
            buildConfigField("boolean", "TESTNET_FUND_ENABLED", "false")
            buildConfigField("boolean", "PEER_BOT_BY_DEFAULT", "false")
            buildConfigField("boolean", "DIM1_BOT_BY_DEFAULT", "false")
            buildConfigField("boolean", "SAMPLE_BOT", "false")
        }
        getByName("nightly") {
            buildConfigField("String", "TESTNET_ENVIRONMENT", "\"NIGHTLY\"")
            buildConfigField("boolean", "ALLOW_SHORT_EVIDENCE_VIDEO", "false")
            buildConfigField("boolean", "PEER_BOT_BY_DEFAULT", "false")
            buildConfigField("boolean", "DIM1_BOT_BY_DEFAULT", "false")
            buildConfigField("boolean", "SAMPLE_BOT", "false")
        }
    }
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(project(":design"))

    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.exifinterface)
    implementation(libs.bundles.androidx.navigation)

    api(libs.androidx.appcompat)
    api(libs.google.android.material)

    api(libs.google.gson)
    api(libs.bundles.nova.substrate)
    api(libs.jakewharton.timber)

    api(libs.bundles.androidx.lifecycle)

    api(libs.bundles.androidx.camera)
    api(libs.google.play.services.mlkit)

    api(libs.kotlinx.collections.immutable)

    implementation(libs.bouncycastle.jdk15)

    implementation(libs.bundles.squareup.okhttp3)
    api(libs.squareup.retrofit2.core)
    implementation(libs.bundles.squareup.retrofit2.converters)

    implementation(libs.neovisionaries.websocket)
    implementation(libs.chrisbanes.insetter)

    implementation(libs.bundles.androidx.media3)

    api(libs.coil.kt)
    implementation(libs.coil.svg)
    implementation(libs.coil.video)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)

    testImplementation(project(":test-shared"))
}
