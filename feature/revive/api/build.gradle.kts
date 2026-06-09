android {
    namespace = "io.paritytech.polkadotapp.feature_revive_api"
}

dependencies {
    api(project(":common"))
    api(project(":chains"))

    implementation(libs.bouncycastle.jdk15)
}
