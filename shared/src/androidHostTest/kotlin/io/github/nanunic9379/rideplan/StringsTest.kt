package io.github.nanunic9379.rideplan

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.w3c.dom.Element

/**
 * Controlla le traduzioni (#33): ogni lingua ha tutte le chiavi dell'inglese (la lingua di base),
 * niente chiavi in piu', e gli stessi segnaposto (%1$s, %1$d) del testo inglese.
 */
class StringsTest {
    private val dir = File("src/commonMain/composeResources")
    private val placeholder = Regex("""%\d+\$[sd]""")

    private fun load(folder: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(folder, "strings.xml"))
        val out = linkedMapOf<String, String>()
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val e = strings.item(i) as Element
            out[e.getAttribute("name")] = e.textContent
        }
        val plurals = doc.getElementsByTagName("plurals")
        for (i in 0 until plurals.length) {
            val e = plurals.item(i) as Element
            val other = e.getElementsByTagName("item").let { items ->
                (0 until items.length).map { items.item(it) as Element }.first { it.getAttribute("quantity") == "other" }
            }
            out["plurals:" + e.getAttribute("name")] = other.textContent
        }
        return out
    }

    @Test
    fun ogniLinguaCompleta() {
        val base = load(File(dir, "values"))
        val languages = dir.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }!!.sortedBy { it.name }
        assertTrue(languages.isNotEmpty())
        for (lang in languages) {
            val tr = load(lang)
            assertEquals(base.keys, tr.keys, "Chiavi diverse in ${lang.name}")
            for ((key, text) in base) {
                assertEquals(
                    placeholder.findAll(text).map { it.value }.toSet(),
                    placeholder.findAll(tr.getValue(key)).map { it.value }.toSet(),
                    "Segnaposto diversi in ${lang.name}/$key",
                )
                assertTrue(tr.getValue(key).isNotBlank(), "Testo vuoto in ${lang.name}/$key")
            }
        }
    }

    /** Le lingue offerte da Android 13+ nelle impostazioni dell'app devono essere quelle tradotte. */
    @Test
    fun lingueAndroidAllineate() {
        val translated = dir.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }!!
            .map { it.name.removePrefix("values-") }.toSet() + "en"
        val config = File("../androidApp/src/main/res/xml/locales_config.xml").readText()
        val declared = Regex("""android:name="([^"]+)"""").findAll(config).map { it.groupValues[1] }.toSet()
        assertEquals(translated, declared)
    }
}
