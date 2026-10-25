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
import java.time.LocalDate

/**
 * Lejarati ertesitesek letrehozasa es kikuldese. Az utemezes a
 * ReminderScheduler dolga (reggeli ebresztes + tartalek WorkManager), a
 * dontes az ExpiryCheck-e; ez a helper csak a NotificationManager-hivasokert felel.
 *
 * Naponta EGY osszesito ertesites (notifyDailySummary). Egyetlen tetelnel
 * ket akciogombbal ("Elfogyott", "Holnap szolj").
 */
object NotificationHelper {

    const val CHANNEL_ID = "expiry_reminders"
    const val REFINE_CHANNEL_ID = "refine_reminders"
    /** MainActivity extra: nyitaskor a "Becsult datum" szuro legyen bekapcsolva. */
    const val EXTRA_SHOW_ESTIMATED = "hu.rsc.shelflife.SHOW_ESTIMATED"
    private const val REFINE_NOTIFICATION_ID = 9100
    private const val DAILY_NOTIFICATION_ID = 9000
    private const val MAX_SUMMARY_LINES = 6
    private const val TEST_NOTIFICATION_ID = 9200

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        // Kulon csatorna, hogy a felhasznalo a rendszerben kulon is lenemithassa.
        val refineChannel = NotificationChannel(
            REFINE_CHANNEL_ID,
            context.getString(R.string.refine_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.refine_channel_description)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
        manager.createNotificationChannel(refineChannel)
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

    /**
     * Napi osszesito: EGY ertesites az osszes lejaro / mar lejart tetelrol
     * (mindig ugyanazzal az azonositoval, igy a tegnapit felulirja).
     * Egyetlen tetelnel a tetel neve a cim, es ott vannak az akciogombok
     * ("Elfogyott", "Holnap szolj"); tobbnel InboxStyle lista.
     */
    fun notifyDailySummary(context: Context, items: List<PantryItem>, today: LocalDate) {
        if (items.isEmpty() || !hasPermission(context)) return
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)

        if (items.size == 1) {
            val item = items.first()
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
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
                .build()
            manager.notify(DAILY_NOTIFICATION_ID, notification)
            return
        }

        val expired = items.count { it.expiry.isBefore(today) }
        val soon = items.size - expired
        val summaryText = when {
            expired == 0 -> context.resources.getQuantityString(R.plurals.notification_summary_soon, soon, soon)
            soon == 0 -> context.resources.getQuantityString(R.plurals.notification_summary_expired, expired, expired)
            else -> context.getString(
                R.string.notification_summary_both,
                context.resources.getQuantityString(R.plurals.notification_summary_expired, expired, expired),
                context.resources.getQuantityString(R.plurals.notification_summary_soon, soon, soon)
            )
        }
        val style = NotificationCompat.InboxStyle().setSummaryText(summaryText)
        items.take(MAX_SUMMARY_LINES).forEach {
            style.addLine(context.getString(R.string.notification_line, it.productName, statusText(context, it)))
        }
        if (items.size > MAX_SUMMARY_LINES) {
            style.addLine(context.getString(R.string.notification_more, items.size - MAX_SUMMARY_LINES))
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_multi_title, items.size))
            .setContentText(summaryText)
            .setStyle(style)
            .setNumber(items.size)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        manager.notify(DAILY_NOTIFICATION_ID, notification)
    }

    /**
     * Heti emlekezteto: `count` tetel lejarata csak becsult. Koppintasra az app
     * a "Becsult datum" szurovel nyilik, igy rogton a pontositando tetelek latszanak.
     */
    fun notifyRefine(context: Context, count: Int) {
        if (count <= 0 || !hasPermission(context)) return
        // Frissites utan a worker hamarabb futhat, mint hogy az app megnyilna es
        // letrehozna az uj csatornat -- csatorna nelkul az ertesites elveszne.
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SHOW_ESTIMATED, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            REFINE_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = context.resources.getQuantityString(R.plurals.refine_notification_title, count, count)
        val notification = NotificationCompat.Builder(context, REFINE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.refine_notification_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.refine_notification_text)))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(REFINE_NOTIFICATION_ID, notification)
    }

    /** Teszt: azonnali ertesites a lejarati csatornan (engedely + csatorna ellenorzese). */
    fun notifyTestNow(context: Context): Boolean {
        if (!hasPermission(context)) return false
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_test_now_title))
            .setContentText(context.getString(R.string.notif_test_now_text))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
        return true
    }

    /** Teszt: a hatter-teszt futas eredmenye (mindig kimegy, ha van engedely). */
    fun notifyTestResult(context: Context, activeCount: Int, dueCount: Int, daysBefore: Int) {
        if (!hasPermission(context)) return
        val text = context.getString(R.string.notif_test_bg_text, activeCount, dueCount, daysBefore)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_test_bg_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
    }

    /** Az akciogombok utan: a napi ertesites eltuntetese. */
    fun cancel(context: Context, itemId: Long) {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(DAILY_NOTIFICATION_ID)
        // Regebbi verziobol itt maradt, tetelenkenti ertesites.
        manager.cancel(notificationIdFor(itemId))
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
