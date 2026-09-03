// App-side companion to the prebuilt TrUAPI core.
//
// The core itself — per-ABI `libtruapi_server.so` (ws-bridge feature), the
// UniFFI Kotlin bindings and the `io.parity.truapi` host adapter — arrives as
// `io.parity:truapi-host-android`, published to GitHub Packages by
// paritytech/host-rust-core's release-android workflow from one source tree,
// so the bindings and the cdylib cannot drift apart. This module carries only
// what the app owns on top: the OkHttp-backed chain transport and the on-device
// diagnostics suite.
//
// To iterate on an unreleased core, publish it locally from a host-rust-core
// checkout (`make android-publish-local`) and bump `truapiHostAndroid` in
// gradle/libs.versions.toml to the locally published version; `mavenLocal()`
// is already in the repositories.

plugins {
    id("polkadotapp.android.library")
}

android {
    namespace = "io.parity.truapi.app"

    sourceSets {
        getByName("main") { java.srcDirs("src/main/kotlin") }
        getByName("androidTest") { java.srcDirs("src/androidTest/kotlin") }
    }
}

// Source-built checkouts still carry the git-ignored UniFFI bindings and
// per-ABI jniLibs on disk; both directories duplicate the AAR's contents and
// break the build with duplicate classes and libraries, so clear them.
listOf("src/main/kotlin/generated", "src/main/jniLibs")
    .map { project.file(it) }
    .filter { it.exists() }
    .forEach { it.deleteRecursively() }

dependencies {
    // The published core pins JNA 5.14, whose libjnidispatch ELF alignment is
    // below 16K on some ABIs, so it refuses to load on 16KB-page devices
    // (Android 16 images). Exclude it and pin 5.17, which is aligned on every
    // ABI; drop this once the upstream POM requires JNA >= 5.17.
    api(libs.truapi.host.android) {
        exclude(group = "net.java.dev.jna", module = "jna")
    }
    api("net.java.dev.jna:jna:5.17.0@aar")

    // OkHttp WebSocket backs the chain JSON-RPC transport (WebSocketChainProvider).
    implementation(libs.squareup.okhttp3.core)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
