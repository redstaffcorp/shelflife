package hu.rsc.shelflife.ocr

import java.time.LocalDate

/**
 * Egy a kamera altal latott kepkockan talalt lejarati datum-jelolt.
 *
 * @param ambiguousDayMonth true, ha a nap/honap sorrend elviekben felcserelheto lett volna
 *                          (pl. 04/05/26 -> lehet aprilis 5 vagy majus 4 is).
 * @param hasPositiveKeyword true, ha a sor kornyeken volt EXP/BBE/LEJARAT-szeru kulcsszo.
 */
data class DateCandidate(
    val date: LocalDate,
    val rawMatch: String,
    val ambiguousDayMonth: Boolean,
    val hasPositiveKeyword: Boolean
)

/**
 * Az OCR altal visszaadott nyers szovegsorokbol probal lejarati datumot kiszurni.
 * Ez NEM ert semmit a szovegbol -- csak mintaillesztes + hasznos/kizaro kulcsszavak
 * + logikai ervenyessegi szurok kombinacioja. Szandekosan egyszeru, hogy a pilotban
 * jol lehessen latni, mikor es mennyire talal el.
 */
object DateParser {

    private val POSITIVE_KEYWORDS = listOf(
        "EXP", "EXPIRY", "EXPIRES", "BBE", "BEST BEFORE", "BEST-BEFORE",
        "LEJARAT", "LEJÁRAT", "FOGYASZTHATO", "FOGYASZTHATÓ",
        "MINOSEGET", "MINŐSÉGÉT", "FOGY", "USE BY", "USE-BY"
    )

    private val NEGATIVE_KEYWORDS = listOf(
        "LOT", "GYARTVA", "GYÁRTVA", "MFG", "PROD", "L:", "TETEL", "TÉTEL", "BATCH"
    )

    private val MONTH_NAMES = mapOf(
        "JAN" to 1, "FEB" to 2, "MAR" to 3, "MÁR" to 3, "APR" to 4, "ÁPR" to 4,
        "MAY" to 5, "MAJ" to 5, "MÁJ" to 5, "JUN" to 6, "JÚN" to 6,
        "JUL" to 7, "JÚL" to 7, "AUG" to 8, "SEP" to 9, "SZEPT" to 9,
        "OCT" to 10, "OKT" to 10, "NOV" to 11, "DEC" to 12
    )

    // pl. 2026.09.11 / 2026-09-11 / 2026/09/11 (ev-honap-nap)
    private val YMD = Regex("""\b(20\d{2})[.\-/](0?[1-9]|1[0-2])[.\-/](0?[1-9]|[12]\d|3[01])\b""")

    // pl. 11.09.2026 / 11/09/2026 (nap-honap-teljes ev)
    private val DMY_FULL = Regex("""\b(0?[1-9]|[12]\d|3[01])[.\-/](0?[1-9]|1[0-2])[.\-/](20\d{2})\b""")

    // pl. 11.09.26 (nap-honap-rovid ev) -- csak kulcsszo mellett fogadjuk el, mert nagyon ketertelmu
    private val DMY_SHORT = Regex("""\b(0?[1-9]|[12]\d|3[01])[.\-/](0?[1-9]|1[0-2])[.\-/](\d{2})\b""")

    // pl. 11 SEP 2026 / 11SZEPT26
    private val DAY_MONTHNAME_YEAR = Regex(
        """\b(0?[1-9]|[12]\d|3[01])\s*[.\-]?\s*([A-ZÁÉÍÓÖŐÚÜŰ]{3,6})\.?\s*(20\d{2}|\d{2})\b"""
    )

    fun findBestCandidate(lines: List<String>): DateCandidate? {
        val candidates = mutableListOf<DateCandidate>()
        for (index in lines.indices) {
            val line = lines[index].uppercase()
            if (NEGATIVE_KEYWORDS.any { line.contains(it) }) continue

            val keywordNearby = POSITIVE_KEYWORDS.any { line.contains(it) } ||
                (index > 0 && POSITIVE_KEYWORDS.any { lines[index - 1].uppercase().contains(it) })

            extractFromLine(line, keywordNearby)?.let { candidates.add(it) }
        }
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { it.hasPositiveKeyword } ?: candidates.first()
    }

    private fun extractFromLine(line: String, keywordNearby: Boolean): DateCandidate? {
        YMD.find(line)?.let { m ->
            val (y, mo, d) = m.destructured
            return buildCandidate(y.toInt(), mo.toInt(), d.toInt(), m.value, ambiguous = false, keywordNearby)
        }
        DMY_FULL.find(line)?.let { m ->
            val (d, mo, y) = m.destructured
            val dayInt = d.toInt()
            val monthInt = mo.toInt()
            val ambiguous = dayInt <= 12 && monthInt <= 12 && dayInt != monthInt
            return buildCandidate(y.toInt(), monthInt, dayInt, m.value, ambiguous, keywordNearby)
        }
        DAY_MONTHNAME_YEAR.find(line)?.let { m ->
            val (d, monName, y) = m.destructured
            val month = MONTH_NAMES.entries.firstOrNull { monName.startsWith(it.key) }?.value
            if (month != null) {
                return buildCandidate(normalizeYear(y), month, d.toInt(), m.value, ambiguous = false, keywordNearby)
            }
        }
        if (keywordNearby) {
            DMY_SHORT.find(line)?.let { m ->
                val (d, mo, y) = m.destructured
                val dayInt = d.toInt()
                val monthInt = mo.toInt()
                val ambiguous = dayInt <= 12 && monthInt <= 12 && dayInt != monthInt
                return buildCandidate(normalizeYear(y), monthInt, dayInt, m.value, ambiguous, keywordNearby)
            }
        }
        return null
    }

    private fun normalizeYear(y: String): Int {
        val yi = y.toInt()
        return if (yi < 100) 2000 + yi else yi
    }

    private fun buildCandidate(
        year: Int,
        month: Int,
        day: Int,
        raw: String,
        ambiguous: Boolean,
        keywordNearby: Boolean
    ): DateCandidate? {
        val date = try {
            LocalDate.of(year, month, day)
        } catch (e: Exception) {
            return null
        }
        val today = LocalDate.now()
        // Szanity-check: lejarat ritkan van mar a multban, es tullogikatlanul messze a jovoben
        // (ilyenkor valoszinuleg egy vonalkod-toredeket vagy tetelszamot olvasott be tevesen).
        if (date.isBefore(today.minusDays(30))) return null
        if (date.isAfter(today.plusYears(6))) return null
        return DateCandidate(date, raw, ambiguous, keywordNearby)
    }
}
