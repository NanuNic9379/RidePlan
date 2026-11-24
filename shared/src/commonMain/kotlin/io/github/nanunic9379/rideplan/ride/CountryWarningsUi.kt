package io.github.nanunic9379.rideplan.ride

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.personal.LocalUnits
import io.github.nanunic9379.rideplan.profile.Measures
import io.github.nanunic9379.rideplan.profile.Units
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.country_note_min_cc
import io.github.nanunic9379.rideplan.resources.country_note_min_speed
import io.github.nanunic9379.rideplan.resources.country_note_toll_motorways
import io.github.nanunic9379.rideplan.resources.country_note_toll_some
import io.github.nanunic9379.rideplan.resources.country_note_vignette
import io.github.nanunic9379.rideplan.resources.country_note_vignette_unclear
import io.github.nanunic9379.rideplan.resources.country_rules_unchecked
import io.github.nanunic9379.rideplan.resources.country_warnings_title
import io.github.nanunic9379.rideplan.resources.nav_speed_kmh
import io.github.nanunic9379.rideplan.resources.nav_speed_mph
import io.github.nanunic9379.rideplan.resources.country_ad
import io.github.nanunic9379.rideplan.resources.country_al
import io.github.nanunic9379.rideplan.resources.country_at
import io.github.nanunic9379.rideplan.resources.country_ba
import io.github.nanunic9379.rideplan.resources.country_be
import io.github.nanunic9379.rideplan.resources.country_bg
import io.github.nanunic9379.rideplan.resources.country_ch
import io.github.nanunic9379.rideplan.resources.country_cz
import io.github.nanunic9379.rideplan.resources.country_de
import io.github.nanunic9379.rideplan.resources.country_dk
import io.github.nanunic9379.rideplan.resources.country_ee
import io.github.nanunic9379.rideplan.resources.country_es
import io.github.nanunic9379.rideplan.resources.country_fi
import io.github.nanunic9379.rideplan.resources.country_fr
import io.github.nanunic9379.rideplan.resources.country_gb
import io.github.nanunic9379.rideplan.resources.country_gr
import io.github.nanunic9379.rideplan.resources.country_hr
import io.github.nanunic9379.rideplan.resources.country_hu
import io.github.nanunic9379.rideplan.resources.country_ie
import io.github.nanunic9379.rideplan.resources.country_it
import io.github.nanunic9379.rideplan.resources.country_li
import io.github.nanunic9379.rideplan.resources.country_lt
import io.github.nanunic9379.rideplan.resources.country_lu
import io.github.nanunic9379.rideplan.resources.country_lv
import io.github.nanunic9379.rideplan.resources.country_mc
import io.github.nanunic9379.rideplan.resources.country_md
import io.github.nanunic9379.rideplan.resources.country_me
import io.github.nanunic9379.rideplan.resources.country_mk
import io.github.nanunic9379.rideplan.resources.country_mt
import io.github.nanunic9379.rideplan.resources.country_nl
import io.github.nanunic9379.rideplan.resources.country_no
import io.github.nanunic9379.rideplan.resources.country_pl
import io.github.nanunic9379.rideplan.resources.country_pt
import io.github.nanunic9379.rideplan.resources.country_ro
import io.github.nanunic9379.rideplan.resources.country_rs
import io.github.nanunic9379.rideplan.resources.country_se
import io.github.nanunic9379.rideplan.resources.country_si
import io.github.nanunic9379.rideplan.resources.country_sk
import io.github.nanunic9379.rideplan.resources.country_sm
import io.github.nanunic9379.rideplan.resources.country_xk
import io.github.nanunic9379.rideplan.route.CountryNote
import io.github.nanunic9379.rideplan.route.CountryRules
import io.github.nanunic9379.rideplan.route.CountryWarnings
import io.github.nanunic9379.rideplan.ui.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Name of every country with an outline, for the ride sheet (#15). */
internal val countryNames: Map<String, StringResource> = mapOf(
    "AD" to Res.string.country_ad,
    "AL" to Res.string.country_al,
    "AT" to Res.string.country_at,
    "BA" to Res.string.country_ba,
    "BE" to Res.string.country_be,
    "BG" to Res.string.country_bg,
    "CH" to Res.string.country_ch,
    "CZ" to Res.string.country_cz,
    "DE" to Res.string.country_de,
    "DK" to Res.string.country_dk,
    "EE" to Res.string.country_ee,
    "ES" to Res.string.country_es,
    "FI" to Res.string.country_fi,
    "FR" to Res.string.country_fr,
    "GB" to Res.string.country_gb,
    "GR" to Res.string.country_gr,
    "HR" to Res.string.country_hr,
    "HU" to Res.string.country_hu,
    "IE" to Res.string.country_ie,
    "IT" to Res.string.country_it,
    "LI" to Res.string.country_li,
    "LT" to Res.string.country_lt,
    "LU" to Res.string.country_lu,
    "LV" to Res.string.country_lv,
    "MC" to Res.string.country_mc,
    "MD" to Res.string.country_md,
    "ME" to Res.string.country_me,
    "MK" to Res.string.country_mk,
    "MT" to Res.string.country_mt,
    "NL" to Res.string.country_nl,
    "NO" to Res.string.country_no,
    "PL" to Res.string.country_pl,
    "PT" to Res.string.country_pt,
    "RO" to Res.string.country_ro,
    "RS" to Res.string.country_rs,
    "SE" to Res.string.country_se,
    "SI" to Res.string.country_si,
    "SK" to Res.string.country_sk,
    "SM" to Res.string.country_sm,
    "XK" to Res.string.country_xk,
)

/**
 * The countries a ride crosses and what to know there (#15, option A: warnings only), e.g.
 * "Switzerland: motorway vignette required". Nothing for a ride that stays in the phone's
 * country. Found offline from the ride line, off the main thread.
 */
@Composable
fun CountryWarningsSection(ride: Ride) {
    val home = androidx.compose.ui.text.intl.Locale.current.region
    val line = ride.line
    var warnings by remember(line, home) { mutableStateOf(CountryWarnings.NONE) }
    LaunchedEffect(line, home) {
        warnings = withContext(Dispatchers.Default) { CountryWarnings.forLine(line, home) }
    }
    CountryWarningsContent(warnings)
}

@Composable
internal fun CountryWarningsContent(warnings: CountryWarnings) {
    if (warnings.isEmpty) return
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.country_warnings_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            warnings.countries.map { countryName(it) }.joinToString(" → "),
            style = MaterialTheme.typography.bodyLarge,
        )
        for (notice in warnings.notices) {
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(countryName(notice.country), style = MaterialTheme.typography.labelLarge)
                for (note in notice.notes) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            AppIcons.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(noteText(note), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (warnings.notices.isNotEmpty() && warnings.unverified) {
            Text(
                stringResource(Res.string.country_rules_unchecked, CountryRules.COMPILED),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
    }
}

@Composable
private fun countryName(code: String): String = countryNames[code]?.let { stringResource(it) } ?: code

@Composable
private fun noteText(note: CountryNote): String = when (note) {
    CountryNote.Vignette -> stringResource(Res.string.country_note_vignette)
    CountryNote.VignetteUnclear -> stringResource(Res.string.country_note_vignette_unclear)
    CountryNote.TollMotorways -> stringResource(Res.string.country_note_toll_motorways)
    CountryNote.TollSome -> stringResource(Res.string.country_note_toll_some)
    is CountryNote.MotorwayMinCc -> stringResource(Res.string.country_note_min_cc, note.cc)
    is CountryNote.MotorwayMinSpeed -> {
        val units = LocalUnits.current
        val unit = stringResource(if (units == Units.MILES) Res.string.nav_speed_mph else Res.string.nav_speed_kmh)
        stringResource(Res.string.country_note_min_speed, "${Measures.speed(note.kmh.toDouble(), units)} $unit")
    }
}
