import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    androidLibrary {
        namespace = "io.github.nanunic9379.rideplan.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
        androidResources { enable = true }
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.maplibre.compose)
            implementation(libs.qrose)
            implementation(libs.kotlinx.serialization.json)
            // Nostr signature check (#3). The native part comes from the app (jni-android) or,
            // in host tests, from jni-jvm below.
            implementation(libs.secp256k1.kmp)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.secp256k1.jni.jvm)
        }
    }
}

compose.resources {
    packageOfResClass = "io.github.nanunic9379.rideplan.resources"
}
