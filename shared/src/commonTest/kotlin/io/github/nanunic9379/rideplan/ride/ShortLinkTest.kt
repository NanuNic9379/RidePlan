package io.github.nanunic9379.rideplan.ride

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A shared "Maps link" only ever reaches Google (#113). */
class ShortLinkTest {
    @Test
    fun googleHostsOnly() {
        listOf(
            "https://maps.app.goo.gl/abc", "https://goo.gl/maps/x", "https://www.google.com/maps/dir/a/b",
            "https://maps.google.co.uk/maps?x", "https://consent.google.de/ml?continue=x", "https://google.it/maps",
        ).forEach { assertTrue(ShortLink.isGoogleHttps(it), it) }
        listOf(
            "http://maps.app.goo.gl/abc", "https://evil.example/maps", "https://google.com.evil.example/maps",
            "https://notgoo.gl/x", "https://www.google.com@evil.example/", "https://google.com:8443/maps", "ftp://goo.gl/x",
        ).forEach { assertFalse(ShortLink.isGoogleHttps(it), it) }
    }

    @Test
    fun followsGoogleRedirects() {
        val asked = ArrayList<String>()
        val full = ShortLink.resolve("https://maps.app.goo.gl/abc") { url ->
            asked += url
            when (url) {
                "https://maps.app.goo.gl/abc" -> 302 to "https://consent.google.com/ml?continue=https%3A%2F%2Fwww.google.com%2Fmaps%2Fdir%2FA%2FB&gl=IT"
                else -> 302 to "https://www.google.com/maps/dir/A/B"
            }
        }
        assertEquals("https://www.google.com/maps/dir/A/B", full)
        assertEquals(listOf("https://maps.app.goo.gl/abc"), asked)
    }

    @Test
    fun foreignRedirectStopsWithoutASecondRequest() {
        val asked = ArrayList<String>()
        val e = assertFailsWith<ImportException> {
            ShortLink.resolve("https://maps.app.goo.gl/abc") { url -> asked += url; 302 to "https://tracker.example/maps?id=1" }
        }
        assertEquals(ImportError.LINK_NO_ROUTE, e.error)
        assertEquals(1, asked.size)
    }

    @Test
    fun foreignFirstLinkMakesNoRequest() {
        var asked = 0
        assertFailsWith<ImportException> { ShortLink.resolve("http://maps.app.goo.gl/abc") { asked++; 302 to null } }
        assertEquals(0, asked)
    }

    @Test
    fun relativeLocation() {
        assertEquals("https://www.google.com/maps/x", ShortLink.absolute("https://www.google.com/a/b", "/maps/x"))
    }
}
