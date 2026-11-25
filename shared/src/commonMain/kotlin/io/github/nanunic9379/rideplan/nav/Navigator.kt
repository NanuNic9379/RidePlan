package io.github.nanunic9379.rideplan.nav

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.Stop
import io.github.nanunic9379.rideplan.ride.distanceKm
import io.github.nanunic9379.rideplan.route.Maneuver
import io.github.nanunic9379.rideplan.route.TurnSign

/**
 * One position from the phone's GPS. [speedMps] and [bearing] when the receiver provides them;
 * [accuracyM]: radius of the error, metres.
 */
data class Fix(
    val point: LatLon,
    val timeMs: Long,
    val speedMps: Double? = null,
    val bearing: Double? = null,
    val accuracyM: Double? = null,
)

/** A stop on the line: [along] metres from the start. [arrival]: a real stop, not a pass-through. */
data class StopMark(val index: Int, val stop: Stop, val along: Double, val arrival: Boolean, val finish: Boolean)

/** A maneuver placed on the line: [along] metres from the start. */
data class PlacedManeuver(val index: Int, val maneuver: Maneuver, val along: Double)

/**
 * The ride prepared for navigation: the line, the maneuvers and the stops placed on it.
 * A ride without maneuvers (GPX track) is followed without turn instructions.
 */
class NavRoute(val ride: Ride) {
    val line = RouteLine(ride.line)

    val maneuvers: List<PlacedManeuver> = ride.maneuvers.mapIndexed { i, m ->
        PlacedManeuver(i, m, line.along.getOrElse(m.beginIndex.coerceIn(0, maxOf(0, line.along.size - 1))) { 0.0 })
    }

    /** Turn by turn; otherwise only the track is followed. */
    val hasInstructions: Boolean get() = maneuvers.isNotEmpty()

    /** Computed on the phone from stops: it can be computed again from where you are. */
    val canReroute: Boolean get() = ride.route != null && ride.stops.size >= 2

    val stops: List<StopMark> = placeStops()

    private fun placeStops(): List<StopMark> {
        if (ride.stops.size < 2 || line.points.size < 2) return emptyList()
        val out = ArrayList<StopMark>()
        // The end of every leg of a route computed on the phone is exactly a "break" stop.
        val legEnds = maneuvers.filter { it.maneuver.isArrival }.map { it.along }
        val breaks = ride.stops.indices.filter { it > 0 && (ride.stops[it].kind == Stop.Kind.STOP || it == ride.stops.lastIndex) }
        val exact = legEnds.size == breaks.size
        var from = 0.0
        for (i in 1 until ride.stops.size) {
            val stop = ride.stops[i]
            val last = i == ride.stops.lastIndex
            val isBreak = i in breaks
            val along = when {
                last -> line.length
                exact && isBreak -> legEnds[breaks.indexOf(i)]
                else -> (line.firstNear(stop.point, STOP_NEAR_M, from) ?: line.snap(stop.point, from))?.along ?: from
            }
            from = along
            out += StopMark(i, stop, along, arrival = isBreak, finish = last)
        }
        return out
    }

    companion object {
        /** A stop is "on" the track within this distance (GPX waypoints are rarely exact). */
        const val STOP_NEAR_M = 300.0
    }
}

/** What to say. The screen turns it into text in the app language. */
sealed interface Announcement {
    /** Start of the navigation: the first instruction, or the track to follow. */
    data class Start(val maneuver: Maneuver?, val remainingM: Double) : Announcement

    /** A maneuver ahead: [stage] tells how far ahead; [distanceM] already rounded for the voice. */
    data class Turn(val maneuver: Maneuver, val stage: Stage, val distanceM: Int) : Announcement

    /** Right after a maneuver, when the next one is far: "Continue for 12 km". [distanceM]: to the next maneuver, to say it in miles (#15). */
    data class Continue(val text: String, val distanceM: Double? = null) : Announcement

    data class Arrived(val stop: StopMark) : Announcement

