package io.github.nanunic9379.rideplan.personal

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A piece of road data on the phone (#21): a region, or all the cells around your rides. */
data class MapDataItem(val id: String, val bytes: Long, val isCells: Boolean)

/** What only the platform can do for the personal area (#21). Every action is optional. */
@Stable
class PlatformActions(
    val versionName: String,
    /** Opens a web page in the browser: the only way the personal area goes online. */
    val openUrl: (String) -> Unit,
    /** "Export all rides": the user chooses where to save a .zip of GPX files. */
    val exportAll: (() -> Unit)? = null,
    /** Language of the app; null hides the choice (Android before 13 follows the phone). */
    val language: LanguageActions? = null,
    val mapData: MapDataActions? = null,
    /** "Delete all data": what lives outside the profile file (map view, road data, shared files). */
    val wipeDevice: () -> Unit = {},
    /** The update card of Info & updates; null where the store handles updates (F-Droid). */
    val updates: UpdateActions? = null,
    /** Country of the phone (ISO code, "US"), for the automatic units (#15); null = from the locale. */
    val region: String? = null,
) {
    /** Outcome of the last export, to show on the Data & privacy page. */
    var exportNotice by mutableStateOf<Boolean?>(null)
}

class LanguageActions(
    /** BCP 47 tag of the chosen language, null = follow the phone. */
    val current: () -> String?,
    val set: (String?) -> Unit,
)

class MapDataActions(
    val list: () -> List<MapDataItem>,
    val delete: (MapDataItem) -> Unit,
    val freeBytes: () -> Long,
)

object Links {
    const val SOURCE = "https://github.com/NanuNic9379/RidePlan"
    const val LICENCES = "https://github.com/NanuNic9379/RidePlan/blob/main/LICENSE"
    const val PRIVACY = "https://github.com/NanuNic9379/RidePlan/blob/main/docs/PRIVACY.md"
    const val COFFEE = "https://buymeacoffee.com/nanunic9379"
}

/** Where the last update check got to (#30). */
enum class UpdateCheck { IDLE, CHECKING, LATEST, AVAILABLE, FAILED }

/** "Check now" on Info & updates: [check] asks GitHub and, if there is one, brings back the banner. */
@Stable
class UpdateActions(val check: () -> Unit) {
    var status by mutableStateOf(UpdateCheck.IDLE)
    /** The newer version found, when [status] is AVAILABLE. */
    var available by mutableStateOf<String?>(null)
}
