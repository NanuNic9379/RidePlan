package io.github.nanunic9379.rideplan.personal

import androidx.compose.runtime.Composable

/** The phone's clock setting (#82): true for "20:30", false for "8:30 PM". */
@Composable
expect fun is24HourClock(): Boolean
