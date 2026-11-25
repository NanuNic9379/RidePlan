package io.github.nanunic9379.rideplan.personal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.profile.Period
import io.github.nanunic9379.rideplan.profile.ProfileStore
import io.github.nanunic9379.rideplan.profile.RideStats
import io.github.nanunic9379.rideplan.profile.civilDate
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.garage_add
import io.github.nanunic9379.rideplan.resources.garage_count
import io.github.nanunic9379.rideplan.resources.garage_title
import io.github.nanunic9379.rideplan.resources.info_title
import io.github.nanunic9379.rideplan.resources.info_version
import io.github.nanunic9379.rideplan.resources.log_title
import io.github.nanunic9379.rideplan.resources.nav_you
import io.github.nanunic9379.rideplan.resources.outings_count
import io.github.nanunic9379.rideplan.resources.privacy_sub
import io.github.nanunic9379.rideplan.resources.privacy_title
import io.github.nanunic9379.rideplan.resources.profile_color
import io.github.nanunic9379.rideplan.resources.profile_edit
import io.github.nanunic9379.rideplan.resources.profile_local
import io.github.nanunic9379.rideplan.resources.profile_name
import io.github.nanunic9379.rideplan.resources.profile_your_bike
import io.github.nanunic9379.rideplan.resources.settings_sub
import io.github.nanunic9379.rideplan.resources.settings_title
import io.github.nanunic9379.rideplan.resources.soon_friends
import io.github.nanunic9379.rideplan.resources.soon_friends_text
import io.github.nanunic9379.rideplan.resources.soon_title
import io.github.nanunic9379.rideplan.resources.stat_km
import io.github.nanunic9379.rideplan.resources.stat_longest
import io.github.nanunic9379.rideplan.resources.stat_outings
import io.github.nanunic9379.rideplan.resources.stats_sub
import io.github.nanunic9379.rideplan.resources.stats_title
import io.github.nanunic9379.rideplan.resources.you_anon
import io.github.nanunic9379.rideplan.resources.you_privacy
import io.github.nanunic9379.rideplan.resources.you_since
import io.github.nanunic9379.rideplan.resources.you_things
import io.github.nanunic9379.rideplan.resources.zones_count
import io.github.nanunic9379.rideplan.resources.zones_title
import io.github.nanunic9379.rideplan.ride.Dest
import io.github.nanunic9379.rideplan.ride.RideScreenState
import io.github.nanunic9379.rideplan.ui.AppIcons
import io.github.nanunic9379.rideplan.ui.Touch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The You tab (#21): who you are, three numbers, then everything that is yours. Nothing here
 * goes online: the "coming soon" card only says what will come.
 */
@Composable
fun YouTab(store: ProfileStore, rides: RideScreenState, platform: PlatformActions?, padding: PaddingValues) {
    val data = store.data
    val p = data.profile
    val all = RideStats.of(data, Period.ALL, store.time())
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.nav_you), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Avatar(p.name, p.color, 64.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    p.name.trim().ifEmpty { stringResource(Res.string.you_anon) },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val (_, m, y) = civilDate(p.createdAt)
                val since = stringResource(Res.string.you_since, monthLong(m), y)
                Text(
                    listOfNotNull(data.defaultBike?.name, since).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(onClick = { rides.open(Dest.EditProfile) }, modifier = Modifier.size(Touch.Glove)) {
                Icon(AppIcons.Edit, contentDescription = stringResource(Res.string.profile_edit))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val open = { rides.open(Dest.Stats) }
            StatTile("${all.outings}", stringResource(Res.string.stat_outings), Modifier.weight(1f), onClick = open)
            StatTile(distanceNumber(all.km), stringResource(Res.string.stat_km), Modifier.weight(1f), unit = distanceUnit(), onClick = open)
            val longest = all.longestKm
            StatTile(
                longest?.let { distanceNumber(it) } ?: "–",
                stringResource(Res.string.stat_longest),
                Modifier.weight(1f),
                unit = longest?.let { distanceUnit() },
                onClick = open,
            )
        }

        SectionLabel(stringResource(Res.string.you_things))
        RowGroup {
            NavRow(AppIcons.Chart, stringResource(Res.string.stats_title), stringResource(Res.string.stats_sub), onClick = { rides.open(Dest.Stats) })
            GroupDivider()
            NavRow(
                AppIcons.Moto,
                stringResource(Res.string.garage_title),
                if (data.bikes.isEmpty()) stringResource(Res.string.garage_add) else pluralStringResource(Res.plurals.garage_count, data.bikes.size, data.bikes.size),
                onClick = { rides.open(Dest.Garage) },
            )
            GroupDivider()
            NavRow(AppIcons.History, stringResource(Res.string.log_title), pluralStringResource(Res.plurals.outings_count, all.outings, all.outings), onClick = { rides.open(Dest.Log) })
        }

        SectionLabel(stringResource(Res.string.you_privacy))
        RowGroup {
            NavRow(
                AppIcons.Pin,
                stringResource(Res.string.zones_title),
                pluralStringResource(Res.plurals.zones_count, data.privateZones.size, data.privateZones.size),
                onClick = { rides.open(Dest.Zones) },
            )
            GroupDivider()
            NavRow(AppIcons.Shield, stringResource(Res.string.privacy_title), stringResource(Res.string.privacy_sub), onClick = { rides.open(Dest.Privacy) })
            GroupDivider()
            NavRow(AppIcons.Settings, stringResource(Res.string.settings_title), stringResource(Res.string.settings_sub), onClick = { rides.open(Dest.Settings) })
        }

        ComingSoon()

        RowGroup {
            NavRow(
                AppIcons.Info,
                stringResource(Res.string.info_title),
                trailing = platform?.let { stringResource(Res.string.info_version, it.versionName) },
                onClick = { rides.open(Dest.Info) },
            )
        }
        Row(
            Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(stringResource(Res.string.profile_local), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** What will come (#16): a quiet section, shown but not usable, with no card around it. */
@Composable
private fun ComingSoon() {
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(Res.string.soon_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
        NavRow(AppIcons.Group, stringResource(Res.string.soon_friends), stringResource(Res.string.soon_friends_text), enabled = false, onClick = null)
    }
}

/** Your name and colour (#21): saved as you type. */
@Composable
fun EditProfilePage(store: ProfileStore, rides: RideScreenState) {
    val p = store.data.profile
    SubPage(stringResource(Res.string.profile_edit), onBack = { rides.back() }) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) { Avatar(p.name, p.color, 104.dp) }
        SectionLabel(stringResource(Res.string.profile_color))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AvatarColors.forEachIndexed { i, c ->
                val selected = p.color == i
                Box(
                    Modifier
                        .size(Touch.Min)
                        .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                        .padding(4.dp)
                        .background(c, CircleShape)
                        .semantics { contentDescription = "${i + 1}" }
                        .selectable(selected = selected, role = Role.RadioButton) {
                            store.update { it.copy(profile = it.profile.copy(color = i)) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(AppIcons.Check, contentDescription = null, tint = Color.White)
                }
            }
        }
        OutlinedTextField(
            value = p.name,
            onValueChange = { v -> store.update { it.copy(profile = it.profile.copy(name = v.take(40))) } },
            label = { Text(stringResource(Res.string.profile_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        RowGroup {
            NavRow(
                AppIcons.Moto,
                stringResource(Res.string.profile_your_bike),
                store.data.defaultBike?.name ?: stringResource(Res.string.garage_add),
                onClick = { rides.open(Dest.Garage) },
            )
        }
        Row(
            Modifier.padding(vertical = 8.dp).heightIn(min = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(stringResource(Res.string.profile_local), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
