package io.github.nanunic9379.rideplan.ride

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Ride as GPX 1.1, to send to Garmin, TomTom, Calimoto, Kurviger or a friend (#5).
 *
 * - `wpt`: the stops, with their names (only when there is also a track).
 * - `rte`: the same stops in order. Motorcycle navigators (Garmin Zūmo, TomTom Rider) recalculate
 *   the road through them.
 * - `trk`: the actual road, when there is one (imported GPX/KML or route calculated on the phone).
 *   For those who want to follow exactly that road.
 * - Stop plans (#10) and overnights (#12) on `wpt` and `rtept`, the ride version (#11) and the
 *   daily cap (#12) in the metadata: see [planTags].
 */
object GpxExport {
    fun write(ride: Ride, fallbackName: String): String {
        val name = ride.name ?: fallbackName
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<gpx version="1.1" creator="RidePlan" xmlns="http://www.topografix.com/GPX/1/1" xmlns:rideplan="$NAMESPACE">""").append('\n')
        sb.append("  <metadata><name>").append(escape(name)).append("</name>")
        ride.version?.let { v ->
            if (v.note.isNotBlank()) sb.append("<desc>").append(escape("v${v.number}: ${v.note}")).append("</desc>")
        }
        val cap = ride.dayCap?.takeIf { it.valid }
        if (ride.version != null || cap != null) {
            sb.append("<extensions>")
            ride.version?.let { v ->
                sb.append("<rideplan:version id=\"").append(escape(v.rideId)).append("\" number=\"").append(v.number)
                    .append("\" updated=\"").append(v.updatedAt).append("\"/>")
            }
            // Daily cap of a multi-day trip (#12), in the same form as the ride link.
            cap?.let { sb.append("<rideplan:days cap=\"").append(RideLink.dayCapCode(it)).append("\"/>") }
            sb.append("</extensions>")
        }
        sb.append("</metadata>\n")
        // Stops as wpt only next to a track: without a track the route points are enough
        // (and whoever reads the file back does not mistake the route for the road).
        if (!ride.straightLines) ride.stops.forEachIndexed { i, stop ->
            sb.append("  <wpt ").append(attrs(stop.point)).append("><name>")
                .append(escape(stopName(stop, i))).append("</name>").append(planTags(stop.plan, overnight(ride, i))).append("</wpt>\n")
        }
        if (ride.stops.size >= 2) {
            sb.append("  <rte>\n    <name>").append(escape(name)).append("</name>\n")
            ride.stops.forEachIndexed { i, stop ->
                sb.append("    <rtept ").append(attrs(stop.point)).append("><name>")
                    .append(escape(stopName(stop, i))).append("</name>").append(planTags(stop.plan, overnight(ride, i))).append("</rtept>\n")
            }
            sb.append("  </rte>\n")
        }
        if (!ride.straightLines) {
            sb.append("  <trk>\n    <name>").append(escape(name)).append("</name>\n    <trkseg>\n")
            ride.track.forEach { p -> sb.append("      <trkpt ").append(attrs(p)).append("/>\n") }
            sb.append("    </trkseg>\n  </trk>\n")
        }
        sb.append("</gpx>\n")
        return sb.toString()
    }

    /** File name: no characters that file systems or chat apps reject. */
    fun fileName(ride: Ride, fallbackName: String): String {
        val base = (ride.name ?: fallbackName)
            .replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), " ")
            .replace("→", "-")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(60)
            .trimEnd('.', ' ')
        return (base.ifEmpty { "RidePlan" }) + ".gpx"
    }

    private fun stopName(stop: Stop, i: Int) = stop.name ?: "${i + 1}"

    /** The rider sleeps at stop [i] (#12): only intermediate stops count. */
    private fun overnight(ride: Ride, i: Int) = i != 0 && i != ride.stops.lastIndex && ride.stops[i].overnight

    /** Namespace of the RidePlan extensions (#10, #11): other apps skip them. */
    const val NAMESPACE = "https://nanunic9379.github.io/RidePlan/gpx/1"

    /**
     * The plan of a stop (#10), in GPX order after the name: the note in `cmt`, a readable
     * line in `desc` for other apps, the purpose in `type`, time and pause in an extension.
     */
    private fun planTags(plan: StopPlan?, overnight: Boolean): String {
        val p = plan?.normalized()
        if (p == null) return if (overnight) "<extensions><rideplan:stop overnight=\"1\"/></extensions>" else ""
        val sb = StringBuilder()
        if (p.note.isNotBlank()) sb.append("<cmt>").append(escape(p.note)).append("</cmt>")
        val desc = listOfNotNull(
            p.time?.let(StopPlan::timeText),
            p.pauseMinutes.takeIf { it > 0 }?.let { "$it min" },
            p.note.ifBlank { null },
        ).joinToString(" · ")
        if (desc.isNotEmpty()) sb.append("<desc>").append(escape(desc)).append("</desc>")
        p.purpose?.let { sb.append("<type>").append(it.code).append("</type>") }
        if (p.time != null || p.pauseMinutes > 0 || overnight) {
            sb.append("<extensions><rideplan:stop")
            p.time?.let { sb.append(" time=\"").append(StopPlan.timeText(it)).append('"') }
            if (p.pauseMinutes > 0) sb.append(" pause=\"").append(p.pauseMinutes).append('"')
            if (overnight) sb.append(" overnight=\"1\"")
            sb.append("/></extensions>")
        }
        return sb.toString()
    }

    private fun attrs(p: LatLon) = """lat="${coord(p.lat)}" lon="${coord(p.lon)}""""

    /** 7 decimals (about 1 cm), no exponential notation and independent of the locale. */
    internal fun coord(d: Double): String {
        val n = (d * 10_000_000).roundToLong()
        val sign = if (n < 0) "-" else ""
        val a = abs(n)
        val frac = (a % 10_000_000).toString().padStart(7, '0').trimEnd('0')
        return sign + (a / 10_000_000) + if (frac.isEmpty()) "" else ".$frac"
    }

    private fun escape(s: String) = buildString {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> if (c.code < 0x20 && c != '\t' && c != '\n') append(' ') else append(c)
        }
    }
}
