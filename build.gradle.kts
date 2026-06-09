import com.android.build.gradle.BaseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.mozilla.rust.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.dagger.hilt) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.detekt)
}

subprojects {
    if (!file("$projectDir/build.gradle.kts").exists()) {
        apply(plugin = "base")
        return@subprojects
    }

    val isApp = project.name == "app"
    val pluginId = if (isApp) {
        rootProject.libs.plugins.android.application.get().pluginId
    } else {
        rootProject.libs.plugins.android.library.get().pluginId
    }

    pluginManager.apply(pluginId)
    pluginManager.apply(rootProject.libs.plugins.kotlin.android.get().pluginId)
    pluginManager.apply(rootProject.libs.plugins.kotlin.ksp.get().pluginId)
    pluginManager.apply(rootProject.libs.plugins.detekt.get().pluginId)

    extensions.configure<DetektExtension> {
        config.setFrom(rootProject.file("config/detekt/config.yml"))
        parallel = true
        autoCorrect = true
    }

    tasks.withType<Detekt>().configureEach {
        reports {
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
            sarif.required.set(false)
            md.required.set(false)
        }
    }

    dependencies {
        detektPlugins(rootProject.libs.detekt.formatting)
    }

    configure<BaseExtension> {
        compileSdkVersion(36)

        defaultConfig {
            minSdk = 29
            targetSdk = 36

            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        buildFeatures.buildConfig = true
        buildFeatures.viewBinding = true

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }

        packagingOptions {
            resources.excludes.add("META-INF/INDEX.LIST")
            resources.excludes.add("META-INF/DEPENDENCIES")
        }

        buildTypes {
            getByName("debug") {
                isShrinkResources = false
                isMinifyEnabled = false
            }
            getByName("release") {
//                isShrinkResources = isApp
//                isMinifyEnabled = isApp
                isShrinkResources = false
                isMinifyEnabled = false

                lintOptions {
                    isAbortOnError = false
                }
            }
            create("nightly") {
                initWith(getByName("debug"))
            }
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-Xcontext-receivers")
            freeCompilerArgs.add("-XXLanguage:+ExplicitBackingFields")
            freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")

            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
}
