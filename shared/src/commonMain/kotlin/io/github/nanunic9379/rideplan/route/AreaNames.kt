package io.github.nanunic9379.rideplan.route

import androidx.compose.runtime.Composable
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.area_alps
import io.github.nanunic9379.rideplan.resources.area_it
import io.github.nanunic9379.rideplan.resources.mapdata_cells
import io.github.nanunic9379.rideplan.resources.mapdata_cells_area
import org.jetbrains.compose.resources.stringResource

/**
 * The name of an area of road data (#14) in the app language; areas added later than this app
 * version keep the (English) name from the index.
 */
@Composable
internal fun areaName(name: String): String = when (name) {
    "Italy" -> stringResource(Res.string.area_it)
    "Alps" -> stringResource(Res.string.area_alps)
    else -> name
}

/** The road-data item in "Data on the phone" (#21): with the area name only when there are several (#14). */
@Composable
fun cellsLabel(area: String?): String =
    if (area == null) stringResource(Res.string.mapdata_cells) else stringResource(Res.string.mapdata_cells_area, areaName(area))
