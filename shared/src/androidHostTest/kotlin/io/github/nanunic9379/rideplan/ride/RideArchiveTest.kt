package io.github.nanunic9379.rideplan.ride

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** #69: a KMZ is unpacked with a running limit, so a zip bomb cannot exhaust the phone's memory. */
class RideArchiveTest {
    private val kml = """<?xml version="1.0"?><kml><Document><name>Lakes</name></Document></kml>"""

    /** A zip with the given entries; each value is written [repeat] times. */
    private fun zip(vararg entries: Pair<String, ByteArray>, repeat: Int = 1): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.setLevel(Deflater.BEST_COMPRESSION)
            for ((name, data) in entries) {
                z.putNextEntry(ZipEntry(name))
                repeat(repeat) { z.write(data) }
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun plainFileAndNormalKmz() {
        assertEquals(kml, RideArchive.text(kml.encodeToByteArray()))
        val kmz = zip("images/icon.png" to ByteArray(2048) { 7 }, "doc.kml" to kml.encodeToByteArray())
        assertEquals(kml, RideArchive.text(kmz))
    }

    /** The issue's case: about 1 MB that expands to 1 GB stops at the plain-file limit. */
    @Test
    fun oneGigabyteBombFailsFast() {
        val bomb = zip("doc.kml" to ByteArray(1024 * 1024), repeat = 1024)
        assertTrue(bomb.size < 4 * 1024 * 1024, "the bomb itself is small: ${bomb.size}")
        val started = System.nanoTime()
        val e = assertFailsWith<ImportException> { RideArchive.text(bomb) }
        assertEquals(ImportError.FILE_TOO_BIG, e.error)
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue(ms < 5_000, "stopped after $ms ms")
    }

    /** A bomb hidden in an entry that is not the ride counts too. */
    @Test
    fun bombInAnotherEntry() {
        val bomb = zip("big.bin" to ByteArray(64 * 1024), "doc.kml" to kml.encodeToByteArray(), repeat = 64)
        val e = assertFailsWith<ImportException> { RideArchive.text(bomb, maxBytes = 1024 * 1024) }
        assertEquals(ImportError.FILE_TOO_BIG, e.error)
    }

    @Test
    fun tooManyEntries() {
        val many = zip(*Array(RideArchive.MAX_ENTRIES + 1) { "icon$it.png" to ByteArray(1) })
        val e = assertFailsWith<ImportException> { RideArchive.text(many) }
        assertEquals(ImportError.FILE_TOO_BIG, e.error)
    }

    @Test
    fun zipWithoutRide() {
        val e = assertFailsWith<ImportException> { RideArchive.text(zip("readme.txt" to ByteArray(10))) }
        assertEquals(ImportError.ZIP_WITHOUT_RIDE, e.error)
    }
}
