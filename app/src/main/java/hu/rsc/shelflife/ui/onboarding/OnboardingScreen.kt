package hu.rsc.shelflife.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import hu.rsc.shelflife.R
import hu.rsc.shelflife.data.NotificationSettingsStore
import hu.rsc.shelflife.notify.NotificationHelper
import hu.rsc.shelflife.notify.ReminderScheduler
import hu.rsc.shelflife.ui.theme.ExpiryFresh
import hu.rsc.shelflife.ui.theme.ExpiryOverdue
import hu.rsc.shelflife.ui.theme.ExpirySoon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val PAGE_COUNT = 5

/**
 * Rovid bemutato az app mukodeserol: elso inditaskor jelenik meg (a kamera-
 * engedely elott), es a menubol barmikor ujra megnyithato. Minden oldal egy
 * egyszeru, Compose-ban rajzolt grafika + cim + 1-2 mondat.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == PAGE_COUNT - 1

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (!isLast) {
                    TextButton(onClick = onFinish) { Text(stringResource(R.string.onboarding_skip)) }
                } else {
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> OnboardingPage(
                        title = stringResource(R.string.onboarding_welcome_title),
                        text = stringResource(R.string.onboarding_welcome_text)
                    ) { WelcomeGraphic() }
                    1 -> OnboardingPage(
                        title = stringResource(R.string.onboarding_scan_title),
                        text = stringResource(R.string.onboarding_scan_text)
                    ) { ScanGraphic() }
                    2 -> OnboardingPage(
                        title = stringResource(R.string.onboarding_quick_title),
                        text = stringResource(R.string.onboarding_quick_text)
                    ) { QuickGridGraphic() }
                    3 -> OnboardingPage(
                        title = stringResource(R.string.onboarding_swipe_title),
                        text = stringResource(R.string.onboarding_swipe_text)
                    ) { SwipeDemoGraphic(active = pagerState.currentPage == 3) }
                    else -> OnboardingPage(
                        title = stringResource(R.string.onboarding_notify_title),
                        text = stringResource(R.string.onboarding_notify_text)
                    ) {
                        NotificationGraphic()
                        Spacer(modifier = Modifier.height(20.dp))
                        EnableRemindersButton()
                    }
                }
            }

            // Oldaljelzo pottyok + tovabb gomb
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(PAGE_COUNT) { i ->
                        val color by animateColorAsState(
                            if (i == pagerState.currentPage) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            label = "dot"
                        )
                        Box(
                            modifier = Modifier
                                .size(if (i == pagerState.currentPage) 10.dp else 8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }
                Button(onClick = {
                    if (isLast) onFinish() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }) {
                    Text(stringResource(if (isLast) R.string.onboarding_start else R.string.onboarding_next))
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    title: String,
    text: String,
    graphic: @Composable () -> Unit
) {
    // A Box kozepre igazit, ha a tartalom elfer; kis kepernyon gorgetheto.
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { graphic() }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
    }
}

// ---------------------------------------------------------------------------
// Grafikak
// ---------------------------------------------------------------------------

@Composable
private fun WelcomeGraphic() {
    Image(
        painter = painterResource(R.mipmap.ic_launcher_art),
        contentDescription = null,
        modifier = Modifier
            .size(180.dp)
            .clip(RoundedCornerShape(44.dp))
    )
}

/** Vonalkod -> datum folyamat harom lepesben. */
@Composable
private fun ScanGraphic() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepBubble(Icons.Filled.PhotoCamera, MaterialTheme.colorScheme.primaryContainer)
        Arrow()
        StepBubble(Icons.Filled.QrCodeScanner, MaterialTheme.colorScheme.primaryContainer)
        Arrow()
        StepBubble(Icons.Filled.CalendarMonth, MaterialTheme.colorScheme.tertiaryContainer)
    }
    Spacer(modifier = Modifier.height(20.dp))
    // Mini "datum-keret", ahogy a kamerakepen latszik
    Box(
        modifier = Modifier
            .width(200.dp)
            .height(64.dp)
            .border(3.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.onboarding_scan_sample_date),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(R.string.estimate_week, R.string.action_later, R.string.action_type_date).forEach {
            SmallChip(stringResource(it))
        }
    }
}

