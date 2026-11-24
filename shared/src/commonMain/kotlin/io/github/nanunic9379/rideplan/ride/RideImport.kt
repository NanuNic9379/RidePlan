package io.github.nanunic9379.rideplan.ride

/** Reasons why a ride cannot be imported. The UI picks the translated text (#33). */
enum class ImportError {
    UNKNOWN_FORMAT, GPX_EMPTY, KML_EMPTY, NOT_GOOGLE_MAPS, NO_STOPS, SINGLE_PLACE,
    STOP_WITHOUT_COORDS, LINK_NAMES_WITHOUT_COORDS, INVALID_COORDS,
    FILE_TOO_BIG, FILE_UNREADABLE, ZIP_WITHOUT_RIDE, LINK_NO_ROUTE, LINK_TOO_MANY_REDIRECTS, NETWORK,
    LINK_BROKEN,
}

/** Import error. [detail]: optional technical detail (e.g. HTTP code), not translated. */
class ImportException(val error: ImportError, val detail: String? = null) : Exception("$error${detail?.let { ": $it" } ?: ""}")

/**
 * Reading a ride from GPX, KML or a Google Maps link (#27, #5).
 * Everything happens on the phone. The only thing that goes over the network is opening a
 * Google short link (maps.app.goo.gl), and the platform does that.
 */
object RideImport {
    private val urlPattern = Regex("""https?://\S+""")
    private val shortLinkHosts = listOf("maps.app.goo.gl", "goo.gl/maps")

    /** The first link in the shared text (Google Maps sends "place name + link"). */
    fun findUrl(text: String): String? = urlPattern.find(text)?.value?.trimEnd('.', ',', ')')

    /** Short link to open over the network to find the full link. */
    fun isShortLink(url: String): Boolean = shortLinkHosts.any { url.contains("://$it") }

    /** Detects the format by itself. [text] is the content of a file or a full link. */
    fun parse(text: String): Ride {
        val trimmed = text.trim().removePrefix("﻿")
        val url = findUrl(trimmed)
        return when {
            trimmed.contains("<gpx", ignoreCase = true) -> Gpx.parse(trimmed)
            trimmed.contains("<kml", ignoreCase = true) -> Kml.parse(trimmed)
            RideLink.isLink(trimmed) -> RideLink.decode(trimmed).ride
            url != null -> GoogleMapsLink.parse(url)
            else -> throw ImportException(ImportError.UNKNOWN_FORMAT)
        }
    }
}

