package hu.rsc.shelflife.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * A reggeli ebresztes (ACTION_DAILY_CHECK, sajat PendingIntent, nem exportalt
 * receiver): beallitja a kovetkezo napit, es goAsync()-kal lefuttatja az
 * ellenorzest (egy Room-lekerdezes + ertesites, joval a ~10 mp-es keret alatt).
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        if (intent.action != ACTION_DAILY_CHECK) return
        // Eloszor a kovetkezo ebresztes -- ha az ellenorzes elszallna, holnap akkor is fusson.
        ReminderScheduler.armAlarm(appContext)
        val pending = goAsync()
        scope.launch {
            try {
                ExpiryCheck.run(appContext, ExpiryCheck.TRIGGER_ALARM)
            } catch (e: Exception) {
                Log.e(TAG, "Daily expiry check failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ReminderAlarm"
        const val ACTION_DAILY_CHECK = "hu.rsc.shelflife.action.DAILY_CHECK"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
