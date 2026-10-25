package hu.rsc.shelflife.notify

import android.content.Context
import hu.rsc.shelflife.data.NotificationSettingsStore
import hu.rsc.shelflife.data.NotificationSettingsStore.RunLogEntry
import hu.rsc.shelflife.data.PantryRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * A napi lejarat-ellenorzes magja. Harom helyrol hivjuk:
 *  - ReminderAlarmReceiver: a reggeli (nem pontos, engedely nelkuli) ebresztes,
 *  - ExpiryReminderWorker: a tartalek napi WorkManager-futas, ha az ebresztes elmaradt,
 *  - ExpiryReminderWorker teszt-modban (NotificationSettingsDialog).
 *
 * Logika: NAPONTA EGY osszesito ertesites, amig van lejaro (N napon belul) vagy
 * mar lejart tetel. A korabbi "tetelenkent csak egyszer" logika helyett: egy
 * lejart tetel ott marad a kamraban, tehat masnap is szolni kell rola. A
 * "Holnap szolj" gomb egy napra kiveszi a tetelt (snoozedUntil).
 */
object ExpiryCheck {

    const val TRIGGER_ALARM = "alarm"
    const val TRIGGER_WORKER = "worker"
    const val TRIGGER_TEST = "test"

    const val OUTCOME_NO_PERMISSION = "no_permission"
    const val OUTCOME_DISABLED = "disabled"
    const val OUTCOME_ALREADY_TODAY = "already_today"
    const val OUTCOME_TOO_EARLY = "too_early"
    const val OUTCOME_NOTHING_DUE = "nothing_due"
    const val OUTCOME_NOTIFIED = "notified"
    const val OUTCOME_TEST = "test"

    suspend fun run(context: Context, trigger: String) {
        val appContext = context.applicationContext
        val testRun = trigger == TRIGGER_TEST
        val settings = NotificationSettingsStore(appContext)
        // Frissites/ujratelepites utan hamarabb futhatunk, mint hogy az app
        // megnyilna -- csatorna nelkul az ertesites csendben elveszne.
        NotificationHelper.ensureChannel(appContext)

        fun log(outcome: String, count: Int) {
            val now = System.currentTimeMillis()
            settings.lastCheckMillis = now
            settings.lastCheckOutcome = outcome
            settings.lastCheckNotifiedCount = count
            settings.appendRunLog(RunLogEntry(now, trigger, outcome, count))
        }

        if (!NotificationHelper.hasPermission(appContext)) return log(OUTCOME_NO_PERMISSION, 0)
        // Teszt-futasnal a kikapcsolt beallitas sem allitja meg.
        if (!settings.enabled && !testRun) return log(OUTCOME_DISABLED, 0)

        val today = LocalDate.now()
        // A tartalek WorkManager-futas ne elozze meg a reggeli idopontot: csak
        // akkor szoljon, ha a reggeli ebresztes mar elmaradt.
        if (trigger == TRIGGER_WORKER && LocalTime.now().hour < ReminderScheduler.PREFERRED_HOUR) {
            return log(OUTCOME_TOO_EARLY, 0)
        }
        if (!testRun && settings.lastSummaryEpochDay == today.toEpochDay()) {
            return log(OUTCOME_ALREADY_TODAY, 0)
        }

        val repository = PantryRepository.get(appContext)
        val items = repository.getActiveItems()
        val threshold = settings.daysBefore

        val due = items
            .filter { item ->
                val daysLeft = ChronoUnit.DAYS.between(today, item.expiry)
                val snoozed = item.snoozedUntil?.isAfter(today) == true
                daysLeft <= threshold && (testRun || !snoozed)
            }
            .sortedBy { it.expiry }

        if (due.isNotEmpty()) {
            NotificationHelper.notifyDailySummary(appContext, due, today)
            if (!testRun) {
                settings.lastSummaryEpochDay = today.toEpochDay()
                // Diagnosztika ("Mar ertesitett tetelek") -- a logikat mar nem befolyasolja.
                repository.markNotified(due)
            }
        }

        if (testRun) {
            // Mindig szolunk, hogy latszodjon: a hatterfuttatas tenyleg lefutott.
            NotificationHelper.notifyTestResult(appContext, items.size, due.size, threshold)
            return log(OUTCOME_TEST, due.size)
        }

        // Heti pontositas-emlekezteto a becsult datumu tetelekrol (lasd RefineReminderPolicy).
        if (settings.refineReminderEnabled) {
            val toRefine = RefineReminderPolicy.itemsToRefine(items, today)
            if (RefineReminderPolicy.shouldSend(toRefine.size, settings.lastRefineReminderEpochDay, today)) {
                NotificationHelper.notifyRefine(appContext, toRefine.size)
                settings.lastRefineReminderEpochDay = today.toEpochDay()
            }
        }

        log(if (due.isEmpty()) OUTCOME_NOTHING_DUE else OUTCOME_NOTIFIED, due.size)
    }
}
