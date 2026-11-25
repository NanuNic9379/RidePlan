package io.github.nanunic9379.rideplan.personal

import androidx.compose.runtime.Composable
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

/** The locale's hour pattern ("j") has an AM/PM marker ("a") on a 12-hour phone. */
@Composable
actual fun is24HourClock(): Boolean =
    NSDateFormatter.dateFormatFromTemplate("j", 0u, NSLocale.currentLocale)?.contains('a') != true
