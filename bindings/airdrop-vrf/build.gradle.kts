plugins {
    alias(libs.plugins.mozilla.rust.android)
}

android {
    namespace = "io.paritytech.polkadotapp.airdrop_vrf"
    ndkVersion = "29.0.14206865"
}

dependencies {
    implementation(project(":common"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    // Derives a valid 96-byte sr25519 keypair for the on-device known-answer test, the same way
    // production sources it from the wallet key.
    androidTestImplementation(libs.nova.substrate.sdk)
}

cargo {
    module = "rust/"
    libname = "airdrop_vrf_java"
    targets = listOf("arm", "arm64", "x86", "x86_64")
    profile = "release"
}

tasks.matching { it.name.matches("merge.*JniLibFolders".toRegex()) }.configureEach {
    inputs.dir(layout.buildDirectory.dir("rustJniLibs/android"))
    dependsOn("cargoBuild")
}