    /** Away from the road: [canReroute] = a new route is being computed. */
    data class OffRoute(val canReroute: Boolean) : Announcement

    data object BackOnRoute : Announcement
}

enum class Stage { FAR, NEAR, NOW }

/** Distances at which a maneuver is announced: farther on fast roads. */
data class AnnounceDistances(val far: Double, val near: Double, val now: Double) {
    companion object {
        fun forSpeed(speedMps: Double): AnnounceDistances {
            val now = maxOf(40.0, speedMps * 6)
            return when {
                speedMps >= 25.0 -> AnnounceDistances(2000.0, 800.0, now) // 90 km/h and more
                speedMps >= 14.0 -> AnnounceDistances(800.0, 300.0, now) // 50 to 90 km/h
                else -> AnnounceDistances(400.0, 150.0, now)
            }
        }
    }
}

/** Thresholds of the navigation; the defaults suit a motorcycle. */
data class NavConfig(
    /** Farther than this from the road (plus GPS error) counts as away. */
    val offRouteM: Double = 45.0,
    /** Back on the road under this distance: lower than [offRouteM], so the state does not flicker. */
    val backOnRouteM: Double = 25.0,
    /** How long one must stay away before it counts as off route. */
    val offRouteSeconds: Double = 5.0,
    /** While still off route, try to compute again after this time. */
    val rerouteRetrySeconds: Double = 20.0,
    /** A stop is reached within this distance. */
    val arriveM: Double = 40.0,
    /** Speed assumed before the GPS gives one: a country road. */
    val defaultSpeedMps: Double = 16.0,
)

/** The navigation after one fix. */
data class NavUpdate(
    /** On the road: the point on the road; off route: the GPS position. */
    val position: LatLon,
    val onRoute: Boolean,
    val offRoute: Boolean,
    val offsetM: Double,
    val alongM: Double,
    val remainingM: Double,
    /** Null when the track has no time (GPX). */
    val remainingSeconds: Double?,
    val next: PlacedManeuver?,
    val distanceToNextM: Double?,
    /** The maneuver after [next], if it comes soon after it. */
    val then: PlacedManeuver?,
    val speedMps: Double?,
    /** Speed limit of the road here, km/h (#31); null off the road or where it is unknown. */
    val speedLimitKmh: Int? = null,
    /** Direction for the map: the road when on it, the GPS course otherwise. */
    val bearing: Double?,
    val announcements: List<Announcement>,
    /** Compute a new route from here now. */
    val reroute: Boolean,
    val finished: Boolean,
    /** The last stop reached, if any. */
    val lastArrival: StopMark?,
)

/**
 * Turn-by-turn navigation (#5), without any platform: feed it GPS fixes, it says where you are
 * on the road, what comes next, what to announce and when the route must be computed again.
 */
class Navigator(val route: NavRoute, private val config: NavConfig = NavConfig()) {
    private var along: Double? = null
    private var awaySince: Long? = null
    private var offRoute = false
    private var lastRerouteMs: Long? = null
    private var lastFix: Fix? = null
    private var lastBearing: Double? = null
    private var started = false
    private var finished = false
    private var nextStop = 0
    private var lastArrival: StopMark? = null
    private var lastNextIndex: Int? = null
    /** Stages already said for each maneuver. */
    private val said = HashMap<Int, Stage>()

