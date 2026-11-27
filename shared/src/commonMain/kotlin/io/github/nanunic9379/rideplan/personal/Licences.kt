package io.github.nanunic9379.rideplan.personal

import androidx.compose.runtime.Composable
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.info_licences
import io.github.nanunic9379.rideplan.resources.licences_app
import io.github.nanunic9379.rideplan.resources.licences_app_text
import io.github.nanunic9379.rideplan.resources.licences_data
import io.github.nanunic9379.rideplan.resources.licences_libraries
import io.github.nanunic9379.rideplan.ride.RideScreenState
import org.jetbrains.compose.resources.stringResource

/**
 * The projects RidePlan is built on and the data it uses, with their licences (#131).
 * Every library of gradle/libs.versions.toml shipped in the app must be listed here by its
 * Maven group: data/test_licences.py checks it, so a new dependency cannot be forgotten.
 */
object Licences {
    data class Entry(val name: String, val licence: String, val url: String, val group: String = "")

    val libraries = listOf(
        Entry("Kotlin, kotlinx.serialization", "Apache-2.0", "https://github.com/JetBrains/kotlin", "org.jetbrains.kotlinx"),
        Entry("Compose Multiplatform", "Apache-2.0", "https://github.com/JetBrains/compose-multiplatform", "org.jetbrains.compose"),
        Entry("AndroidX Activity", "Apache-2.0", "https://developer.android.com/jetpack/androidx", "androidx.activity"),
        Entry("AndroidX WorkManager", "Apache-2.0", "https://developer.android.com/jetpack/androidx", "androidx.work"),
        Entry("MapLibre Compose", "BSD-3-Clause", "https://github.com/maplibre/maplibre-compose", "org.maplibre.compose"),
        Entry("MapLibre Native", "BSD-2-Clause", "https://github.com/maplibre/maplibre-native"),
        Entry("Valhalla", "MIT", "https://github.com/valhalla/valhalla"),
        Entry("valhalla-mobile", "MIT", "https://github.com/Rallista/valhalla-mobile", "io.github.rallista"),
        Entry("secp256k1-kmp", "Apache-2.0", "https://github.com/ACINQ/secp256k1-kmp", "fr.acinq.secp256k1"),
        Entry("libsecp256k1", "MIT", "https://github.com/bitcoin-core/secp256k1"),
        Entry("OkHttp, Okio", "Apache-2.0", "https://github.com/square/okhttp", "com.squareup.okhttp3"),
        Entry("QRose", "MIT", "https://github.com/alexzhirkevich/qrose", "io.github.alexzhirkevich"),
    )

    val data = listOf(
        Entry("OpenStreetMap contributors", "ODbL 1.0", "https://www.openstreetmap.org/copyright"),
        Entry("OpenFreeMap", "OpenFreeMap terms, OpenMapTiles", "https://openfreemap.org"),
        Entry("Geofabrik extracts", "ODbL 1.0", "https://download.geofabrik.de"),
        Entry("Natural Earth", "Public domain", "https://www.naturalearthdata.com"),
    )
}

/** Settings > About > Licences: RidePlan's own licence, then the libraries and the data. */
@Composable
fun LicencesPage(rides: RideScreenState, platform: PlatformActions?) {
    val open = platform?.openUrl
    SubPage(stringResource(Res.string.info_licences), onBack = { rides.back() }) {
        RowGroup {
            NavRow(null, stringResource(Res.string.licences_app), stringResource(Res.string.licences_app_text), onClick = open?.let { { it(Links.LICENCES) } })
        }
        LicenceGroup(stringResource(Res.string.licences_libraries), Licences.libraries, open)
        LicenceGroup(stringResource(Res.string.licences_data), Licences.data, open)
    }
}

@Composable
private fun LicenceGroup(title: String, entries: List<Licences.Entry>, open: ((String) -> Unit)?) {
    SectionLabel(title)
    RowGroup {
        entries.forEachIndexed { i, e ->
            if (i > 0) GroupDivider()
            NavRow(null, e.name, e.licence, onClick = open?.let { { it(e.url) } })
        }
    }
}
