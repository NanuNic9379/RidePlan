package io.github.nanunic9379.rideplan.ride

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Giro in GPX 1.1, da mandare a Garmin, TomTom, Calimoto, Kurviger o a un amico (#5).
 *
 * - `wpt`: le tappe, con il loro nome (solo quando c'è anche la traccia).
 * - `rte`: le stesse tappe in ordine. I navigatori moto (Garmin Zūmo, TomTom Rider) ricalcolano
 *   la strada passando da qui.
 * - `trk`: la strada vera, quando c'è (GPX/KML importato o percorso calcolato sul telefono).
 *   Serve a chi vuole seguire esattamente quella strada.
 */
object GpxExport {
    fun write(ride: Ride, fallbackName: String): String {
        val name = ride.name ?: fallbackName
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<gpx version="1.1" creator="RidePlan" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        sb.append("  <metadata><name>").append(escape(name)).append("</name></metadata>\n")
        // Le tappe come wpt solo accanto a una traccia: senza traccia bastano i punti della rotta
        // (e chi rilegge il file non scambia la rotta per la strada).
        if (!ride.straightLines) ride.stops.forEachIndexed { i, stop ->
            sb.append("  <wpt ").append(attrs(stop.point)).append("><name>")
                .append(escape(stopName(stop, i))).append("</name></wpt>\n")
        }
        if (ride.stops.size >= 2) {
            sb.append("  <rte>\n    <name>").append(escape(name)).append("</name>\n")
            ride.stops.forEachIndexed { i, stop ->
                sb.append("    <rtept ").append(attrs(stop.point)).append("><name>")
                    .append(escape(stopName(stop, i))).append("</name></rtept>\n")
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

    /** Nome del file: niente caratteri che i file system o le app di chat rifiutano. */
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

    private fun attrs(p: LatLon) = """lat="${coord(p.lat)}" lon="${coord(p.lon)}""""

    /** 7 decimali (circa 1 cm), senza notazione esponenziale e senza dipendere dalla lingua. */
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
