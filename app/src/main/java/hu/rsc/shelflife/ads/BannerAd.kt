package hu.rsc.shelflife.ads

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import hu.rsc.shelflife.BuildConfig

/**
 * Rogzitett (anchored) adaptiv banner a fo lista aljan. A Scaffold
 * bottomBar-jaba valo: a FAB-ok automatikusan fole kerulnek. Ha nincs
 * hozzajarulas vagy nem toltott be, nem foglal helyet.
 */
@Composable
fun AnchoredBannerAd(modifier: Modifier = Modifier) {
    if (!AdsManager.canRequestAds) return
    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val adSize = remember(widthDp) {
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
    }
    var failed by remember { mutableStateOf(false) }
    if (failed) return

    val adView = remember(adSize) {
        AdView(context).apply {
            adUnitId = BuildConfig.ADMOB_BANNER_ID
            setAdSize(adSize)
            adListener = object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    failed = true
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(adView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> adView.resume()
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adView.destroy()
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(adSize.height.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = {
                    (adView.parent as? ViewGroup)?.removeView(adView)
                    adView
                },
                modifier = Modifier
                    .width(adSize.width.dp)
                    .height(adSize.height.dp)
            )
        }
    }
}
