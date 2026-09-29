package hu.rsc.shelflife.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import hu.rsc.shelflife.MainActivity
import hu.rsc.shelflife.R
import hu.rsc.shelflife.data.PantryItem
import hu.rsc.shelflife.ui.expiryLabel

/**
 * Lejarati ertesitesek letrehozasa es kikuldese. Nincs foreground service
 * es nincs exact alarm -- a WorkManager (lasd ReminderScheduler) egy
 * kb. naponkenti hatterellenorzest futtat, ez a helper csak a tenyleges
 * NotificationManager-hivasokert felelos.
 *
 * Minden tetel kulon ertesitest kap ket akcioval ("Elfogyott", "Holnap
 * szolj"), tobb tetelnel egy csoportba (Android bundle) osszefogva.
 */
object NotificationHelper {

    const val CHANNEL_ID = "expiry_reminders"
    private const val GROUP_KEY = "hu.rsc.shelflife.EXPIRY"
    private const val SUMMARY_NOTIFICATION_ID = 9000

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Egy teteles ertesites azonositoja (az akciok is ezzel zarjak be). */
    fun notificationIdFor(itemId: Long): Int = itemId.hashCode()

    fun notifyExpiring(context: Context, items: List<PantryItem>) {
        if (items.isEmpty() || !hasPermission(context)) return
        val manager = NotificationManagerCompat.from(context)
        val grouped = items.size > 1

        items.forEach { item ->
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notification_single_title, item.productName))
                .setContentText(statusText(context, item))
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(context))
                .addAction(
                    0,
                    context.getString(R.string.notification_action_consumed),
                    actionIntent(context, NotificationActionReceiver.ACTION_CONSUMED, item.id)
                )
                .addAction(
                    0,
                    context.getString(R.string.notification_action_snooze),
                    actionIntent(context, NotificationActionReceiver.ACTION_SNOOZE, item.id)
                )
            if (grouped) builder.setGroup(GROUP_KEY)
            manager.notify(notificationIdFor(item.id), builder.build())
        }

        if (grouped) {
            val style = NotificationCompat.InboxStyle()
            items.forEach {
                style.addLine(context.getString(R.string.notification_line, it.productName, statusText(context, it)))
            }
            val summary = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notification_multi_title, items.size))
                .setContentText(context.getString(R.string.notification_multi_text))
                .setStyle(style)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(context))
                .build()
            manager.notify(SUMMARY_NOTIFICATION_ID, summary)
        }
    }

    fun cancel(context: Context, itemId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationIdFor(itemId))
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionIntent(context: Context, action: String, itemId: Long): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(NotificationActionReceiver.EXTRA_ITEM_ID, itemId)
        }
        // Tetelenkent es akcionkent egyedi requestCode, kulonben az Android
        // ugyanazt a PendingIntent-et adna vissza (felulirt extrakkal).
        val requestCode = 31 * notificationIdFor(itemId) + action.hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // Becsult lejaratnal jelezzuk, hogy erdemes a csomagolason megnezni a valos datumot.
    private fun statusText(context: Context, item: PantryItem): String {
        val label = context.resources.expiryLabel(item.expiry)
        return if (item.expiryEstimated) {
            context.getString(R.string.notification_estimated, label)
        } else label
    }
}
