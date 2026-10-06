package hu.rsc.shelflife.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.MobileAds
import hu.rsc.shelflife.BuildConfig
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reklam: hozzajarulas (UMP, EGT/UK/CH-ban kotelezo) + a Mobile Ads SDK
 * inditasa. A reklam-composable-ok csak akkor toltenek, ha [canRequestAds].
 *
 * Elv: rogzites kozben (kamera, gyorsracs, dialogusok) SOHA nincs reklam,
 * es nincs teljes kepernyos reklam. Csak ket hely: banner a lista aljan
 * (PantryScreen) es egy nativ hirdetes a statisztika-kepernyon.
 * A reklam semmit nem kap a lista tartalmabol.
 */
object AdsManager {

    const val TAG = "ShelfLifeAds"

    /** Igaz, ha a hozzajarulas rendben van es az SDK el lett inditva. */
    var canRequestAds by mutableStateOf(false)
        private set

    /** Kell-e "Adatvedelmi beallitasok (hirdetesek)" menupont (EGT-ben igen). */
    var privacyOptionsRequired by mutableStateOf(false)
        private set

    private val initStarted = AtomicBoolean(false)

    /**
     * Minden indulaskor hivando (az onboarding utan). Ha kell, megmutatja a
     * hozzajarulasi urlapot; utana inditja az SDK-t. Olcso, ismetelheto.
     */
    fun gatherConsent(activity: Activity) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()
        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                Log.d(TAG, "Consent info updated: status=${info.consentStatus}, canRequestAds=${info.canRequestAds()}")
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error ${formError.errorCode}: ${formError.message}")
                    }
                    onConsentResolved(activity, info)
                }
            },
            { error ->
                Log.w(TAG, "Consent info update FAILED ${error.errorCode}: ${error.message}")
                onConsentResolved(activity, info)
            }
        )
        // Korabbi munkamenetbol mar meglevo hozzajarulas: ne varjunk a frissitesre.
        if (info.canRequestAds()) initializeSdk(activity)
    }

    /** A menubol: a hozzajarulas modositasa / visszavonasa. */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { _ ->
            val info = UserMessagingPlatform.getConsentInformation(activity)
            onConsentResolved(activity, info)
        }
    }

    private fun onConsentResolved(activity: Activity, info: ConsentInformation) {
        privacyOptionsRequired = info.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        Log.d(TAG, "Consent resolved: status=${info.consentStatus}, canRequestAds=${info.canRequestAds()}, privacyOptions=${info.privacyOptionsRequirementStatus}")
        if (info.canRequestAds()) {
            initializeSdk(activity)
        } else if (BuildConfig.DEBUG) {
            // Fejlesztes alatt (teszt-azonositokkal) a hozzajarulasi uzenet meg
            // nincs beallitva az AdMobban, ezert a UMP hibat ad / nem engedi a
            // reklamot. Debug buildben ettol fuggetlenul inditunk, hogy a
            // TESZT-reklamok lathatok legyenek. Release buildben ez nem fut.
            Log.w(TAG, "DEBUG: consent not obtained -> starting SDK anyway (test ads only)")
            initializeSdk(activity)
        }
    }

    private fun initializeSdk(context: Context) {
        if (!initStarted.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        // Az inicializalas lassu lehet: ne a fo szalon.
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(appContext) { status ->
                Log.d(TAG, "MobileAds initialized: ${status.adapterStatusMap.mapValues { it.value.initializationState }}")
            }
            withContext(Dispatchers.Main) {
                canRequestAds = true
                Log.d(TAG, "canRequestAds = true")
            }
        }
    }
}
