package io.github.nanunic9379.rideplan.ride

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * The text of an imported file (#27): a GPX or KML as it is, or the ride inside a KMZ (a zip with
 * a KML inside, Google My Maps export).
 *
 * A zip can expand to far more than it weighs (#69, "zip bomb"): every byte unpacked counts
 * against [maxBytes], while it is read, and at most [maxEntries] entries are looked at. Past
 * either limit the import stops with [ImportError.FILE_TOO_BIG] before the phone runs out of memory.
 */
object RideArchive {
    /** Same limit as a plain file. */
    const val MAX_BYTES = 30 * 1024 * 1024
    /** A My Maps KMZ has the KML and a few icons. */
    const val MAX_ENTRIES = 256

    fun text(bytes: ByteArray, maxBytes: Int = MAX_BYTES, maxEntries: Int = MAX_ENTRIES): String {
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (!isZip) return bytes.decodeToString()
        var budget = maxBytes.toLong()
        var entries = 0
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++entries > maxEntries) throw ImportException(ImportError.FILE_TOO_BIG, "zip entries")
                    val ride = entry.name.endsWith(".kml", ignoreCase = true) || entry.name.endsWith(".gpx", ignoreCase = true)
                    // Other entries are read through too (moving to the next one would unpack them
                    // anyway): they count against the same limit.
                    val out = if (ride) ByteArrayOutputStream() else null
                    budget -= drain(zip, out, budget)
                    if (out != null) return out.toByteArray().decodeToString()
                }
            }
        } catch (e: java.io.IOException) {
            throw ImportException(ImportError.FILE_UNREADABLE, e.javaClass.simpleName)
        }
        throw ImportException(ImportError.ZIP_WITHOUT_RIDE)
    }

    /** Reads the current entry into [out] (or discards it); more than [budget] bytes is too big. */
    private fun drain(input: InputStream, out: ByteArrayOutputStream?, budget: Long): Long {
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) return total
            total += n
            if (total > budget) throw ImportException(ImportError.FILE_TOO_BIG)
            out?.write(buf, 0, n)
        }
    }
}
