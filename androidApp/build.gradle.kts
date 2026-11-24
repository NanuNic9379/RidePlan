plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.maplibre.compose)
    implementation(libs.valhalla.mobile)
    implementation(libs.valhalla.models.config)
    runtimeOnly(libs.maplibre.compose.runtime.vulkan.android)
}

/**
 * Version number = number of commits on main. It grows with every merge, so
 * Release vN+1 is always "newer" than vN (needed by the update, #30).
 * Without git (e.g. sources from a zip) it starts from 1.
 */
val commitCount: Int = runCatching {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
        .standardOutput.asText.get().trim().toInt()
}.getOrDefault(1)

/**
 * Official Releases (pipeline, #46) start from 100 upwards: so they are always "newer"
 * than any test APK built on a work branch, which only has [commitCount]
 * (branches have more commits than main, where each PR becomes a single commit).
 */
val officialOffset: Int = if (providers.environmentVariable("RIDEPLAN_OFFICIAL_RELEASE").orNull == "true") 100 else 0
val appVersionCode: Int = commitCount + officialOffset

/**
 * Release signing key: it comes only from environment variables (pipeline secrets).
 * Never in the repo. Without it, the release is signed with the debug key and does not update from the Releases.
 */
val releaseKeystore: String? = System.getenv("RIDEPLAN_KEYSTORE_FILE")?.takeIf { file(it).exists() }

android {
    namespace = "io.github.nanunic9379.rideplan"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.nanunic9379.rideplan"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = "0.1.$appVersionCode"
        // Road data for route calculation (#3), "routing-data" Release from routing-data.yml
        buildConfigField("String", "ROUTING_DATA_URL", "\"https://github.com/NanuNic9379/RidePlan/releases/download/routing-data/\"")
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
     * github: Release APKs, with in-app update (#30).
     * fdroid: no in-app update and no permission to install apps (F-Droid does that).
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

    /** One APK per processor: smaller to download. */
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
