package hu.rsc.shelflife.ocr

import java.time.LocalDate

/**
 * Gyors, szamjegyes lejarati datum bevitel ertelmezese (nap-honap-[ev] sorrend).
 *
 * Elfogadott formak:
 *  - "2510"      -> okt. 25., ev nelkul: a legkozelebbi ilyen datum (lasd lent)
 *  - "510"       -> okt. 5. (1 jegyu nap + 2 jegyu honap), vagy ha az nem ervenyes: "25 1" jellegu
 *  - "251026"    -> 2026. okt. 25.
 *  - "25102026"  -> 2026. okt. 25.
 *  - "25.10", "25 10 26", "5/1/2027" -- barmilyen nem-szamjegy elvalasztoval is
 *
 * Ev nelkul az idei evet vesszuk, kiveve ha az igy kapott datum tobb mint egy hete
 * mar elmult -- akkor a kovetkezo evet (pl. januar elejen beirt "0501" -> jovore nem,
 * de decemberben beirt "0501" -> jovo januar).
 */
object QuickDateInput {

    fun parse(input: String, today: LocalDate = LocalDate.now()): LocalDate? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(Regex("""\D+""")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null

        return if (parts.size >= 2) {
            if (parts.size > 3) return null
            val d = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            val y = parts.getOrNull(2)?.let { normalizeYear(it) ?: return null }
            build(d, m, y, today)
        } else {
            val digits = parts[0]
            when (digits.length) {
                3 -> build(digits.substring(0, 1).toInt(), digits.substring(1).toInt(), null, today)
                    ?: build(digits.substring(0, 2).toInt(), digits.substring(2).toInt(), null, today)
                4 -> build(digits.substring(0, 2).toInt(), digits.substring(2, 4).toInt(), null, today)
                6 -> build(
                    digits.substring(0, 2).toInt(), digits.substring(2, 4).toInt(),
                    normalizeYear(digits.substring(4)), today
                )
                8 -> build(
                    digits.substring(0, 2).toInt(), digits.substring(2, 4).toInt(),
                    normalizeYear(digits.substring(4)), today
                )
                else -> null
            }
        }
    }

    private fun normalizeYear(y: String): Int? = when (y.length) {
        2 -> 2000 + y.toInt()
        4 -> y.toInt().takeIf { it in 2000..2099 }
        else -> null
    }

    private fun build(d: Int, m: Int, y: Int?, today: LocalDate): LocalDate? {
        if (y != null) return safeDate(y, m, d)
        val thisYear = safeDate(today.year, m, d)
        if (thisYear != null && !thisYear.isBefore(today.minusDays(7))) return thisYear
        return safeDate(today.year + 1, m, d) ?: thisYear
    }

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? = try {
        LocalDate.of(y, m, d)
    } catch (e: Exception) {
        null
    }
}
