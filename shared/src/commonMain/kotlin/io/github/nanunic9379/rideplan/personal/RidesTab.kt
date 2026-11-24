package io.github.nanunic9379.rideplan.personal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.profile.LengthFilter
import io.github.nanunic9379.rideplan.profile.ProfileStore
import io.github.nanunic9379.rideplan.profile.RideChip
import io.github.nanunic9379.rideplan.profile.RideFilter
import io.github.nanunic9379.rideplan.profile.RideQuery
import io.github.nanunic9379.rideplan.profile.RideSort
import io.github.nanunic9379.rideplan.profile.SavedRide
import io.github.nanunic9379.rideplan.profile.doneText
import io.github.nanunic9379.rideplan.profile.title
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.chip_all
import io.github.nanunic9379.rideplan.resources.chip_done
import io.github.nanunic9379.rideplan.resources.chip_favourites
import io.github.nanunic9379.rideplan.resources.chip_received
import io.github.nanunic9379.rideplan.resources.chip_todo
import io.github.nanunic9379.rideplan.resources.fav_add
import io.github.nanunic9379.rideplan.resources.fav_remove
import io.github.nanunic9379.rideplan.resources.filter_any
import io.github.nanunic9379.rideplan.resources.filter_bike
import io.github.nanunic9379.rideplan.resources.filter_length
import io.github.nanunic9379.rideplan.resources.filter_reset
import io.github.nanunic9379.rideplan.resources.filter_show
import io.github.nanunic9379.rideplan.resources.home_empty_text
import io.github.nanunic9379.rideplan.resources.home_empty_title
import io.github.nanunic9379.rideplan.resources.home_new_ride
import io.github.nanunic9379.rideplan.resources.home_paste_link
import io.github.nanunic9379.rideplan.resources.plan_on_map
import io.github.nanunic9379.rideplan.resources.import_open_file
import io.github.nanunic9379.rideplan.resources.len_100_200
import io.github.nanunic9379.rideplan.resources.len_over_200
import io.github.nanunic9379.rideplan.resources.len_up_100
import io.github.nanunic9379.rideplan.resources.nav_map
import io.github.nanunic9379.rideplan.resources.nav_rides
import io.github.nanunic9379.rideplan.resources.nav_you
import io.github.nanunic9379.rideplan.resources.new_ride_explore
import io.github.nanunic9379.rideplan.resources.ride_stops
import io.github.nanunic9379.rideplan.resources.rides_empty_filter
import io.github.nanunic9379.rideplan.resources.rides_search
import io.github.nanunic9379.rideplan.resources.rides_sort
import io.github.nanunic9379.rideplan.resources.sort_last_done
import io.github.nanunic9379.rideplan.resources.sort_last_opened
import io.github.nanunic9379.rideplan.resources.sort_longest
import io.github.nanunic9379.rideplan.resources.sort_name
import io.github.nanunic9379.rideplan.resources.sort_title
import io.github.nanunic9379.rideplan.resources.tag_received
import io.github.nanunic9379.rideplan.ride.Dest
import io.github.nanunic9379.rideplan.ride.ImportActions
import io.github.nanunic9379.rideplan.ride.ImportStatus
import io.github.nanunic9379.rideplan.ride.LinkDialog
import io.github.nanunic9379.rideplan.ride.Page
import io.github.nanunic9379.rideplan.ride.RideScreenState
import io.github.nanunic9379.rideplan.route.durationText
import io.github.nanunic9379.rideplan.ui.AppIcons
import io.github.nanunic9379.rideplan.ui.Touch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Bottom bar of the three tabs (#21): Rides, Map, You. 80 dp, labels always shown. */
@Composable
fun MainNavBar(rides: RideScreenState, modifier: Modifier = Modifier) {
    NavigationBar(modifier) {
        val items = listOf(
            Triple(Page.HOME, AppIcons.Route, Res.string.nav_rides),
            Triple(Page.MAP, AppIcons.Map, Res.string.nav_map),
            Triple(Page.YOU, AppIcons.Person, Res.string.nav_you),
        )
        for ((page, icon, label) in items) {
            NavigationBarItem(
                selected = rides.page == page && rides.ride == null,
                onClick = { rides.tab(page) },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

/** "New ride" (#21): an extended FAB that opens the ways to start one; planning on the map first (#33). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRideFab(actions: ImportActions, rides: RideScreenState, modifier: Modifier = Modifier) {
    var sheet by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf(false) }
    if (link) LinkDialog(actions, onDismiss = { link = false })
    ExtendedFloatingActionButton(
        onClick = { sheet = true },
        modifier = modifier.heightIn(min = Touch.Glove),
        icon = { Icon(AppIcons.Add, contentDescription = null) },
        text = { Text(stringResource(Res.string.home_new_ride), style = MaterialTheme.typography.labelLarge) },
    )
    if (sheet) {
        ModalBottomSheet(onDismissRequest = { sheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(
                    stringResource(Res.string.home_new_ride),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
                )
                NavRow(AppIcons.Route, stringResource(Res.string.plan_on_map), onClick = { sheet = false; rides.startPlan() }, end = {})
                NavRow(AppIcons.Link, stringResource(Res.string.home_paste_link), onClick = { sheet = false; link = true }, end = {})
                NavRow(AppIcons.File, stringResource(Res.string.import_open_file), onClick = { sheet = false; actions.openFile() }, end = {})
                NavRow(AppIcons.Map, stringResource(Res.string.new_ride_explore), onClick = { sheet = false; rides.tab(Page.MAP) }, end = {})
            }
        }
    }
}

/**
 * The Rides tab (#21): search, quick chips with counts, then your rides. Each row opens the
 * ride page; the star is a 48 dp target of its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RidesTab(store: ProfileStore, rides: RideScreenState, padding: PaddingValues) {
    val data = store.data
    val q = rides.query
    var sortSheet by remember { mutableStateOf(false) }
    val titles = data.rides.associate { it.id to title(it) }
    val counts = RideFilter.counts(data.rides)
    val shown = RideFilter.apply(data.rides, q) { titles[it.id].orEmpty() }

    if (sortSheet) SortSheet(store, q, onChange = { rides.query = it }, onDismiss = { sortSheet = false }) { candidate ->
        RideFilter.apply(data.rides, candidate) { titles[it.id].orEmpty() }.size
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.nav_rides),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                val filtered = q.bike != null || q.length != LengthFilter.ANY || q.sort != RideSort.LAST_OPENED
                FilledTonalIconButton(onClick = { sortSheet = true }, modifier = Modifier.size(Touch.Glove)) {
                    Box {
                        Icon(AppIcons.Sort, contentDescription = stringResource(Res.string.rides_sort))
                        if (filtered) Box(Modifier.size(8.dp).align(Alignment.TopEnd).background(MaterialTheme.colorScheme.primary, CircleShape))
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = q.text,
                onValueChange = { rides.query = q.copy(text = it.take(60)) },
                placeholder = { Text(stringResource(Res.string.rides_search), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                trailingIcon = if (q.text.isNotEmpty()) ({
                    IconButton(onClick = { rides.query = q.copy(text = "") }) { Icon(AppIcons.Close, contentDescription = null) }
                }) else null,
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RideChip.entries) { chip ->
                    FilterChip(
                        selected = q.chip == chip,
                        onClick = { rides.query = q.copy(chip = chip) },
                        label = { Text("${chipLabel(chip)}  ${counts[chip] ?: 0}") },
                        leadingIcon = if (q.chip == chip) ({ Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }) else null,
                        modifier = Modifier.heightIn(min = Touch.Min),
                    )
                }
            }
        }
        item { Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { ImportStatus(rides) } }
        if (data.rides.isEmpty()) {
            item { EmptyState(stringResource(Res.string.home_empty_title), stringResource(Res.string.home_empty_text)) }
        } else if (shown.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(Res.string.rides_empty_filter), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { rides.query = RideQuery() }, modifier = Modifier.heightIn(min = Touch.Min)) {
                        Text(stringResource(Res.string.filter_reset))
                    }
                }
            }
        }
        items(shown, key = { it.id }) { saved ->
            RideRow(
                saved,
                titles[saved.id].orEmpty(),
                onOpen = { rides.open(Dest.RideDetail(saved.id)) },
                onStar = { store.update { it.setFavourite(saved.id, !saved.favourite) } },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun chipLabel(chip: RideChip): String = stringResource(
    when (chip) {
        RideChip.ALL -> Res.string.chip_all
        RideChip.TODO -> Res.string.chip_todo
        RideChip.DONE -> Res.string.chip_done
        RideChip.FAVOURITES -> Res.string.chip_favourites
        RideChip.RECEIVED -> Res.string.chip_received
    },
)

@Composable
fun EmptyState(title: String, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(72.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Route, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(36.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One ride of the list: its shape, name, km · time · stops, when you did it, the star. */
@Composable
fun RideRow(saved: SavedRide, title: String, onOpen: () -> Unit, onStar: (() -> Unit)?, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 88.dp).clickable(onClick = onOpen).padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RideSketch(listOf(saved.shape), Modifier.size(64.dp), strokeWidth = 2.5.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (saved.received != null) {
                    Text(
                        stringResource(Res.string.tag_received),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val parts = buildList {
                    add(distanceText(saved.km))
                    saved.seconds?.let { add(durationText(it.toDouble())) }
                    if (saved.stops.isNotEmpty()) add(pluralStringResource(Res.plurals.ride_stops, saved.stops.size, saved.stops.size))
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    doneText(saved),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (saved.ridden.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onStar != null) StarButton(saved.favourite, onStar)
        }
    }
}

@Composable
fun StarButton(on: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle, modifier = Modifier.size(Touch.Min)) {
        Icon(
            if (on) AppIcons.Star else AppIcons.StarOutline,
            contentDescription = stringResource(if (on) Res.string.fav_remove else Res.string.fav_add),
            tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Sort & filter (#21): order, bike and length; the button says how many rides you will see. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SortSheet(store: ProfileStore, current: RideQuery, onChange: (RideQuery) -> Unit, onDismiss: () -> Unit, count: (RideQuery) -> Int) {
    var q by remember { mutableStateOf(current) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(Res.string.sort_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            for (sort in RideSort.entries) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Touch.Min)
                        .selectable(selected = q.sort == sort, role = Role.RadioButton) { q = q.copy(sort = sort) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(selected = q.sort == sort, onClick = null)
                    Text(
                        stringResource(
                            when (sort) {
                                RideSort.LAST_OPENED -> Res.string.sort_last_opened
                                RideSort.LAST_DONE -> Res.string.sort_last_done
                                RideSort.LONGEST -> Res.string.sort_longest
                                RideSort.NAME -> Res.string.sort_name
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            val bikes = RideFilter.bikes(store.data)
            if (bikes.isNotEmpty()) {
                SectionLabel(stringResource(Res.string.filter_bike))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(q.bike == null, onClick = { q = q.copy(bike = null) }, label = { Text(stringResource(Res.string.filter_any)) }, modifier = Modifier.heightIn(min = Touch.Min))
                    for (b in bikes) {
                        FilterChip(q.bike.equals(b, ignoreCase = true), onClick = { q = q.copy(bike = b) }, label = { Text(b) }, modifier = Modifier.heightIn(min = Touch.Min))
                    }
                }
            }
            SectionLabel(stringResource(Res.string.filter_length))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (len in LengthFilter.entries) {
                    FilterChip(
                        q.length == len,
                        onClick = { q = q.copy(length = len) },
                        label = {
                            Text(
                                stringResource(
                                    when (len) {
                                        LengthFilter.ANY -> Res.string.filter_any
                                        LengthFilter.UP_TO_100 -> Res.string.len_up_100
                                        LengthFilter.FROM_100_TO_200 -> Res.string.len_100_200
                                        LengthFilter.OVER_200 -> Res.string.len_over_200
                                    },
                                ),
                            )
                        },
                        modifier = Modifier.heightIn(min = Touch.Min),
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            val n = count(q)
            Button(onClick = { onChange(q); onDismiss() }, modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Glove)) {
                Text(pluralStringResource(Res.plurals.filter_show, n, n), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(
                onClick = { q = q.copy(sort = RideSort.LAST_OPENED, bike = null, length = LengthFilter.ANY) },
                modifier = Modifier.fillMaxWidth().heightIn(min = Touch.Min),
            ) { Text(stringResource(Res.string.filter_reset)) }
        }
    }
}
