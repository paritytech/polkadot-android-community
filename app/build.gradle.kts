import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.google.services)
    alias(libs.plugins.sentry.android.gradle)
}

val localProperties = gradleLocalProperties(rootDir, providers)

android {
    namespace = "io.paritytech.polkadotapp.app"

    defaultConfig {
        applicationId = "io.paritytech.polkadotapp"

        versionCode = computeVersionCode()
        versionName = computeVersionName()

        manifestPlaceholders["sentryDsn"] = localProperties.readSecretOrNull("SENTRY_DSN") ?: ""

        buildConfigField(
            "String",
            "LOG_COLLECTION_EMAIL",
            "\"${localProperties.readSecretOrNull("LOG_COLLECTION_EMAIL") ?: "logs@example.com"}\""
        )
    }

    signingConfigs {
        create("dev") {
            storeFile = file(localProperties.readSecretOrNull("DEV_KEYSTORE_FILE") ?: "../develop_key.jks")
            keyPassword = localProperties.readSecret("CI_KEYSTORE_KEY_PASS")
            keyAlias = localProperties.readSecret("CI_KEYSTORE_KEY_ALIAS")
            storePassword = localProperties.readSecret("CI_KEYSTORE_PASS")
        }

        create("release") {
            storeFile = file(localProperties.readSecretOrNull("RELEASE_KEYSTORE_FILE") ?: "../release_key.jks")
            keyPassword = localProperties.readSecret("RELEASE_KEYSTORE_KEY_PASS")
            keyAlias = localProperties.readSecret("RELEASE_KEYSTORE_KEY_ALIAS")
            storePassword = localProperties.readSecret("RELEASE_KEYSTORE_PASS")
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")

            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("dev")
            applicationIdSuffix = ".debug"

            buildConfigField("String", "BuildType", "\"debug\"")
        }
        getByName("nightly") {
            matchingFallbacks.add("debug")

            signingConfig = signingConfigs.getByName("dev")
            applicationIdSuffix = ".nightly"
        }
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("gp") { dimension = "distribution" }
        create("vanilla") { dimension = "distribution" }
    }
}

dependencies {
    implementation(libs.hilt.android)
    implementation(libs.hilt.androidx.work)
    ksp(libs.hilt.android.compiler)
    ksp(libs.hilt.androidx.compiler)

    implementation(libs.androidx.appcompat)

    implementation(libs.bundles.androidx.navigation)

    implementation(libs.chrisbanes.insetter)

    implementation(libs.kirich.viewbinding)

    implementation(libs.coil.kt)

    implementation(project(":common"))
    implementation(project(":design"))
    implementation(project(":chains"))
    implementation(project(":database"))

    // Region features
    implementation(project(":feature:backup:impl"))
    implementation(project(":feature:xcm:impl"))
    implementation(project(":feature:fund:impl"))
    implementation(project(":feature:swap:impl"))
    implementation(project(":feature:chats:impl"))
    implementation(project(":feature:device-sync:impl"))
    implementation(project(":feature:people:impl"))
    implementation(project(":feature:members:impl"))
    implementation(project(":feature:wallet:impl"))
    implementation(project(":feature:tokens:impl"))
    implementation(project(":feature:splash:impl"))
    implementation(project(":feature:wallet:impl"))
    implementation(project(":feature:prices:impl"))
    implementation(project(":feature:account:impl"))
    implementation(project(":feature:vouchers:impl"))
    implementation(project(":feature:settings:impl"))
    implementation(project(":feature:balances:impl"))
    implementation(project(":feature:transfers:impl"))
    implementation(project(":feature:usernames:impl"))
    implementation(project(":feature:videogame:impl"))
    implementation(project(":feature:transactions:impl"))
    implementation(project(":feature:become-citizen:impl"))
    implementation(project(":feature:statement-store:impl"))
    implementation(project(":feature:transaction-storage:impl"))
    implementation(project(":feature:pgas:impl"))
    implementation(project(":feature:chain-resources:impl"))
    implementation(project(":feature:cross-chain-transfers:impl"))
    implementation(project(":feature:sso:impl"))
    implementation(project(":feature:scan:impl"))
    implementation(project(":feature:mobrules:api"))
    implementation(project(":feature:mobrules:impl"))
    implementation(project(":feature:products:impl"))
    implementation(project(":feature:upgrade-username:impl"))
    implementation(project(":feature:calls:api"))
    implementation(project(":feature:calls:impl"))
    implementation(project(":feature:coinage:impl"))
    implementation(project(":feature:dotns:impl"))
    implementation(project(":feature:connection-status:api"))
    implementation(project(":feature:connection-status:impl"))
    implementation(project(":feature:revive:impl"))
    implementation(project(":feature:web3summit:api"))
    implementation(project(":feature:web3summit:impl"))
    implementation(project(":feature:w3s-pay:impl"))
    // Endregion features

    // Region tools
    implementation(project(":tools:auth:impl"))
    implementation(project(":tools:backup:impl"))
    implementation(project(":tools:integrity:impl"))
    implementation(project(":tools:jwt-auth:impl"))
    implementation(project(":tools:biometrics:impl"))
    implementation(project(":tools:remoteconfig:impl"))
    implementation(project(":tools:ipfs:impl"))
    implementation(project(":tools:assethub-sdk:impl"))
    implementation(project(":tools:hydration-sdk:impl"))
    implementation(project(":tools:push-notifications:impl"))
    implementation(project(":tools:media-connection:impl"))
    implementation(project(":tools:media-connection:api"))
    // Endregion tools

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
}


sentry {
    org.set(localProperties.readSecretOrNull("SENTRY_ORG") ?: "your-sentry-org")
    projectName.set(localProperties.readSecretOrNull("SENTRY_PROJECT") ?: "your-sentry-project")

    // this will upload your source code to Sentry to show it as part of the stack traces
    // disable if you don't want to expose your sources
    includeSourceContext.set(true)

    // Sentry only runs on debug/nightly; skip release so the plugin
    // doesn't try to process a variant that has no DSN/auth token
    ignoredBuildTypes.set(setOf("release"))
}
