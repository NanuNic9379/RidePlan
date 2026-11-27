package io.github.nanunic9379.rideplan.personal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.nanunic9379.rideplan.profile.BackupActions
import io.github.nanunic9379.rideplan.profile.ProfileStore
import io.github.nanunic9379.rideplan.ride.Dest
import io.github.nanunic9379.rideplan.ride.ImportActions
import io.github.nanunic9379.rideplan.ride.Page
import io.github.nanunic9379.rideplan.ride.RideScreenState

/**
 * The personal area (#21): the Rides and You tabs with the bottom bar, and the pages opened
 * on top of them (two levels at most below a tab). Back always goes one level up.
 */
@Composable
fun PersonalArea(
    store: ProfileStore,
    rides: RideScreenState,
    importActions: ImportActions?,
    backup: BackupActions?,
    platform: PlatformActions?,
    snackbar: SnackbarHostState,
) {
    val top = rides.stack.lastOrNull()
    if (top != null) {
        Surface(Modifier.fillMaxSize()) {
            when (top) {
                is Dest.RideDetail -> RideDetailPage(store, rides, importActions, top.id, snackbar, platform)
                Dest.EditProfile -> EditProfilePage(store, rides)
                Dest.Stats -> StatsPage(store, rides)
                Dest.Garage -> GaragePage(store, rides)
                is Dest.BikePage -> BikePage(store, rides, top.id)
                Dest.Log -> LogPage(store, rides)
                Dest.Zones -> ZonesPage(store, rides)
                Dest.PhotoSpots -> PhotoSpotsPage(store, rides, platform?.openUrl, platform?.nostr, importActions?.shareText)
                Dest.Privacy -> PrivacyPage(store, rides, backup, platform)
                Dest.DeleteAll -> DeleteAllPage(store, rides, backup, platform)
                Dest.Settings -> SettingsPage(store, rides, platform)
                Dest.Language -> LanguagePage(rides, platform)
                Dest.MapData -> MapDataPage(rides, platform)
                Dest.Info -> InfoPage(store, rides, platform)
                Dest.Licences -> LicencesPage(rides, platform)
                Dest.Nostr -> platform?.nostr?.let { io.github.nanunic9379.rideplan.social.NostrSettingsPage(it, rides, platform.openUrl) }
            }
        }
        return
    }
    Scaffold(
        bottomBar = { MainNavBar(rides) },
        floatingActionButton = {
            if (rides.page == Page.HOME && importActions != null && !rides.busy) NewRideFab(importActions, rides)
        },
    ) { padding ->
        when (rides.page) {
            Page.YOU -> YouTab(store, rides, platform, padding)
            else -> RidesTab(store, rides, padding)
        }
    }
}
