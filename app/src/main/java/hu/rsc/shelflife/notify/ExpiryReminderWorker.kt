package hu.rsc.shelflife.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters

/**
 * WorkManager-belepesi pont az ExpiryCheck-hez:
 *  - tartalek napi futas 9:30 korul (ReminderScheduler), ha a reggeli ebresztes
 *    valamiert elmaradt. Az ExpiryCheck naponta csak egyszer kuld osszesitot,
 *    igy a ket ut nem duplaz. Lefutas utan a holnapit fuzi maga utan.
 *  - teszt-futas (KEY_TEST_RUN), lasd NotificationSettingsDialog.
 */
class ExpiryReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val testRun = inputData.getBoolean(KEY_TEST_RUN, false)
        ExpiryCheck.run(applicationContext, if (testRun) ExpiryCheck.TRIGGER_TEST else ExpiryCheck.TRIGGER_WORKER)
        // A reggeli ebresztest is "ujraelesitjuk" -- ha valami (force stop,
        // ujrainditas) torolte, a tartalek futas visszaallitja.
        if (!testRun) {
            ReminderScheduler.armAlarm(applicationContext)
            ReminderScheduler.scheduleBackup(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
        return Result.success()
    }

    companion object {
        /** InputData kulcs: teszt-futas (lasd ReminderScheduler.scheduleTestRun). */
        const val KEY_TEST_RUN = "test_run"
    }
}
