package hu.rsc.shelflife.ads

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import hu.rsc.shelflife.BuildConfig
import hu.rsc.shelflife.R

/**
 * Kis nativ hirdetes (ikon, cim, szoveg, gomb, "Hirdetes" cimke) a
 * statisztika-kepernyon, kartyaba illesztve. Amig nem toltott be (vagy
 * nincs hozzajarulas), semmit nem jelenit meg.
 */
@Composable
fun NativeAdCard(modifier: Modifier = Modifier) {
    if (!AdsManager.canRequestAds) return
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(Unit) {
        var disposed = false
        val loader = AdLoader.Builder(context, BuildConfig.ADMOB_NATIVE_ID)
            .forNativeAd { ad ->
                if (disposed) {
                    ad.destroy()
                } else {
                    nativeAd?.destroy()
                    nativeAd = ad
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) = Unit
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build()
            )
            .build()
        loader.loadAd(AdRequest.Builder().build())
        onDispose {
            disposed = true
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    val ad = nativeAd ?: return
    val colors = NativeAdColors(
        onSurface = MaterialTheme.colorScheme.onSurface.toArgb(),
        onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant.toArgb(),
        primary = MaterialTheme.colorScheme.primary.toArgb(),
        onPrimary = MaterialTheme.colorScheme.onPrimary.toArgb(),
        badgeBg = MaterialTheme.colorScheme.tertiaryContainer.toArgb(),
        badgeText = MaterialTheme.colorScheme.onTertiaryContainer.toArgb()
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        AndroidView(
            factory = { ctx -> SmallNativeAdView(ctx).root },
            update = { view ->
                (view.tag as SmallNativeAdView).bind(ad, colors)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private data class NativeAdColors(
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val primary: Int,
    val onPrimary: Int,
    val badgeBg: Int,
    val badgeText: Int
)

/** Programozottan felepitett "small template" jellegu NativeAdView. */
private class SmallNativeAdView(context: Context) {
    val root = NativeAdView(context)
    private val badge = TextView(context)
    private val icon = ImageView(context)
    private val headline = TextView(context)
    private val body = TextView(context)
    private val cta = TextView(context)
    private val density = context.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    init {
        badge.apply {
            text = context.getString(R.string.ad_label)
            textSize = 11f
            setPadding(dp(6), dp(1), dp(6), dp(1))
        }
        icon.scaleType = ImageView.ScaleType.CENTER_CROP
        headline.apply {
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        body.apply {
            textSize = 13f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        cta.apply {
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }

        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(badge, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(headline, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) })
            addView(body, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(12) })
            addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(cta, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(12) })
        }
        root.addView(row)
        root.iconView = icon
        root.headlineView = headline
        root.bodyView = body
        root.callToActionView = cta
        root.tag = this
    }

    fun bind(ad: NativeAd, colors: NativeAdColors) {
        badge.setTextColor(colors.badgeText)
        badge.background = GradientDrawable().apply {
            cornerRadius = dp(4).toFloat()
            setColor(colors.badgeBg)
        }
        headline.setTextColor(colors.onSurface)
        body.setTextColor(colors.onSurfaceVariant)
        cta.setTextColor(colors.onPrimary)
        cta.background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(colors.primary)
        }

        headline.text = ad.headline
        body.text = ad.body
        body.visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
        cta.text = ad.callToAction
        cta.visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
        val drawable = ad.icon?.drawable
        icon.setImageDrawable(drawable)
        icon.visibility = if (drawable == null) View.GONE else View.VISIBLE
        root.setNativeAd(ad)
    }
}
