import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
}

group = "io.github.dipeshlama"
version = "1.1.0"

kotlin {
    explicitApi()

    // `./gradlew :passportscanner:updateKotlinAbi` after an intended public API change.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()

    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "io.github.dipeshlama.passportscanner"
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
            api(libs.kotlinx.datetime)

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
            implementation(libs.mlkit.textRecognition)
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

    coordinates(group.toString(), "passport-scanner", version.toString())

    pom {
        name.set("Passport Scanner")
        description.set(
            "Compose Multiplatform passport MRZ scanner for Android and iOS: live camera scanning, " +
                "gallery import and a check-digit validated ICAO 9303 TD3 parser."
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
