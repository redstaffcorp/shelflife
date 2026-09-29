package hu.rsc.shelflife.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import hu.rsc.shelflife.data.ItemStatus
import hu.rsc.shelflife.data.PantryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Az ertesites akciogombjai: "Elfogyott" (a tetel lezarasa) es "Holnap szolj"
 * (egy napra elhallgattatja, utana ujra ertesithet). Az app megnyitasa nelkul.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
        if (itemId < 0) return
        val appContext = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                val repository = PantryRepository.get(appContext)
                when (intent.action) {
                    ACTION_CONSUMED -> repository.finishItem(itemId, ItemStatus.CONSUMED)
                    ACTION_SNOOZE -> repository.snooze(itemId, LocalDate.now().plusDays(1))
                }
                NotificationHelper.cancel(appContext, itemId)
            } catch (e: Exception) {
                Log.e(TAG, "Notification action failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "NotificationAction"
        const val ACTION_CONSUMED = "hu.rsc.shelflife.action.CONSUMED"
        const val ACTION_SNOOZE = "hu.rsc.shelflife.action.SNOOZE"
        const val EXTRA_ITEM_ID = "item_id"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
