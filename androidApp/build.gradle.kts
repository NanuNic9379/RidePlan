plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    runtimeOnly(libs.maplibre.compose.runtime.vulkan.android)
}

android {
    namespace = "io.github.nanunic9379.rideplan"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.nanunic9379.rideplan"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        getByName("release") { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    dependenciesInfo {
        // F-Droid: niente blocco di metadati cifrato di Google nell'APK
        includeInApk = false
        includeInBundle = false
    }
}