@Composable
private fun StepBubble(icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun Arrow() {
    Text(
        "→",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

@Composable
private fun SmallChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

/** Vonalkod nelkuli gyorsracs kicsinyitve, egy "kivalasztott" csempevel. */
@Composable
private fun QuickGridGraphic() {
    val names = listOf(
        R.string.quick_bread, R.string.quick_eggs, R.string.quick_vegetables,
        R.string.quick_fruit, R.string.quick_meat, R.string.quick_leftovers
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        names.chunked(3).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { i, res ->
                    val highlighted = rowIndex == 0 && i == 0
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .width(92.dp)
                            .height(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(stringResource(res), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    MiniItemCard(
        name = stringResource(R.string.quick_bread),
        status = stringResource(R.string.onboarding_quick_sample_status),
        color = ExpirySoon
    )
}

/**
 * Animalt huzas-demo: a mintakartya balra csuszik ("Elfogyott", zold), vissza,
 * majd jobbra ("Kidobtam", piros), vegtelenitve -- csak amig az oldal latszik.
 */
@Composable
private fun SwipeDemoGraphic(active: Boolean) {
    val offset = remember { Animatable(0f) }
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxShiftPx = with(density) { (maxWidth * 0.42f).toPx() }
        LaunchedEffect(active) {
            if (!active) {
                offset.snapTo(0f)
                return@LaunchedEffect
            }
            while (true) {
                delay(700)
                offset.animateTo(-maxShiftPx, tween(900))
                delay(900)
                offset.animateTo(0f, tween(500))
                delay(600)
                offset.animateTo(maxShiftPx, tween(900))
                delay(900)
                offset.animateTo(0f, tween(500))
            }
        }
        val dx = offset.value
        Box(modifier = Modifier.fillMaxWidth()) {
            if (dx != 0f) {
                val consumed = dx < 0f
                Row(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (consumed) ExpiryFresh else ExpiryOverdue)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = if (consumed) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (consumed) Icons.Filled.Check else Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringResource(if (consumed) R.string.swipe_consumed else R.string.swipe_wasted),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
            Box(modifier = Modifier.offset { IntOffset(dx.roundToInt(), 0) }) {
                MiniItemCard(
                    name = stringResource(R.string.onboarding_swipe_sample_name),
                    status = stringResource(R.string.onboarding_swipe_sample_status),
                    color = ExpirySoon,
                    trailing = {
                        FilledTonalButton(onClick = {}, enabled = false) {
                            Text(stringResource(R.string.action_minus_one))
                        }
                    }
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(ExpiryOverdue, stringResource(R.string.onboarding_swipe_legend_right))
        LegendDot(ExpiryFresh, stringResource(R.string.onboarding_swipe_legend_left))
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

/** A lista egy tetelkartyajanak egyszerusitett masa. */
@Composable
private fun MiniItemCard(
    name: String,
    status: String,
    color: Color,
    trailing: (@Composable () -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(64.dp)
                    .background(color)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp)
            ) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.bodyMedium, color = color)
            }
            if (trailing != null) {
                Box(modifier = Modifier.padding(end = 8.dp)) { trailing() }
            }
        }
    }
}

/** Egy ertesites masa a ket akciogombbal. */
@Composable
private fun NotificationGraphic() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelMedium)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                stringResource(R.string.notification_single_title, stringResource(R.string.onboarding_swipe_sample_name)),
                style = MaterialTheme.typography.titleSmall
            )
            Text(stringResource(R.string.expiry_tomorrow), style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Row {
                Text(
                    stringResource(R.string.notification_action_consumed),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 20.dp)
                )
                Text(
                    stringResource(R.string.notification_action_snooze),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * Ertesitesek bekapcsolasa kozvetlenul a bemutatobol (Android 13+ eseten a
 * rendszer engedelykero ablakaval). Kesobb a harang ikonnal allithato.
 */
@Composable
private fun EnableRemindersButton() {
    val context = LocalContext.current
    val settings = remember { NotificationSettingsStore(context) }
    var enabled by remember { mutableStateOf(settings.enabled && NotificationHelper.hasPermission(context)) }

    fun turnOn() {
        NotificationHelper.ensureChannel(context)
        settings.enabled = true
        ReminderScheduler.schedule(context)
        enabled = true
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) turnOn()
    }

    if (enabled) {
        OutlinedButton(onClick = {}, enabled = false) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.onboarding_notify_enabled))
        }
    } else {
        FilledTonalButton(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationHelper.hasPermission(context)) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                turnOn()
            }
        }) {
            Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.onboarding_notify_enable))
        }
    }
}
