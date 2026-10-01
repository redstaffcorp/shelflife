package hu.rsc.shelflife.data

import android.content.Context

/**
 * Ertesitesi beallitasok: be van-e kapcsolva a lejarat-emlekezteto, es
 * hany nappal a lejarat elott szoljon. Alapertelmezes: kikapcsolva (amig
 * a felhasznalo kifejezetten be nem kapcsolja, es meg nem adja az
 * engedelyt), 2 nap.
 */
class NotificationSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var daysBefore: Int
        get() = prefs.getInt(KEY_DAYS_BEFORE, DEFAULT_DAYS_BEFORE)
        set(value) = prefs.edit().putInt(KEY_DAYS_BEFORE, value.coerceIn(0, 30)).apply()

    /**
     * Heti emlekezteto a becsult (meg pontositando) lejaratu tetelekrol. Csak
     * akkor el, ha a lejarati ertesites is be van kapcsolva (ugyanaz a napi
     * hatterellenorzes kuldi). Alapertelmezes: be.
     */
    var refineReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_REFINE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_REFINE_ENABLED, value).apply()

    /** Mikor ment ki utoljara a pontositas-emlekezteto (epoch-nap), null = meg soha. */
    var lastRefineReminderEpochDay: Long?
        get() = prefs.getLong(KEY_REFINE_LAST, NONE).takeIf { it != NONE }
        set(value) = prefs.edit().putLong(KEY_REFINE_LAST, value ?: NONE).apply()

    companion object {
        private const val KEY_REFINE_ENABLED = "refine_reminder_enabled"
        private const val KEY_REFINE_LAST = "refine_reminder_last_epoch_day"
        private const val NONE = Long.MIN_VALUE
        private const val PREFS_NAME = "shelflife_notification_settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_DAYS_BEFORE = "days_before"
        const val DEFAULT_DAYS_BEFORE = 2
    }
}
