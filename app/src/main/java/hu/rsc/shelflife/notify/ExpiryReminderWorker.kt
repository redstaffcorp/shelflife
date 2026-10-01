package hu.rsc.shelflife.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import hu.rsc.shelflife.data.NotificationSettingsStore
import hu.rsc.shelflife.data.PantryRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Naponta kb. egyszer lefuto hatterellenorzes (lasd ReminderScheduler):
 * megnezi, van-e olyan tetel, aminek a lejarata a beallitott napszamon
 * belul van, es meg nem kuldtunk ra ertesitest -- ha igen, ertesitest
 * kuld, es megjegyzi, hogy erre a lejarati datumra mar tortent ertesites
 * (igy nem spammel naponta ugyanarra a tetelre).
 *
 * Ugyanez a futas kuldi a heti pontositas-emlekeztetot is (becsult datumok).
 *
 * Tudatosan NEM foreground service es NEM exact alarm: a WorkManager
 * altal ajanlott, akkumulator-barat, "deferrable periodic work" mintat
 * kovetjuk.
 */
class ExpiryReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = NotificationSettingsStore(applicationContext)
        if (!settings.enabled) return Result.success()
        if (!NotificationHelper.hasPermission(applicationContext)) return Result.success()

        val repository = PantryRepository.get(applicationContext)
        val items = repository.getActiveItems()
        if (items.isEmpty()) return Result.success()

        val today = LocalDate.now()
        val threshold = settings.daysBefore

        val due = items.filter { item ->
            val daysLeft = ChronoUnit.DAYS.between(today, item.expiry)
            val snoozed = item.snoozedUntil?.isAfter(today) == true
            daysLeft <= threshold && item.notifiedForExpiry != item.expiry && !snoozed
        }

        if (due.isNotEmpty()) {
            NotificationHelper.notifyExpiring(applicationContext, due)
            repository.markNotified(due)
        }

        // Heti pontositas-emlekezteto a becsult datumu tetelekrol (lasd RefineReminderPolicy).
        if (settings.refineReminderEnabled) {
            val toRefine = RefineReminderPolicy.itemsToRefine(items, today)
            if (RefineReminderPolicy.shouldSend(toRefine.size, settings.lastRefineReminderEpochDay, today)) {
                NotificationHelper.notifyRefine(applicationContext, toRefine.size)
                settings.lastRefineReminderEpochDay = today.toEpochDay()
            }
        }

        return Result.success()
    }
}
