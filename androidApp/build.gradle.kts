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

/**
 * Numero di versione = numero di commit su main. Cresce a ogni merge, quindi
 * la Release vN+1 e' sempre "piu' nuova" della vN (serve all'aggiornamento, #30).
 * Senza git (es. sorgenti in zip) si parte da 1.
 */
val commitCount: Int = runCatching {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
        .standardOutput.asText.get().trim().toInt()
}.getOrDefault(1)

/**
 * Chiave di firma delle Release: arriva solo da variabili d'ambiente (secret della pipeline).
 * Mai nel repo. Senza, la release si firma con la chiave debug e non si aggiorna dalle Release.
 */
val releaseKeystore: String? = System.getenv("RIDEPLAN_KEYSTORE_FILE")?.takeIf { file(it).exists() }

android {
    namespace = "io.github.nanunic9379.rideplan"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.nanunic9379.rideplan"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = commitCount
        versionName = "0.1.$commitCount"
    }

    buildFeatures { buildConfig = true }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("RIDEPLAN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RIDEPLAN_KEY_ALIAS")
                keyPassword = System.getenv("RIDEPLAN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    /**
     * github: APK delle Release, con aggiornamento dall'app (#30).
     * fdroid: senza aggiornamento interno ne' permesso di installare app (lo fa F-Droid).
     */
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField("String", "UPDATE_REPO", "\"NanuNic9379/RidePlan\"")
        }
        create("fdroid") {
            dimension = "distribution"
        }
    }

    /** Un APK per processore: piu' piccolo da scaricare. */
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}