    fun update(fix: Fix): NavUpdate {
        val out = ArrayList<Announcement>()
        val speed = fix.speedMps?.takeIf { it >= 0 } ?: derivedSpeed(fix)
        val accuracy = (fix.accuracyM ?: 10.0).coerceIn(0.0, 40.0)
        val snap = locate(fix, speed, accuracy)
        val limit = (if (offRoute) config.backOnRouteM else config.offRouteM) + accuracy
        val away = snap == null || snap.offset > limit

        // Off route only after some seconds away: one bad fix is not enough.
        var reroute = false
        if (away) {
            val since = awaySince ?: fix.timeMs.also { awaySince = it }
            if (!offRoute && fix.timeMs - since >= config.offRouteSeconds * 1000) {
                offRoute = true
                out += Announcement.OffRoute(route.canReroute)
                if (route.canReroute) {
                    reroute = true
                    lastRerouteMs = fix.timeMs
                }
            } else if (offRoute && route.canReroute &&
                fix.timeMs - (lastRerouteMs ?: 0L) >= config.rerouteRetrySeconds * 1000
            ) {
                reroute = true
                lastRerouteMs = fix.timeMs
            }
        } else {
            awaySince = null
            if (offRoute) {
                offRoute = false
                out += Announcement.BackOnRoute
            }
            along = snap!!.along
        }

        val at = along ?: 0.0
        val onRoute = !away
        val next = route.maneuvers.firstOrNull { it.along > at + PASSED_M && it.maneuver.sign != TurnSign.DEPART }
        val distance = next?.let { it.along - at }
        val then = next?.let { n ->
            route.maneuvers.firstOrNull { it.index > n.index && it.maneuver.sign != TurnSign.DEPART }
                ?.takeIf { it.along - n.along <= THEN_M }
        }

        if (onRoute && !started) {
            started = true
            out += Announcement.Start(route.maneuvers.firstOrNull()?.maneuver, route.line.length - at)
        } else if (onRoute && !offRoute && !finished) {
            announceTurns(next, distance, speed ?: config.defaultSpeedMps, out)
        }
        lastNextIndex = next?.index

        if (onRoute) arrivals(fix, at, out)

        // Off the road the bearing feeds the new route's start heading (#221): when the receiver
        // gives none, the direction of the last movement, never the old route's, which would make
        // the new route start with a short back-and-forth.
        val bearing = when {
            onRoute && route.line.points.size >= 2 -> route.line.bearingAt(at)
            fix.bearing != null && (speed ?: 0.0) > 1.5 -> fix.bearing
            else -> movedBearing(fix) ?: lastBearing
        }
        lastBearing = bearing
        lastFix = fix
        return NavUpdate(
            position = if (onRoute) snap!!.point else fix.point,
            onRoute = onRoute,
            offRoute = offRoute,
            offsetM = snap?.offset ?: Double.MAX_VALUE,
            alongM = at,
            remainingM = (route.line.length - at).coerceAtLeast(0.0),
            remainingSeconds = remainingSeconds(at),
            next = next,
            distanceToNextM = distance,
            then = then,
            speedMps = speed,
            speedLimitKmh = if (onRoute) route.ride.speedLimits.at(at) else null,
            bearing = bearing,
            announcements = out,
            reroute = reroute,
            finished = finished,
            lastArrival = lastArrival,
        )
    }

    /** The stops still ahead (pass-throughs included), to compute a new route towards them. */
    fun remainingStops(): List<Stop> {
        val at = along ?: 0.0
        val ahead = route.stops.filter { it.along > at + config.arriveM }.map { it.stop }
        return ahead.ifEmpty { listOfNotNull(route.ride.stops.lastOrNull()) }
    }

    /**
     * The road near the last known point first: on out-and-back rides and loops the same
     * road is ridden twice and the position must not jump to the other pass.
     */
    private fun locate(fix: Fix, speed: Double?, accuracy: Double): Snap? {
        val line = route.line
        val limit = config.offRouteM + accuracy
        val last = along
        if (last == null) return line.firstNear(fix.point, limit) ?: line.snap(fix.point)
        val ahead = maxOf(1000.0, (speed ?: config.defaultSpeedMps) * 60)
        // Forwards first: at the turning point of an out-and-back road both passes are equally
        // close, and the rider is on the one ahead.
        val forward = line.snap(fix.point, last - 20, last + ahead)
        if (forward != null && forward.offset <= limit) return forward
        val window = line.snap(fix.point, last - 150, last + ahead)
        if (window != null && window.offset <= limit) return window
        // A shortcut or a long GPS gap: the first later stretch of road that is close enough.
        return line.firstNear(fix.point, limit, last - 150) ?: window
    }

