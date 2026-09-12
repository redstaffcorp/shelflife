package hu.rsc.shelflife.notify

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * A lejarati emlekezteto naponkenti hatterellenorzesenek utemezese.
 *
 * Tudatos dontes: WorkManager PeriodicWorkRequest, NEM AlarmManager exact
 * alarm es NEM foreground service.
 *  - Exact alarm (SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM): Google csak
 *    naptar-/ebreszto-jellegu appoknak ajanlja, a Play Store szigoruan
 *    ellenorzi a hasznalatat, es a felhasznalonak kulon, feltuno
 *    engedelyt kellene adnia ra a rendszerbeallitasokban -- egy "N
 *    nappal a lejarat elott" emlekezteto ehhez nem eleg idokritikus.
 *  - Foreground service: folyamatosan futna, allando ertesitest
 *    igenyelne magarol is, es feleslegesen fogyasztana az akkut egy
 *    olyan feladathoz, aminek naponta egyszer eleg lefutnia.
 *  - A WorkManager periodikus munkaja a hivatalosan ajanlott megoldas
 *    "deferrable, garantalt" hatterfeladatokhoz: a rendszer sajat,
 *    Doze-baratan optimalizalt utemezoje futtatja, tuleli az
 *    (app- vagy telefon-) ujrainditast automatikusan, es nem igenyel
 *    semmilyen specialis engedelyt a POST_NOTIFICATIONS-on kivul.
 */
object ReminderScheduler {

    private const val UNIQUE_WORK_NAME = "expiry_reminder_check"
    private const val PREFERRED_HOUR = 9

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ExpiryReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(computeInitialDelayMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun computeInitialDelayMinutes(): Long {
        val now = LocalDateTime.now()
        var next = now.withHour(PREFERRED_HOUR).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return Duration.between(now, next).toMinutes()
    }
}
