package io.github.nanunic9379.rideplan.ride

/**
 * Opening Google's short link (maps.app.goo.gl → full link, #113) without letting a shared link
 * make the phone contact any other server: every hop, the first one included, must be https
 * on a Google host, otherwise it stops with [ImportError.LINK_NO_ROUTE] before the request.
 */
object ShortLink {
    const val MAX_REDIRECTS = 6

    /** One request that does not follow redirects: the HTTP status and the Location header. */
    fun interface Fetch {
        fun head(url: String): Pair<Int, String?>
    }

    /** "google.com", "www.google.co.uk", "consent.google.de", "goo.gl", "maps.app.goo.gl". */
    fun isGoogleHttps(url: String): Boolean {
        if (!url.startsWith("https://", ignoreCase = true)) return false
        val host = url.substring(8).substringBefore('/').substringBefore('?').substringBefore('#')
            .lowercase()
        if ('@' in host || ':' in host) return false
        if (host == "goo.gl" || host.endsWith(".goo.gl")) return true
        val labels = host.split('.')
        val google = labels.indexOf("google")
        // google.<tld> or google.<sld>.<tld> (co.uk, com.au), with any subdomain before.
        return google >= 0 && labels.size - google - 1 in 1..2 && labels.drop(google + 1).all { it.isNotEmpty() && it.all(Char::isLetter) }
    }

    /** Follows the short link's redirects until it reaches the full Google Maps link. */
    fun resolve(shortUrl: String, fetch: Fetch): String {
        var url = shortUrl
        repeat(MAX_REDIRECTS) {
            if (!isGoogleHttps(url)) throw ImportException(ImportError.LINK_NO_ROUTE)
            if (!RideImport.isShortLink(url) && url.contains("/maps")) return url
            val (code, location) = fetch.head(url)
            if (code !in 300..399 || location == null) throw ImportException(ImportError.LINK_NO_ROUTE, "HTTP $code")
            url = consentTarget(absolute(url, location))
        }
        if (isGoogleHttps(url) && url.contains("/maps")) return url
        throw ImportException(ImportError.LINK_TOO_MANY_REDIRECTS)
    }

    /** [location] as a full URL, relative to [base] when it starts with "/". */
    internal fun absolute(base: String, location: String): String = when {
        location.contains("://") -> location
        location.startsWith("//") -> base.substringBefore("//") + location
        location.startsWith("/") -> base.substringBefore("://") + "://" + base.substringAfter("://").substringBefore('/') + location
        else -> base.substringBeforeLast('/') + "/" + location
    }

    /** In Europe Google may go through the cookie page: the real link is in the "continue" parameter. */
    internal fun consentTarget(url: String): String {
        if (!url.contains("consent.google.")) return url
        val cont = url.substringAfter("continue=", "").substringBefore('&')
        return if (cont.isEmpty()) url else percentDecode(cont)
    }

    private fun percentDecode(s: String): String {
        val out = ArrayList<Byte>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 <= s.lastIndex) {
                val b = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (b != null) { out += b.toByte(); i += 3; continue }
            }
            if (c == '+') out += ' '.code.toByte() else out.addAll(c.toString().encodeToByteArray().toList())
            i++
        }
        return out.toByteArray().decodeToString()
    }
}