/** Minimal XML reader for GPX and KML: no libraries, only the parts needed. */
internal object Xml {
    private val cdata = Regex("""<!\[CDATA\[(.*?)]]>""", RegexOption.DOT_MATCHES_ALL)
    private val comment = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)

    fun stripComments(xml: String) = comment.replace(xml, "")

    /** `<tag ...>...</tag>` or `<tag .../>` blocks, with an optional namespace prefix. */
    fun elements(xml: String, tag: String): List<Element> =
        Regex("""<(?:\w+:)?$tag\b([^>]*?)(?:/>|>(.*?)</(?:\w+:)?$tag\s*>)""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(xml).map { Element(it.groupValues[1], it.groupValues[2]) }.toList()

    fun text(xml: String, tag: String): String? =
        elements(xml, tag).firstOrNull()?.body?.let(::decode)?.trim()?.ifEmpty { null }

    fun decode(s: String): String =
        cdata.replace(s) { it.groupValues[1] }
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
            .replace("&amp;", "&")

    data class Element(val attributes: String, val body: String) {
        fun attr(name: String): String? =
            Regex("""\b$name\s*=\s*["']([^"']*)["']""").find(attributes)?.groupValues?.get(1)
    }
}

internal object Gpx {
    fun parse(raw: String): Ride {
        val xml = Xml.stripComments(raw)
        val trackPoints = Xml.elements(xml, "trkpt").mapNotNull(::point)
        val routePoints = Xml.elements(xml, "rtept").map { it to point(it) }
        val waypoints = Xml.elements(xml, "wpt").mapNotNull { e -> point(e)?.let { stop(e, it) } }

        // A route (rte) without a track: its points are the stops.
        val stops = waypoints.ifEmpty {
            if (trackPoints.isEmpty()) routePoints.mapNotNull { (e, p) -> p?.let { stop(e, it) } } else emptyList()
        }
        val track = trackPoints.ifEmpty { if (waypoints.isNotEmpty()) routePoints.mapNotNull { it.second } else emptyList() }
        if (track.size < 2 && stops.size < 2) throw ImportException(ImportError.GPX_EMPTY)

        val name = listOf("metadata", "trk", "rte").firstNotNullOfOrNull { tag ->
            // Only the ride's <name>: strip the points first, they have their own <name>.
            Xml.elements(xml, tag).firstOrNull()?.body?.let { body -> Xml.text(withoutPoints(body), "name") }
        }
        // A planned stop (#10) or a night's stay (#12) is a real stop, even without a name.
        val guessed = Stop.guessKinds(stops, towns = false).mapIndexed { i, s ->
            val overnight = s.overnight && i != 0 && i != stops.lastIndex
            if (s.plan != null || overnight) s.copy(kind = Stop.Kind.STOP, overnight = overnight) else s.copy(overnight = false)
        }
        return Ride(name, track, guessed, Ride.Source.GPX, dayCap = dayCap(xml), version = version(xml))
    }

    private fun stop(e: Xml.Element, p: LatLon) = Stop(
        Xml.text(e.body, "name"),
        p,
        overnight = Xml.elements(e.body, "stop").firstOrNull()?.attr("overnight") == "1",
        plan = plan(e.body),
    )

    /** The daily cap written by RidePlan (#12) in the metadata. */
    private fun dayCap(xml: String): DayCap? {
        val meta = Xml.elements(xml, "metadata").firstOrNull()?.body ?: return null
        return Xml.elements(meta, "days").firstOrNull()?.attr("cap")?.let(RideLink::parseDayCap)
    }

    /**
     * The plan of a stop written by RidePlan (#10): purpose in `type`, note in `cmt`, time and
     * pause in the extension. Other apps' `type` and `cmt` are read too when they fit.
     */
    private fun plan(body: String): StopPlan? {
        val ext = Xml.elements(body, "stop").firstOrNull()
        return StopPlan(
            purpose = StopPlan.Purpose.of(Xml.text(body, "type")),
            time = ext?.attr("time")?.let(StopPlan::parseTime),
            pauseMinutes = ext?.attr("pause")?.toIntOrNull() ?: 0,
            note = Xml.text(body, "cmt").orEmpty(),
        ).normalized()
    }

    /** The ride version written by RidePlan (#11), with its note from the metadata description. */
    private fun version(xml: String): RideVersion? {
        val meta = Xml.elements(xml, "metadata").firstOrNull()?.body ?: return null
        val e = Xml.elements(meta, "version").firstOrNull() ?: return null
        val id = e.attr("id")?.takeIf { it.isNotBlank() && it.length <= 64 } ?: return null
        val number = e.attr("number")?.toIntOrNull()?.takeIf { it >= 1 } ?: return null
        val note = Xml.text(meta, "desc")?.removePrefix("v$number:")?.trim().orEmpty()
        return RideVersion(id, number, note.take(RideVersion.MAX_NOTE), e.attr("updated")?.toLongOrNull() ?: 0)
    }

    private val points = Regex("""<(trkseg|trkpt|rtept|wpt)\b.*?</\1\s*>""", RegexOption.DOT_MATCHES_ALL)

    private fun withoutPoints(body: String) = points.replace(body, "")

    private fun point(e: Xml.Element): LatLon? {
        val lat = e.attr("lat")?.toDoubleOrNull() ?: return null
        val lon = e.attr("lon")?.toDoubleOrNull() ?: return null
        return runCatching { LatLon(lat, lon) }.getOrNull()
    }
}

internal object Kml {
    fun parse(raw: String): Ride {
        val xml = Xml.stripComments(raw)
        val track = mutableListOf<LatLon>()
        val stops = mutableListOf<Stop>()
        for (placemark in Xml.elements(xml, "Placemark")) {
            val name = Xml.text(placemark.body, "name")
            for (line in Xml.elements(placemark.body, "LineString")) {
                Xml.elements(line.body, "coordinates").forEach { track += coordinates(it.body) }
            }
            // Recorded track (gx:Track): "lon lat alt" per line.
            Xml.elements(placemark.body, "coord").forEach { c ->
                val parts = c.body.trim().split(Regex("\\s+"))
                coord(parts.getOrNull(0), parts.getOrNull(1))?.let { track += it }
            }
            for (pt in Xml.elements(placemark.body, "Point")) {
                Xml.elements(pt.body, "coordinates").firstOrNull()?.let { coordinates(it.body).firstOrNull() }
                    ?.let { stops += Stop(name, it) }
            }
        }
        if (track.size < 2 && stops.size < 2) throw ImportException(ImportError.KML_EMPTY)
        val name = Xml.elements(xml, "Document").firstOrNull()?.body?.let { body ->
            Regex("""^\s*<name>(.*?)</name>""", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)?.let(Xml::decode)?.trim()
        }
        return Ride(name?.ifEmpty { null }, track, Stop.guessKinds(stops, towns = false), Ride.Source.KML)
    }

    /** "lon,lat[,alt] lon,lat[,alt] ..." */
    private fun coordinates(s: String): List<LatLon> =
        s.trim().split(Regex("\\s+")).mapNotNull { tuple ->
            val p = tuple.split(',')
            coord(p.getOrNull(0), p.getOrNull(1))
        }

    private fun coord(lon: String?, lat: String?): LatLon? {
        val la = lat?.toDoubleOrNull() ?: return null
        val lo = lon?.toDoubleOrNull() ?: return null
        return runCatching { LatLon(la, lo) }.getOrNull()
    }
}

/**
 * Google Maps links that are already full (after opening the short one).
 * Formats: /maps/dir/A/B/C/data=...!3d<lat>!4d<lon>..., /maps/place/Name/@lat,lon,
 * ?api=1&origin=..&destination=..&waypoints=a|b.
 * Google gives only the stops, not the road: the road is recalculated (#3).
 */
internal object GoogleMapsLink {
    private val coordPair = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*$""")
    private val dataCoords = Regex("""!3d(-?\d+(?:\.\d+)?)!4d(-?\d+(?:\.\d+)?)""")
    private val atCoords = Regex("""/@(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")

    fun parse(url: String): Ride {
        if (!url.contains("google.") || !url.contains("/maps")) {
            throw ImportException(ImportError.NOT_GOOGLE_MAPS)
        }
        val path = url.substringBefore('?')
        val query = url.substringAfter('?', "")
        val stops = when {
            query.contains("api=1") -> apiLink(query)
            "/maps/dir/" in path -> directions(path)
            "/maps/place/" in path -> listOfNotNull(place(path))
            else -> emptyList()
        }
        if (stops.isEmpty()) throw ImportException(ImportError.NO_STOPS)
        if (stops.size == 1) throw ImportException(ImportError.SINGLE_PLACE)
        return Ride(null, emptyList(), Stop.guessKinds(stops, towns = true), Ride.Source.GOOGLE_LINK)
    }

    private fun directions(path: String): List<Stop> {
        val afterDir = path.substringAfter("/maps/dir/")
        val segments = afterDir.split('/')
            .takeWhile { !it.startsWith("data=") && !it.startsWith("@") && !it.startsWith("am=") }
            .map(::urlDecode).filter { it.isNotBlank() }
        val coords = ArrayDeque(dataCoords.findAll(path).map { latLon(it.groupValues[1], it.groupValues[2]) }.toList())
        return segments.mapNotNull { seg ->
            val literal = coordPair.matchEntire(seg)
            when {
                literal != null -> latLon(literal.groupValues[1], literal.groupValues[2])?.let { Stop(null, it) }
                else -> coords.removeFirstOrNull()?.let { Stop(seg, it) }
            }
        }.takeIf { it.size == segments.size } ?: throw ImportException(ImportError.STOP_WITHOUT_COORDS)
    }

    private fun apiLink(query: String): List<Stop> {
        val params = query.split('&').associate { it.substringBefore('=') to urlDecode(it.substringAfter('=', "")) }
        val names = listOfNotNull(params["origin"]) + (params["waypoints"]?.split('|') ?: emptyList()) + listOfNotNull(params["destination"])
        return names.map { n ->
            val m = coordPair.matchEntire(n) ?: throw ImportException(ImportError.LINK_NAMES_WITHOUT_COORDS)
            Stop(null, latLon(m.groupValues[1], m.groupValues[2]) ?: throw ImportException(ImportError.INVALID_COORDS))
        }
    }

    private fun place(path: String): Stop? {
        val name = urlDecode(path.substringAfter("/maps/place/").substringBefore('/'))
        val p = dataCoords.find(path)?.let { latLon(it.groupValues[1], it.groupValues[2]) }
            ?: atCoords.find(path)?.let { latLon(it.groupValues[1], it.groupValues[2]) }
        return p?.let { Stop(name.ifBlank { null }, it) }
    }

    private fun latLon(lat: String, lon: String): LatLon? =
        runCatching { LatLon(lat.toDouble(), lon.toDouble()) }.getOrNull()

    /** Decodes %XX (UTF-8) and '+'. */
    fun urlDecode(s: String): String {
        val bytes = ArrayList<Byte>(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '+' -> { bytes += ' '.code.toByte(); i++ }
                c == '%' && i + 2 < s.length -> {
                    val b = s.substring(i + 1, i + 3).toIntOrNull(16)
                    if (b != null) { bytes += b.toByte(); i += 3 } else { bytes += c.code.toByte(); i++ }
                }
                else -> { c.toString().encodeToByteArray().forEach { bytes += it }; i++ }
            }
        }
        return bytes.toByteArray().decodeToString()
    }
}
