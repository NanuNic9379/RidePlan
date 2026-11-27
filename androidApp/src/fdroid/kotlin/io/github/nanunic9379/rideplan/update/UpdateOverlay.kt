package io.github.nanunic9379.rideplan.update

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import io.github.nanunic9379.rideplan.personal.UpdateActions
import kotlinx.coroutines.CoroutineScope

/** F-Droid version: F-Droid handles updates, the app checks nothing. */
@Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
@Composable
fun BoxScope.UpdateOverlay(
    checkAtStart: Boolean,
    backgroundChecksEnabled: Boolean = false,
    onBackgroundCheckPermissionDenied: () -> Unit = {},
) = Unit

/** No update card on Info & updates: the store shows new versions. */
@Suppress("UNUSED_PARAMETER")
fun updateActions(scope: CoroutineScope): UpdateActions? = null
