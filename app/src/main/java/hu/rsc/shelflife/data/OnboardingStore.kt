package hu.rsc.shelflife.data

import android.content.Context

/** Megjegyzi, hogy a felhasznalo mar vegigment-e (vagy atugrotta) a bemutatot. */
class OnboardingStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var completed: Boolean
        get() = prefs.getBoolean(KEY_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_COMPLETED, value).apply()

    companion object {
        private const val PREFS_NAME = "shelflife_onboarding"
        private const val KEY_COMPLETED = "completed"
    }
}
