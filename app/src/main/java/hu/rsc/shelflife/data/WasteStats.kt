package hu.rsc.shelflife.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Pazarlas-/megmentes-statisztika a lezart (elfogyott / kidobott) tetelekbol.
 * Tiszta Kotlin, Android-fuggoseg nelkul -> unit-tesztelheto (WasteStatsTest).
 *
 * Mertekegyseg: tetel (egy felvitt sor). A "-1" gombos reszleges fogyasztas
 * nem kulon esemeny, csak az egesz tetel lezarasa szamit.
 */
enum class StatsPeriod { LAST_30_DAYS, ALL_TIME }

data class MonthBar(val month: YearMonth, val saved: Int, val wasted: Int)

data class WastedProduct(val name: String, val count: Int)

data class WasteStats(
    val period: StatsPeriod,
    /** Elfogyott tetelek a periodusban. */
    val saved: Int,
    /** Kidobott tetelek a periodusban. */
    val wasted: Int,
    /**
     * "Az utolso pillanatban megmentve": elfogyott, es a lezaras a lejarat
     * elotti utolso [RESCUE_WINDOW_DAYS] napra (vagy kesobbre) esett -- vagyis
     * jo esellyel kidobas lett volna belole.
     */
    val rescued: Int,
    /** Elfogyott / (elfogyott + kidobott), egeszre lefele kerekitve; null, ha nincs adat. */
    val saveRatePercent: Int?,
    /** Ugyanez az elozo 30 napra (csak LAST_30_DAYS periodusnal), osszehasonlitashoz. */
    val previousRatePercent: Int?,
    /** Hany napja volt az utolso kidobas; null, ha meg soha. */
    val daysSinceLastWaste: Long?,
    /** Az utolso 6 naptari honap (a mostanival bezarolag), idorendben. */
    val months: List<MonthBar>,
    /** Az utolso 90 napban legalabb ketszer kidobott termekek (max. 3). */
    val frequentlyWasted: List<WastedProduct>,
    /** Aktiv tetelek, amelyek 3 napon belul lejarnak (vagy mar lejartak). */
    val expiringSoon: Int
) {
    val total: Int get() = saved + wasted
    val hasAnyHistory: Boolean get() = months.any { it.saved + it.wasted > 0 } || total > 0

    /** Szazalekpontnyi javulas az elozo 30 naphoz kepest (csak ha pozitiv). */
    val improvementPoints: Int?
        get() {
            val now = saveRatePercent ?: return null
            val prev = previousRatePercent ?: return null
            return (now - prev).takeIf { it > 0 }
        }

    companion object {
        const val RESCUE_WINDOW_DAYS = 3L
        const val EXPIRING_SOON_DAYS = 3L
        private const val PERIOD_DAYS = 30L
        private const val FREQUENT_WINDOW_DAYS = 90L
        private const val MONTHS_SHOWN = 6

        fun compute(
            finished: List<PantryItem>,
            active: List<PantryItem>,
            period: StatsPeriod,
            today: LocalDate = LocalDate.now()
        ): WasteStats {
            // Csak a datummal lezart tetelek szamitanak (a finishedAt a v2-tol mindig kitoltott).
            val closed = finished.filter { it.status != ItemStatus.ACTIVE && it.finishedAt != null }

            fun inRange(item: PantryItem, fromInclusive: LocalDate, toInclusive: LocalDate) =
                !item.finishedAt!!.isBefore(fromInclusive) && !item.finishedAt.isAfter(toInclusive)

            val periodStart = today.minusDays(PERIOD_DAYS - 1)
            val inPeriod = when (period) {
                StatsPeriod.ALL_TIME -> closed
                StatsPeriod.LAST_30_DAYS -> closed.filter { inRange(it, periodStart, today) }
            }
            val saved = inPeriod.count { it.status == ItemStatus.CONSUMED }
            val wasted = inPeriod.count { it.status == ItemStatus.WASTED }
            val rescued = inPeriod.count {
                it.status == ItemStatus.CONSUMED &&
                    !it.finishedAt!!.isBefore(it.expiry.minusDays(RESCUE_WINDOW_DAYS - 1))
            }

            val previousRate = if (period == StatsPeriod.LAST_30_DAYS) {
                val prev = closed.filter {
                    inRange(it, periodStart.minusDays(PERIOD_DAYS), periodStart.minusDays(1))
                }
                rate(prev.count { it.status == ItemStatus.CONSUMED }, prev.count { it.status == ItemStatus.WASTED })
            } else null

            val lastWaste = closed.filter { it.status == ItemStatus.WASTED }.maxOfOrNull { it.finishedAt!! }
            val daysSinceLastWaste = lastWaste?.let { ChronoUnit.DAYS.between(it, today).coerceAtLeast(0) }

            val thisMonth = YearMonth.from(today)
            val months = (MONTHS_SHOWN - 1 downTo 0).map { back ->
                val ym = thisMonth.minusMonths(back.toLong())
                val ofMonth = closed.filter { YearMonth.from(it.finishedAt!!) == ym }
                MonthBar(
                    month = ym,
                    saved = ofMonth.count { it.status == ItemStatus.CONSUMED },
                    wasted = ofMonth.count { it.status == ItemStatus.WASTED }
                )
            }

            val frequentFrom = today.minusDays(FREQUENT_WINDOW_DAYS - 1)
            val frequentlyWasted = closed
                .filter { it.status == ItemStatus.WASTED && inRange(it, frequentFrom, today) }
                .groupBy { KnownProductEntity.keyOf(it.productName) }
                .filter { it.key.isNotBlank() && it.value.size >= 2 }
                .map { (_, group) ->
                    // A legutobb hasznalt irasmodot mutatjuk.
                    WastedProduct(group.maxBy { it.finishedAt!! }.productName.trim(), group.size)
                }
                .sortedWith(compareByDescending<WastedProduct> { it.count }.thenBy { it.name })
                .take(3)

            val soonLimit = today.plusDays(EXPIRING_SOON_DAYS)
            val expiringSoon = active.count { it.status == ItemStatus.ACTIVE && !it.expiry.isAfter(soonLimit) }

            return WasteStats(
                period = period,
                saved = saved,
                wasted = wasted,
                rescued = rescued,
                saveRatePercent = rate(saved, wasted),
                previousRatePercent = previousRate,
                daysSinceLastWaste = daysSinceLastWaste,
                months = months,
                frequentlyWasted = frequentlyWasted,
                expiringSoon = expiringSoon
            )
        }

        /** Lefele kerekitunk, igy 100% csak akkor jelenik meg, ha tenyleg semmi nem ment karba. */
        private fun rate(saved: Int, wasted: Int): Int? {
            val total = saved + wasted
            return if (total == 0) null else saved * 100 / total
        }
    }
}
