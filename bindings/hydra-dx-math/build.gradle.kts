plugins {
    alias(libs.plugins.mozilla.rust.android)
}

android {
    namespace = "io.paritytech.polkadotapp.hydra_dx_math"
    ndkVersion = "29.0.14206865"
}

dependencies {
    implementation(project(":common"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}

cargo {
    module = "rust/"
    libname = "hydra_dx_math_java"
    targets = listOf("arm", "arm64", "x86", "x86_64")
    profile = "release"
}

tasks.matching { it.name.matches("merge.*JniLibFolders".toRegex()) }.configureEach {
    inputs.dir(layout.buildDirectory.dir("rustJniLibs/android"))
    dependsOn("cargoBuild")
}
