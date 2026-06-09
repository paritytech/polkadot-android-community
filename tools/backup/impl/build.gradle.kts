plugins {
    alias(libs.plugins.dagger.hilt)
}

android {
    namespace = "io.paritytech.polkadotapp.tools_backup_impl"

    buildTypes {

        getByName("release") {
            buildConfigField("String", "BACKUP_FILE_SUFFIX", "\"production\"")
            buildConfigField("String", "BACKUP_KEY_SUFFIX", "\"-production\"")
        }

        getByName("debug") {
            buildConfigField("String", "BACKUP_FILE_SUFFIX", "\"debug\"")
            buildConfigField("String", "BACKUP_KEY_SUFFIX", "\"-debug\"")
        }

        getByName("nightly") {
            buildConfigField("String", "BACKUP_FILE_SUFFIX", "\"nightly\"")
            buildConfigField("String", "BACKUP_KEY_SUFFIX", "\"-nightly\"")
        }
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("gp") { dimension = "distribution" }
        create("vanilla") { dimension = "distribution" }
    }
}

dependencies {
    api(project(":tools:backup:api"))

    implementation(project(":chains"))
    implementation(project(":tools:common"))
    implementation(project(":tools:auth:api"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.google.play.services.auth)
    implementation(libs.google.api.client)
    implementation(libs.google.drive)
    implementation(libs.bouncycastle.jdk15)
    implementation(libs.androidx.credentials.core)
    implementation(libs.androidx.credentials.play)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)

}