    /** Direction from the last fix to [fix], once the two are far enough apart to trust it. */
    private fun movedBearing(fix: Fix): Double? {
        val prev = lastFix ?: return null
        if (distanceKm(prev.point, fix.point) * 1000 < MOVED_M) return null
        return RouteLine.bearing(prev.point, fix.point)
    }

    private fun derivedSpeed(fix: Fix): Double? {
        val prev = lastFix ?: return null
        val dt = (fix.timeMs - prev.timeMs) / 1000.0
        if (dt <= 0 || dt > 10) return null
        return distanceKm(prev.point, fix.point) * 1000 / dt
    }

    private fun announceTurns(next: PlacedManeuver?, distance: Double?, speed: Double, out: MutableList<Announcement>) {
        // Just passed a maneuver and the next one is far: say how long to keep going.
        val passed = lastNextIndex
        if (passed != null && next?.index != passed && next != null && distance != null) {
            val d = AnnounceDistances.forSpeed(speed)
            route.maneuvers.getOrNull(passed)?.maneuver?.verbalPost?.let { post ->
                if (distance > d.far * 1.5) out += Announcement.Continue(post, distance)
            }
        }
        if (next == null || distance == null) return
        val d = AnnounceDistances.forSpeed(speed)
        val stage = when {
            distance <= d.now -> Stage.NOW
            distance <= d.near -> Stage.NEAR
            distance <= d.far -> Stage.FAR
            else -> return
        }
        val done = said[next.index]
        if (done != null && done >= stage) return
        said[next.index] = stage
        // Arrival is said by the stop itself, when it is reached.
        if (stage == Stage.NOW && next.maneuver.isArrival) return
        out += Announcement.Turn(next.maneuver, stage, spokenDistance(distance))
    }

    private fun arrivals(fix: Fix, at: Double, out: MutableList<Announcement>) {
        while (nextStop < route.stops.size) {
            val mark = route.stops[nextStop]
            // Close to the stop counts only near its place on the line: on a loop the finish is
            // also where the ride starts.
            val close = at >= mark.along - 1000 && distanceKm(fix.point, mark.stop.point) * 1000 <= config.arriveM
            val passed = at >= mark.along - config.arriveM
            if (!close && !passed) break
            nextStop++
            if (mark.arrival) {
                lastArrival = mark
                if (mark.finish) finished = true
                out += Announcement.Arrived(mark)
            }
        }
    }

    private fun remainingSeconds(at: Double): Double? {
        val ms = route.maneuvers
        if (ms.isNotEmpty() && ms.any { it.maneuver.seconds > 0 }) {
            var total = 0.0
            for ((i, m) in ms.withIndex()) {
                val end = ms.getOrNull(i + 1)?.along ?: route.line.length
                val len = end - m.along
                when {
                    end <= at -> Unit
                    m.along >= at -> total += m.maneuver.seconds
                    len > 0 -> total += m.maneuver.seconds * (end - at) / len
                }
            }
            return total
        }
        val seconds = route.ride.route?.seconds ?: return null
        val length = route.line.length
        return if (length > 0) seconds * (length - at).coerceAtLeast(0.0) / length else null
    }

    companion object {
        /** A maneuver this close behind is already done. */
        const val PASSED_M = 5.0

        /** Fixes closer than this are GPS noise for the direction of travel. */
        const val MOVED_M = 8.0

        /** Show "then" only if the second maneuver follows within this distance. */
        const val THEN_M = 600.0

        /** Distance for the voice: whole km from 1 km, otherwise steps of 50 m (never 0). */
        fun spokenDistance(m: Double): Int = when {
            m >= 950 -> (kotlin.math.round(m / 1000) * 1000).toInt()
            else -> maxOf(50, (kotlin.math.round(m / 50) * 50).toInt())
        }
    }
}
