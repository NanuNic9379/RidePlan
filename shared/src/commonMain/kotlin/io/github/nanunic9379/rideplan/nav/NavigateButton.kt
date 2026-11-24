package io.github.nanunic9379.rideplan.nav

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nanunic9379.rideplan.resources.Res
import io.github.nanunic9379.rideplan.resources.nav_start
import io.github.nanunic9379.rideplan.route.TurnSign
import io.github.nanunic9379.rideplan.ui.Touch
import org.jetbrains.compose.resources.stringResource

/** "Navigate" in the ride card (#5): big enough for gloves. */
@Composable
fun NavigateButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = Touch.Glove)) {
        TurnArrow(TurnSign.STRAIGHT, LocalContentColor.current, Modifier.size(22.dp))
        Text(stringResource(Res.string.nav_start), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
    }
}
