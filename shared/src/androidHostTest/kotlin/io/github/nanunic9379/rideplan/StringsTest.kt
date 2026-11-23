package io.github.nanunic9379.rideplan

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.w3c.dom.Element

/**
 * Checks the translations (#33): every language has all the keys of English (the base language),
 * no extra keys, and the same placeholders (%1$s, %1$d) as the English text.
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
    fun everyLanguageComplete() {
        val base = load(File(dir, "values"))
        val languages = dir.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }!!.sortedBy { it.name }
        assertTrue(languages.isNotEmpty())
        for (lang in languages) {
            val tr = load(lang)
            assertEquals(base.keys, tr.keys, "Different keys in ${lang.name}")
            for ((key, text) in base) {
                assertEquals(
                    placeholder.findAll(text).map { it.value }.toSet(),
                    placeholder.findAll(tr.getValue(key)).map { it.value }.toSet(),
                    "Different placeholders in ${lang.name}/$key",
                )
                assertTrue(tr.getValue(key).isNotBlank(), "Empty text in ${lang.name}/$key")
            }
        }
    }

    /** The languages offered by Android 13+ in the app settings must be the translated ones. */
    @Test
    fun androidLanguagesAligned() {
        val translated = dir.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }!!
            .map { it.name.removePrefix("values-") }.toSet() + "en"
        val config = File("../androidApp/src/main/res/xml/locales_config.xml").readText()
        val declared = Regex("""android:name="([^"]+)"""").findAll(config).map { it.groupValues[1] }.toSet()
        assertEquals(translated, declared)
    }
}
