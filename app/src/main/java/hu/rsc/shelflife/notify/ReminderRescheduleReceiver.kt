package hu.rsc.shelflife.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Rendszer-esemenyek (ujrainditas, app-frissites, ora/idozona-valtas): az
 * AlarmManager ezeknel elfelejti a reggeli ebresztest, ezert ujraelesitjuk.
 * Exportalt (rendszer-broadcast), de csak ujrautemez -- ellenorzest nem indit.
 */
class ReminderRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> ReminderScheduler.rescheduleIfEnabled(context.applicationContext)
        }
    }
}
