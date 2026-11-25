import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
}

group = "io.github.dipeshlama"
version = "1.0.1"

kotlin {
    explicitApi()

    // `./gradlew :barcodescanner:updateKotlinAbi` after an intended public API change.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()

    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "io.github.dipeshlama.barcodescanner"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.ui)

            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.navigationevent.compose)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            implementation(libs.androidx.exifinterface)
            // Bundled model: works offline right after install, and doesn't need the
            // com.google.mlkit.vision.DEPENDENCIES manifest entry that passport-scanner sets.
            implementation(libs.mlkit.barcodeScanning)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    coordinates(group.toString(), "barcode-scanner", version.toString())

    pom {
        name.set("Barcode Scanner")
        description.set(
            "Compose Multiplatform QR code and barcode scanner for Android and iOS: live camera scanning " +
                "inside a frame, flashlight, and gallery import with optional cropping."
        )
        inceptionYear.set("2026")
        url.set("https://github.com/DipeshLama/passportScanner-KMM")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("DipeshLama")
                name.set("Dipesh Lama")
                url.set("https://github.com/DipeshLama")
            }
        }
        scm {
            url.set("https://github.com/DipeshLama/passportScanner-KMM")
            connection.set("scm:git:git://github.com/DipeshLama/passportScanner-KMM.git")
            developerConnection.set("scm:git:ssh://git@github.com/DipeshLama/passportScanner-KMM.git")
        }
    }
}
