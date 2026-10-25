package hu.rsc.shelflife.data

import android.content.Context

/**
 * Ertesitesi beallitasok: be van-e kapcsolva a lejarat-emlekezteto, es
 * hany nappal a lejarat elott szoljon. Alapertelmezes: BEKAPCSOLVA (opt-out:
 * a felhasznalonak kell kikapcsolnia, ha nem keri), 2 nap. Android 13+ alatt
 * a rendszer-engedelyt a bemutato vegen kerjuk be; ha ott elutasitja, az
 * kikapcsolasnak szamit (lasd OnboardingScreen).
 */
class NotificationSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
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

    /**
     * Diagnosztika: mikor futott utoljara a hatterellenorzes (epoch millis),
     * null = meg soha. A NotificationSettingsDialog teszt-blokkja mutatja.
     */
    var lastCheckMillis: Long?
        get() = prefs.getLong(KEY_LAST_CHECK, NONE).takeIf { it != NONE }
        set(value) = prefs.edit().putLong(KEY_LAST_CHECK, value ?: NONE).apply()

    /** Diagnosztika: az utolso futas eredmenye (ExpiryReminderWorker.OUTCOME_*). */
    var lastCheckOutcome: String?
        get() = prefs.getString(KEY_LAST_OUTCOME, null)
        set(value) = prefs.edit().putString(KEY_LAST_OUTCOME, value).apply()

    /** Diagnosztika: hany tetelrol kuldott ertesitest az utolso futas. */
    var lastCheckNotifiedCount: Int
        get() = prefs.getInt(KEY_LAST_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_COUNT, value).apply()

    /**
     * Melyik napon ment ki utoljara a napi osszesito (epoch-nap). A reggeli
     * ebresztes es a tartalek WorkManager-futas is ezt nezi, igy egy nap
     * legfeljebb egyszer szolunk.
     */
    var lastSummaryEpochDay: Long?
        get() = prefs.getLong(KEY_LAST_SUMMARY, NONE).takeIf { it != NONE }
        set(value) = prefs.edit().putLong(KEY_LAST_SUMMARY, value ?: NONE).apply()

    /** Diagnosztika: mikorra van beallitva a kovetkezo reggeli ebresztes (epoch millis). */
    var nextAlarmMillis: Long?
        get() = prefs.getLong(KEY_NEXT_ALARM, NONE).takeIf { it != NONE }
        set(value) = prefs.edit().putLong(KEY_NEXT_ALARM, value ?: NONE).apply()

    /**
     * Diagnosztika: az utolso RUN_LOG_SIZE futas, legujabb elol.
     * Sor-formatum: "millis|trigger|outcome|count".
     */
    val runLog: List<RunLogEntry>
        get() = prefs.getString(KEY_RUN_LOG, "").orEmpty()
            .lineSequence()
            .mapNotNull { RunLogEntry.parse(it) }
            .toList()

    fun appendRunLog(entry: RunLogEntry) {
        val lines = listOf(entry.serialize()) + runLog.map { it.serialize() }
        prefs.edit().putString(KEY_RUN_LOG, lines.take(RUN_LOG_SIZE).joinToString("\n")).apply()
    }

    data class RunLogEntry(val millis: Long, val trigger: String, val outcome: String, val count: Int) {
        fun serialize() = "$millis|$trigger|$outcome|$count"

        companion object {
            fun parse(line: String): RunLogEntry? {
                val parts = line.split('|')
                if (parts.size != 4) return null
                val millis = parts[0].toLongOrNull() ?: return null
                return RunLogEntry(millis, parts[1], parts[2], parts[3].toIntOrNull() ?: 0)
            }
        }
    }

    companion object {
        private const val KEY_LAST_SUMMARY = "last_summary_epoch_day"
        private const val KEY_NEXT_ALARM = "next_alarm_millis"
        private const val KEY_RUN_LOG = "run_log"
        private const val RUN_LOG_SIZE = 10
        private const val KEY_LAST_CHECK = "last_check_millis"
        private const val KEY_LAST_OUTCOME = "last_check_outcome"
        private const val KEY_LAST_COUNT = "last_check_count"
        private const val KEY_REFINE_ENABLED = "refine_reminder_enabled"
        private const val KEY_REFINE_LAST = "refine_reminder_last_epoch_day"
        private const val NONE = Long.MIN_VALUE
        private const val PREFS_NAME = "shelflife_notification_settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_DAYS_BEFORE = "days_before"
        const val DEFAULT_DAYS_BEFORE = 2
    }
}
