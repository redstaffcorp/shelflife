package hu.rsc.shelflife.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import hu.rsc.shelflife.data.NotificationSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * A napi lejarat-ellenorzes utemezese -- ket, egymast kiegeszito uton:
 *
 * 1) Reggeli ebresztes: AlarmManager.setWindow() PREFERRED_HOUR:00 es +ALARM_WINDOW_MINUTES
 *    kozott. Nem exact alarm (nem kell SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM).
 *    TANULSAG (2026.10.24): a korabbi setAndAllowWhileIdle() "inexact" ebresztes,
 *    amit a rendszer a beallitas es a celidopont kozti ido akar 75%-aval
 *    elcsusztathat -- delutan 9-re allitva ez akar 15 ora keses. A setWindow()
 *    a megadott ablakot tartja; Doze alatt a kovetkezo karbantartasi ablakig
 *    csuszhat, de reggel a telefon felvetelekor biztosan lefut.
 *    Az ebresztest ujrainditas/frissites/ora-valtas torli, ezt a
 *    ReminderRescheduleReceiver allitja vissza; az app minden inditasakor is
 *    ujraelesitjuk (mindig a KOVETKEZO PREFERRED_HOUR-ra, tehat nem csuszik).
 *
 * 2) Tartalek: WorkManager egyszeri munka BACKUP_HOUR:BACKUP_MINUTE-re, amely
 *    lefutasa utan a kovetkezo napit fuzi maga utan (APPEND_OR_REPLACE). Appnyitaskor
 *    KEEP -- a korabbi periodikus munkanal az idopont az elso utemezeshez kotodott
 *    (pl. 13:14), igy a tartalek is delutan futott. Az ExpiryCheck naponta
 *    legfeljebb egy osszesitot kuld, tehat a ket ut nem duplaz.
 *
 * Foreground service tovabbra sincs.
 */
object ReminderScheduler {

    /** A regi (periodikus) tartalek-munka neve -- csak a migracio miatt (cancel). */
    private const val LEGACY_PERIODIC_WORK_NAME = "expiry_reminder_check"
    private const val BACKUP_WORK_NAME = "expiry_reminder_daily"
    private const val TEST_WORK_NAME = "expiry_reminder_test"
    const val PREFERRED_HOUR = 9
    private const val ALARM_WINDOW_MINUTES = 20L
    private val BACKUP_TIME: LocalTime = LocalTime.of(PREFERRED_HOUR, 30)
    private const val ALARM_REQUEST_CODE = 4200

    fun schedule(context: Context) {
        armAlarm(context)
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(LEGACY_PERIODIC_WORK_NAME)
        scheduleBackup(context, ExistingWorkPolicy.KEEP)
    }

    /**
     * A tartalek-futas a kovetkezo BACKUP_TIME-ra. A workerbol APPEND_OR_REPLACE-szel
     * hivjuk (a futo munka utan fuzi a holnapit), appnyitaskor KEEP-pel.
     */
    fun scheduleBackup(context: Context, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<ExpiryReminderWorker>()
            .setInitialDelay(millisUntil(BACKUP_TIME), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(BACKUP_WORK_NAME, policy, request)
    }

    fun cancel(context: Context) {
        alarmManager(context).cancel(alarmIntent(context))
        NotificationSettingsStore(context).nextAlarmMillis = null
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(BACKUP_WORK_NAME)
        workManager.cancelUniqueWork(LEGACY_PERIODIC_WORK_NAME)
    }

    /** Rendszer-esemeny (ujrainditas, frissites) utan: csak ha a felhasznalo bekapcsolta. */
    fun rescheduleIfEnabled(context: Context) {
        if (NotificationSettingsStore(context).enabled) schedule(context)
    }

    /** A kovetkezo reggeli ebresztes beallitasa (a korabbit felulirja, idempotens). */
    fun armAlarm(context: Context) {
        val settings = NotificationSettingsStore(context)
        if (!settings.enabled) return
        val triggerAt = System.currentTimeMillis() + millisUntil(LocalTime.of(PREFERRED_HOUR, 0))
        alarmManager(context).setWindow(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            TimeUnit.MINUTES.toMillis(ALARM_WINDOW_MINUTES),
            alarmIntent(context)
        )
        settings.nextAlarmMillis = triggerAt
    }

    /**
     * Teszt: egyszeri hatterellenorzes `delayMinutes` perc mulva, ugyanazzal a
     * logikaval, mint a napi futas (teszt-modban).
     */
    fun scheduleTestRun(context: Context, delayMinutes: Long = 1) {
        val request = OneTimeWorkRequestBuilder<ExpiryReminderWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setInputData(workDataOf(ExpiryReminderWorker.KEY_TEST_RUN to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            TEST_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** A tartalek napi feladat allapota (diagnosztika), null = nincs utemezve. */
    fun dailyWorkInfo(context: Context): Flow<WorkInfo?> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(BACKUP_WORK_NAME)
            .map { list -> list.firstOrNull { !it.state.isFinished } ?: list.firstOrNull() }

    /** A teszt-futas allapota (diagnosztika), null = meg nem volt. */
    fun testWorkInfo(context: Context): Flow<WorkInfo?> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(TEST_WORK_NAME)
            .map { it.firstOrNull() }

    private fun alarmManager(context: Context) =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun alarmIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_DAILY_CHECK
        }
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Ennyi ms van hatra a kovetkezo `time` idopontig (ma, ha meg nem mult el, kulonben holnap). */
    private fun millisUntil(time: LocalTime): Long {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }
}
