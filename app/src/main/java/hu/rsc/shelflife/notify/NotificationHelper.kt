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
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Lejarati ertesitesek letrehozasa es kikuldese. Nincs foreground service
 * es nincs exact alarm -- a WorkManager (lasd ReminderScheduler) egy
 * kb. naponkenti, rendszer altal Doze-baratan utemezett hatterellenorzest
 * futtat, ez a helper csak a tenyleges NotificationManager-hivasokert
 * felelos.
 */
object NotificationHelper {

    const val CHANNEL_ID = "expiry_reminders"
    private const val SUMMARY_NOTIFICATION_ID = 9000

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Lejárati emlékeztetők",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Értesítés, amikor egy kamrában rögzített termék lejárata közeleg."
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

    fun notifyExpiring(context: Context, items: List<PantryItem>) {
        if (items.isEmpty() || !hasPermission(context)) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val manager = NotificationManagerCompat.from(context)

        if (items.size == 1) {
            val item = items[0]
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Hamarosan lejár: ${item.productName}")
                .setContentText(daysLeftLabel(item.expiry))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            manager.notify(item.id.hashCode(), notification)
        } else {
            val style = NotificationCompat.InboxStyle()
            items.forEach { style.addLine("${it.productName} — ${daysLeftLabel(it.expiry)}") }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("${items.size} termék lejárata közeleg")
                .setContentText("Koppints a részletekért")
                .setStyle(style)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            manager.notify(SUMMARY_NOTIFICATION_ID, notification)
        }
    }

    private fun daysLeftLabel(expiry: LocalDate): String {
        val daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), expiry)
        return when {
            daysLeft < 0 -> "Lejárt"
            daysLeft == 0L -> "Ma jár le"
            daysLeft == 1L -> "Holnap jár le"
            else -> "$daysLeft nap múlva jár le"
        }
    }
}
