package io.github.nanunic9379.rideplan.nav

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.distanceKm

/**
 * The ongoing notification while navigating (#65): it keeps guidance and the position going
 * with the screen off or another app open. [title] is the next turn, [text] what is left.
 * [stop] and [channel] are the labels of its Stop action and of its notification channel.
 */
data class NavNotice(val title: String, val text: String, val stop: String, val channel: String)

/** Fixed words of the notification, in the app language. */
data class NoticeTexts(
    val waiting: String,
    val rerouting: String,
    val followTrack: String,
    val arrived: String,
    val toFinish: String,
    val stop: String,
    val channel: String,
)

/**
 * What the notification says for [u]: "300 m · Turn right onto Via Roma" and "42 km to the
 * finish". [distance] formats metres in the unit of the settings.
 */
fun navNotice(u: NavUpdate?, rerouting: Boolean, texts: NoticeTexts, distance: (Double) -> String): NavNotice {
    val left = u?.let { "${distance(it.remainingM)} ${texts.toFinish}" } ?: ""
    val title = when {
        u == null -> texts.waiting
        rerouting -> texts.rerouting
        u.finished -> texts.arrived
        u.next != null && u.distanceToNextM != null -> "${distance(u.distanceToNextM)} · ${u.next.maneuver.instruction}"
        else -> texts.followTrack
    }
    return NavNotice(title, if (u?.finished == true) "" else left, texts.stop, texts.channel)
}

/**
 * Pauses navigation after a long stop (#65), to save the battery when the phone was left on
 * with the bike parked: no movement for [limitMs] (speed under [movingMps] and never farther than
 * [moveM] from where it stopped). Also counts time without any GPS fix.
 */
class IdleWatch(
    private val limitMs: Long = 45 * 60_000L,
    private val moveM: Double = 150.0,
    private val movingMps: Double = 2.0,
) {
    private var anchor: LatLon? = null
    private var since: Long? = null

    /** Navigation (re)started at [nowMs]. */
    fun start(nowMs: Long) {
        anchor = null
        since = nowMs
    }

    fun onFix(fix: Fix, nowMs: Long) {
        val a = anchor
        val moving = (fix.speedMps ?: 0.0) >= movingMps
        if (a == null || moving || distanceKm(a, fix.point) * 1000 > moveM) {
            anchor = fix.point
            since = nowMs
        }
    }

    fun idle(nowMs: Long): Boolean = since?.let { nowMs - it >= limitMs } ?: false
}
