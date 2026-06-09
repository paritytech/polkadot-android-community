plugins {
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.paritytech.polkadotapp.feature_products_impl"
}

val containerDir = rootProject.file("feature/products/product-container")
val containerOutput = containerDir.resolve("dist/container.js")
val assetsDir = project.file("src/main/assets")
val isWindows = org.gradle.internal.os.OperatingSystem.current().isWindows

fun Exec.runMultiplatformCommand(command: String) {
    if (isWindows) {
        commandLine("cmd", "/c", command)
    } else {
        commandLine("bash", "-lc", command)
    }
}

val npmInstallContainer by tasks.registering(Exec::class) {
    workingDir = containerDir
    runMultiplatformCommand("npm install")

    inputs.file(containerDir.resolve("package.json"))
    inputs.file(containerDir.resolve("package-lock.json"))
    outputs.dir(containerDir.resolve("node_modules"))
}

val buildContainerScript by tasks.registering(Exec::class) {
    dependsOn(npmInstallContainer)
    workingDir = containerDir
    runMultiplatformCommand("npm run build")

    inputs.dir(containerDir.resolve("src"))
    outputs.file(containerOutput)
}

val copyContainerScript by tasks.registering(Copy::class) {
    dependsOn(buildContainerScript)
    from(containerOutput)
    into(assetsDir)
}

afterEvaluate {
    tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
        dependsOn(copyContainerScript)
    }
    tasks.matching { "lint" in it.name.lowercase() }.configureEach {
        dependsOn(copyContainerScript)
    }
}

dependencies {
    api(project(":feature:products:api"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.fragment.ktx)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.nova.substrate.serialization)

    implementation(project(":common"))
    implementation(project(":tools:ipfs:api"))
    implementation(project(":design"))
    implementation(project(":database"))
    implementation(project(":chains"))
    implementation(project(":feature:chats:api"))
    implementation(project(":feature:account:api"))
    implementation(project(":feature:transaction-storage:api"))
    implementation(project(":feature:transactions:api"))
    implementation(project(":feature:statement-store:api"))
    implementation(project(":feature:pgas:api"))
    implementation(project(":feature:balances:api"))
    implementation(project(":feature:people:api"))
    implementation(project(":feature:usernames:api"))
    implementation(project(":feature:dotns:api"))
    implementation(project(":feature:coinage:api"))

    implementation(libs.squareup.okhttp3.core)

    testImplementation(project(":test-shared"))

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.google.gson)
}
