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

    companion object {
        private const val PREFS_NAME = "shelflife_notification_settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_DAYS_BEFORE = "days_before"
        const val DEFAULT_DAYS_BEFORE = 2
    }
}
